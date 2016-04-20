package upmt.server.rme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.jsonref.JSONException;
import org.jsonref.JSONObject;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.tools.Log;

import upmt.Default;
import upmt.client.UPMTClient;
import upmt.client.application.manager.impl.GUIApplicationManager;
import upmt.client.core.Socket;
import upmt.client.rme.RMEInterface;
import upmt.client.rme.RoutingCheck;
import upmt.os.Module;
import upmt.server.gui.AssociationInfo;
import upmt.server.gui.TunnelInfo;
import upmt.server.tunnel.TunnelManager;
import upmt.signaling.BaseUpmtEntity;
import upmt.signaling.UpmtSignal;
import upmt.signaling.message.AssociationReq;
import upmt.signaling.message.AssociationResp;
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

public class RMEServer extends BaseUpmtEntity {

//	private static VipaManager vipaManager;
	public static TunnelManager tunnelManager;
	private static final boolean PRINT_STATISTICS_ON_CONSOLE = true;
	private static final int PRINT_STATISTICS_ON_CONSOLE_INTERVAL = 7500;
	public static HashMap<String, AssociationInfo> associations = new HashMap<String, AssociationInfo>(); // <sipid, <vipa, last_ack> >
	public static HashMap<Integer, TunnelInfo> tunnels = new HashMap<Integer, TunnelInfo>(); // <vipa, arraylist<TunnelInfo>>
	//TODO MARCO Sostituire registeredANs con ASDASD e poi rinominarla
	private static HashMap<String, Long> CurrentANList = new HashMap<String, Long>();
	private static HashMap<AnchorNode, Long> AssociatedANs = new HashMap<AnchorNode, Long>();
//	private static IpAddress anchor_IPaddress = IpAddress.getLocalHostAddress();
//	private int port= Default.SERVER_SIP_PORT;;	
//	private int freeSlots;
	public static ArrayList<RMEInterface> rmeAddresses = new ArrayList<RMEInterface>();
	public boolean rme;
	private static HashMap<String, HashMap<Integer, TunnelInfo>> RMEtunnel = new HashMap<String, HashMap<Integer,TunnelInfo>>();
	private static HashMap<String, HashMap<String, AssociationInfo>> RMEassociations = new HashMap<String, HashMap<String,AssociationInfo>>();
	private String serverName;
	private static RMEConfigManager RMEcfg;
	public static boolean SERVER_KEEP_ALIVE = true;
	public static HashMap<String, SipProvider> RMESignalers = new HashMap<String, SipProvider>();
	public static ArrayList<RMEServer> RMEServers = new ArrayList<RMEServer>();
	private UPMTClient upmtClient; 
	private String vepa;
	
	private HashMap<String, String[]> rmeDiscoveredAddresses = new HashMap<String, String[]>();
	
	/**
	 * hashtable (ifname -> SipProvider) 
	 * stores the sipProviders used to send KA messages towards any AN for each given interface
	 */
	private static Hashtable<Integer, SipProvider> tunnelProviders = new Hashtable<Integer, SipProvider>();

