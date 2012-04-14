package upmt.client.network;

import upmt.client.core.InterfaceInfo;

public interface NetworkMonitorListener
{
	/** Fired when a NetworkInterface become active */
	public void onNetworkInterfaceAdding(String ifName, InterfaceInfo newIf);

	/** Fired when a NetworkInterface become inactive */
	public void onNetworkInterfaceRemoval(String ifName);
}
