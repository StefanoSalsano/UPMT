package upmt.client.network;

import java.util.Hashtable;

import upmt.client.core.InterfaceInfo;

public interface NetworkMonitor
{
	/** Set a listener for any interfaces list changing */
	public void startListen(NetworkMonitorListener listener);

	/** Remove the listener for the monitor */
	public void stop();

	/** Return an hashtable that, for each interfaces, matches the name with the characteristics (IP address and gateway) */
	public Hashtable<String, InterfaceInfo> getInterfaceList();

	/** Set the interfaces to skip */
	public void setInterfToSkip(String[] interfs);
}
