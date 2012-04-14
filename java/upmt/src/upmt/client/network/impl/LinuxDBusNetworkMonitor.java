package upmt.client.network.impl;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

import org.freedesktop.DBus.Properties;
import org.freedesktop.NetworkManager;
import org.freedesktop.NetworkManager.Device;
import org.freedesktop.NetworkManager.Device.StateChanged;
import org.freedesktop.NetworkManager.DeviceAdded;
import org.freedesktop.NetworkManager.DeviceRemoved;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;
import upmt.client.network.NetworkMonitor;
import upmt.client.network.NetworkMonitorListener;
import upmt.client.rme.Route;
import upmt.client.rme.RoutingCheck;
import upmt.os.Shell;

/** Network monitor for Linux. Require DBus. */
public class LinuxDBusNetworkMonitor implements NetworkMonitor
{
	private static final UInt32 NM_DEVICE_STATE_ACTIVATED = new UInt32(8); //The device is active.

	private static final String NETWORK_MANAGER_NAME = NetworkManager.class.getCanonicalName();
	private static final String DEVICE_NAME = NETWORK_MANAGER_NAME + ".Device";
	//private static final String DHCP_NAME = NETWORK_MANAGER_NAME + ".DHCP4Config";
	private static final String IP4_NAME = NETWORK_MANAGER_NAME + ".IP4Config";
	private static final String NM_PATH = "/"+NETWORK_MANAGER_NAME.replace('.', '/');

	/** The listener for the Network Interface change event */
	private NetworkMonitorListener listener;

	/** The connection to DBus. Used to interrogate and receive signal from the NetworkManager */
	private DBusConnection connection;

	/** The NetworkManager instance */
	private NetworkManager netMan;
	
	private Hashtable<String,DeviceHandler> handlerList;
	private AddDeviceHandler addHandler;
	private RmvDeviceHandler rmvHandler;

	/** List of the interfaces out of UPMT control */
	private String[] ifToSkip = new String[]{};

	public LinuxDBusNetworkMonitor()
	{
		try
		{
			connection = DBusConnection.getConnection(DBusConnection.SYSTEM);
			netMan = connection.getRemoteObject(NETWORK_MANAGER_NAME, NM_PATH, NetworkManager.class);
		}
		catch (DBusException De)
		{
			System.err.println("Could not connect to bus");
			De.printStackTrace();
		}
	}

	public void setInterfToSkip(String[] interfs)
	{
		this.ifToSkip = interfs;
	}

	public Hashtable<String, InterfaceInfo> getInterfaceList()
	{
		Hashtable<String, InterfaceInfo> ret = new Hashtable<String, InterfaceInfo>();
		for (DBusInterface dev : netMan.GetDevices())
		{
			Device device = (Device)dev;
			String devName = getDevName(device);

			if(!deviceToBeSkipped(devName))
			{
				InterfaceInfo info = getInfo(devName, device);
				if(info!=null)
				{
					if(devName.startsWith("tty"))
					{
						String result = Shell.executeCommand(new String[]{"sh","-c", "ifconfig | grep -B 1 "+info.ipAddress});
						devName = result.trim().split(" ", 2)[0];
						info.id = devName.trim();
					}
					ret.put(devName, info);
				}
				System.out.println("DEBUG: Device name "+devName+"\n");
			}
		}
		return ret;
	}

	public void startListen(NetworkMonitorListener listener)
	{
		this.listener = listener;
		this.handlerList = new Hashtable<String, DeviceHandler>();
		this.addHandler = new AddDeviceHandler();
		this.rmvHandler = new RmvDeviceHandler();

		if (listener!=null && connection!=null && netMan!=null) try
		{
			connection.addSigHandler(DeviceAdded.class, netMan, addHandler);
			connection.addSigHandler(DeviceRemoved.class, netMan, rmvHandler);
			for (DBusInterface device : netMan.GetDevices()) addDevice((Device)device);
		}
		catch (DBusException e) {e.printStackTrace();}
	}

	public void stop() {
		this.listener = null;
		if(connection!=null) {
			try {
				connection.removeSigHandler(DeviceAdded.class, netMan, addHandler);
				connection.removeSigHandler(DeviceRemoved.class, netMan, rmvHandler);
				if (handlerList!=null) {
					for (String devName : handlerList.keySet()) {
						DeviceHandler handler = handlerList.get(devName);
						connection.removeSigHandler(StateChanged.class, handler.getDevice(), handler);
					}
				}
			} catch (DBusException e) {
				e.printStackTrace();
			}	
		}
	}

	private String getDevName(Device device) {return ((Properties)device).Get(DEVICE_NAME, "Interface");}
	private boolean deviceToBeSkipped(String devName) {return Arrays.asList(ifToSkip).contains(devName);}

	private synchronized void addDevice(Device device)
	{
		String devName = getDevName(device);
		if(deviceToBeSkipped(devName)) return;
		DeviceHandler handler = new DeviceHandler(devName, device);
		if(connection!=null)
			try {connection.addSigHandler(StateChanged.class, device, handler);}
			catch (DBusException e) {e.printStackTrace();}
		handlerList.put(devName, handler);
	}

	private synchronized void rmvDevice(Device device)
	{
		String devName = getDevName(device);
		if(deviceToBeSkipped(devName)) return;
		if(connection!=null)
			try {connection.removeSigHandler(StateChanged.class, device, handlerList.remove(devName));}
			catch (DBusException e) {e.printStackTrace();}
	}

