package upmt.client.sip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.MessageFactory;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.tools.Log;

import android.R.string;
import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.application.manager.impl.GUIApplicationManager;
import upmt.client.core.ConfigManager;
import upmt.client.core.InterfaceInfo;
import upmt.client.core.Socket;
import upmt.client.rme.RoutingCheck;
import upmt.client.tunnel.TunnelManager;
import upmt.os.Module;
import upmt.server.rme.RMEServer;
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
	public static final boolean CLIENT_KEEP_ALIVE = (!cfg.keepaliveKernel);
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
	
	public ArrayList<String> currentAssociations = new ArrayList<String>();

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

	/**
	 * hashtable (ifname -> SipProvider) 
	 * stores the sipProviders used to send KA messages towards any AN for each given interface
	 */
	private Hashtable<String, SipProvider> keepAliveProviders = new Hashtable<String, SipProvider>();


	private TunnelManager tunnelManager;
	private UPMTClient upmtClient;
	private static Thread keepAliveThread = null;
	private static Thread reconnectionThread = null;
	private boolean keepAlive = (!cfg.keepaliveKernel);
	private boolean reconnection = true;


	public SipSignalManager(String file, TunnelManager tunnelManager, UPMTClient client)
	{
		super(file, new SipProvider(Module.getVipaFix(), cfg.sipTunneledPort, SipStack.default_transport_protocols, Module.getVipaFix()));

		//Setting from file
		//		this.sipID = cfg.sipID;

		if(UPMTClient.coreEmulator) {
			// concatenating hostname with sip usersname (Sander)
			try {
				this.sipID = InetAddress.getLocalHost().getHostName()+"_"+cfg.sipID;
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				this.sipID=cfg.sipID;
				e1.printStackTrace();
			}
		}
		else if(UPMTClient.getRME()) {
			this.sipID = cfg.vepa+"@"+cfg.vepa;
		}
		else {
//			this.sipID = cfg.vepa+"@"+cfg.vepa;
			this.sipID=cfg.sipID;
		}
		
		this.sipTunneledPort = cfg.sipTunneledPort;
		this.sbcSipPort = cfg.sbcSipPort;

		this.tunnelManager = tunnelManager;
		this.upmtClient = client;
		if (!SipStack.isInit()) SipStack.init();


		// KeepAlive Thread 
		keepAliveThread = new Thread("KeepAliveThread"){
			public void run(){

				printLog("KeepAlive activated with KeepAlive period: " + CLIENT_KEEP_ALIVE_INTERVAL);

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

					for(String ANAddressIF : remoteTidTableCopy.keySet()){
						String[] splittedKey = ANAddressIF.split(":");

						boolean existingAndNotDeadTunnel = false;

						synchronized(remoteTidStatusTable){
							existingAndNotDeadTunnel = (remoteTidStatusTable.get(ANAddressIF) != null && remoteTidStatusTable.get(ANAddressIF).getStatus() != TunnelInfo.CLOSED);
						}

						if(splittedKey.length == 2 && existingAndNotDeadTunnel) {
							keepAlive(splittedKey[0], splittedKey[1]);
						}
						else {
							synchronized (remoteTidStatusTable) {
								if(remoteTidStatusTable.containsKey(splittedKey[0] + ":" + splittedKey[1])) {
									remoteTidStatusTable.get(splittedKey[0] + ":" + splittedKey[1]).setWaitingForKA(false);
								}
							}
						}

						if(KA_Console_Log)
							printLog(">>> KeepAlive THREAD stopped <<<");
					}
				}

			}

		}; // KeepAlive Thread END		


		// Reconnection Thread 
		reconnectionThread = new Thread("ReconnectionThread"){
			public void run(){

				ArrayList<String> connectedIFs = new ArrayList<String>();
				HashMap<String, Integer> deadANs = new HashMap<String, Integer>();
				Vector<String> anListCopy;

				while(reconnection){
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if(KA_Console_Log)
						System.out.println(">>> Reconnection THREAD activated <<<");


					synchronized (UPMTClient.getCfgANList()) {
						anListCopy = new Vector<String>(UPMTClient.getCfgANList());
					}
					
//					for(String ANAddress : UPMTClient.getCfgANList()) {
					for(String ANAddress : anListCopy) {

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
							if(!UPMTClient.getRME()) {
								removeAn(ANip); // remove vipa and if necessary update the defaultAN
							}
							if(!upmtClient.removeAssociatedANAnTryReconnect(ANip)){//remove association, try to recreate it and try to recreate all respective tunnels
								if(deadANs.get(ANip) == null)
									deadANs.put(ANip, 1);
								else{
									int score = deadANs.get(ANip);
									deadANs.put(ANip, score + 1);
								}
							}
							else {
								deadANs.put(ANip, 0);
							}
						}
						else {
							if(upmtClient.getAssociatedANList().keySet().contains(ANip) && iFs.size() != connectedIFs.size()) {
								if(UPMTClient.getRME()) {
									if(upmtClient.getOlsrDetectedEndPoint().contains(ANip)) {
										for(String interf : iFs) {
											if(!connectedIFs.contains(interf)){
												Iterator<String> iter = upmtClient.getRmeTunnelIpInterfaceList().keySet().iterator();
												synchronized (upmtClient.getRmeTunnelIpInterfaceList()) {
													while(iter.hasNext())  {
														String an = iter.next();
														if(UPMTClient.crossTunnel) {
															if(!TunnelManager.getTidTable().containsKey(interf+":"+an)) {
																upmtClient.createRmeTunnels(an, interf);
																upmtClient.createRmeSingleTunnel(an, interf);
																connectedIFs.add(interf);													
																
															}
															else if(TunnelManager.getTidTable().get(interf+":"+an) == UPMTClient.TID_DROP) {
																upmtClient.createRmeTunnels(an, interf);
																connectedIFs.add(interf);
															}
														}
														else {
															if(!TunnelManager.getTidTable().containsKey(interf+":"+an)) {
																if(upmtClient.getRmeDirectTunnels().contains(an+":"+interf)) {
																	upmtClient.createRmeSingleTunnel(an, interf);
																	connectedIFs.add(interf);
																}
															}
															else if(TunnelManager.getTidTable().get(interf+":"+an) == UPMTClient.TID_DROP) {
																if(upmtClient.getRmeDirectTunnels().contains(an+":"+interf)) {
																	upmtClient.createRmeTunnels(an, interf);
																	connectedIFs.add(interf);
																}
															}
														}
													}
												}
											}
										}
									}
								}
								else {
									for(String interf : iFs) {
										if(!connectedIFs.contains(interf)){
											printLog("Single tunnel regeneration for "+  ANip +":"+UPMTClient.getAssociatedANs().get(ANip) + " on interface " + interf, Log.LEVEL_HIGH);
											upmtClient.tunnelSetup(interf, ANip, UPMTClient.getAssociatedANs().get(ANip));
										}
									}
								}
							}
						}
					}
					
//					if(UPMTClient.crossTunnel && UPMTClient.getRME()) {
//						Iterator<String> iterIFs = UPMTClient.getRMEInterfacesList().iterator();
//						while(iterIFs.hasNext()) {
//							String iface = iterIFs.next();
//							Iterator<String> iterAn = upmtClient.getOlsrDetectedEndPoint().iterator();
//							while(iterAn.hasNext()) {
//								String anIp =  iterAn.next();
//								System.err.println("interfaccia: "+iface+" indirizzoIP: "+anIp);
//								if(!TunnelManager.getTidTable().containsKey(iface+":"+anIp)) {
//									if(upmtClient.getRoutingCheck().isCrossTunnelAvailable(anIp, iface)) {
//										System.err.println("nuova riconnessione per ---> "+iface+":"+anIp);
//										upmtClient.createRmeTunnels(anIp, iface);
//										upmtClient.createRmeSingleTunnel(anIp, iface);
//										connectedIFs.add(iface);
//									}
//								}
//							}
//						}
//					}

					//#ifndef ANDROID
					if(!UPMTClient.textMode) {
						if(!((GUIApplicationManager)upmtClient.getGui()).getActiveTabName().contains("Applications"))
							((GUIApplicationManager)upmtClient.getGui()).refreshGui();
					}
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
	 * locally moves the tunneled SIP signaling socket into the tunnel towards the AN identified
	 * by ANaddress over the interface identified by ifName<BR> 
	 * this means that future handover messages will be sent over this interface
	 * @param ANAddress
	 * @param ifName
	 */
	private void moveProviderToTunnel(String ANAddress, String ifName, int sport, SipProvider provider) {
		// all udp messages containing SIP UPMT signaling (i.e. from local port for signaling into the tunnel
		// toward ANAddress:AN_port_for_signaling) will go into a specific tunnel (i.e. the interface is chosen)
		tunnelManager.assignSocketToTunnel("udp", sport, ANAddress, sbcSipPort, ANAddress, ifName);
		//forces the sip_provider for UPMT signaling to use the AN as outbound proxy
		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));
		//		sip_provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));
	}

	/**
	 * It corresponds to the initial association. A out-of-tunnel message is sent and VIPA and tsaPost are obtained.<br>
	 * if blocking is true this method is blocking: it does not exit until the association has been completed
	 */
	public boolean signalAssociationOnIf(boolean blocking, String ANAddress, String ifName, InterfaceInfo ifInfo) {

		printLog("Starting association procedure for \"" + sipID + "\" to " + ANAddress + " using " + ifName, Log.LEVEL_MEDIUM);
		if(!associationProviders.containsKey(ifName)) { //ma se ne puo' usare uno solo???
			if(UPMTClient.getRME()) {
				associationProviders.put(ifName,
						//get the provider used to send message outside the tunnels
						RMEServer.RMESignalers.get(ifName));

			}
			else {
				associationProviders.put(ifName,
						//creates the provider used to send message outside the tunnels
						new SipProvider(ifInfo.ipAddress, SipStack.default_port, SipStack.default_transport_protocols, ifInfo.ipAddress));

			}
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
		AssociationReq reqSignal;
		//		AssociationReq reqSignal = new AssociationReq(sipID, null, null); //#Mettere gli altri parametri
		if(UPMTClient.getRME()) {
			reqSignal = new AssociationReq(sipID, null, UPMTClient.rmeAssocAddress, UPMTClient.getVepa());
		} else {
			reqSignal = new AssociationReq(sipID, null, null, null); //#Mettere gli altri parametri
		}
		Message message = newUpmtMessage(provider, anchorNode, sipIdName, reqSignal);

		//printLog("\n=======================================\n"+ message.toString() + "\n=======================================\n\n", Log.LEVEL_HIGH);


		//DoTransaction
		if (blocking) {
			return associationResp(startBlockingTransaction(provider, message), ANAddress, ifName);
		} else {
			if(UPMTClient.getRME()) {
				currentAssociations.add(ANAddress+":"+ifName);
			}
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
		AssociationReq reqSignal = new AssociationReq(sipID, null, null, null); //#Mettere gli altri parametri
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
//			if(!UPMTClient.blockerAssociation) {
				if(UPMTClient.getRME()) {
					if(currentAssociations.contains(ANAddress+":"+ifName) && vipaTable.get(ANAddress)==null) {
						currentAssociations.remove(ANAddress+":"+ifName);
						upmtClient.rmeAnAssociation(ANAddress, ifName);
					}
				}
//			}
			return false;
		}
		boolean associationSuccess=false;
		String vipa = null;
		int tsa = -1;
		String reason = null;
		String[] addressInUse = null;
		String vepa = "";

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
					if(UPMTClient.getRME()) {
						vepa = respSignal.vipa;
						addressInUse = respSignal.getAddressesInUse();
						vipa = UPMTClient.getVepa();
						if(addressInUse!=null) {
							if(addressInUse.length!=0) {
								upmtClient.addDiscoveredaddresses(vepa, addressInUse);
							}
							else {
								return false;
							}
						}
						else {
							return false;
						}
					}
					else {
						vipa = respSignal.vipa;
					}
					tsa = respSignal.tsa;
					reason = respSignal.reason;
					associationSuccess = respSignal.associationSuccess;

					if(vipa!=null) {
						//						failure = false;
					}
				}
			}
		}

		if(UPMTClient.getRME() && UPMTClient.getAssociatedANs().contains(ANAddress)) {
			associationSuccess = true;
		}

		if (!associationSuccess) {
			ifInfo.setANIFStatus(ANAddress,InterfaceInfo.IFANFailure);
			printLog("Association procedure to " + ANAddress + " on interface " + ifName +" failed! NO VIPA OBTAINED!!!\n" + "reason: " + reason, Log.LEVEL_MEDIUM);
//			if(!UPMTClient.blockerAssociation) {
				if(UPMTClient.getRME()) {
					if(currentAssociations.contains(ANAddress+":"+ifName) && vipaTable.get(ANAddress)==null) {
						currentAssociations.remove(ANAddress+":"+ifName);
						upmtClient.rmeAnAssociation(ANAddress, ifName);
					}
				}
//			}
			return false;
		}

		ifInfo.setANIFStatus(ANAddress, InterfaceInfo.IFANOK);

		String existingVipa = vipaTable.get(ANAddress);
		if (existingVipa != null) {
			printLog("VIPA already existing for " + ANAddress);
			//			return false;
			if(!UPMTClient.blockerAssociation) {
				upmtClient.setRoutingPeerModeFromAssociation(ANAddress, ifName);
			}
			return true;
		} 
		else {
			if(UPMTClient.getRME()) {
				if(addressInUse==null) {
					return false;
				}
				else {
					for(String address: addressInUse) {
						vipaTable.put(address, vipa);
						UPMTClient.addAssociatedAN(address, tsa);
					}
					if(!UPMTClient.blockerAssociation) {
						upmtClient.setRoutingPeerModeFromAssociation(ANAddress, ifName);
					}
				}
			}
			else {
				vipaTable.put(ANAddress, vipa);
				UPMTClient.addAssociatedAN(ANAddress, tsa);		
			}

			//the first AN that is contacted becomes the default AN
			if(defaultAN == null) {
				defaultAN = ANAddress;
			}
			printLog("Association procedure successful! Obtained VIPA: "+vipa+" and tsa: " + tsa + " from AN " + ANAddress, Log.LEVEL_MEDIUM);
		}
		if(!UPMTClient.textMode) {
				((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();
			
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
		if(!UPMTClient.getRME()) {
			upmtClient.endAddAN(ANAddress, tsaPort);
		}
	}


	/** currently, it is blocking :-( */
	public synchronized int createTunnel(String ifName, String ANAddress, int TID) {
		//in order to create a tunnel over the interface, we switch the signaling socket so
		//that SIP messages are sent on that interface 
		//this may have side effects as all SIP signaling will be sent through this interface
		//but we still do not know if it is possible to send to the AN on this interface
		//may be we should use another socket (e.g. changing the UDP source port?)
		int sport = sipTunneledPort+TID; // new siptunnelled port for each tunnel
		SipProvider provider;
		if(!upmtClient.getTunnelProviders().containsKey(Integer.valueOf(TID))) {
			// if I am allocating a new provider (because I have a new interface) it will get a new port (siptunnelledport +TID)
			upmtClient.getTunnelProviders().put(new Integer(TID), new SipProvider(Module.getVipaFix(), sport, SipStack.default_transport_protocols, Module.getVipaFix()));
//			System.err.println("non esistente---> Tid: "+TID+ " VIPAFIX: "+Module.getVipaFix()+" source port: "+sport);
		}
		provider = upmtClient.getTunnelProviders().get(Integer.valueOf(TID));
		moveProviderToTunnel(ANAddress, ifName, sport, provider);
		printLog("Creating tunnel from " + ifName + " to " + ANAddress, Log.LEVEL_MEDIUM);

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+upmtClient.getANSipPort(ANAddress));

		NameAddress from = new NameAddress("sip:" + vipaTable.get(ANAddress));//TODO: #come va costruito qsto identificativo?
		//		TunnelSetupReq req = new TunnelSetupReq(sipID, sipTunneledPort, vipaTable.get(ANAddress));
		TunnelSetupReq req = new TunnelSetupReq(sipID, sport, vipaTable.get(ANAddress));
		Signal[] reqList;
		if(ifName.equals(activeInterfaceName)) {
			//if the interface Name corresponds to the activeInterfaceName, it creates a new socket
			//and sends a SetHandoverModeReq with staticRule = true over this new socket in addition to the TunnelSetupReq
			//			Socket socket = new Socket("udp", Module.getVipaFix(), ANAddress, sipTunneledPort, sbcSipPort);
			Socket socket = new Socket("udp", Module.getVipaFix(), ANAddress, sport, sbcSipPort);
			reqList = new Signal[]{req, new SetHandoverModeReq(sipID, socket, vipaTable.get(ANAddress), true)};
		} else {
			reqList = new Signal[]{req};
		}
		//		Message message = newUpmtMessage(anchorNode, from, reqList);
		Message message = newUpmtMessage(provider, anchorNode, from, reqList);

		//printLog("SENDING OUT MESSAGE:"+UpmtSignal.serialize(reqList), Log.LEVEL_HIGH);
		//printLog("\n=======================================\n"+ message.toString() + "\n=========================ifName==============\n\n", Log.LEVEL_HIGH);
		//DoTransaction

		Message respMessage;
		//		if(UPMTClient.getRME()) {
		//			respMessage = startBlockingTransaction(provider, message);
		//		} else {
		//			respMessage = startBlockingTransaction(message);
		//		}
		respMessage = startBlockingTransaction(provider, message);

		printLog("Tunnel from " + ifName + " to " + ANAddress + (isNull(respMessage)?" was NOT created":" created succesfully!"), Log.LEVEL_MEDIUM);
		if(isNull(respMessage)) {
			return 0;
		}

		//RespMessage handling
		TunnelSetupResp resp = (TunnelSetupResp)UpmtSignal.deSerialize(respMessage.getBody())[0];
		if(resp.serverTunnelID == UPMTClient.TID_DROP) {
			printLog("Remote server error: ServerTunnelID " + resp.serverTunnelID + "my nat address should be: " + resp.yourAddress+":"+resp.yourPort+"\n");
			return 0;
		}
		synchronized(remoteTidTable){
			remoteTidTable.put(ANAddress+":"+ifName, resp.serverTunnelID);
		}
		synchronized(remoteTidStatusTable){
			TunnelInfo oldTInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
			if(oldTInfo == null)
				remoteTidStatusTable.put(ANAddress+":"+ifName, new TunnelInfo(TunnelInfo.TUNNEL_SETUP, resp.yourAddress, resp.yourPort, 0, 0, TID, upmtClient));
			else
				remoteTidStatusTable.put(ANAddress+":"+ifName, new TunnelInfo(TunnelInfo.TUNNEL_SETUP, resp.yourAddress, resp.yourPort, oldTInfo.getEWMA_Delay(), oldTInfo.getEWMA_Loss()/100, TID, upmtClient));			
		}
		printLog("ServerTunnelID: " + resp.serverTunnelID + "\nMy nat address: " + resp.yourAddress+":"+resp.yourPort+"\n");

		return resp.serverTunnelID;
	}	

	public void startKeepALiveThread(){
		if(CLIENT_KEEP_ALIVE){
			if(!keepAliveThread.isAlive())
				keepAliveThread.start();
		}
		if(!reconnectionThread.isAlive())
			reconnectionThread.start();
	}

	/**
	 * keepAlive method in SipSignalManager.java is called by the KeepAliveThread<br>
	 * the keepAliveThread for each cycle calls the keepAlive method for each tunnel<br>
	 * this method for a given tunnel (ANAddress+ifname)<br>
	 * first binds the signalling socket to the tunnel with upmtconf<br>
	 * then it originates a keepAlive message SipTransaction 
	 * 
	 * this is used to send a keep alive on a given interface toward a given anchor node
	 * the first time it is called on a given interface it creates a sip provider and stores it into the keepAliveProviders hash table
	 * the next times it is called it retrieves the sip provider from the hash table
	 * 
	 * if it is not able to get a tid for the ANaddress / ifname it returns doing nothing<br>
	 * TODO: may be we could do better, like introducing a return code 

	 * @param ANAddress
	 * @param ifName
	 */
	//	private void keepAlive(String ANAddress, String ifName) {
	//
	//		boolean wait = false;
	//
	//		synchronized(remoteTidStatusTable){
	//			wait = remoteTidStatusTable.get(ANAddress+":"+ifName).isWaitingForKA();
	//		}
	//
	//		if(wait){
	//			System.err.println("Waiting for previous keepalive to return on " + ANAddress + ":" + ifName + "");
	//			return;
	//		}
	//
	//		SipProvider provider =  null;
	//		Message message = null;
	//		int remoteTid ;
	//		
	//		synchronized(keepAliveProviders){
	//			int port = sipTunneledPort;
	//
	//			// TODO understand what happens if an interface with the same name (e.g. eth0 cames up again but with a different IP address because it
	//			// is in a different network
	//			
	//			// looking for the highest allocated port in the set of Providers
	//			for(String key : keepAliveProviders.keySet()){
	//				if(keepAliveProviders.get(key).getPort() > port)
	//					port = keepAliveProviders.get(key).getPort();
	//			}
	//
	//
	//			if(!keepAliveProviders.containsKey(ifName)) {
	//				port++; // if I am allocating a new provider (because I have a new interface) it will get a new port (highest previous port +1)
	//				keepAliveProviders.put(ifName, 	new SipProvider(Module.getVipaFix(), port, SipStack.default_transport_protocols, Module.getVipaFix()));
	//			}
	//
	//			provider = keepAliveProviders.get(ifName);
	//			
	////		}//SSallargato la parte protetta
	//
	//		//TODO check if it is ok that further keep alive directed towards different ANs will change the outbound proxy while the sip transaction could
	//		//be still active
	//		provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));
	//
	////		int tid = getAnTid(ANAddress, ifName);
	//		remoteTid = getAnTid(ANAddress, ifName);
	//		if(remoteTid == -1) {
	//			printLog("UNABLE TO RETRIEVE TID FOR AN : "+ ANAddress + " if : "+ ifName);
	//			return;
	//		}
	//
	//		Integer localTidObj = TunnelManager.getTidTable().get(ifName+":"+ANAddress);
	//		if (localTidObj==null) {
	//			printLog("UNABLE TO GET LOCAL TID FOR AN : "+ ANAddress + " if : "+ ifName);
	//			return;
	//		}
	//
	//		setPaftForKeepAlive (ANAddress, ifName, localTidObj, provider.getPort()) ;
	//
	////		System.out.println("%%%%%%%%%Going to sleep");
	////		try {
	////			Thread.sleep(10);
	////		} catch (InterruptedException e) {
	////			e.printStackTrace();
	////		}
	////		System.out.println("%%%%%%%%%Awake again");
	//		
	//
	//// 2012-06-15 - SS : removed the change of association between "java" application and the tunnel, to be used for the first packets of the sockets
	////                   this is because the association is statically associated with the socket by the port numbers
	//		
	////		//#ifndef ANDROID
	////		if(tunnelManager.getTid(ifName, ANAddress) != -1)
	////			upmtClient.forceRuleByTheInside("java", tunnelManager.getTid(ifName, ANAddress));
	////		//#else
	////		// if(tunnelManager.getTid(ifName, ANAddress) != -1)
	////		// upmtClient.forceRuleByTheInside("com.and.gui", tunnelManager.getTid(ifName, ANAddress));
	////		//#endif
	//
	//		printLog("KeepAlive for remote tunnel " + remoteTid + " on " + ANAddress + ":" + ifName + " from signaling port: " + provider.getPort() + " on local tunnel " + tunnelManager.getTid(ifName, ANAddress));		
	//
	//		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+ upmtClient.getANSipPort(ANAddress));
	//		NameAddress from = new NameAddress("sip:" + vipaTable.get(ANAddress));
	//		KeepAlive req = new KeepAlive(sipID, remoteTid, provider.getPort());
	//		Signal[] reqList;
	//		reqList = new Signal[]{req};
	//
	////		Message message = newUpmtMessage(anchorNode, from, reqList);
	//		message = newUpmtMessage(anchorNode, from, reqList);
	//		//System.out.println("messaggio:"+message);
	//
	//		//printSimplifiedOverheadLog(message.toString(), ifName);
	//
	//		//printLog("SENDING OUT MESSAGE:"+UpmtSignal.serialize(reqList), Log.LEVEL_HIGH);
	//
	//		} //SSallargato la parte protetta
	//		
	//		synchronized(remoteTidStatusTable){
	//			remoteTidStatusTable.get(ANAddress+":"+ifName).setWaitingForKA(true);
	//		}
	//
	//		startNonBlockingTransaction(provider, message, "keepAliveResp", ANAddress, ifName, remoteTid, new Integer(-1), new Integer(-1));
	//	}

	private void keepAlive(String ANAddress, String ifName) {

		boolean wait = false;

		synchronized (remoteTidStatusTable) {
			wait = remoteTidStatusTable.get(ANAddress + ":" + ifName).isWaitingForKA();
		}

		if (wait) {
			System.err.println("Waiting for previous keepalive to return on "
					+ ANAddress + ":" + ifName + "");
			return;
		}

		SipProvider provider = null;
		Message message = null;
		int remoteTid;

		synchronized (keepAliveProviders) {
			Integer localTid = TunnelManager.getTidTable().get(ifName + ":" + ANAddress);
			if(localTid.intValue()==-1) return;
			int port = sipTunneledPort + localTid.intValue();
			if (!upmtClient.getTunnelProviders().containsKey(localTid)) {
				// if I am allocating a new provider (because I have a new
				// interface) it will get a new port (siptunnelledport +localTid)
				upmtClient.getTunnelProviders().put(localTid, 
						new SipProvider(Module.getVipaFix(), port,SipStack.default_transport_protocols, Module.getVipaFix()));
			}
			provider = upmtClient.getTunnelProviders().get(localTid);
			synchronized (upmtClient.getTunnelProviders()) {
				provider.setOutboundProxy(new SipURL(ANAddress, sbcSipPort));
			}
			remoteTid = getAnTid(ANAddress, ifName);
			if (remoteTid == -1) {
				printLog("UNABLE TO RETRIEVE TID FOR AN : " + ANAddress+ " if : " + ifName);
				if(TunnelManager.getTidTable().containsKey(ifName + ":" + ANAddress)) {
					TunnelManager.getTidTable().remove(ifName + ":" + ANAddress);
				}
				if (upmtClient.getTunnelProviders().containsKey(localTid)) {
					upmtClient.getTunnelProviders().remove(localTid.intValue());
				}
				if(remoteTidStatusTable.containsKey(localTid)) {
					remoteTidStatusTable.get(localTid).setStatus(TunnelInfo.CLOSED);;
				}
				return;
			}

			Integer localTidObj = TunnelManager.getTidTable().get(ifName + ":" + ANAddress);
			if (localTidObj == null) {
				printLog("UNABLE TO GET LOCAL TID FOR AN : " + ANAddress
						+ " if : " + ifName);
				tunnelManager.removeTunnel(ifName, ANAddress);
				TunnelManager.getTidTable().remove(ifName + ":" + ANAddress);
				//				upmtClient.getTunnelProviders().get(TID.intValue()).halt();
				upmtClient.getTunnelProviders().remove(localTid.intValue());
				remoteTidStatusTable.remove(localTid);
				return;
			}

			printLog("KeepAlive for remote tunnel " + remoteTid + " on "
					+ ANAddress + ":" + ifName + " from signaling port: "
					+ provider.getPort() + " on local tunnel "
					+ localTid.intValue());


			NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"
					+ upmtClient.getANSipPort(ANAddress));
			NameAddress from = new NameAddress("sip:"
					+ vipaTable.get(ANAddress));

			TunnelInfo tInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);

			KeepAlive req = new KeepAlive(sipID, remoteTid, provider.getPort(), tInfo.getLastDelay(), tInfo.getNumberRetry(), tInfo.getEWMA_Delay(), tInfo.getEWMA_Loss());
			Signal[] reqList;
			reqList = new Signal[] { req };
			message = newUpmtMessage(anchorNode, from, reqList);

		}

		synchronized (remoteTidStatusTable) {
			remoteTidStatusTable.get(ANAddress + ":" + ifName).setWaitingForKA(true);
		}

		startNonBlockingTransaction(provider, message, "keepAliveResp",
				ANAddress, ifName, remoteTid, new Integer(-1), new Integer(-1));
	}


	/**
	 * it stores the association between ANAddress:IfName and a local tid, as perceived by java
	 */
	private HashMap<String, Integer> tableAnIfnameToLocalTid = new HashMap<String, Integer>();


	/**
	 * it checks if the PAFT for keep alive for a given ANAddress:ifName has been already setup
	 * for the provided Tid, if not it sets the local table and then it actually set the PAFT
	 * @param anAddress
	 * @param ifName
	 * @param localTidObj  // metodo non chiamato più inutile ---> Valerio
	 */
	@SuppressWarnings("unused")
	private void setPaftForKeepAlive (String anAddress, String ifName, Integer localTidObj, int socketPort){
		String key = anAddress+":"+ifName;
		synchronized (tableAnIfnameToLocalTid) {
			Integer value = tableAnIfnameToLocalTid.get(key);
			if (value!=null && value.intValue()==localTidObj.intValue() ) {
				printLog("already assigned socket local port : " + socketPort + " dest IP " + anAddress + " desp port:" + sbcSipPort + " local interface: " + ifName + " tunnel dest IP " + anAddress);
				return ;
			} else {
				tableAnIfnameToLocalTid.put(key, localTidObj);
				tunnelManager.assignSocketToTunnel("udp", socketPort, anAddress, sbcSipPort, anAddress, ifName);
				printLog("assigned socket local port : " + socketPort + " dest IP " + anAddress + " desp port:" + sbcSipPort + " local interface: " + ifName + " tunnel dest IP " + anAddress);

			}
		}
		return ;
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

	/**
	 * NB it is called back by a NonBlockingTransaction using Method by name<br>
	 * as far as I can tell there is no reason to provide a boolean as return value...
	 */
	public boolean keepAliveResp(Message resp, String ANAddress, String ifName, Integer remoteTid, Integer delay, Integer numberRetry) {

		//printSimplifiedOverheadLog(resp.toString(), ifName);

		// for same stupid reason the response message is null if the response is an error message
		// this stupid behavior is implemented in onTransFailureResponse in BaseUpmtEntity.java

		if(resp != null && resp.getStatusLine() != null)
			if(KA_Console_Log)
				printLog("Remote Tunnel " + remoteTid + " from " + ANAddress + ":" + ifName + " KeepAlive sip response is " + resp.getStatusLine().toString().replaceAll("\n", ""), Log.LEVEL_LOW);

		boolean res ;
		synchronized (keepAliveProviders) {



			synchronized(remoteTidStatusTable) {

				res = false;

				TunnelInfo tInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
				if (tInfo == null) { 
					printLog("ERROR: tInfo null in keepAliveResp", Log.LEVEL_HIGH);
					String VIPA = upmtClient.getVipa(ANAddress);
					upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);

				} else {
					tInfo.setWaitingForKA(false);
					if(resp.getStatusLine() == null) {


						if( tInfo.getStatus() == TunnelInfo.TUNNEL_SETUP) {
							tInfo.setStatus(TunnelInfo.CLOSED);
							//						remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);
							remoteTidStatusTable.remove(ANAddress+":"+ifName);
							Integer localTid = TunnelManager.getTidTable().remove(ifName + ":" + ANAddress);
							printLog("Remote Tunnel " + remoteTid + " from " + ANAddress + ":" + ifName + " on local tunnel "+localTid.intValue()+" changes state to CLOSED");
							//						upmtClient.checkAllPolicy(UPMTClient.EVENT_INTERFACE_DOWN);
							if(UPMTClient.getRME()) {
								String VIPA = upmtClient.getVipa(ANAddress);
								upmtClient.delRMETunnelToTunnelList(VIPA, localTid.intValue());
								upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
							}
//							tunnelManager.removeTunnel(ifName, ANAddress, remoteTid);
							tunnelManager.removeTunnel(ifName, ANAddress, localTid);

						}
					}

					if(resp.getStatusLine() != null){
						if(resp.getStatusLine().toString().contains("200 OK")) {
							tInfo.setStatus(TunnelInfo.TUNNEL_SETUP);
							tInfo.setLastDelayAndNumberRetry(delay, numberRetry);
							remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);
							res = true;
							upmtClient.setBestTunnelForVipa(ANAddress, tInfo);
						} else {

							tInfo.setStatus(TunnelInfo.CLOSED);
							remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);
							printLog("Remote Tunnel " + remoteTid + " from " + ANAddress + ":" + ifName + " changes state to CLOSED", Log.LEVEL_HIGH);

							if(UPMTClient.getRME()) {
								String VIPA = upmtClient.getVipa(ANAddress);
								upmtClient.delRMETunnelToTunnelList(VIPA, remoteTid);
								upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
							}

							tunnelManager.removeTunnel(ifName, ANAddress);

						}
					}

				}

			}
		}
		if(!UPMTClient.textMode) {
			((GUIApplicationManager)upmtClient.getGui()).refreshGui();
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


			//I have commented this because we have not yet set the activeInterfaceName used for signaling
			//we should do changeSignalingInterf(String newInterf);

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
			printLog("KeepAliveThread Stopped");
		}
		if(reconnectionThread.isAlive()) {
			//reconnectionThread.interrupt();
			reconnection = false;
			printLog("ReconnectionThread Stopped");
		}
		//		Iterator<Integer> iter = upmtClient.getTunnelProviders().keySet().iterator();
		//		while(iter.hasNext()) {
		//			upmtClient.getTunnelProviders().get(iter.next()).halt();
		//		}

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
		vipaTable.remove(ANAddress);

		if(!UPMTClient.getRME()) {

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

	public ArrayList<String> getCurrentAssociations() {
		return currentAssociations;
	}


	public void setCurrentAssociations(ArrayList<String> currentAssociations) {
		this.currentAssociations = currentAssociations;
	}


	protected void printLog(String str, int level){System.out.println("[SipSignalManager]: "+str); super.printLog("UPMTServer: "+str, level);}
	protected void printLog(String str){printLog(str,0);}

	//	static void logSignals (Signal[] sigVector){
	//		System.out.println(UpmtSignal.serialize(sigVector));
	////		for (int i=0; i<sigVector.length; i++){
	////			System.out.println(sigVector[i].toString());
	////		}
	//	}

	public Hashtable<String, SipProvider> getAssociationProviders() {
		return this.associationProviders;
	}

	public String getEndPointAssociationProviders(String ifName) {
		if(this.associationProviders.containsKey(ifName)) {
			return this.associationProviders.get(ifName).getViaAddress();
		}
		else {
			return null;
		}
	}
	
	public void removeTunnel(int tid, String ifName, String ANAddress) {
		synchronized(remoteTidStatusTable) {
			TunnelInfo tInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
			if(tInfo!=null) {
				if(tInfo.getStatus() == TunnelInfo.TUNNEL_SETUP) {
					tInfo.setStatus(TunnelInfo.CLOSED);
					remoteTidStatusTable.remove(ANAddress+":"+ifName);
					Integer localTid = TunnelManager.getTidTable().remove(ifName + ":" + ANAddress);
					printLog("Remote Tunnel " + tInfo.getTid() + " from " + ANAddress + ":" + ifName + " on local tunnel "+localTid.intValue()+" changes state to CLOSED");
					if(UPMTClient.getRME()) {
						String VIPA = upmtClient.getVipa(ANAddress);
						upmtClient.delRMETunnelToTunnelList(VIPA, localTid.intValue());
						upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
					}
					tunnelManager.removeTunnel(ifName, ANAddress, tid);
				}	
			}
			else {
				String VIPA = upmtClient.tryToDelRMETunnelToTunnelList(tid, ifName, ANAddress);
				if(VIPA!=null) {
					upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
				}

				tunnelManager.removeTunnel(ifName, ANAddress, tid);
			}
		}
	}
	
	public void infoTunnel(String ANAddress, String ifName, int delay, int numberRetry) {
		synchronized(remoteTidStatusTable) {
			TunnelInfo tInfo = remoteTidStatusTable.get(ANAddress+":"+ifName);
			if(tInfo!=null) {
				if(tInfo.getStatus() != TunnelInfo.TUNNEL_SETUP) {
					tInfo.setStatus(TunnelInfo.TUNNEL_SETUP);
				}
//				if(delay==0 && numberRetry==0) {
//					int Min = 0;
//					int Max = 0;
//					Min=20;
//					Max=100;
//					delay = (int) (Min + (Math.random() * (Max - Min)));
//					Min=0;
//					Max=2;
//					numberRetry = (int) (Min + (Math.random() * (Max - Min)));
//				}
				tInfo.setLastDelayAndNumberRetry(delay, numberRetry);
				remoteTidStatusTable.put(ANAddress+":"+ifName, tInfo);
				upmtClient.setBestTunnelForVipa(ANAddress, tInfo);
			}
		}
	}

}
