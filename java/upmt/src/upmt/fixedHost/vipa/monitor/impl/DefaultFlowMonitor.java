package upmt.fixedHost.vipa.monitor.impl;

import upmt.fixedHost.vipa.monitor.FlowMonitor;
import upmt.fixedHost.vipa.monitor.FlowMonitorListener;
import upmt.fixedHost.vipa.monitor.impl.message.FlowMonMessage;
import upmt.fixedHost.vipa.monitor.impl.message.FlowNotifMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.istsms.util.Javabean2JSON;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;

import upmt.os.Shell;

public class DefaultFlowMonitor implements FlowMonitor
{
	/** The listener for the application-or-socket change event */
	private FlowMonitorListener listener = null;
	private Thread thisThread;
	private Thread flowMonThread;
	private final int PORT_L = 35002;
	private final int PORT_S = 35000;
	
	private final String LOCALHOST = "127.0.0.1";
	private DatagramSocket sendsocket;
	
	public DefaultFlowMonitor()
	{
		try
		{
			stop();
			flowMonThread = new Thread(new Runnable()
			{
				public void run()
				{
					Shell.executeCommand(new String[]{"upmt-flowmon", ""+PORT_S, ""+PORT_L});
				}
			});
			flowMonThread.start();
			
			sendsocket = new DatagramSocket();
			sendsocket.connect(Inet4Address.getByName(LOCALHOST), PORT_S);
		}
		catch (SocketException e) {e.printStackTrace();}
		catch (UnknownHostException e) {e.printStackTrace();}
	}

	public void startListen(FlowMonitorListener listener)
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
					while (true)
					{
						DatagramPacket packet = new DatagramPacket(buf, buf.length);
						srv.receive(packet);
						String pkt = new String(buf);
						FlowNotifMessage msg = (FlowNotifMessage) Javabean2JSON.fromJSONObject(new JSONObject(pkt), FlowNotifMessage.class);
						DefaultFlowMonitor.this.listener.flowOpened(msg.srcAddress, msg.srcPort, msg.vipa, msg.vipaPort);
					}
				}
				catch (IOException e) {e.printStackTrace();}
				catch (JSONException e) {e.printStackTrace();}
			}
		});
		thisThread.start();
	}

	public void stop() {Shell.executeCommand(new String[]{"killall", "upmt-flowmon"});}

	private void socketSend(FlowMonMessage msg)
	{
		try
		{
			String json = new JSONObject(msg).toString();
			byte[] pkt = json.getBytes();
			sendsocket.send(new DatagramPacket(pkt, pkt.length));
		}
		catch (SocketException e) {e.printStackTrace();}
		catch (UnknownHostException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
	}

	public void addAN(String ANAddress) {socketSend(new FlowMonMessage(FlowMonMessage.CMD_ADD_AN, ANAddress));}
	public void setVipaRange(String startAddress, String endAddress) {socketSend(new FlowMonMessage(FlowMonMessage.CMD_SET_VIPA_RANGE, startAddress+":"+endAddress));}
}
