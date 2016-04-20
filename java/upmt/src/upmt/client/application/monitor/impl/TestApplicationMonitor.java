package upmt.client.application.monitor.impl;

import upmt.client.UPMTClient;
import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.core.Socket;
import upmt.client.sip.SipSignalManager;

/**
 *  this is only used for development purpose 
 */
public class TestApplicationMonitor implements ApplicationMonitor
{
	/** The listener for the application-or-socket change event */
	private ApplicationMonitorListener listener = null;
	private Thread thisThread;

	public void startListen(ApplicationMonitorListener listener)
	{
		this.listener = listener;
		thisThread = new Thread(new Runnable() {
			public void run() {
				for(int i=0; i<1; i++) {
					try {Thread.sleep(8000);}
					catch (InterruptedException e) {e.printStackTrace();}

					String appName = "nc"+(i==0?"":i);
					TestApplicationMonitor.this.listener.appOpened(appName);
					TestApplicationMonitor.this.listener.socketOpened(appName, new Socket("udp","5.6.7.8","192.168.100.220",4000+i,5000+i));
				}
			}
		});
		thisThread.start();
	}
	public void stop() {}
	public void setApp(String appName, int tunnelID) {System.out.println("setApp "+appName+" to tunnel "+tunnelID);}
	public void rmApp(String appName) {System.out.println("RmApp "+appName);}
	public void setDefault(int tunnelID) {System.out.println("setDefault to tunnel "+tunnelID);}
	public void flushAppList() {System.out.println("flushList");}
	public void setNoUpmtApp(String appName) {}
	public void rmNoUpmtApp(String appName) {}
	public void flushNoUpmtList() {}
	public void setAppFlow(String dstIp, int tunnelID) {}
	public void rmAppFlow(String dstIp, int port) {}
	public void flushAppFlowList() {}
	@Override
	public void rmeSetAppAndVIPA(String appName, String VIPA, int tunnelID) {}
	@Override
	public void setClient(UPMTClient upmtClient) {
		// TODO Auto-generated method stub
		
	}
}
