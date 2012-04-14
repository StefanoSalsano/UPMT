/*
* MMUSE - https://netgroup.uniroma2.it/twiki/bin/view.cgi/Netgroup/MMUSEProject
* Copyright (C) 2004  by:
* by Luca Veltri - University of Parma - Italy
* Andrea Polidoro - University of Rome "Tor Vergata" - Italy
* Stefano Salsano - University of Rome "Tor Vergata" - Italy
*
* MMUSE is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* MMUSE is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with MMUSE; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* Author(s):
* Luca Veltri (luca.veltri@unipr.it)
* Andrea Polidoro (andrea.polidoro@uniroma2.it)
* Stefano Salsano (stefano.salsano@uniroma2.it)
*/
package upmt.client.core;

import java.net.NetworkInterface;
import java.util.Hashtable;

//#ifdef ANDROID
//import upmt.client.network.impl.AndroidNetworkMonitor;
//#endif
import upmt.client.network.impl.LinuxDBusNetworkMonitor;
import upmt.client.network.impl.netprobe.WirelessProber;

public class InterfaceInfo
{
	/** VARIABLE OF STATE : 20110206 SS: it is not used!*/
	public static final int INTERFACE_STATUS_IDLE = 0;
//	public static final int INTERFACE_STATUS_LOCTUNCREATE_SENT = 1; //MESSAGGIO LOCALE
//	public static final int INTERFACE_STATUS_LOCAL_TUNNEL_CREATE = 2; // quando ricevo la risposta locale dal tmc 
//	public static final int INTERFACE_STATUS_TUNNEL_SET_REQ_SENT = 3; //invio il messaggio all'mms
//	public static final int INTERFACE_STATUS_TUNNEL_SET_RESPONSE_RECEIVED = 4; //ricevo la risposta dell'mms

	public static final int IFANUnknown = 0;
	public static final int IFANFailure = 1;
	public static final int IFANOK = 2;
	

	
	//TODO: #public??
	/** Network interface unique identifier */
	public String id;

	/** Description of the network interface as returned by the OS */
	public String description;

	/** IP address associated with the network interface */
	public String ipAddress;
	public int ipAddressInt;
	
	/** Netmask associated with the network interface */
	public int netmask;
	
	/** Prefix associated with the network interface */
	public String prefix;

	/** Default gateway associated with the network interface */
	public String defaultGw;

	/** Handover server ip address associated to the network interface */
	public String hsAddr;

	/** Queue associated with the network interface */
	private String queue;

	public int interface_status = INTERFACE_STATUS_IDLE;
	
	private boolean ipRulePresent = false;
	private boolean isWifi;
	private boolean isUMTS;
	private boolean classified;	
	private int sigLevel;
	
	/** Serial port number of the UMTS network interface from which retrieve signal strength level */
	public int UMTSport;
	private static Hashtable<String,Integer> ANIFStatus = new Hashtable<String, Integer>();
	
	public boolean isIpRulePresent()
	{return ipRulePresent;}

	public void setIpRulePresent(boolean ipRulePresent)
	{this.ipRulePresent = ipRulePresent;}
	
	/**
	 * Default constructor
	 * @param ni {@link NetworkInterface} object
	 * @param ipAddr IP address to associate with the network interface
	 * @param defaultgw default gateway to associate with the network interface
	 */
	public InterfaceInfo (NetworkInterface ni, String ipAddr, int ipAddrInt, int netmask, String defaultgw, String def_queue)
	{
		ipAddress = ipAddr;
		ipAddressInt = ipAddrInt;
		id = ni.getName().trim();
		description = ni.getDisplayName().trim();
		defaultGw = defaultgw;
		queue = def_queue;
		isWifi = isUMTS = classified = false;
		this.netmask = netmask;
		this.prefix = getPrefix(ipAddressInt, netmask);
	}
	
