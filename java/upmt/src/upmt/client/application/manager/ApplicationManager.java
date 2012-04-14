package upmt.client.application.manager;

import upmt.client.core.Socket;

/** 
 * 
 * This interface is implemented by GUIApplicationManager
 */
public interface ApplicationManager
{
	public void startListen(ApplicationManagerListener listener);

	/**
	 * displays an application on the list of active applications
	 * (i.e. applications with open sockets)
	 */
	public void addApp(String appName);
	/**
	 * undisplays an application from the list of active applications
	 * (i.e. applications with open sockets)
	 */
	public void removeApp(String appName);
	
	public void addSocket(String appName, Socket socket);
	public void rmvSocket(Socket socket);

	//status bar
	public void changeDefaultAN(String ifName);
	public void setConnectedAN(int n);

	//per fornire le politiche + frequenti nel comboBox
	public void addInterface(String ifName);
	public void removeInterface(String ifName);

	/**
	 * refreshes (displayed) data after a policy check
	 */
	public void onPolicyCheck();

	public void startWorking();
}