	private class AddDeviceHandler implements DBusSigHandler<DeviceAdded>
	{
		public void handle(DeviceAdded signal)
		{addDevice((Device)signal.a);}
	}

	private class RmvDeviceHandler implements DBusSigHandler<DeviceRemoved>
	{
		public void handle(DeviceRemoved signal) 
		{rmvDevice((Device)signal.a);}
	}

	private class DeviceHandler implements DBusSigHandler<StateChanged>
	{
		private String devName;
		private Device device;
		public DeviceHandler(String devName, Device device){this.devName=devName;this.device=device;}
		public Device getDevice(){return device;}
		public void handle(StateChanged signal)
		{
			if(signal.new_state.equals(NM_DEVICE_STATE_ACTIVATED)) interfAdding(devName, getInfo(devName, device));
			else if(signal.old_state.equals(NM_DEVICE_STATE_ACTIVATED)) interfRemoval(devName);
		}
	}

	private synchronized void interfAdding(String devName, InterfaceInfo info)
	{LinuxDBusNetworkMonitor.this.listener.onNetworkInterfaceAdding(devName, info);}

	private synchronized void interfRemoval(String devName)
	{LinuxDBusNetworkMonitor.this.listener.onNetworkInterfaceRemoval(devName);}


	//TODO: estendere con informazioni + approfondite!!!!
	private InterfaceInfo getInfo(String devName, Device device)
	{
		Properties devProperties = (Properties) device;
//		String dhcpPropertiesPath = ((Path)devProperties.Get(DEVICE_NAME, "Dhcp4Config")).getPath();
//		if (dhcpPropertiesPath.equals("/")){
//			return null;
//		}
		//Skip devices not connected (with no dhcp-configuration)
		
//		Properties dhcpProperties = null;
//		try {dhcpProperties = connection.getRemoteObject(NETWORK_MANAGER_NAME, dhcpPropertiesPath, Properties.class);}
//		catch (DBusException e) {e.printStackTrace(); return null;}
//		Map<String,Variant> dhcpOption = dhcpProperties.Get(DHCP_NAME, "Options");
//
//		String ipaddr=null, gw=null;
//		for (String key : dhcpOption.keySet())
//			if (key.equals("ip_address"))
//				ipaddr = dhcpOption.get(key).toString().replaceAll("\\[", "").replaceAll("\\]","");
//			else if (key.equals("routers"))
//				gw = dhcpOption.get(key).toString().replaceAll("\\[", "").replaceAll("\\]","");
		
		String ipv4PropertiesPath = ((Path)devProperties.Get(DEVICE_NAME, "Ip4Config")).getPath();
		if (!ipv4PropertiesPath.equals("/")) { 
			Properties ipv4Properties = null;
			try {ipv4Properties = connection.getRemoteObject(NETWORK_MANAGER_NAME, ipv4PropertiesPath, Properties.class);}
			catch (DBusException e) {e.printStackTrace(); return null;}
	
			Vector<Object> ipv4Option = ipv4Properties.Get(IP4_NAME, "Addresses");
			int intIP = ((UInt32) ((Vector<Object>)ipv4Option.get(0)).get(0)).intValue();
			int intNetmask = ((UInt32) ((Vector<Object>)ipv4Option.get(0)).get(1)).intValue();
			int intGW = ((UInt32) ((Vector<Object>)ipv4Option.get(0)).get(2)).intValue();
			
			return new InterfaceInfo(devName, "", ipFromInt(intIP), intIP, intNetmask, ipFromInt(intGW), "");
		}
		else if(UPMTClient.getRME()) { //necessary for RME package to check whether an interface is handled by OLSR and not by network-manager
			for(int i=0; i<UPMTClient.getRMEInterfacesList().size(); i++) {
				if(UPMTClient.getRMEInterfacesList().get(i).equals(devName)) {
					return new InterfaceInfo(devName, "", RoutingCheck.getIP(devName), RoutingCheck.intFromIP(RoutingCheck.getIP(devName)), 32, RoutingCheck.blankIp, "");
				}
			}
		}
		return null;
	}

	public static String ipFromInt(int intIP)
	{
		byte[] byteIP = new byte[] {
				(byte) ((intIP & 0x000000ff)),
				(byte) ((intIP & 0x0000ff00)>>8),
				(byte) ((intIP & 0x00ff0000)>>16),
				(byte) ((intIP & 0xff000000)>>24)};
		try {return Inet4Address.getByAddress(byteIP).getHostAddress();}
		catch (UnknownHostException e) {return "";}
	}
	
	
	
	/* "dhcpOption" contains the configuration options provided from DHCP.
	 * EXAMPLE:
	 *				broadcast_address: [10.0.2.255]
	 *				dhcp_lease_time: [86400]
	 *				dhcp_message_type: [5]
	 *				dhcp_server_identifier: [10.0.2.2]
	 *				domain_name: [ ]
	 *				domain_name_servers: [192.168.1.1]
	 *				expiry: [1273760220]
	 *				ip_address: [10.0.2.15]
	 *				network_number: [10.0.2.0]
	 *				routers: [10.0.2.2]
	 *				subnet_mask: [255.255.255.0]
	 */

	// ****************************** Logs *****************************
	private void printLog(String text, int loglevel) {UPMTClient.printGenericLog(this, text, loglevel);}
}