	/**
	 * Default constructor
	 * @param ifName The name of the interface
	 * @param description A human readable String describing the network device. 
	 * @param ipAddr IP address to associate with the network interface
	 * @param defaultgw default gateway to associate with the network interface
	 */
	public InterfaceInfo(String ifName, String description, String ipAddr, int ipAddrInt, int netmask, String defaultgw, String def_queue)
	{
		ipAddress = ipAddr;
		ipAddressInt = ipAddrInt;
		id = ifName.trim();
		this.netmask = netmask;
		description = description.trim();
		defaultGw = defaultgw;
		queue = def_queue;
		isWifi = isUMTS = classified = false;
		this.prefix = getPrefix(ipAddressInt, netmask);
	}
	
//	public static int ipToInt(final String addr) {
//		final String[] addressBytes = addr.split("\\.");
//		int ip = 0;
//		for (int i = 0; i < 4; i++) {
//			ip <<= 8;
//			ip |= Integer.parseInt(addressBytes[i]);
//		}
//		return ip;
//	}
	
	
	
	/**returns the string representation of the prefix taking as input the ip address (represented as an int)
	 * and the netmask represented as an int (1 to 32)
	 * 
	 */
	private String getPrefix(int ip, int netmask) {

		//#ifndef ANDROID
		System.out.println("ip is " + LinuxDBusNetworkMonitor.ipFromInt(ip));
		//#else
//		System.out.println("ip is " + AndroidNetworkMonitor.ipFromInt(ip));
		//#endif
		System.out.println("netmask is " + netmask);
		
//		BitSet bsIP = new BitSet(32);
//		int index = 0;
//		while (ip != 0) {
//		  if (ip % 2 != 0) {
//			  bsIP.set(index);
//		  }
//		  ++index;
//		  ip = ip >>> 1;
//		}
		
//		BitSet bsIP = new BitSet(32);
//		for (int i=0; i<32; i++) {
////			int bit = ip & (0x1 << 31);
////			bsIP.set(i, ((bit == 1) ? true : false));
//			bsIP.set(i, ip & 0x1);
//			ip = ip << 1;
//		}
//		System.out.println("bsIP: " + bsIP);
		
		int intNetmask = 0;
//		BitSet bsNetmask = new BitSet(32);
		int i = 0;
		for (; i<netmask&&i<8; i++) {
//			bsNetmask.set(7-i);
			intNetmask = intNetmask | (1<<(7-i));
//			System.out.println("intNetmask " + intNetmask+ " "+LinuxDBusNetworkMonitor.ipFromInt(intNetmask));
		}
		for (; i<netmask&&i<16; i++) {
//			bsNetmask.set(15-i);
			intNetmask = intNetmask | (1<<(8+15-i));
//			System.out.println("intNetmask " + intNetmask+ " "+LinuxDBusNetworkMonitor.ipFromInt(intNetmask));
		}
		for (; i<netmask&&i<24; i++) {
//			bsNetmask.set(24-i);
			intNetmask = intNetmask | (1<<(16+23-i));
//			System.out.println("intNetmask " + intNetmask+ " "+LinuxDBusNetworkMonitor.ipFromInt(intNetmask));
		}
		for (; i<netmask&&i<32; i++) {
//			bsNetmask.set(32-i);
			intNetmask = intNetmask | (1<<(24+31-i));
//			System.out.println("intNetmask " + intNetmask+ " "+LinuxDBusNetworkMonitor.ipFromInt(intNetmask));
		}

		
//		System.out.println("netmask " + bsNetmask);
		
		
//		bsIP.and(bsNetmask);
//		System.out.println("prefix " + bsIP);
		
		
		int temp = 0;
		temp = ip & intNetmask;
		
		
//		for (int i = 0; i < 32; i++)
//			if (bsIP.get(i))
//				temp |= 1 << i;
		
		//#ifndef ANDROID
		String toBeReturned = LinuxDBusNetworkMonitor.ipFromInt(temp);
		//#else
//		String toBeReturned = AndroidNetworkMonitor.ipFromInt(temp);
		//#endif
		System.out.println("returned prefix " + toBeReturned);
		
		return toBeReturned;
	}
	
