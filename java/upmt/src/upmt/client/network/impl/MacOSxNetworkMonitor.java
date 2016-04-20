package upmt.client.network.impl;

import java.util.Hashtable;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;
import upmt.client.network.NetworkMonitor;
import upmt.client.network.NetworkMonitorListener;

public class MacOSxNetworkMonitor implements NetworkMonitor
{

	public Hashtable<String, InterfaceInfo> getInterfaceList()
	{
		return null;
	}

	public void startListen(NetworkMonitorListener listener)
	{
	}

	public void stop()
	{
	}

	public void setInterfToSkip(String[] interfs)
	{	
	}
}
