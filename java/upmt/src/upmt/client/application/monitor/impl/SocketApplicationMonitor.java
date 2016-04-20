package upmt.client.application.monitor.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

import org.istsms.util.Javabean2JSON;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;
import org.zoolu.sip.provider.SipProvider;

import upmt.client.UPMTClient;
import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.application.monitor.impl.standard.AppMessage;
import upmt.client.application.monitor.impl.standard.AppMonMessage;
import upmt.client.application.monitor.impl.standard.ConnNotifMessage;
import upmt.client.application.monitor.impl.standard.Constants;
import upmt.client.core.Socket;
import upmt.os.Shell;

public class SocketApplicationMonitor implements ApplicationMonitor
{
	/** The listener for the application-or-socket change event */
	private ApplicationMonitorListener listener = null;
	private static UPMTClient upmtClient = null;
	DatagramPacket packet =null;
	private Thread thisThread;
	private Thread appMonThread;
	private final int PORT_L = 35002;
	private final int PORT_S = 35000;

	private final String LOCALHOST = "127.0.0.1";
	private DatagramSocket sendsocket;

	private Vector<String> openedApp;
	//TODO:qnd ci sar� la rimozione dei socket bisogna salvarsi i socket aperti di ciascuna app in modo da lanciare appClosed quando si rimuove l'ultimo.

	public SocketApplicationMonitor()
	{
		try
		{
			stop();
			appMonThread = new Thread(new Runnable()
			{
				public void run()
				{
					Shell.executeCommand(new String[]{"upmt-appmon", ""+PORT_S, ""+PORT_L});
				}
			});
			appMonThread.start();		
			sendsocket = new DatagramSocket();
			sendsocket.connect(Inet4Address.getByName(LOCALHOST), PORT_S);
			openedApp = new Vector<String>();	
		}
		catch (SocketException e) {e.printStackTrace();}
		catch (UnknownHostException e) {e.printStackTrace();}
	}

