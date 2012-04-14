package upmt.server;



import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import org.zoolu.net.IpAddress;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.tools.Configurable;
import org.zoolu.tools.Configure;
import org.zoolu.tools.Log;
import org.zoolu.tools.Parser;

import upmt.Default;
import upmt.client.core.Socket;
import upmt.os.Module;
import upmt.server.gui.AssociationInfo;
import upmt.server.gui.TunnelInfo;
import upmt.server.tunnel.TunnelManager;
import upmt.server.vipa.VipaManager;
import upmt.server.vipa.VipaManagerFactory;
import upmt.signaling.BaseUpmtEntity;
import upmt.signaling.UpmtSignal;
import upmt.signaling.message.ANListReq;
import upmt.signaling.message.ANListResp;
import upmt.signaling.message.AssociationReq;
import upmt.signaling.message.AssociationResp;
import upmt.signaling.message.BrokerRegistrationReq;
import upmt.signaling.message.HandoverReq;
import upmt.signaling.message.HandoverResp;
import upmt.signaling.message.KeepAlive;
import upmt.signaling.message.SetHandoverModeReq;
import upmt.signaling.message.SetHandoverModeResp;
import upmt.signaling.message.Signal;
import upmt.signaling.message.TsaBinding;
import upmt.signaling.message.TunnelSetupReq;
import upmt.signaling.message.TunnelSetupResp;
import upmt.server.AnchorNode;
import java.util.*;



public class UPMTServer extends BaseUpmtEntity implements Configurable
{
	private String vipaManagerPolicy = Default.SERVER_VIPA_MANAGER_POLICY;

	private Vector<String> tsas;
	private int serverMark = Default.SERVER_MARK;
	private int sipPort = Default.SERVER_SIP_PORT;

	private VipaManager vipaManager;
	private TunnelManager tunnelManager;

	private static final boolean PRINT_STATISTICS_ON_CONSOLE = true;
	private static final int PRINT_STATISTICS_ON_CONSOLE_INTERVAL = 7500;
	public static final boolean SERVER_KEEP_ALIVE = true;
	private static  int SERVER_KEEP_ALIVE_INTERVAL =  20000;

	private static HashMap<String, AssociationInfo> associations = new HashMap<String, AssociationInfo>(); // <sipid, <vipa, last_ack> >
	private static HashMap<Integer, TunnelInfo> tunnels = new HashMap<Integer, TunnelInfo>(); // <vipa, arralist<TunnelInfo>>

	//TODO MARCO Sostituire registeredANs con ASDASD e poi rinominarla
	private static HashMap<String, Long> CurrentANList = new HashMap<String, Long>();

	private static HashMap<AnchorNode, Long> AssociatedANs = new HashMap<AnchorNode, Long>();


	private Vector<String> ANBrokerList = new Vector<String>();


	private int maxMHs;
	private Vector<String> Black_List = new Vector<String>();
	//MARCO
	private String anchor_identifier;
	private static IpAddress anchor_IPaddress = IpAddress.getLocalHostAddress();
	private int port= Default.SERVER_SIP_PORT;;	
	private static boolean isAN = false;
	private static boolean isBroker = false;
	private static boolean isFH = false;
	private int freeSlots;


