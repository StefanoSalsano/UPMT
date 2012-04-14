package upmt.client.application.monitor;

public interface ApplicationMonitor
{
	/** Set a listener for any socket list changing */
	public void startListen(ApplicationMonitorListener listener);

	/** Remove the listener for the monitor */
	public void stop();

	//AppList
	public void setApp(String appName, int tunnelID);
	public void rmApp(String appName);
	public void setDefault(int tunnelID);
	public void flushAppList();

	//NoUpmtList
	public void setNoUpmtApp(String appName);
	public void rmNoUpmtApp(String appName);
	public void flushNoUpmtList();

	//AppFlowList
	public void setAppFlow(String dstIp, int tunnelID);
	public void rmAppFlow(String dstIp, int port);
	public void flushAppFlowList();

}
