package upmt.client.network.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WindowsNetworkMonitor
{
	static final private String CRLF = System.getProperty("line.separator");

	/** For windows configuration */

	private class WinIpConfigParser
	{
		private String ipCfgOutput = "";

		protected WinIpConfigParser()
		{
			try
			{
				Process p = Runtime.getRuntime().exec("ipconfig");
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				// legge l'output del sottoprogramma e lo visualizza
				String line;
				// skip empty lines
				while ((line = br.readLine()) != null && !line.equals(CRLF)) ipCfgOutput += line + CRLF;
			}
			catch (Exception e) {e.printStackTrace();}
		}

		protected String winGetGw(String ipAddr)
		{
			int i = ipCfgOutput.indexOf(ipAddr);// ritorna l'indice dell'indirizzo
			if (i < 0) return null;
			i = ipCfgOutput.indexOf(CRLF, i);
			if (i < 0) return null;
			i = ipCfgOutput.indexOf(":", i);// Returns the index of the first occurrence of the specified substring, starting at the specified index.
			if (i < 0) return null;
			i = ipCfgOutput.indexOf(CRLF, i);
			if (i < 0) return null;
			i = ipCfgOutput.indexOf(":", i) + 1;
			if (i < 0) return null;
			int k = ipCfgOutput.indexOf(CRLF, i);
			if (i < 0) return null;
			String defaultGw = ipCfgOutput.substring(i, k).trim();
			if (defaultGw.equalsIgnoreCase("")) return null;
			else return defaultGw;
		}
	}	

	// /** for linux configuration */
	/*private class LinIpConfigParser
	{
		private String defaultGw = "";
		private String ipCfgOutput = "";

		protected LinIpConfigParser()
		{
			try
			{
				Process p = Runtime.getRuntime().exec("route ");
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line;
				// skip empty lines
				//after the route parsing have been done, I obtain the default gateway address using the class stringTokenzier
				while ((line = br.readLine()) != null)
				{
					ipCfgOutput += line + CRLF;
					StringTokenizer st = new StringTokenizer(ipCfgOutput);
					while (st.hasMoreTokens())
						if (st.nextToken().equals("default")) defaultGw = st.nextToken();
				}
			}
			catch (Exception e) {erLog.logException(e);}

			// DEBUG
			System.out.println("********\nOUTPUT:\n" + ipCfgOutput + "********");
			System.out.println("il default getway è:" + defaultGw + "*********");
		}

		protected String linGetGw()
		{return defaultGw;}
	}*/
	
	










/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////Vekkio codice del monitor per linux. Prendere parte delle NetworkInterface java /////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//	
//	private Map<String, String> mapgw;
//
//	/** getGw is a method that return, for each ip_address interface included in mapgw, a  */
//	private String getGw(String ipaddr)
//	{return mapgw.get(ipaddr);}
//
//	private Hashtable<String, InterfaceInfo> manualCheck_old()
//	{
//		Hashtable<String, NetworkInterface> detectedInterfaces = new Hashtable<String, NetworkInterface>();
//
//		/**
//		 * Finds all the interfaces on this machine. null if no network interfaces can be found on this machine for each interface return its name
//		 * and addresses. i.e:[name:eth1 (eth1) index: 3 addresses:/fe80:0:0:0:212:f0ff:fe22:aa06%3;/192.168.0.102; , name:eth0 (eth0) index: 2
//		 * addresses:/fe80:0:0:0:203:dff:fe2b:538d%2;/192.168.0.155; , name:lo (lo) index: 1 addresses:/0:0:0:0:0:0:0:1%1;/127.0.0.1;]
//		 */
//		Enumeration<NetworkInterface> e = null;
//		try
//		{
//			e = NetworkInterface.getNetworkInterfaces();
//		}
//		catch (SocketException e1)
//		{
//			e1.printStackTrace();
//		}
//
//		//for (Enumeration<NetworkInterface> NIs = NetworkInterface.getNetworkInterfaces(); NIs.hasMoreElements();)
//		//	System.out.println("\ngetNetworkInterfaces():" + NIs.nextElement().toString());
//		// end debug
//
//		if (e == null) {return new Hashtable<String,InterfaceInfo>();}
//
//		/**
//		 * it includes the interfaces detected with the latest NetworkInterface.getNetworkInterfaces() initially, it is a map (interface name-> IP address)
//		 * after that discoverDefaultGw is called, it is a map (interface name-> InterfaceInfo)
//		 */
//
//		//logging with lowest log level...
//		System.out.println("\n\nMonitor: starting available interfaces status detection phase");
//		/** check whether there are network interfaces with an assigned IP address or not */
//
//		//Discover gateways for every interface using Network Manager over DBus
//		Map<String, String> discovered = discoverGw();
//		mapgw = discovered;
//
//		while (e.hasMoreElements())
//		{
//			NetworkInterface ni = e.nextElement();
//			// if considered network interface is not the loop-back if and the virtual one
//			if ((!ni.getName().trim().equalsIgnoreCase("lo")) && (!ni.getName().trim().equalsIgnoreCase("dummy0")))
//			{
//				Enumeration<InetAddress> ipAddrs = ni.getInetAddresses();
//				// if the detected interface has not an assigned IP address skip further processing
//				if (ipAddrs == null || !ipAddrs.hasMoreElements()) continue;
//				String niName = ni.getName().trim();
//				// if a valid network interface is found, it is added to detectedInterfaces hashtable
//				detectedInterfaces.put(niName, ni);
//			}
//		}
//
//		Enumeration<String> e3 = detectedInterfaces.keys();
//		while (e3.hasMoreElements()) System.out.println("DETECTED IFS BEFORE GW DISCOVER: " + e3.nextElement().toString());
//
//
//		/**
//		 * if there are network interfaces with an assigned IP address determine the default gateway for each interface with an assigned
//		 * IP address (active) and update the detectedinterfaces list
//		 */
//		return discoverInterfaceGw(detectedInterfaces, discovered);
//	}
//
//	private Map<String, String> discoverGw()
//	{
//		Map<String, String> discovered = new HashMap<String, String>();
//		
//		for (DBusInterface dBusInt : netMan.GetDevices()) try
//		{
//			DBus.Properties deviceProperty = (DBus.Properties)dBusInt;
//			
//			String dhcpPropertyPath = ((Path)deviceProperty.Get(DEVICE, "Dhcp4Config")).getPath();
//			
//			if (dhcpPropertyPath.equals("/")) {continue;}
//			
//			DBus.Properties dhcpProperty = connection.getRemoteObject(NETWORK_MANAGER, dhcpPropertyPath, DBus.Properties.class);
//			
//			Map<String,Variant> dhcpOption = dhcpProperty.Get(DHCP, "Options"); //myMap contains the configuration options provide from DHCP
//
//			String ipaddr=null, gw=null;
//			for (String key : dhcpOption.keySet())
//				if (key.equals("ip_address"))
//					ipaddr = dhcpOption.get(key).toString().replaceAll("\\[", "").replaceAll("\\]","");
//				else if (key.equals("routers"))
//					gw = dhcpOption.get(key).toString().replaceAll("\\[", "").replaceAll("\\]","");
//
//			System.out.println("BBBBBBBBBBBBBBB ipadd: " + ipaddr + " gw: " + gw);
//			discovered.put(ipaddr,gw);
//			
//		}
//		catch (DBusException e) {e.printStackTrace();}
//		return discovered;
//	}
//
//	/**
//	 * Takes as input an hashtable (interface name -> ip address) with all the detected interfaces and returns an hashtable (interface name -> InterfaceInfo)
//	 * only for the interfaces that have a gateway
//	 */
//	private Hashtable<String, InterfaceInfo> discoverInterfaceGw(Hashtable<String, NetworkInterface> detectedInterfaces, Map<String, String> discovered)
//	{
//		Hashtable<String, InterfaceInfo> netIfs = new Hashtable<String, InterfaceInfo>();
//		Enumeration<String> keys = detectedInterfaces.keys();
//
//		while (keys.hasMoreElements())
//		{
//			NetworkInterface netIf = detectedInterfaces.get(keys.nextElement());
//			Enumeration<InetAddress> ipAddrs = netIf.getInetAddresses();
//			InetAddress ipAddr = null;
//			String ipAddrStr = null;
//			while (ipAddrs.hasMoreElements())
//			{
//				ipAddr = ((InetAddress) ipAddrs.nextElement());
//				if (ipAddr instanceof Inet4Address) ipAddrStr = (ipAddr.toString().trim().substring(1));
//			}
//			System.out.print("discoverDefaultGw(): discovering gateway for " + ipAddrStr + " (" + netIf.getName() + ") ");//
//			String defaultGw = null;
//			
//			defaultGw = discovered.get(ipAddrStr);
//			
//			if (defaultGw != null)
//			{
//				System.out.println(": FOUND " + defaultGw);//
//				String niName = netIf.getName();
//				InterfaceInfo wrapper = new InterfaceInfo(netIf, ipAddrStr, defaultGw, "");
//				netIfs.put(niName, wrapper);
//			}
//			else
//			{
//				System.out.println(" NOT FOUND!!");//
//			}
//		}
//		return netIfs;
//	}
}