	public UPMTServer(String file)
	{
		super(file);
		System.out.println("Starting server with cfg file: " + file);

		Module.setVipaFix(Default.SERVER_VIPA);
		Module.setServerIfName(Default.SERVER_IFNAME);
		new Configure(this, file);
		vipaManager = VipaManagerFactory.getVipaManager(vipaManagerPolicy, file);
		System.out.println(tsas);
		tunnelManager = new TunnelManager(tsas, serverMark);

		System.out.println("\n################################\nSECURITY RESTICTIONS:\n");
		System.out.println("Max MHs allowed set to " + maxMHs + "\n");
		if(Black_List.size() > 0){
			System.out.println("Refusing association from following users: ");		
			for(String baduser : Black_List){
				System.out.println(baduser + "   ");
			}
		}
		System.out.println("################################\n");

		if(ANBrokerList.size() > 0){
			System.out.println("Anchor Node Broker List size: " + ANBrokerList.size());
			for(String broker : ANBrokerList){
				if(registerToBroker(broker.split(":")[0], new Integer(broker.split(":")[1])))
					System.out.println("Registered to broker: " + broker);
			}
		}

		if(isAN && isBroker && freeSlots>0){
			CurrentANList.put(IpAddress.getLocalHostAddress().toString() + ":" + sipPort, System.currentTimeMillis());
		}


		// KeepAlive Thread
		Thread keepalivethread = new Thread(){
			public void run(){

				System.err.println("KeepAlive activated with KeepAlive period: " + SERVER_KEEP_ALIVE_INTERVAL);

				while(true){					

					try {
						Thread.sleep(UPMTServer.SERVER_KEEP_ALIVE_INTERVAL);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}


					synchronized(tunnels){
						Iterator<Integer> iter = tunnels.keySet().iterator();

						while(iter.hasNext()){
							Integer tid = (Integer)iter.next();
							printLog("Keepalive check for tunnel " + tid + " (" + tunnels.keySet().size() + ") ");

							long time = tunnels.get(tid).getLastAck();

							if(System.currentTimeMillis() - time > UPMTServer.SERVER_KEEP_ALIVE_INTERVAL){
								printLog("tunnel " + tid + " is dead, removing");							
								iter.remove();
								tunnelManager.removeTunnel(tid);
							}
							else
								printLog("tunnel " + tid + " is alive");
						}
					}


					synchronized(associations){

						Iterator<String> iter = associations.keySet().iterator();

						while(iter.hasNext()){			

							AssociationInfo assocs = associations.get(iter.next());

							long time = assocs.getLastAck();
							printLog("Keepalive check for vipa " + assocs.getVipa() + " (" + tunnels.keySet().size() + ") ");


							if(System.currentTimeMillis() - time > UPMTServer.SERVER_KEEP_ALIVE_INTERVAL){
								printLog("vipa " + assocs.getVipa() + " is dead, removing");							
								iter.remove();			

							}
							else
								printLog("vipa " + assocs.getVipa() + " is alive");							
						}
					}

					//TODO deactivate for performance evaluation
					/*
					if(ANBrokerList.size() > 0){
						System.out.println("Anchor Node Broker List size: " + ANBrokerList.size());
						for(String broker : ANBrokerList){
							if(registerToBroker(broker.split(":")[0], new Integer(broker.split(":")[1])))
								System.out.println("Refreshing registration to broker: " + broker);
						}
					}

					if(isAN && isBroker){
						registeredANs.put(IpAddress.getLocalHostAddress().toString() + ":" + sipPort, System.currentTimeMillis());
					}


					synchronized(registeredANs){

						Iterator<String> iter = registeredANs.keySet().iterator();

						while(iter.hasNext()){			

							String an = iter.next();
							Long last_ack = registeredANs.get(an);

							long time = last_ack.longValue();

							if(System.currentTimeMillis() - time > UPMTServer.SERVER_KEEP_ALIVE_INTERVAL){
								printLog("an " + an + " is dead, removing");							
								iter.remove();			
							}
							else
								printLog("an " + an + " is alive");							
						}
					}
					 */



				}
			}
		};
		// KeepAlive Thread End

		if(SERVER_KEEP_ALIVE)
			keepalivethread.start();

	}

	//MARCO 
	/** currently, it is blocking :-( */
	private boolean registerToBroker(String ANAddress, int brokerSipPort) {
		freeSlots=maxMHs-associations.size();
		AnchorNode thisAN = new AnchorNode(anchor_identifier, anchor_IPaddress, port, isAN, isBroker, isFH, freeSlots);

		printLog("Trying to register " +thisAN.anchor_identifier  +" to ANBroker " + ANAddress);

		String ipAddr = IpAddress.getLocalHostAddress().toString();
		sip_provider.setOutboundProxy(new SipURL(ANAddress, SipStack.default_port));		
		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+ brokerSipPort);
		NameAddress from = new NameAddress("sip:" + ipAddr);


		Signal[] reqList;

		BrokerRegistrationReq req = new BrokerRegistrationReq();
		req.setSipId(this.anchor_identifier);
		String aaa[]= new String[1];
		aaa[0]= this.anchor_IPaddress.toString() + ":" + this.port;
		req.setIp(aaa);
		req.setImFH(this.isFH);
		req.setBroker(this.isBroker);
		req.setImAN(this.isAN);
		req.setFreeSlots(this.freeSlots);