	/**
	 * Allows to copy the information contained inside an {@link InterfaceInfo}
	 * object into this InterfaceInfo object
	 * @param ni {@link InterfaceInfo} object to copy
	 */
	public void copyInfo(InterfaceInfo info)
	{
		this.hsAddr = info.hsAddr;
		this.queue = info.queue;
		this.description = info.description;
		this.isWifi = info.isWifi;
		this.isUMTS = info.isUMTS;
		this.classified = info.classified;
		this.sigLevel = info.sigLevel;
		this.UMTSport = info.UMTSport;
		this.netmask = info.netmask;
	}
	
	/** Set the status*/
	public void  setInterfaceStatus (int status)
	{interface_status = status;}
	
	public int getInterfaceStatus ()
	{return this.interface_status;}

	/**
	 * Whether the network interface is a Wi-Fi wireless interface or not
	 * @return true if the network interface is a Wi-Fi interface
	 */
	public boolean isWifi()
	{return isWifi;}

	/**
	 * Whether the network interface is a UMTS wireless interface or not
	 * @return true if the network interface is a UMTS interface
	 */
	public boolean isUMTS()
	{return isUMTS;}

	/**
	 * Set the network interface as a Wi-Fi interface
	 * @param iswifi	true if the network interface is a Wi-Fi interface
	 *					false otherwise
	 */
	public void setWifi(boolean iswifi)
	{
		isWifi = iswifi;
		classified = true;
	}
	
	/**
	 * Set the network interface as an UMTS interface
	 * @param iswifi	true if the network interface is an UMTS interface
	 *					false otherwise
	 */
	public void setUMTS(boolean isumts)
	{
		isUMTS = isumts;
		classified = true;
	}
	
	/**
	 * Set the network interface as already classified (as Wi-Fi or UMTS interface)
	 */
	public void setClassified()
	{classified = true;}
	
	/**
	 * Whether the network interface has been already classified (as Wi-Fi or UMTS interface)
	 * @return true if the interface has been already classified
	 */
	public boolean isClassified()
	{return classified;}
	
	/**
	 * Set the handover server IP address to associate with network interface
	 * @param hsaddr the handover server IP address
	 */
	public void setHsAddr(String hsaddr)
	{hsAddr = hsaddr;}
	
	/**
	 * Set the signal level of the wireless interface as returned by
	 * {@link WirelessProber#WiFiLevel(String)} or {@link WirelessProber#UMTSLevel(String)} or
	 * {@link WirelessProber#GSMLevel(String)}
	 * @param level the signal level to set
	 */
	public void setSigLevel(int level)
	{sigLevel = level;}
	
	/**
	 * Get the signal level of the wireless interface
	 */
	public int getSigLevel()
	{return sigLevel;}

	public static String getStatusName(int i) {
		switch(i) {
			case INTERFACE_STATUS_IDLE: return "INTERFACE_STATUS_IDLE";
//			case INTERFACE_STATUS_LOCTUNCREATE_SENT: return "INTERFACE_STATUS_LOCTUNCREATE_SENT";
//			case INTERFACE_STATUS_LOCAL_TUNNEL_CREATE: return "INTERFACE_STATUS_LOCAL_TUNNEL_CREATE";
//			case INTERFACE_STATUS_TUNNEL_SET_REQ_SENT: return "INTERFACE_STATUS_TUNNEL_SET_REQ_SENT";
//			case INTERFACE_STATUS_TUNNEL_SET_RESPONSE_RECEIVED: return "INTERFACE_STATUS_TUNNEL_SET_RESPONSE_RECEIVED";
			default: return "unknown"; 
		}
	}

	public String toString()
	{return id + " (" + ipAddress + ")";}

	public void setANIFStatus(String aNAddress, int status) {
		ANIFStatus.put (aNAddress, status);
	}
	
	public Integer getANIFStatus(String aNAddress) {
		if (aNAddress==null) {
			return null;
		} else {
			return ANIFStatus.get (aNAddress);
		}
	}
}
