package upmt.client.application.monitor.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Vector;

import org.istsms.util.Javabean2JSON;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;

import upmt.client.UPMTClient;
import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.application.monitor.impl.standard.AppMessage;
import upmt.client.application.monitor.impl.standard.AppMonMessage;
import upmt.client.application.monitor.impl.standard.ConnNotifMessage;
import upmt.client.application.monitor.impl.standard.Constants;
import upmt.os.Shell;

public class SocketApplicationMonitor implements ApplicationMonitor
{
	/** The listener for the application-or-socket change event */
	private ApplicationMonitorListener listener = null;
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
						buf = new byte[800];
						ConnNotifMessage msg = (ConnNotifMessage) Javabean2JSON.fromJSONObject(new JSONObject(pkt), ConnNotifMessage.class);

						//System.out.println("SocketMonitor received: " + pkt);
						if(msg.appname.equals("java") || msg.appname.equals("com.and.gui"))
							continue;

						if (!openedApp.contains(msg.appname)) {
							openedApp.add(msg.appname);
							SocketApplicationMonitor.this.listener.appOpened(msg.appname);
						}
						
						SocketApplicationMonitor.this.listener.socketOpened(msg.appname, msg.key);
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
		socketSend(new AppMessage(Constants.CMD_ADD, appName, tunnelID));
	}
	
	public void rmApp(String appName) {socketSend(new AppMessage(Constants.CMD_RM, appName, -1));}
	public void setDefault(int tunnelID) {socketSend(new AppMessage(Constants.CMD_SET_DEFAULT_TID, "", tunnelID));}
	public void flushAppList() {socketSend(new AppMessage(Constants.CMD_FLUSH_LIST, "", -1));}

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
	private void printLog(String text, int loglevel) {UPMTClient.printGenericLog(this, text, loglevel);}

}
