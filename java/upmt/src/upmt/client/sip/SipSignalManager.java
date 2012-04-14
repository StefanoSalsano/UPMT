package upmt.client.sip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.MessageFactory;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.tools.Log;

import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.application.manager.impl.GUIApplicationManager;
import upmt.client.core.ConfigManager;
import upmt.client.core.InterfaceInfo;
import upmt.client.core.Socket;
import upmt.client.tunnel.TunnelManager;
import upmt.os.Module;
import upmt.signaling.BaseUpmtEntity;
import upmt.signaling.UpmtSignal;
import upmt.signaling.message.ANListReq;
import upmt.signaling.message.ANListResp;
import upmt.signaling.message.AssociationReq;
import upmt.signaling.message.AssociationResp;
import upmt.signaling.message.HandoverReq;
import upmt.signaling.message.KeepAlive;
import upmt.signaling.message.SetHandoverModeReq;
import upmt.signaling.message.Signal;
import upmt.signaling.message.TunnelSetupReq;
import upmt.signaling.message.TunnelSetupResp;

public class SipSignalManager extends BaseUpmtEntity
{
	private static ConfigManager cfg = ConfigManager.instance();

	/** The sipURI that identifies this MH*/
	private String sipID;
	private int sipTunneledPort;
	private int sbcSipPort;

	public static final int CLIENT_KEEP_ALIVE_INTERVAL = cfg.keepalivePeriod;
	public static final boolean CLIENT_KEEP_ALIVE = true;
	public static final boolean KA_Console_Log = false;

	//TODO: aggiungere controlli nel signaler ke se l'interf e' nulla nn deve fare niente.
	/** The name of the network interface currently used to transport signaling traffic<BR>
	 * it is used in the handover and register procedures to set the default provider (for the handover
	 * I think this is wrong)<BR>
	 * it is used in the association procedure to set the interface that will be used by the provider
	 * specific for the association procedure<BR>
	 * in the create tunnel procedure if the interface Name corresponds to the activeInterfaceName,
	 * it creates a new socket and sends a SetHandoverModeReq with staticRule = true over this new socket
	 * in addition to the TunnelSetupReq
	 */
	private String activeInterfaceName;

	/** 
	 * it is used in the association procedure to set the interface that will be used by the provider
	 * specific for the association procedure<BR>
	 */
	private InterfaceInfo activeInterface;

	/**ANAddress -> Vipa*/
	private static Hashtable<String, String> vipaTable = new Hashtable<String, String>();
	/**
	 * hash table (ANAddress+ifName) -> ANTid<BR>
	 * hash table for remote TIDs, it is set when there is a signaling response from the AN<BR>
	 * (the local TID is stored in TunnelManager.localTidTable)
	 * 
	 */
	private static Hashtable<String, Integer> remoteTidTable = new Hashtable<String, Integer>();

	public static Hashtable<String, Integer> getRemoteTidTable() {
		synchronized(remoteTidTable){
			return remoteTidTable;
		}
	}


	private static Hashtable<String, TunnelInfo> remoteTidStatusTable = new Hashtable<String, TunnelInfo>();

	public static Hashtable<String, TunnelInfo> getRemoteTidStatusTable() {
		synchronized(remoteTidStatusTable){
			return new Hashtable<String, TunnelInfo>(remoteTidStatusTable);
		}
	}

	/** the first AN to which the client connects becomes the defaultAN
	 * MAYBE IT IS BETTER TO MOVE THIS FIELD INTO UPMTClient
	 * */

	private String defaultAN = null;
	private String activeAN = null;
	/** the SIP registration server for sending SIP registrations and becoming reachable*/
	private String registrarServer = null;

	/**
	 * hashtable (ifname -> SipProvider)<BR>
	 * stores the sipProviders used to send messages outside the tunnels
	 * maybe only one sipProvider could be enough...
	 */
	private Hashtable<String, SipProvider> associationProviders = new Hashtable<String, SipProvider>();


	private Hashtable<String, SipProvider> keepAliveProviders = new Hashtable<String, SipProvider>();


	private TunnelManager tunnelManager;
	private UPMTClient upmtClient;
	private static Thread keepAliveThread = null;
	private static Thread reconnectionThread = null;
	private boolean keepAlive = true;
	private boolean reconnection = true;