	public void startListen(ApplicationMonitorListener listener)
	{
		this.listener = listener;
		thisThread = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					DatagramSocket srv = new DatagramSocket(PORT_L, Inet4Address.getByName(LOCALHOST));
					byte[] buf = new byte[800];
					while (true) {	
						DatagramPacket packet = new DatagramPacket(buf, buf.length);
						srv.receive(packet);
						String pkt = new String(buf);
						if(pkt!=null) {
							if(pkt.trim().length()!=0) {
								buf = new byte[800];
								ConnNotifMessage msg = null;
								try{
									msg = (ConnNotifMessage) Javabean2JSON.fromJSONObject(new JSONObject(pkt), ConnNotifMessage.class);
									if(msg!=null) {
										Socket socket;
										if(msg.command!=null) {
											if(msg.command.equals("deltun")) {
												upmtClient.handleDelTun(msg.tid, msg.ifname, msg.daddr);
											}
											else if(msg.command.equals("delconn")){
												socket = new Socket(msg.key.proto,msg.key.vipa, msg.key.dstIp, msg.key.srcPort, msg.key.dstPort);
												String appName = SocketApplicationMonitor.this.listener.getAppnameFromSocket(socket);
												if(appName != null){
													//System.err.println("Chiudo una socket dell'appname: " + appName);
													SocketApplicationMonitor.this.listener.socketClosed(socket);
													if(!SocketApplicationMonitor.this.listener.isActiveApp(appName)){
														//System.err.println("AppName senza più socket... rimuovo!!!");
														SocketApplicationMonitor.this.listener.appClosed(appName);
														openedApp.remove(appName);
													}
												}
											}
											else if(msg.command.equals("newconn")) {
												if(msg.appname.equals("java") && ((msg.key.srcPort==5060  && msg.key.dstPort==5066) || (msg.key.srcPort==5066 && msg.key.dstPort==5060))) {
													continue;
												}
												if(msg.appname.equals("java") && (msg.key.dstPort==5066)) {
													boolean signalPort = false;
													Iterator<Integer> sipProviderIterator = upmtClient.getTunnelProviders().keySet().iterator();
													while(sipProviderIterator.hasNext()) {
														Integer tid = sipProviderIterator.next(); 
														if(msg.key.srcPort==upmtClient.getTunnelProviders().get(tid).getPort()) {
															signalPort = true;
															break;
														}
													}
													if(signalPort) {
														continue;
													}
												}
												if(msg.appname.equals("com.and.gui")) {
													continue;	
												}
												// nc viene riconosciuta da appmon come nc.openbsd
												if(msg.appname.equals("nc.openbsd")) {
													msg.appname = "nc";
												}
												if (!openedApp.contains(msg.appname)){
													openedApp.add(msg.appname);
													SocketApplicationMonitor.this.listener.appOpened(msg.appname);
												}				
												SocketApplicationMonitor.this.listener.socketOpened(msg.appname, msg.key);
											}
											else if(msg.command.equals("infotun")) {
												upmtClient.handleInfoTunnel(msg.tid, msg.ifname, msg.daddr, msg.delay, msg.loss, msg.ewmadelay, msg.ewmaloss);
											}
										}
										else {
											if(msg.appname.equals("java") && ((msg.key.srcPort==5060  && msg.key.dstPort==5066) || (msg.key.srcPort==5066 && msg.key.dstPort==5060))) {
												continue;
											}
											if(msg.appname.equals("com.and.gui")) {
												continue;	
											}
											// nc viene riconosciuta da appmon come nc.openbsd
											if(msg.appname.equals("nc.openbsd")) {
												msg.appname = "nc";
											}
											// (bonus)
											if((msg.key.dstPort == 53) && (msg.key.proto.equals("udp"))){	
												//System.err.println("msg.key.proto: " + msg.key.proto);
												continue;
											}
											if(!msg.appname.equals("delete")){
												if (!openedApp.contains(msg.appname)){
													openedApp.add(msg.appname);
													SocketApplicationMonitor.this.listener.appOpened(msg.appname);
												}				
												SocketApplicationMonitor.this.listener.socketOpened(msg.appname, msg.key);
											}
											else{
												socket = new Socket(msg.key.proto,msg.key.vipa, msg.key.dstIp, msg.key.srcPort, msg.key.dstPort);
												String appName = SocketApplicationMonitor.this.listener.getAppnameFromSocket(socket);
												if(appName != null){
													//System.err.println("Chiudo una socket dell'appname: " + appName);
													SocketApplicationMonitor.this.listener.socketClosed(socket);
													if(!SocketApplicationMonitor.this.listener.isActiveApp(appName)){
														//System.err.println("AppName senza più socket... rimuovo!!!");
														SocketApplicationMonitor.this.listener.appClosed(appName);
														openedApp.remove(appName);
													}
												}													
											}
										}


									}
									else {
										System.err.println("notifica nulla");
										System.err.println("mi arriva questo messaggio-----> ");
										System.err.println(pkt);
										System.err.println("--------------fine messaggio ---------");
									}
								}
								catch(IllegalArgumentException e) {
									System.err.println("eccezione parsing messaggio da upmtconf");
									System.err.println(e.getMessage());
									
								}
							}
						}
					}
				}
				catch (IOException e) {e.printStackTrace();}
				catch (JSONException e) {e.printStackTrace();}
			}
		});
		thisThread.start();
	}
	public void stop() {
		//#ifdef ANDROID
		//		Shell.executeRootCommand(new String[]{"killall", "upmt-appmon"});
		//#else
		Shell.executeCommand(new String[]{"killall", "upmt-appmon"});
		//#endif
	}

	/** sends the local message that instructs the appmon to assign a TID to the
	 * first packet of sockets that will be originated by this application
	 */
	public void setApp(String appName, int tunnelID) {
		//socketSend(new AppMessage(Constants.CMD_ADD, appName, tunnelID)); // OLD VERSION
		String VIPA = "0"; //a 0 VIPA is sent to xt_UPMT_ex, it will be not considered
		socketSend(new AppMessage(Constants.CMD_ADD, VIPA, appName, tunnelID));
	}
	public void rmeSetAppAndVIPA (String appName, String VIPA, int tunnelID) {
		socketSend(new AppMessage(Constants.CMD_ADD, VIPA, appName, tunnelID));
	}

	public void rmApp(String appName) {socketSend(new AppMessage(Constants.CMD_RM, appName, -1));}
	public void flushAppList() {socketSend(new AppMessage(Constants.CMD_FLUSH_LIST, "", -1));}

	public void setDefault(int tunnelID) {
		socketSend(new AppMessage(Constants.CMD_SET_DEFAULT_TID, "", tunnelID));
	}

	//TODO:
	public void setNoUpmtApp(String appName) {}
	public void rmNoUpmtApp(String appName) {}
	public void flushNoUpmtList() {}
	public void setAppFlow(String dstIp, int tunnelID) {}
	public void rmAppFlow(String dstIp, int port) {}
	public void flushAppFlowList() {}

	private void socketSend(AppMonMessage msg) {
		try {
			String json = new JSONObject(msg).toString();
			//System.out.println("socketSend: " + json);
			byte[] pkt = json.getBytes();
			sendsocket.send(new DatagramPacket(pkt, pkt.length));
		}
		catch (SocketException e) {e.printStackTrace();}
		catch (UnknownHostException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
	}

	// ****************************** Logs *****************************
	@SuppressWarnings("unused")
	private void printLog(String text, int loglevel) {UPMTClient.printGenericLog(this, text, loglevel);}

	@Override
	public void setClient(UPMTClient client) {
		// TODO Auto-generated method stub
		upmtClient = client;

	}

}