		reqList = new Signal[]{req};

		Message message = newUpmtMessage(anchorNode, from, reqList);

		Message respMessage = startBlockingTransaction(message);

		if(respMessage.getStatusLine() != null && respMessage.getStatusLine().toString().contains("200 OK")) {
			return true;
		}

		return false;
	}



	public void parseLine(String line)
	{
		int index = line.indexOf("=");
		String attribute = index>0 ? line.substring(0, index).trim() : line;
		Parser par = index>0 ? new Parser(line, index+1) : new Parser("");
		if (attribute.equals(Default.SERVER_VIPA_MANAGER_POLICY_TAG)) vipaManagerPolicy = par.getString();
		else if (attribute.equals(Default.SERVER_TSA_PORT_TAG)) tsas = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.SERVER_MARK_TAG)) serverMark = par.getInt();
		else if (attribute.equals(Default.SERVER_SIP_PORT_TAG)) sipPort = par.getInt();
		else if (attribute.equals(Default.SERVER_VIPA_TAG)) Module.setVipaFix(par.getString());
		else if (attribute.equals(Default.SERVER_IFNAME_TAG)) Module.setServerIfName(par.getString());
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_PERIOD_TAG)) SERVER_KEEP_ALIVE_INTERVAL = par.getInt() * 2;

		//Anchor node Broker List for registration
		else if (attribute.equals(Default.ANBrokerList_TAG)) ANBrokerList = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.isAN_TAG)) isAN = (par.getString().contains("yes") ? true : false);
		else if (attribute.equals(Default.isBroker_TAG)) isBroker = (par.getString().contains("yes") ? true : false);
		else if (attribute.equals(Default.isFH_TAG)) isFH = (par.getString().contains("yes") ? true : false);

		else if (attribute.equals(Default.SERVER_IDENTIFIER_TAG)) anchor_identifier = par.getString();

		else if (attribute.equals(Default.SERVER_MAX_MH_TAG)) maxMHs = par.getInt();
		else if (attribute.equals(Default.BLACK_LIST_TAG)) Black_List = par.getWordVector(Default.delim);

	}
	protected void printLog(String str, int level){System.out.println("UPMTServer: "+str); super.printLog("UPMTServer: "+str, level);}
	protected void printLog(String str){printLog(str, Log.LEVEL_LOWER);}



	// ****************************** UPMT *****************************
	protected AssociationResp handleAssociation(AssociationReq req){
		printLog("ASSOCIATION for " + req.sipId);

		String vipa = vipaManager.getNewVipa(req);

		//TODO what to do when isAN is false?????

		AssociationResp resp = (AssociationResp)UpmtSignal.createResp(req);


		//MARCO 		
		if (associations.size()>maxMHs){
			resp.associationSuccess = false;
			resp.reason="overload";
		}

		else if (Black_List.contains(req.sipId)) {
			resp.associationSuccess = false;
			resp.reason="bad user";			
		}

		else {
			resp.associationSuccess = true;
			resp.vipa = vipa;
			resp.tsa = new Integer(tsas.get(0).split(":")[1]);

			printLog("Vipa assigned: "+vipa);

			synchronized(associations){
				associations.put(req.sipId, new AssociationInfo(vipa, System.currentTimeMillis()));
			}
		}
		return resp;
	}

	protected boolean handleBrokerRegistration(BrokerRegistrationReq reqSignal) {

		boolean added = false;

		if(isBroker){
			synchronized(CurrentANList){

				for (int i = 0; i <reqSignal.getIp().length; i++) {


					AnchorNode an = new AnchorNode(reqSignal.getSipId(), 
							new IpAddress(reqSignal.getIp()[i].split(":")[0]), Integer.parseInt(reqSignal.getIp()[i].split(":")[1]),
							reqSignal.isImAN(), reqSignal.isImFH(), reqSignal.isBroker(),reqSignal.getFreeSlots() );

					printLog("Adding Anchor Node " + reqSignal.getSipId() +" ");

					AssociatedANs.put(an, System.currentTimeMillis());

					if(reqSignal.isImAN() && reqSignal.getFreeSlots()>0){
						CurrentANList.put(reqSignal.getIp()[i], System.currentTimeMillis());					
					}

					added = true;
				}
			}
		}		

		return added;
	}


	protected TunnelSetupResp handleTunnelSetup(TunnelSetupReq req)
	{
		printLog("TUNNEL-SETUP for " + req.sipId);

		int tunnelID = tunnelManager.getTunnelID(req.vipa, req.sipSignalingPort);

		String[] yourAddressCouple = tunnelManager.getTunnelEndAddress(tunnelID).split(":");

		TunnelSetupResp resp = (TunnelSetupResp)UpmtSignal.createResp(req);
		resp.serverTunnelID = tunnelID;
		resp.yourAddress = yourAddressCouple[0];
		resp.yourPort = Integer.parseInt(yourAddressCouple[1]);

		synchronized(tunnels){
			Integer TID = new Integer(tunnelID);
			TunnelInfo value = tunnels.get(TID);
			if(value == null)
				value = new TunnelInfo(resp.serverTunnelID, resp.yourAddress, resp.yourPort, req.sipId, req.vipa, System.currentTimeMillis());
			tunnels.put(TID, value);
		}

		printLog("tunnelID: "+tunnelID+"\nyourAddress: "+yourAddressCouple[0]+":"+yourAddressCouple[1]);
		return resp;
	}


	protected ANListResp handleANListReq(ANListReq req){

		printLog("ANListReq received from " + req.sipId);


		String ANlist[] = null;

		if(isBroker){
			ANlist = new String[CurrentANList.keySet().size()];

			int i = 0;
			for(String an: CurrentANList.keySet()){
				ANlist[i] = an;
				i++;
			}
		}
		else{
			ANlist = new String[ANBrokerList.size()];

			for(int i = 0; i < ANBrokerList.size(); i++){
				ANlist[i] = ANBrokerList.get(i);
			}			
		}

		ANListResp resp = new ANListResp();
		resp.setAnList(ANlist);
		resp.setBroker(isBroker);

		printLog("SENDING OUT MESSAGE:" + UpmtSignal.serialize(resp), Log.LEVEL_HIGH);

		return resp;		
	}


	protected boolean handleKeepAlive(KeepAlive req){

		boolean alive = false;
		int reqTunnelID = req.tunnelId;

		synchronized(associations){

			AssociationInfo assoc = associations.get(req.sipId);
			//String[] yourAddressCouple = tunnelManager.getTunnelEndAddress(tunnelID).split(":");

			if(assoc != null){
				int tunnelID = tunnelManager.getTunnelID(assoc.getVipa(), req.sipSignalingPort);
				if(reqTunnelID == tunnelID){
					assoc.setLastAck(System.currentTimeMillis());
					alive = true;
				}
				else
					System.err.println("KEEPALIVE ERROR for tunnel: " + tunnelID + " istead of "+ reqTunnelID);
			}

		}

		if(alive){
			synchronized(tunnels){
				TunnelInfo value = tunnels.get(new Integer(reqTunnelID));
				if(value != null)
					value.setLastAck(System.currentTimeMillis());
				else
					alive = false;
			}
		}

		return alive;
	}


	protected HandoverResp handleHandover(HandoverReq req)
	{
		printLog("Handover for " + req.sipId);

		Socket socket = req.socket;

		//TODO: gestire il caso di req.tunnelId==-1 ke significa spostare il socket sul tunnel da cui si riceve! Serve sta cosa?
		tunnelManager.assignSocketToTunnel(socket.proto, socket.dstIp, socket.dstPort, req.vipa, socket.srcPort, req.tunnelId);

		Integer TID = null;
		TunnelInfo value = null;

		synchronized(tunnels){
			TID = new Integer(req.tunnelId);
			value = tunnels.get(TID);
		}

		if(value != null){
			value.setYourAddress(socket.dstIp);
			value.setYourPort(socket.dstPort);
			value.setLastAck(System.currentTimeMillis());


			synchronized(associations){

				AssociationInfo assoc = associations.get(value.sipId);

				if(assoc != null){
					assoc.setLastAck(System.currentTimeMillis());
				}
			}

			synchronized(tunnels){
				tunnels.put(TID, value);
			}
		}


		HandoverResp resp = (HandoverResp)UpmtSignal.createResp(req);
		return resp;
	}

	protected SetHandoverModeResp handleSetHandoverMode(SetHandoverModeReq req)
	{
		printLog("SetHandoverMode for " + req.sipId);
		//TODO:

		SetHandoverModeResp resp = (SetHandoverModeResp)UpmtSignal.createResp(req);

		return resp;
	}

	protected Signal handleTsaBinding(TsaBinding req)
	{
		printLog("TsaBinding for " + req.sipId);
		//TODO:

		return null;
	}



	public HashMap<String, AssociationInfo> getAssociations(){
		synchronized(associations){
			return associations;
		}
	}

	public HashMap<Integer, TunnelInfo> getTunnels(){
		synchronized(tunnels){
			return tunnels;
		}
	}

	public HashMap<String, Long> getRegisteredANs(){
		synchronized(CurrentANList){
			return CurrentANList;
		}
	}

	public void clearAssociations(){
		synchronized(associations){
			associations.clear();
		}
	}

	public void clearTunnels(){
		synchronized(tunnels){
			tunnels.clear();
		}
	}


	public void clearRegisteredANs(){
		synchronized(CurrentANList){
			CurrentANList.clear();
		}
	}
	// ****************************** MAIN *****************************
	public static void main(String[] args)
	{
		String file = Default.SERVER_CONFIG_FILE;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) quitAndPrintUsage();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];

		new UPMTServer(file);

		if(PRINT_STATISTICS_ON_CONSOLE){
			while(true){

				System.out.println("\n\t[" + getSystemDate(System.currentTimeMillis()) + "]");


				System.out.println("\n\t" + AssociatedANs.keySet().size() +" CURRENTLY REGISTERED ANs");

				for(AnchorNode an: AssociatedANs.keySet()) {  

					//Long last_ack = registeredANs.get(an);  
					System.out.println("\t" + an.anchor_identifier +"---- Free Slots: " +an.freeSlots );
					//System.out.println("\t" + an + " --> "  + " [" + getSystemDate(last_ack.longValue()) + "]");
				}


				System.out.println("\n\t" + CurrentANList.keySet().size() +" ANs put on the ANList");

				for(String an: CurrentANList.keySet()) {  

					Long last_ack = CurrentANList.get(an);  

					System.out.println("\t" + an + " --> "  + " [" + getSystemDate(last_ack.longValue()) + "]");

				}

				synchronized(associations){

					System.out.println("\n\t" +associations.keySet().size() + " ACTIVE ASSOCIATIONS");

					for(String sipId: associations.keySet()) {  

						AssociationInfo assoc = associations.get(sipId);  

						System.out.println("\t" + sipId + " --> " + assoc.getVipa() + " [" + getSystemDate(assoc.getLastAck()) + "]");

					}
				}

				synchronized(tunnels){

					System.out.println("\n\t" + tunnels.keySet().size() + " ACTIVE TUNNELS");

					for (Integer key : tunnels.keySet()) {  

						TunnelInfo value = tunnels.get(key);  

						System.out.println("\t" + key + " --> " + value.serverTunnelID + ") " + value.yourAddress + ":" + value.yourPort + " (" + value.sipId + ":" + value.vipa + ")" + " [" + getSystemDate(value.getLastAck()) + "]");

					}

				}

				try {
					Thread.sleep(PRINT_STATISTICS_ON_CONSOLE_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
		}
	}

	public boolean getIsAN(){
		return isAN;
	}

	public boolean getIsBroker(){
		return isBroker;
	}

	public int getTsaPort(){
		System.out.println(tsas.get(0).split(":")[1]);
		return new Integer(tsas.get(0).split(":")[1]);
	}

	public void stop(){
		System.exit(0);
	}

	public static String getSystemDate (long millis) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm:ss");
		Date currentDate = new Date (millis);
		String sysDate = sdf.format(currentDate);
		return sysDate;

	}

	private static void quitAndPrintUsage()
	{
		System.err.println("usage:\n\tjava UPMTServer [options]\n\toptions:" +
				"\n\t-f <config_file> specifies a configuration file");
		System.exit(-1);
	}
}
