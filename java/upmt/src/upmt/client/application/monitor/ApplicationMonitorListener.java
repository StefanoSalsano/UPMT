package upmt.client.application.monitor;

import upmt.client.core.Socket;

public interface ApplicationMonitorListener
{
	public void appOpened(String appName);
	public void socketOpened(String appName, Socket socket);
	public void socketClosed(Socket socket);
	public void appClosed(String appName);
	//(bonus)
	public boolean isActiveApp(String appName);
	public String getAppnameFromSocket(Socket socket);
}