	public RMEServer(String file, String ip, String ifname, SipProvider sipProvider, String vepa)
	{
		
		super(file, sipProvider);
		this.serverName=ifname;
//		this.upmtClient = null;
		this.vepa = vepa;
		RMEtunnel.put(serverName, new HashMap<Integer, TunnelInfo>());
		RMEassociations.put(serverName, new HashMap<String, AssociationInfo>()); // <sipid, <vipa, last_ack> >
		
//		if(RMEcfg.ANBrokerList.size() > 0) {
//			System.out.println("Anchor Node Broker List size: " + RMEcfg.ANBrokerList.size());
//			for(String broker : RMEcfg.ANBrokerList){
//				if(registerToBroker(broker.split(":")[0], new Integer(broker.split(":")[1])))
//					System.out.println("Registered to broker: " + broker);
//			}
//		}

//		if(RMEcfg.isAN && RMEcfg.isBroker && freeSlots>0){
//			for(int i=0; i<rmeInterfaces.length; i++) {
//				CurrentANList.put(ip + ":" + RMEcfg.sipPort, System.currentTimeMillis());
//			}
//		}


		// KeepAlive Thread
		Thread keepalivethread = new Thread(){
			public void run(){

				printLog("KeepAlive activated with KeepAlive period: " + RMEcfg.SERVER_KEEP_ALIVE_INTERVAL);

				//synchronized (UPMTClient.getAvailableIfs()) {
					
				
				while((!RMEcfg.keepaliveKernel)){					

					try {
						Thread.sleep(RMEServer.RMEcfg.SERVER_KEEP_ALIVE_INTERVAL);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}


					synchronized(tunnels){
						synchronized (UPMTClient.getRMERemoteTidStatusTable()) {



							Iterator<Integer> iter = RMEtunnel.get(serverName).keySet().iterator();

							while(iter.hasNext()) {
								Integer tid = (Integer)iter.next();
								printLog("Keepalive check for tunnel " + tid + " (" + tunnels.keySet().size() + ") ");
								if(tunnels.get(tid)!=null) {
									long time = tunnels.get(tid).getLastAck();

									if(System.currentTimeMillis() - time > RMEcfg.SERVER_KEEP_ALIVE_INTERVAL) {
										printLog("tunnel " + tid + " is dead, removing");							
										TunnelInfo deletedTunnel = tunnels.remove(tid);
										tunnelManager.removeTunnel(tid);
										iter.remove();
										synchronized (upmtClient.getRmePerVipaTunnelList()) {
											String VIPA = deletedTunnel.getVipa();
											upmtClient.delRMETunnelToTunnelList(VIPA, tid);
											upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
										}
										//UPMTClient.delRMETunnelsToGUI(deletedTunnel.getYourAddress());
										//UPMTClient.getRMERemoteTidStatusTable().remove(deletedTunnel.getYourAddress()+":"+serverName);
										if(UPMTClient.getRMERemoteTidStatusTable().containsKey(deletedTunnel.getYourAddress()+":"+serverName)) {
											UPMTClient.getRMERemoteTidStatusTable().get(deletedTunnel.getYourAddress()+":"+serverName).setStatus(RMETunnelInfo.NO_TUNNEL);
										}
										if(!UPMTClient.textMode) {
											((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();
										}
									}
									else {
										if(tunnels.containsKey(tid)) {
											TunnelInfo tunnelCheck = tunnels.get(tid);
											String address = tunnelCheck.getYourAddress();
											if(UPMTClient.getRMERemoteTidStatusTable().containsKey(address+":"+serverName)) {
												UPMTClient.getRMERemoteTidStatusTable().get(address+":"+serverName).setStatus(RMETunnelInfo.TUNNEL_SETUP);
											}
										}
									}
								}
								else {
									if(tunnels.containsKey(tid)) {
										TunnelInfo tunnelCheck = tunnels.get(tid);
										String address = tunnelCheck.getYourAddress();
										if(UPMTClient.getRMERemoteTidStatusTable().containsKey(address+":"+serverName)) {
											UPMTClient.getRMERemoteTidStatusTable().get(address+":"+serverName).setStatus(RMETunnelInfo.TUNNEL_SETUP);
										}
									}
									printLog("tunnel " + tid + " is alive");
								}
							}
						}
					}
					if(upmtClient!=null) {
						if(!UPMTClient.textMode) {
							((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();
						}
					}
					
//					if(!UPMTClient.getRME()) { // lasciamo le associazioni attive

						synchronized(RMEassociations) {

							Iterator<String> iter = RMEassociations.get(serverName).keySet().iterator();

							while(iter.hasNext()) {

								String res = iter.next();
								AssociationInfo assocs = associations.get(res);
								long time = 0;
								if(assocs!=null) {

									//                               if(Long.valueOf(assocs.getLastAck())!=null){
									time = assocs.getLastAck();
									printLog("Keepalive check for vipa " + assocs.getVipa() + " (" + tunnels.keySet().size() + ") ");

									if(System.currentTimeMillis() - time > RMEcfg.SERVER_KEEP_ALIVE_INTERVAL){
										printLog("vipa " + assocs.getVipa() + " is dead, removing");
//										System.err.println("rimuovo associazione");
										associations.remove(res);
										iter.remove();
									}
									else
										printLog("vipa " + assocs.getVipa() + " is alive");
								}
								else {
//									System.err.println("rimuovo associazione");
									associations.remove(res);
									iter.remove();
								}
							}
						}
//					}
				}
			//}
			}
		};
		// KeepAlive Thread End

		if(SERVER_KEEP_ALIVE)
			keepalivethread.start();

	}

	public String getServerName() {
		return this.serverName;
	}
	
//	//MARCO 
//	/** currently, it is blocking :-( */
//	private boolean registerToBroker(String ANAddress, int brokerSipPort) {
//		freeSlots=RMEcfg.maxMHs-associations.size();
//		AnchorNode thisAN = new AnchorNode(RMEcfg.anchor_identifier, anchor_IPaddress, port, RMEcfg.isAN, RMEcfg.isBroker, RMEcfg.isFH, freeSlots);
//
//		printLog("Trying to register " +thisAN.anchor_identifier  +" to ANBroker " + ANAddress);
//
//		String ipAddr = IpAddress.getLocalHostAddress().toString();
//		sip_provider.setOutboundProxy(new SipURL(ANAddress, SipStack.default_port));		
//		NameAddress anchorNode = new NameAddress("sip:" + ANAddress + ":"+ brokerSipPort);
//		NameAddress from = new NameAddress("sip:" + ipAddr);
//
//
//		Signal[] reqList;
//
//		BrokerRegistrationReq req = new BrokerRegistrationReq();
//		req.setSipId(RMEcfg.anchor_identifier);
//		String aaa[]= new String[1];
//		aaa[0]= anchor_IPaddress.toString() + ":" + this.port;
//		req.setIp(aaa);
//		req.setImFH(RMEcfg.isFH);
//		req.setBroker(RMEcfg.isBroker);
//		req.setImAN(RMEcfg.isAN);
//		req.setFreeSlots(this.freeSlots);
//
//		reqList = new Signal[]{req};
//
//		Message message = newUpmtMessage(anchorNode, from, reqList);
//
//		Message respMessage = startBlockingTransaction(message);
//
//		if(respMessage.getStatusLine() != null && respMessage.getStatusLine().toString().contains("200 OK")) {
//			return true;
//		}
//
//		return false;
//	}
	
	protected void printLog(String str, int level){System.out.println("RMEServer: "+str); /*super.printLog("RMEServer: "+str, level);*/}
	protected void printLog(String str){printLog(str, Log.LEVEL_LOWER);}



	// ****************************** UPMT *****************************
	protected AssociationResp handleAssociation(AssociationReq req){
		synchronized (associations) {
			printLog("ASSOCIATION for " + req.sipId);

//			String vipa = vipaManager.getNewVipa(req);
			String vipa = req.vipa;
			String[] addressInUse = req.getAddressInUse();
			addDiscoveredAddresses(vipa, addressInUse);
			
			

			//TODO what to do when isAN is false?????

			AssociationResp resp = (AssociationResp)UpmtSignal.createResp(req);


//			//MARCO 		
//			if (associations.size()>RMEcfg.maxMHs){
//				resp.associationSuccess = false;
//				resp.reason="overload";
//			}
//			else if (RMEcfg.Black_List.contains(req.sipId)) {
//				resp.associationSuccess = false;
//				resp.reason="bad user";	
//			}
//			else {
				resp.associationSuccess = true;
				resp.vipa = this.vepa;
//				resp.tsa = new Integer(RMEcfg.tsas.get(0).split(":")[1]);
				resp.tsa = new Integer(RMEcfg.rmeTsa);
				
				
				resp.addressesInUse = UPMTClient.rmeAssocAddress;

				printLog("Vipa: "+vipa);

				synchronized(associations){
					associations.put(req.sipId, new AssociationInfo(vipa, System.currentTimeMillis()));
					RMEassociations.get(serverName).put(req.sipId, new AssociationInfo(vipa, System.currentTimeMillis()));

					//finire qui
				}
//			}
			return resp;
		}
	}

//	protected boolean handleBrokerRegistration(BrokerRegistrationReq reqSignal) {
//
//		boolean added = false;
//
//		if(RMEcfg.isBroker){
//			synchronized(CurrentANList){
//
//				for (int i = 0; i <reqSignal.getIp().length; i++) {
//
//
//					AnchorNode an = new AnchorNode(reqSignal.getSipId(), 
//							new IpAddress(reqSignal.getIp()[i].split(":")[0]), Integer.parseInt(reqSignal.getIp()[i].split(":")[1]),
//							reqSignal.isImAN(), reqSignal.isImFH(), reqSignal.isBroker(),reqSignal.getFreeSlots() );
//
//					printLog("Adding Anchor Node " + reqSignal.getSipId() +" ");
//
//					AssociatedANs.put(an, System.currentTimeMillis());
//
//					if(reqSignal.isImAN() && reqSignal.getFreeSlots()>0){
//						CurrentANList.put(reqSignal.getIp()[i], System.currentTimeMillis());					
//					}
//
//					added = true;
//				}
//			}
//		}		
//
//		return added;
//	}


	protected TunnelSetupResp handleTunnelSetup(TunnelSetupReq req)
	{
		synchronized (tunnelManager) {

			printLog("TUNNEL-SETUP for " + req.sipId);
			int tunnelID = tunnelManager.getTunnelID(req.vipa, req.sipSignalingPort);
			
			TunnelSetupResp resp = null ;
			resp = (TunnelSetupResp)UpmtSignal.createResp(req);
			
			if(this.upmtClient==null) {
				resp.serverTunnelID = -1;
				return resp;
			}
			
			synchronized(associations){
				if(!associations.containsKey(req.sipId)) {
					associations.put(req.sipId, new AssociationInfo(req.vipa, System.currentTimeMillis()));
					RMEassociations.get(serverName).put(req.sipId, new AssociationInfo(req.vipa, System.currentTimeMillis()));
					
				}
			}

			if (tunnelID != -1) {

				String tunEndAddr = tunnelManager.getTunnelEndAddress(tunnelID);
				String[] yourAddressCouple = null;
				if (tunEndAddr!=null) {
					yourAddressCouple = tunEndAddr.split(":");

					resp.serverTunnelID = tunnelID;
					resp.yourAddress = yourAddressCouple[0];
					resp.yourPort = Integer.parseInt(yourAddressCouple[1]);

					synchronized(tunnels) {
						Integer TID = new Integer(tunnelID);
						TunnelInfo value = tunnels.get(TID);
						if(value == null) {
							value = new TunnelInfo(resp.serverTunnelID, resp.yourAddress, resp.yourPort, req.sipId, req.vipa, System.currentTimeMillis());
						} else {
							value.setLastAck(System.currentTimeMillis());
						}

						tunnels.put(TID, value);
						RMEtunnel.get(serverName).put(TID, value);
						UPMTClient.addRMETunnelsToGUI(resp.yourAddress);
						UPMTClient.getRMERemoteTidStatusTable().put(resp.yourAddress+":"+this.serverName, new RMETunnelInfo(resp.serverTunnelID, resp.yourAddress, resp.yourPort, req.sipId, req.vipa, System.currentTimeMillis(), RMETunnelInfo.TUNNEL_SETUP, TID.intValue()));
					}
					
					/** sincronizzare*/
					synchronized (this.upmtClient.getRmePerVipaTunnelList()) {
						String VIPA = this.upmtClient.getVipa(resp.yourAddress);
						if(UPMTClient.firstTunnel && tunnelID!=UPMTClient.TID_DROP) {
//							this.upmtClient.getAppMonitor().setDefault(tunnelID);
//							this.upmtClient.setDefaultAN(resp.yourAddress);
							UPMTClient.firstTunnel = false;
						}
						if(tunnelID!=UPMTClient.TID_DROP) {
							if(VIPA==null) {
								VIPA = req.vipa;
							}
//							this.upmtClient.addRMETunnelToTunnelList(VIPA, this.serverName, resp.yourAddress, tunnelID);
//							this.upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_UP);
						}
						else {
							if(UPMTClient.getRMERemoteTidStatusTable().containsKey(resp.yourAddress+":"+this.serverName)) {
								RMETunnelInfo tunInf = UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName);
								tunInf.setStatus(RMETunnelInfo.NO_TUNNEL);
								tunInf.setKeepaliveNumber(0);
//								UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName).setStatus(RMETunnelInfo.NO_TUNNEL);
							}
						}

//						System.err.println("VIPA: "+VIPA+" ifname: "+this.serverName+" Address: "+resp.yourAddress+" tunnelID: "+tunnelID);
					}
					/** fine sincronizzazione */

//					printLog("tunnelID: "+tunnelID+"\nyourAddress: "+yourAddressCouple[0]+":"+yourAddressCouple[1]);
					if(!UPMTClient.textMode) {
						((GUIApplicationManager) this.upmtClient.getApplicationManager()).refreshGui();
					}
					return resp;
				} else {
					if(resp.yourAddress!=null && this.serverName!=null) {
						if(UPMTClient.getRMERemoteTidStatusTable().containsKey(resp.yourAddress+":"+this.serverName)) {
							RMETunnelInfo tunInf = UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName);
							tunInf.setStatus(RMETunnelInfo.NO_TUNNEL);
							tunInf.setKeepaliveNumber(0);
//							UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName).setStatus(RMETunnelInfo.NO_TUNNEL);
						}
					}
					printLog("ERROR: your address NULL" );
					resp.serverTunnelID = tunnelID;
					resp.yourAddress = "NULL";
					return resp;				
				}
			} else {
				if(resp.yourAddress!=null && this.serverName!=null) {
					if(UPMTClient.getRMERemoteTidStatusTable().containsKey(resp.yourAddress+":"+this.serverName)) {
//						UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName).setStatus(RMETunnelInfo.NO_TUNNEL);
						RMETunnelInfo tunInf = UPMTClient.getRMERemoteTidStatusTable().get(resp.yourAddress+":"+this.serverName);
						tunInf.setStatus(RMETunnelInfo.NO_TUNNEL);
						tunInf.setKeepaliveNumber(0);
					}
				}
				printLog("ERROR: tunnelID=-1" );
				resp.serverTunnelID = tunnelID;
				return resp;
			}
		}
		
		
	}


//	protected ANListResp handleANListReq(ANListReq req){
//
//		printLog("ANListReq received from " + req.sipId);
//
//
//		String ANlist[] = null;
//
//		if(RMEcfg.isBroker){
//			ANlist = new String[CurrentANList.keySet().size()];
//
//			int i = 0;
//			for(String an: CurrentANList.keySet()){
//				ANlist[i] = an;
//				i++;
//			}
//		}
//		else{
//			ANlist = new String[RMEcfg.ANBrokerList.size()];
//
//			for(int i = 0; i < RMEcfg.ANBrokerList.size(); i++){
//				ANlist[i] = RMEcfg.ANBrokerList.get(i);
//			}			
//		}
//
//		ANListResp resp = new ANListResp();
//		resp.setAnList(ANlist);
//		resp.setBroker(RMEcfg.isBroker);
//
//		printLog("SENDING OUT MESSAGE:" + UpmtSignal.serialize(resp), Log.LEVEL_HIGH);
//
//		return resp;		
//	}


	protected boolean handleKeepAlive(KeepAlive req){

		boolean alive = false;
		int reqTunnelID = req.tunnelId;

		synchronized(associations){

			AssociationInfo assoc = associations.get(req.sipId);
			//String[] yourAddressCouple = tunnelManager.getTunnelEndAddress(tunnelID).split(":");

			if(assoc != null){
				int tunnelID = tunnelManager.getTunnelID(assoc.getVipa(), req.sipSignalingPort);
				//					System.err.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
				//					System.err.println("--------------------------------> associations vipa----->"+req.sipSignalingPort);
				//					System.err.println("--------------------------------> associations vipa----->"+assoc.getVipa());
				//					System.err.println("--------------------------------> Tunnel ID req ------> "+req.tunnelId);
				//					System.err.println("--------------------------------> Tunnel ID tunnel ------> "+tunnelManager.getTunnelID(assoc.getVipa(), req.sipSignalingPort));
				//					System.err.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
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
				if(value != null) {
					value.setLastAck(System.currentTimeMillis());
					RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(value.getYourAddress()+":"+this.serverName);
					if(ti!=null) {
						ti.setLastDelay(req.lastDelay);
						ti.setNumberRetry(req.numberRetry);
						ti.setEWMA_delay(req.EWMA_delay);
						ti.setEWMA_loss(req.EWMA_loss);
						
						if(upmtClient!=null) {
//							System.err.println(value.getYourAddress()+" delay----> "+ti.getEWMA_delay()+" nel pacchetto "+req.getEWMA_delay());
							upmtClient.setBestTunnelForVipaServer(value.getYourAddress(), ti);
						}
					}
				}
				else {
					alive = false;
				}
			}
			if(!UPMTClient.textMode) {
				((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();

			}
		}

		return alive;
	}
	
	protected boolean handleKeepAlive(KeepAlive req, JSONObject msg){

		boolean alive = false;
		int reqTunnelID = req.tunnelId;

		synchronized(associations){

			AssociationInfo assoc = associations.get(req.sipId);
			//String[] yourAddressCouple = tunnelManager.getTunnelEndAddress(tunnelID).split(":");

			if(assoc != null){
				int tunnelID = tunnelManager.getTunnelID(assoc.getVipa(), req.sipSignalingPort);
				//					System.err.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
				//					System.err.println("--------------------------------> associations vipa----->"+req.sipSignalingPort);
				//					System.err.println("--------------------------------> associations vipa----->"+assoc.getVipa());
				//					System.err.println("--------------------------------> Tunnel ID req ------> "+req.tunnelId);
				//					System.err.println("--------------------------------> Tunnel ID tunnel ------> "+tunnelManager.getTunnelID(assoc.getVipa(), req.sipSignalingPort));
				//					System.err.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
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
				if(value != null) {
					value.setLastAck(System.currentTimeMillis());
					RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(value.getYourAddress()+":"+this.serverName);
					if(ti!=null) {
						try {
							ti.setLastDelay(Integer.parseInt(msg.get("lastDelay").toString()));
							ti.setNumberRetry(Integer.parseInt(msg.get("numberRetry").toString()));
							ti.setEWMA_delay(Double.parseDouble(msg.get("EWMA_delay").toString()));
							ti.setEWMA_loss(Double.parseDouble(msg.get("EWMA_loss").toString()));
						} catch (NumberFormatException e) {
							e.printStackTrace();
						} catch (JSONException e) {
							e.printStackTrace();
						}
						if(upmtClient!=null) {
							upmtClient.setBestTunnelForVipaServer(value.getYourAddress(), ti);
						}
					}
				}
				else {
					alive = false;
				}
			}
			if(!UPMTClient.textMode) {
				((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();

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
				RMEtunnel.get(serverName).put(TID, value);
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

//	public HashMap<String, Long> getRegisteredANs(){
//		synchronized(CurrentANList){
//			return CurrentANList;
//		}
//	}

	public void clearAssociations(){
		synchronized(associations){
			associations.clear();
			RMEassociations.get(serverName).clear();
		}
	}

	public void clearTunnels(){
		synchronized(tunnels){
			tunnels.clear();
			RMEtunnel.get(serverName).clear();
		}
	}


//	public void clearRegisteredANs(){
//		synchronized(CurrentANList){
//			CurrentANList.clear();
//		}
//	}
	
	/*************************RMEPEER**********************************/
	public static void startRMEServer(final String[] args) {
		System.out.println("RMEServer Up and Running");

		String file = Default.PEER_CONFIG_FILE;
		file = file!=null?file:Default.PEER_CONFIG_FILE;
		RMEServer.RMEcfg = RMEConfigManager.instance();
		RMEcfg.ParseConfig(file);
		rmeAddresses = RMEInterface.parseConfiguration(RMEcfg.rmeNet, RMEcfg.adhocwlan);
		stopNetworkManager();
		SERVER_KEEP_ALIVE = (!RMEcfg.keepaliveKernel);
		for(int h=0; h<rmeAddresses.size(); h++) {
			checkRMEAddresses(rmeAddresses.get(h).getRmeInterface(), h);
		}
		
		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) quitAndPrintUsage();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];	

		Module.setVipaFix(RMEcfg.vepa);
		Module.setServerIfName(Default.SERVER_IFNAME);
		Vector<String> rmeTsas = new Vector<String>();
		
		for(int h=0; h<rmeAddresses.size(); h++) {
			SipProvider sipProv = new SipProvider(rmeAddresses.get(h).getIp(), 5060, SipStack.default_transport_protocols, rmeAddresses.get(h).getIp());
			
			RMEServer server = new RMEServer(file, rmeAddresses.get(h).getIp(), rmeAddresses.get(h).getRmeInterface(), sipProv, RMEcfg.vepa);
			RMEServers.add(server);
			RMESignalers.put(rmeAddresses.get(h).getRmeInterface(), sipProv);
			rmeTsas.add(rmeAddresses.get(h).getRmeInterface()+":"+RMEcfg.rmeTsa);
		}
		tunnelManager = new TunnelManager(rmeTsas,RMEcfg.serverMark);
		
		/***********************CLIENT************************/

		Thread client = new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub

				UPMTClient.startRMEClient(args, RMESignalers, RMEServers,true);
			}
		}, "Client RME Thread");


		client.start();

		/***********************END CLIENT**************************/

		System.out.println("Starting server with cfg file: " + file);

//		System.out.println("\n################################\nSECURITY RESTICTIONS:\n");
//		System.out.println("Max MHs allowed set to " + RMEcfg.maxMHs + "\n");
//		if(RMEcfg.Black_List.size() > 0){
//			System.out.println("Refusing association from following users: ");		
//			for(String baduser : RMEcfg.Black_List){
//				System.out.println(baduser + "   ");
//			}
//		}
//		System.out.println("################################\n");

		if(PRINT_STATISTICS_ON_CONSOLE){
			while(true){

				System.out.println("\n\t[" + getSystemDate(System.currentTimeMillis()) + "]");


				System.out.println("\n\t" + AssociatedANs.keySet().size() +" CURRENTLY REGISTERED ANs");

				for(AnchorNode an: AssociatedANs.keySet()) {  
					System.out.println("\t" + an.anchor_identifier +"---- Free Slots: " +an.freeSlots );
				}


				System.out.println("\n\t" + CurrentANList.keySet().size() +" ANs put on the ANList");

//				for(String an: CurrentANList.keySet()) {  
//
//					Long last_ack = CurrentANList.get(an);  
//
//					System.out.println("\t" + an + " --> "  + " [" + getSystemDate(last_ack.longValue()) + "]");
//
//				}

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


	/****************************END RME PEER*******************************/
	// ****************************** MAIN *****************************
	public static void main(String[] args)
	{
		String file = Default.SERVER_CONFIG_FILE;
		file = file!=null?file:Default.SERVER_CONFIG_FILE;
		RMEServer.RMEcfg = RMEConfigManager.instance();
		RMEcfg.ParseConfig(file);

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) quitAndPrintUsage();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];
		
		System.out.println(RMEcfg.tsas);
//		Module.setVipaFix(Default.SERVER_VIPA);
		Module.setServerIfName(Default.SERVER_IFNAME);
//		vipaManager = VipaManagerFactory.getVipaManager(RMEcfg.vipaManagerPolicy, file);
		tunnelManager = new TunnelManager(RMEcfg.tsas,RMEcfg.serverMark);
		
		for(int h=0; h<rmeAddresses.size(); h++) {
//			SipProvider sipProv = new SipProvider(getIP(rmeInterfaces[h]), 5060, SipStack.default_transport_protocols, getIP(rmeInterfaces[h]));
			SipProvider sipProv = new SipProvider(rmeAddresses.get(h).getIp(), 5060, SipStack.default_transport_protocols, rmeAddresses.get(h).getIp());
			//	new RMEServer(file, getIP(rmeInterfaces[h]), rmeInterfaces[h]);
//			
			RMEServer server = new RMEServer(file, rmeAddresses.get(h).getIp(), rmeAddresses.get(h).getRmeInterface(), sipProv, RMEcfg.vepa);
			RMEServers.add(server);
//			RMESignalers.put(rmeInterfaces[h], sipProv);
			RMESignalers.put(rmeAddresses.get(h).getRmeInterface(), sipProv);
		}
		
		System.out.println("Starting server with cfg file: " + file);
		
		System.out.println("\n################################\nSECURITY RESTICTIONS:\n");
		System.out.println("Max MHs allowed set to " + RMEcfg.maxMHs + "\n");
		if(RMEcfg.Black_List.size() > 0){
			System.out.println("Refusing association from following users: ");		
			for(String baduser : RMEcfg.Black_List){
				System.out.println(baduser + "   ");
			}
		}
		System.out.println("################################\n");


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
//					for(int l=0; l<rmeInterfaces.length; l++) {

//						for (String sipId : RMEassociations.get(rmeInterfaces[l]).keySet()) {
						for(String sipId: associations.keySet()) {  

							AssociationInfo assoc = associations.get(sipId);  

							System.out.println("\t" + sipId + " --> " + assoc.getVipa() + " [" + getSystemDate(assoc.getLastAck()) + "]");

						}
//					}
				}

				synchronized(tunnels){

					System.out.println("\n\t" + tunnels.keySet().size() + " ACTIVE TUNNELS");

//					for(int l=0; l<rmeInterfaces.length; l++) {
//						
//						for (Integer key : RMEtunnel.get(rmeInterfaces[l]).keySet()) {
						for (Integer key : tunnels.keySet()) {

							TunnelInfo value = tunnels.get(key);  

							System.out.println("\t" + key + " --> " + value.serverTunnelID + ") " + value.yourAddress + ":" + value.yourPort + " (" + value.sipId + ":" + value.vipa + ")" + " [" + getSystemDate(value.getLastAck()) + "]");

						}
//					}

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
		return RMEcfg.isAN;
	}

//	public boolean getIsBroker(){
//		return RMEcfg.isBroker;
//	}

	public int getTsaPort(){
		if(rme) {
			return RMEcfg.rmeTsa;
		}
		else {
			System.out.println(RMEcfg.tsas.get(0).split(":")[1]);
			return new Integer(RMEcfg.tsas.get(0).split(":")[1]);
		}
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
		System.err.println("usage:\n\tjava RMEServer [options]\n\toptions:" +
				"\n\t-f <config_file> specifies a configuration file");
		System.exit(-1);
	}
	
	/**
	 * Function that returns the IP address from the interface name
	 * @param ifName
	 * @return String
	 */
	public static String getIP(String ifName) {
		String ip = "";
		try {
			Process p = Runtime.getRuntime().exec("ip address show "+ifName);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			br.readLine(); 
			br.readLine(); 
			ip = br.readLine();
			p.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(ip==null || ip.length()<9) {
			ip = RoutingCheck.blankIp;
		}
		else {
			StringTokenizer tok = new StringTokenizer(ip.substring(9));
			ip = tok.nextToken("/");
		}
		
		return ip;
	}
	
	public void setUpmtClient(UPMTClient upmtClient) {
		this.upmtClient = upmtClient;
	}
	
	public UPMTClient getUpmtCient() {
		return this.upmtClient;
	}
	
	public String getVepa() {
		return this.vepa;
	}
	
	public static void checkRMEAddresses(String devName, int pos) {
		if (rmeAddresses.get(pos).getRmeInterface().equals(devName)) {
			String ip = rmeAddresses.get(pos).getIp();
			String netmask = rmeAddresses.get(pos).getNetMask();
			if(rmeAddresses.get(pos).isWless()) {
				String essid = rmeAddresses.get(pos).getSsid();
				String channel = rmeAddresses.get(pos).getChannel();
				try {
					Thread.sleep(500);
					Runtime.getRuntime().exec("sudo ifconfig "+devName+" down");
					Thread.sleep(1000);
					Runtime.getRuntime().exec("sudo iwconfig "+devName+" mode ad-hoc channel "+channel+" essid "+essid+" key off ap off"+" commit");
					Thread.sleep(1000);
					Runtime.getRuntime().exec("sudo ifconfig "+devName+" "+ip+" netmask "+netmask+" up");
					Thread.sleep(500);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			else {
				try {
					Thread.sleep(500);
					Runtime.getRuntime().exec("sudo ifconfig "+devName+" "+ip+" netmask "+netmask+" up");
					Thread.sleep(500);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static Hashtable<Integer, SipProvider> getTunnelProviders() {
		return tunnelProviders;
	}
	
	public static void stopNetworkManager() {
		try {
			Runtime.getRuntime().exec("sudo service network-manager stop");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addDiscoveredAddresses(String vepa, String[] addressesInUse) {
		if(!this.rmeDiscoveredAddresses.containsKey(vepa)) {
			this.rmeDiscoveredAddresses.put(vepa, addressesInUse);
			if(this.upmtClient!=null) {
				this.upmtClient.addDiscoveredaddresses(vepa, addressesInUse);
			}
		}
	}
	
	public void handleDelTunnel(int tid, String ifname, String address) {
		synchronized(tunnels){
			synchronized (UPMTClient.getRMERemoteTidStatusTable()) {
				if(tunnels.get(tid)!=null) {
					printLog("tunnel " + tid + " is dead, removing");							
					TunnelInfo deletedTunnel = tunnels.remove(tid);
					RMEtunnel.get(ifname).remove(tid);
					String VIPA = deletedTunnel.getVipa();
					upmtClient.delRMETunnelToTunnelList(VIPA, tid);
					upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
					if(UPMTClient.getRMERemoteTidStatusTable().containsKey(deletedTunnel.getYourAddress()+":"+serverName)) {
						if(UPMTClient.getRMERemoteTidStatusTable().remove(deletedTunnel.getYourAddress()+":"+serverName).getTid()==tid) {
							UPMTClient.getRMERemoteTidStatusTable().remove(deletedTunnel.getYourAddress()+":"+serverName);
						}
					}
					tunnelManager.removeTunnel(tid);
				}
				else {
					String VIPA = upmtClient.tryToDelRMETunnelToTunnelList(tid, ifname, address);
					if(VIPA!=null) {
						upmtClient.checkAllRMEPolicy(VIPA, UPMTClient.RME_EVENT_TUN_DOWN);
					}

					tunnelManager.removeTunnel(tid);
				}
			}
		}
	}

	public void handleInfoTunnel(int tid, String ifname, String address, int delay, int numberRetry, double ewmadelay, double ewmaloss) {
		synchronized(tunnels) {
			RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(address+":"+ifname);
			if(ti!=null) {
				if(ti.getStatus()!=RMETunnelInfo.TUNNEL_SETUP) {
					ti.setStatus(RMETunnelInfo.TUNNEL_SETUP);
					if(!UPMTClient.getRMETunnelsToGUI().contains(ti.getYourAddress())) {
						UPMTClient.addRMETunnelsToGUI(ti.yourAddress);
					}
				}
				ti.setLastDelayAndNumberRetry(delay, numberRetry);
				ti.incrementKeepaliveNumber();
				if(ti.getKeepaliveNumber()==2) {
					this.upmtClient.addRMETunnelToTunnelList(ti.getVipa(), this.serverName, ti.getYourAddress(), ti.getTid());
					this.upmtClient.checkAllRMEPolicy(ti.getVipa(), UPMTClient.RME_EVENT_TUN_UP);
					UPMTClient.getRMERemoteTidStatusTable().put(address+":"+ifname, ti);
					if(upmtClient!=null) {
						upmtClient.setBestTunnelForVipaServer(address, ti);
					}
				}
				else if(ti.getKeepaliveNumber()>=2) {
					UPMTClient.getRMERemoteTidStatusTable().put(address+":"+ifname, ti);
					if(upmtClient!=null) {
						upmtClient.setBestTunnelForVipaServer(address, ti);
					}
				}
			}
		}
	}

	
}