	public SipSignalManager(String file, TunnelManager tunnelManager, UPMTClient client)
	{
		super(file, new SipProvider(Module.getVipaFix(), cfg.sipTunneledPort, SipStack.default_transport_protocols, Module.getVipaFix()));

		//Setting from file
		this.sipID = cfg.sipID;
		this.sipTunneledPort = cfg.sipTunneledPort;
		this.sbcSipPort = cfg.sbcSipPort;

		this.tunnelManager = tunnelManager;
		this.upmtClient = client;
		if (!SipStack.isInit()) SipStack.init();


		// KeepAlive Thread 
		keepAliveThread = new Thread("KeepAliveThread"){
			public void run(){

				System.err.println("KeepAlive activated with KeepAlive period: " + CLIENT_KEEP_ALIVE_INTERVAL);

				while(keepAlive){

					try {
						Thread.sleep(CLIENT_KEEP_ALIVE_INTERVAL);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if(KA_Console_Log)
						System.out.println(">>> KeepAlive THREAD activated <<<");

					HashMap<String, Integer> remoteTidTableCopy = null;
					synchronized(remoteTidTable){
						remoteTidTableCopy = new HashMap<String, Integer>(remoteTidTable);
					}

					// tunnel keepalive
					for(String ANAddressIF : remoteTidTableCopy.keySet()){
						String[] splittedKey = ANAddressIF.split(":");

						boolean existingAndNotDeadTunnel = false;

						synchronized(remoteTidStatusTable){
							existingAndNotDeadTunnel = remoteTidStatusTable.get(ANAddressIF) != null && remoteTidStatusTable.get(ANAddressIF).getStatus() != TunnelInfo.CLOSED;
						}

						//System.out.println("Tunnel " + ANAddressIF + " " + existingAndNotDeadTunnel);

						if(splittedKey.length == 2 && existingAndNotDeadTunnel){
							keepAlive(splittedKey[0], splittedKey[1]);
						}

						if(KA_Console_Log)
							System.out.println(">>> KeepAlive THREAD stopped <<<");
					}
				}

			}

		}; // KeepAlive Thread END		


		// Reconnection Thread 
		reconnectionThread = new Thread("ReconnectionThread"){
			public void run(){

				ArrayList<String> connectedIFs = new ArrayList<String>();
				HashMap<String, Integer> deadANs = new HashMap<String, Integer>();

				while(reconnection){
					try {
						Thread.sleep(CLIENT_KEEP_ALIVE_INTERVAL);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if(KA_Console_Log)
						System.out.println(">>> Reconnection THREAD activated <<<");


					for(String ANAddress : UPMTClient.getCfgANList()){

						String ANip = ANAddress.split(":")[0];
						int tunnels = 0;
						int disconnectedTunnels = 0;
						connectedIFs.clear();

						synchronized(remoteTidStatusTable){

							for(String ANStatusTableKey : remoteTidStatusTable.keySet()){

								if(ANStatusTableKey.startsWith(ANip)){
									tunnels++;

									if(remoteTidStatusTable.get(ANStatusTableKey).getStatus() == TunnelInfo.CLOSED){
										disconnectedTunnels++;
									}
									else{
										connectedIFs.add(ANStatusTableKey.split(":")[1]);
									}
								}
							}
						}

						Vector<String> iFs = upmtClient.getInterfacesList();

						if(KA_Console_Log)
							System.out.println("AN: " + ANip + " tunnels: " + tunnels + " disconnected " + disconnectedTunnels );

						if((tunnels == disconnectedTunnels) || 
								(upmtClient.getAssociatedANList().size() < upmtClient.getMaxANNumber() &&
										!upmtClient.getAssociatedANList().keySet().contains(ANip) && 
										tunnels == 0)){
							removeAn(ANip); // remove vipa and if necessary update the defaultAN
							if(!upmtClient.removeAssociatedANAnTryReconnect(ANip)){//remove association, try to recreate it and try to recreate all respective tunnels
								if(deadANs.get(ANip) == null)
									deadANs.put(ANip, 1);
								else{
									int score = deadANs.get(ANip);
									deadANs.put(ANip, score + 1);
								}
							}
							else
								deadANs.put(ANip, 0);

						}


						if(upmtClient.getAssociatedANList().keySet().contains(ANip) && iFs.size() != connectedIFs.size()){
							for(String interf : iFs){
								if(!connectedIFs.contains(interf)){
									System.err.println("Single tunnel regeneration for "+  ANip +":"+UPMTClient.getAssociatedANs().get(ANip) + " on interface " + interf);
									upmtClient.tunnelSetup(interf, ANip, UPMTClient.getAssociatedANs().get(ANip));
								}
							}
						}

					}

					//TODO deactivated for performance evaluation
					//upmtClient.downloadANList();

					//#ifndef ANDROID
					if(!((GUIApplicationManager)upmtClient.getGui()).getActiveTabName().contains("Applications"))
						((GUIApplicationManager)upmtClient.getGui()).refreshGui();
					//#endif

					boolean connected = false;

					synchronized(remoteTidStatusTable){
						for(String tid : remoteTidStatusTable.keySet())
							if(remoteTidStatusTable.get(tid).getStatus() != TunnelInfo.CLOSED){
								connected = true;
								break;
							}	
					}

					if(KA_Console_Log)
						System.out.println("Client status: " + (connected ? "CONNECTED!":"DISCONNECTED!"));
					//#ifndef ANDROID
					if(connected)
						upmtClient.updateMsg("Up and running.");
					else
						upmtClient.updateMsg("Client is disconnected!");
					//#endif

					
					//uncomment for a primitive elimination of ANs with many disconnections
					/*
					for(String ANip: deadANs.keySet()){
						System.out.println("ANip: " + ANip + " failed reassociation: " + deadANs.get(ANip));
						if(deadANs.get(ANip) > 3)
							upmtClient.cleanDeadANs(ANip);
						continue;
					}
					 */

					if(KA_Console_Log)
						System.out.println(">>> Reconnection THREAD stopped <<<");
				}
			}
		};	// Reconnection Thread END

	}


	public String[] ANListReq(String ANAddress, int anSipPort, String ifName, InterfaceInfo ifInfo, int depth) {

		//printLog("ANListReq to " + ANAddress + ":" + anSipPort + " using " + ifName + " depth " + depth, Log.LEVEL_MEDIUM);

		depth--;

		if(!associationProviders.containsKey(ifName)) {
			associationProviders.put(ifName,
					new SipProvider(ifInfo.ipAddress, SipStack.default_port, SipStack.default_transport_protocols, ifInfo.ipAddress));
		}
		SipProvider provider = associationProviders.get(ifName);
		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress+":"+ anSipPort);
		NameAddress sipIdName = new NameAddress(sipID);
		ANListReq reqSignal = new ANListReq();
		reqSignal.sipId = sipID;
		Message message = newUpmtMessage(provider, anchorNode, sipIdName, reqSignal);

		//printOverheadLog(message.toString(), true, false, "");

		//printLog("SENDING OUT MESSAGE:" + UpmtSignal.serialize(reqSignal), Log.LEVEL_HIGH);

		Message respMessage = startBlockingTransaction(provider, message);

		//printOverheadLog(respMessage.toString(), false, false, "");


		if(respMessage == null || isNull(respMessage)){
			printLog("No response from " + ANAddress);
			return null;
		}

		ANListResp resp = (ANListResp)UpmtSignal.deSerialize(respMessage.getBody())[0];

		if(resp.isBroker()){
			for(int i = 0; i < resp.anList.length; i++)
				printLog("Received AN: " + resp.anList[i] + " from " + ANAddress);
			return resp.anList;
		}
		else if(!resp.isBroker() && depth >= 0){
			if(resp.anList != null && resp.anList.length > 0)
				return ANListReq(resp.anList[0].split(":")[0], Integer.parseInt(resp.anList[0].split(":")[1]), ifName, ifInfo, depth);
		}

		return null;
	}


	/**
	 * locally moves the tunneled SIP signaling socket into the tunnel towards the AN identified
	 * by ANaddress over the interface identified by ifName<BR> 
	 * this means that future handover messages will be sent over this interface
	 * @param ANAddress
	 * @param ifName
	 */
	private void moveProviderToTunnel(String ANAddress, String ifName) {
		// all udp messages containing SIP UPMT signaling (i.e. from local port for signaling into the tunnel
		// toward ANAddress:AN_port_for_signaling) will go into a specific tunnel (i.e. the interface is chosen)
		tunnelManager.assignSocketToTunnel("udp", sipTunneledPort, ANAddress, sbcSipPort, ANAddress, ifName);
		//forces the sip_provider for UPMT signaling to use the AN as outbound proxy
		sip_provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));
	}

	/**
	 * It corresponds to the initial association. A out-of-tunnel message is sent and VIPA and tsaPost are obtained.<br>
	 * if blocking is true this method is blocking: it does not exit until the association has been completed
	 */
	public boolean signalAssociationOnIf(boolean blocking, String ANAddress, String ifName, InterfaceInfo ifInfo) {

		printLog("Starting association procedure for \"" + sipID + "\" to " + ANAddress + " using " + ifName, Log.LEVEL_MEDIUM);
		if(!associationProviders.containsKey(ifName)) { //ma se ne puo' usare uno solo???
			associationProviders.put(ifName,
					//creates the provider used to send message outside the tunnels
					new SipProvider(ifInfo.ipAddress, SipStack.default_port, SipStack.default_transport_protocols, ifInfo.ipAddress));
		}
		//this is the provider used to send messages outside the tunnels
		SipProvider provider = associationProviders.get(ifName);
		//TODO may be we can change it and associate the outboundproxy to the message?? 
		//the problem is that we have a single provider per each interface, but we could have
		//different Anchor nodes reachable through the same interface
		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress+":"+ upmtClient.getANSipPort(ANAddress));
		NameAddress sipIdName = new NameAddress(sipID);
		AssociationReq reqSignal = new AssociationReq(sipID, null, null); //#Mettere gli altri parametri
		Message message = newUpmtMessage(provider, anchorNode, sipIdName, reqSignal);
		//printLog("\n=======================================\n"+ message.toString() + "\n=======================================\n\n", Log.LEVEL_HIGH);


		//DoTransaction
		if (blocking) {
			return associationResp(startBlockingTransaction(provider, message), ANAddress, ifName);
		} else {
			//startUnblockingTransaction(provider, message, getMethod("associationResp", Message.class, String.class, Integer.class), ANAddress, tsaPort);}
			startNonBlockingTransaction(provider, message, "associationResp", ANAddress, ifName);
		}

		return false;
	}


	/**
	 * OLD VERSION - It corresponds to the initial association. A out-of-tunnel message is sent and a VIPA is obtained.<br>
	 * if registrarServer is null this method is blocking: it does not exit until the association has been completed
	 */
	public void signalAssociation(String ANAddress, int tsaPort) {

		// I think we should select one of the available interfaces and try to send the message
		// using this selected interface, if it does not work we should move to the next available interface, just in case...
		printLog("Starting association procedure for \"" + sipID + "\" to " + ANAddress + " using " + activeInterfaceName, Log.LEVEL_MEDIUM);
		if(!associationProviders.containsKey(activeInterfaceName)) { //ma se ne puo' usare uno solo???
			associationProviders.put(activeInterfaceName,
					//creates the provider used to send message outside the tunnels
					new SipProvider(activeInterface.ipAddress, SipStack.default_port, SipStack.default_transport_protocols, activeInterface.ipAddress));
		}
		//this is the provider used to send message outside the tunnels
		SipProvider provider = associationProviders.get(activeInterfaceName);
		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress+":" + upmtClient.getANSipPort(ANAddress));
		NameAddress sipIdName = new NameAddress(sipID);
		AssociationReq reqSignal = new AssociationReq(sipID, null, null); //#Mettere gli altri parametri
		Message message = newUpmtMessage(provider, anchorNode, sipIdName, reqSignal);

		//DoTransaction
		if (registrarServer == null) {
			associationResp(startBlockingTransaction(provider, message), ANAddress, tsaPort);
		} else {
			//startUnblockingTransaction(provider, message, getMethod("associationResp", Message.class, String.class, Integer.class), ANAddress, tsaPort);}
			startNonBlockingTransaction(provider, message, "associationResp", ANAddress, new Integer(tsaPort));
		}

	}

	/** 
	 * Handler for the response to the association Message
	 * @param resp
	 * @param ANAddress
	 * @param sipPort
	 */
	public boolean associationResp(Message resp, String ANAddress, String ifName) {


		//RespMessage handling
		//		boolean failure = true;

		InterfaceInfo ifInfo = UPMTClient.getAvailableIfs().get(ifName);
		if (ifInfo==null) {
			printLog("[SipSignalManager.associationResp] ERROR: no ifInfo for  " + ifName, Log.LEVEL_MEDIUM);
			return false;
		}
		boolean associationSuccess=false;
		String vipa = null;
		int tsa = -1;
		String reason = null;

		if (resp==null || isNull(resp)) {

		} 
		else {
			String msgBody = resp.getBody();
			if (msgBody==null){
			} 
			else {
				Signal[] sigArray = UpmtSignal.deSerialize(msgBody);
				if (sigArray==null){
				} 
				else {
					AssociationResp respSignal = (AssociationResp) sigArray[0];

					vipa = respSignal.vipa;
					tsa = respSignal.tsa;
					reason = respSignal.reason;
					associationSuccess= respSignal.associationSuccess;
					if(vipa!=null) {
						//						failure = false;
					}
				}
			}
		}
		if (!associationSuccess) {

			ifInfo.setANIFStatus(ANAddress,InterfaceInfo.IFANFailure);
			printLog("Association procedure to " + ANAddress + " on interface " + ifName +" failed! NO VIPA OBTAINED!!!\n" + "reason: " + reason, Log.LEVEL_MEDIUM);
			return false;
		}

		ifInfo.setANIFStatus(ANAddress,InterfaceInfo.IFANOK);

		String existingVipa = vipaTable.get(ANAddress);
		if (existingVipa != null) {
			printLog("VIPA already existing for " + ANAddress);
			return false;
		} 
		else {
			vipaTable.put(ANAddress, vipa);
			UPMTClient.addAssociatedAN(ANAddress, tsa);

			//the first AN that is contacted becomes the default AN
			if(defaultAN == null) {
				defaultAN = ANAddress;
			}
			printLog("Association procedure successful! Obtained VIPA: "+vipa+" and tsa: " + tsa + " from AN " + ANAddress, Log.LEVEL_MEDIUM);
		}

		//it does not create automatically tunnels
		//newANlistener.endAddAN(ANAddress, tsaPort);
		return true;
	}

	/** 
	 * OLD VERSION Handler for the response to the association Message
	 * @param resp
	 * @param ANAddress
	 * @param tsaPort
	 */
	public void associationResp(Message resp, String ANAddress, Integer tsaPort) {

		//RespMessage handling
		if (isNull(resp)) {
			printLog("OLD VERSION Association procedure to " + ANAddress + " terminated! NO VIPA OBTAINED!!!\n", Log.LEVEL_MEDIUM);
			return;
		}
		AssociationResp respSignal = (AssociationResp) UpmtSignal.deSerialize(resp.getBody())[0];
		String vipa = respSignal.vipa;
		if(vipa==null) {
			printLog("Association procedure to " + ANAddress + " terminated! NO VIPA OBTAINED!!!\n", Log.LEVEL_MEDIUM);
			return;
		}
		vipaTable.put(ANAddress, vipa);
		if(defaultAN == null) {
			defaultAN = ANAddress;
		}
		printLog("Association procedure terminated! Obtained VIPA: "+vipa+"\n", Log.LEVEL_MEDIUM);
		upmtClient.endAddAN(ANAddress, tsaPort);
	}


	/** currently, it is blocking :-( */
	public int createTunnel(String ifName, String ANAddress) {
		//in order to create a tunnel over the interface, we switch the signaling socket so
		//that SIP messages are sent on that interface 
		//this may have side effects as all SIP signaling will be sent through this interface
		//but we still do not know if it is possible to send to the AN on this interface
		//may be we should use another socket (e.g. changing the UDP source port?)
		moveProviderToTunnel(ANAddress, ifName);
		printLog("Creating tunnel from " + ifName + " to " + ANAddress, Log.LEVEL_MEDIUM);

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+upmtClient.getANSipPort(ANAddress));
		NameAddress from = new NameAddress("sip:" + vipaTable.get(ANAddress));//TODO: #come va costruito qsto identificativo?
		TunnelSetupReq req = new TunnelSetupReq(sipID, sipTunneledPort, vipaTable.get(ANAddress));
		Signal[] reqList;
		if(ifName.equals(activeInterfaceName)) {
			//if the interface Name corresponds to the activeInterfaceName, it creates a new socket
			//and sends a SetHandoverModeReq with staticRule = true over this new socket in addition to the TunnelSetupReq
			Socket socket = new Socket("udp", Module.getVipaFix(), ANAddress, sipTunneledPort, sbcSipPort);
			reqList = new Signal[]{req, new SetHandoverModeReq(sipID, socket, vipaTable.get(ANAddress), true)};
		} else {
			reqList = new Signal[]{req};
		}
		Message message = newUpmtMessage(anchorNode, from, reqList);
		//System.out.println("messaggio:"+message);

		//printLog("SENDING OUT MESSAGE:"+UpmtSignal.serialize(reqList), Log.LEVEL_HIGH);
		//printLog("\n=======================================\n"+ message.toString() + "\n=======================================\n\n", Log.LEVEL_HIGH);
		//DoTransaction
		Message respMessage = startBlockingTransaction(message);



		printLog("Tunnel from " + ifName + " to " + ANAddress + (isNull(respMessage)?" was NOT created":" created succesfully!"), Log.LEVEL_MEDIUM);
		if(isNull(respMessage)) {
			return 0;
		}

		//RespMessage handling
		TunnelSetupResp resp = (TunnelSetupResp)UpmtSignal.deSerialize(respMessage.getBody())[0];
		synchronized(remoteTidTable){
			remoteTidTable.put(ANAddress+":"+ifName, resp.serverTunnelID);
		}
		synchronized(remoteTidStatusTable){
			TunnelInfo oldTInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
			if(oldTInfo == null)
				remoteTidStatusTable.put(ANAddress+":"+ifName, new TunnelInfo(TunnelInfo.TUNNEL_SETUP, resp.yourAddress, resp.yourPort, 0, 0));
			else
				remoteTidStatusTable.put(ANAddress+":"+ifName, new TunnelInfo(TunnelInfo.TUNNEL_SETUP, resp.yourAddress, resp.yourPort, oldTInfo.getEWMA_Delay(), oldTInfo.getEWMA_Loss()/100));			
		}
		printLog("ServerTunnelID: " + resp.serverTunnelID + "\nMy nat address: " + resp.yourAddress+":"+resp.yourPort+"\n");
		return resp.serverTunnelID;
	}	

	public void startKeepALiveThread(){
		if(CLIENT_KEEP_ALIVE){
			if(!keepAliveThread.isAlive())
				keepAliveThread.start();
			if(!reconnectionThread.isAlive())
				reconnectionThread.start();
		}
	}

	private void keepAlive(String ANAddress, String ifName) {

		boolean wait = false;

		synchronized(remoteTidStatusTable){
			wait = remoteTidStatusTable.get(ANAddress+":"+ifName).isWaitingForKA();
		}

		if(wait){
			System.err.println("Waiting for previous keepalive to return on " + ANAddress + ":" + ifName + "");
			return;
		}

		SipProvider provider =  null;

		synchronized(keepAliveProviders){
			int port = sipTunneledPort;

			for(String key : keepAliveProviders.keySet()){
				if(keepAliveProviders.get(key).getPort() >= port)
					port = keepAliveProviders.get(key).getPort();
			}

			port++;

			if(!keepAliveProviders.containsKey(ifName)) {
				keepAliveProviders.put(ifName, 	new SipProvider(Module.getVipaFix(), port, SipStack.default_transport_protocols, Module.getVipaFix()));
			}

			provider = keepAliveProviders.get(ifName);
		}

		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));

		int tid = getAnTid(ANAddress, ifName);
		if(tid == -1)
			return;

		tunnelManager.assignSocketToTunnel("udp", provider.getPort(), ANAddress, sbcSipPort, ANAddress, ifName);

		//#ifndef ANDROID
		if(tunnelManager.getTid(ifName, ANAddress) != -1)
			upmtClient.forceRuleByTheInside("java", tunnelManager.getTid(ifName, ANAddress));
		//#else
		// if(tunnelManager.getTid(ifName, ANAddress) != -1)
		// upmtClient.forceRuleByTheInside("com.and.gui", tunnelManager.getTid(ifName, ANAddress));
		//#endif

		printLog("KeepAlive for remote tunnel " + tid + " on " + ANAddress + ":" + ifName + " from signaling port: " + provider.getPort() + " on tunnel " + tunnelManager.getTid(ifName, ANAddress));		

		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+ upmtClient.getANSipPort(ANAddress));
		NameAddress from = new NameAddress("sip:" + vipaTable.get(ANAddress));
		KeepAlive req = new KeepAlive(sipID, tid, provider.getPort());
		Signal[] reqList;
		reqList = new Signal[]{req};

		Message message = newUpmtMessage(anchorNode, from, reqList);
		//System.out.println("messaggio:"+message);

		//printSimplifiedOverheadLog(message.toString(), ifName);

		//printLog("SENDING OUT MESSAGE:"+UpmtSignal.serialize(reqList), Log.LEVEL_HIGH);

		synchronized(remoteTidStatusTable){
			remoteTidStatusTable.get(ANAddress+":"+ifName).setWaitingForKA(true);
		}

		startNonBlockingTransaction(provider, message, "keepAliveResp", ANAddress, ifName, tid, new Integer(-1), new Integer(-1));
	}


	public String keepAlivePortToKASignalerInterface(int port){
		String iFace = null;

		synchronized(keepAliveProviders){
			for(String key : keepAliveProviders.keySet()){
				if(keepAliveProviders.get(key).getPort() == port)
					iFace = key;
			}
		}

		return iFace;
	}


	public boolean keepAliveResp(Message resp, String ANAddress, String ifName, Integer tid, Integer delay, Integer numberRetry) {

		//printSimplifiedOverheadLog(resp.toString(), ifName);

		if(resp != null && resp.getStatusLine() != null)
			if(KA_Console_Log)
				printLog("Tunnel " + tid + " from " + ANAddress + ":" + ifName + " KeepAlive sip response is " + resp.getStatusLine().toString().replaceAll("\n", ""), Log.LEVEL_LOW);

		boolean res = false;

		synchronized(remoteTidStatusTable){

			TunnelInfo tInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
			tInfo.setWaitingForKA(false);

			if(resp.getStatusLine() == null) {
				if( tInfo != null && remoteTidStatusTable.get(ANAddress+":"+ifName).getStatus() == TunnelInfo.TUNNEL_SETUP){
					tInfo.setStatus(TunnelInfo.CLOSED);
					remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);

					printLog("Tunnel " + tid + " from " + ANAddress + ":" + ifName + " changes state to CLOSED");
					tunnelManager.removeTunnel(ifName, ANAddress);

					res = false;
				}
			}


			if(resp.getStatusLine() != null){
				if(resp.getStatusLine().toString().contains("200 OK")) {
					tInfo.setStatus(TunnelInfo.TUNNEL_SETUP);
					tInfo.setLastDelayAndNumberRetry(delay, numberRetry);
					remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);
					res = true;
				}
				else{
					tInfo.setStatus(TunnelInfo.CLOSED);
					remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);

					printLog("Tunnel " + tid + " from " + ANAddress + ":" + ifName + " changes state to CLOSED");
					tunnelManager.removeTunnel(ifName, ANAddress);

					res = false;
				}
			}
		}

		return res;
	}



	/** 
	 * perform Sip registration through the default AN in order to become reachable from external sip agent.<BR>
	 * it is NON-blocking
	 */
	public void register() {
		printLog("Starting SIP register procedure for " + sipID, Log.LEVEL_MEDIUM);
		//TODO I am not convinced that we should change the default provider, in order to deliver a
		//message to a given AN
		moveProviderToTunnel(defaultAN, activeInterfaceName);

		//ReqMessage creation
		//TODO to, from and contact are wrong hereafter !!!
		//to and from should be the same !! may be also the contact...
		String registrarAddress = new SipURL(sipID).getHost();
		NameAddress anchorNode = new NameAddress("sip:" + registrarAddress + ":"+SipStack.default_port);
		NameAddress from = new NameAddress("sip:" + vipaTable.get(defaultAN));//TODO: #come va costruito qsto identificativo?
		NameAddress sipIdName = new NameAddress(sipID);
		Message message = MessageFactory.createRegisterRequest(sip_provider, null, anchorNode, from, sipIdName);

		//DoTransaction
		startNonBlockingTransaction(message, "registrationResp", registrarAddress);
	}





	/**
	 * NB it is called back by a NonBlockingTransaction using Method by name
	 * @param resp
	 * @param ANAddress
	 */
	public void registrationResp(Message resp, String ANAddress) {

		//RespMessage handling
		printLog("SIP Registration to " + ANAddress + (isNull(resp)?" ERROR!!!":" done succesfully!"), Log.LEVEL_MEDIUM);
		if (!isNull(resp)) {
			registrarServer = ANAddress;
		}
	}

	public void handover(Socket socket, String ANAddress, String ifName) {
		moveProviderToTunnel(ANAddress, activeInterfaceName);
		//printLog("Handover of " + socket.id() + " for AN=" + ANAddress + " to interface: " + ifName, Log.LEVEL_MEDIUM);

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+upmtClient.getANSipPort(ANAddress));
		NameAddress from = new NameAddress("sip:" + vipaTable.get(ANAddress));//TODO: #come va costruito qsto identificativo?
		int tun = -1;
		synchronized(remoteTidTable) {
			tun = remoteTidTable.get(ANAddress+":"+ifName);
		}
		HandoverReq req = new HandoverReq(sipID, socket, vipaTable.get(ANAddress), tun);
		Message message = newUpmtMessage(anchorNode, from, req);

		System.out.println("Sending handover message for tunnel " + tun);

		//DoTransaction
		startNonBlockingTransaction(message, "handoverResp", socket, ifName, ANAddress);

	}
	public void handoverResp(Message resp, Socket socket, String ifName, String ANAddress) {

		//RespMessage handling
		//printLog("Handover of "+socket.id()+" for AN="+ANAddress+" to interface: "+ifName+(isNull(resp)?" ERROR!!!":" done succesfully!"),Log.LEVEL_MEDIUM);
	}

	/**
	 * the signaler is initialized here
	 * @param client
	 */
	public void startListen(UPMTClient client) {
		if (defaultAN!=null){
			register();
		}

		//TODO: vedere se serve una classe listener... senno' nn ha senso passare il client qui' xke'
		//lo faccio gia' nel costruttore (e mi serve x l'associazione)
		//this.newANlistener = client;
		//Allo start listen si fa la registrazione! (xke' se nn acolti nn ha senso registrarsi!)
		//il sip registrato dovrebbe stare in ascolto per la rikiesta ICE
		//e fare da server per la rikiesta di un vipa
		//aggiungere un eventuale listener al sipprovider(del tunnel)
	}

	public void stop()
	{
		if(keepAliveThread.isAlive()) {
			//keepAliveThread.interrupt();
			keepAlive = false;
			System.out.println("KeepAliveThread Stopped");
		}
		if(reconnectionThread.isAlive()) {
			//reconnectionThread.interrupt();
			reconnection = false;
			System.out.println("ReconnectionThread Stopped");
		}
	}

	protected AssociationResp handleAssociation(AssociationReq req) {
		return null;
	}

	/**
	 * returns true if there is a tunnel setup toward the tunnel server
	 * over a given interface; it uses the information in the remoteTidStatusTable 
	 * @param tunnelServer
	 * @param ifName
	 * @return
	 */
	public static boolean canUseTunnel (String tunnelServer, String ifName) {

		TunnelInfo myStatus = null;

		synchronized(remoteTidStatusTable){
			myStatus = remoteTidStatusTable.get(tunnelServer+":"+ifName);
		}

		if (myStatus == null) {
			return false;
		}
		if (myStatus.getStatus() == TunnelInfo.TUNNEL_SETUP) {
			return true;
		} else {
			return false;
		}
	}




	/** 
	 * Change the active default network interface for in-tunnel signaling
	 * and performs the registration so that incoming SIP request will arrive<BR>
	 * ifName and ifInfo could be null, if there is no available interface for signaling
	 * @param ifName unique identifier of the selected network interface
	 */
	public void changeDefaultInterface(String ifName, InterfaceInfo ifInfo) {
		activeInterfaceName = ifName;
		activeInterface = ifInfo;

		if (activeInterfaceName!=null) {
			if (registrarServer!=null) {
				register();
			}
		}
	}

	public void removeAn(String ANAddress)//TODO: controllare ke succede se si rimuove l'ultimo.
	{
		String oldVipa = vipaTable.remove(ANAddress);
		//System.out.println("Removing AN " + ANAddress + " VIPA " + oldVipa);
		if (defaultAN != null && defaultAN.equals(ANAddress)) {

			String newDefaultAN = null;

			if(vipaTable.keySet().iterator().hasNext())
				newDefaultAN = vipaTable.keySet().iterator().next();

			System.out.println("NEW DEFAULT AN: " + newDefaultAN);

			if(newDefaultAN != null) { // ??? Insert by Valerio ----> It has to check if it is null otherwise The SipSignalManager's ThreadReconnection crashes
				defaultAN = newDefaultAN;
				/*TODO experimental: needed to change Marco Trenca's modifications about
				* defaultAN selection, fault tolerance and per-AN policies
				*/
				upmtClient.updateDefaultAN();
			}
		}
	}

	public String getActiveAN() {return activeAN;}
	public String getDefaultAN() {return defaultAN;}
	public String getSipID() {return sipID;}
	public static String getVipaForAN(String ANAddress) {return vipaTable.get(ANAddress);}
	public int getAnTid(String ANAddress, String ifName) {
		int res = -1;
		synchronized(remoteTidTable){
			res = remoteTidTable.get(ANAddress+":"+ifName);
		}
		return res;
	}

	protected void printLog(String str, int level){System.out.println("[SipSignalManager]: "+str); super.printLog("UPMTServer: "+str, level);}
	protected void printLog(String str){printLog(str,0);}

	//	static void logSignals (Signal[] sigVector){
	//		System.out.println(UpmtSignal.serialize(sigVector));
	////		for (int i=0; i<sigVector.length; i++){
	////			System.out.println(sigVector[i].toString());
	////		}
	//	}
}
