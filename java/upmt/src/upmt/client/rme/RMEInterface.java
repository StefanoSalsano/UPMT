package upmt.client.rme;

import java.util.ArrayList;

public class RMEInterface {
	
	private String ip;
	private String netMask;
	private String broadcast;
	private String rmeInterface;
	private String ssid;
	private String channel;
	private boolean wless;
	
	public RMEInterface(String rmeInterface, String ip, String netMask, String broadCast, boolean wless)  {
		this.rmeInterface = rmeInterface;
		this.ip = ip;
		this.netMask = netMask;
		this.broadcast = broadCast;
		this.wless = wless;
		
	}
	
	public RMEInterface(String rmeInterface, String ip, String netMask, String broadCast, String ssid, String channel, boolean wless)  {
		this.rmeInterface = rmeInterface;
		this.ip = ip;
		this.netMask = netMask;
		this.broadcast = broadCast;
		this.ssid = ssid;
		this.channel = channel;
		this.wless = wless;
	}
	
	public RMEInterface(String[] configuration)  {
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getNetMask() {
		return netMask;
	}

	public void setNetMask(String netMask) {
		this.netMask = netMask;
	}

	public String getBroadcast() {
		return broadcast;
	}

	public void setBroadcast(String broadcast) {
		this.broadcast = broadcast;
	}
	
	public String getRmeInterface() {
		return this.rmeInterface;
	}

	public void setRmeInterface(String rmeInterface) {
		this.rmeInterface = rmeInterface;
	}
	
	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getSsid() {
		return ssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}
	
	public boolean isWless() {
		return wless;
	}

	public void setWless(boolean wless) {
		this.wless = wless;
	}
	
	public static ArrayList<RMEInterface> parseConfiguration(String[] config, String[] wlanConfig) {
		ArrayList<RMEInterface> parseConfiguration = new ArrayList<RMEInterface>();
		ArrayList<String> visitedInterface = new ArrayList<String>();
		
		if(wlanConfig!=null) {
			
		}
		
		for(int i=0; i<config.length; i++) {
			String[] divide = config[i].split(":");
			if(divide.length<2 || divide.length>2) {
				System.err.println("Check Configuration files");
				System.exit(0);
			}
			else {
				String rmeInterface = divide[0];
				String[] divideIP = divide[1].split("/");
				if(divide.length<2 || divide.length>2) {
					System.err.println("Check Configuration files");
					System.exit(0);
				}
				else {
					if(wlanConfig != null) {
						for(int j = 0; j<wlanConfig.length; j++) {
							String[] wlanDivide = wlanConfig[j].split(":");
							if(wlanDivide[0].equals(rmeInterface)) {
								String ip = divideIP[0];
								String netmask = divideIP[1];
								String netMask = calculateNetmask(divideIP[1]);
								String ssid = wlanDivide[1];
								String channel = wlanDivide[2];
								String broadCast = calculateBroadcast(netmask, ip);
								RMEInterface interf = new RMEInterface(rmeInterface, ip, netMask, broadCast, ssid, channel, true);								parseConfiguration.add(interf);
								visitedInterface.add(rmeInterface);
							}
						}
						if(!visitedInterface.contains(rmeInterface)) {
							String ip = divideIP[0];
							String netmask = divideIP[1];
							String netMask = calculateNetmask(divideIP[1]);
							String broadCast = calculateBroadcast(netmask, ip);
							RMEInterface interf = new RMEInterface(rmeInterface, ip, netMask, broadCast, false);
							parseConfiguration.add(interf);
							visitedInterface.add(rmeInterface);
						}
					}
					else {
						String ip = divideIP[0];
						String netmask = divideIP[1];
						String netMask = calculateNetmask(divideIP[1]);
						String broadCast = calculateBroadcast(netmask, ip);
						RMEInterface interf = new RMEInterface(rmeInterface, ip, netMask, broadCast, false);
						parseConfiguration.add(interf);
					}
				}
			}
		}
		return parseConfiguration;
	}
	
	public static String[] getInterfacesNames(ArrayList<RMEInterface> rmeInterfaceList) {
		String[] interfaces = new String[rmeInterfaceList.size()];
		for(int i=0; i<rmeInterfaceList.size(); i++) {
			interfaces[i] = rmeInterfaceList.get(i).getRmeInterface();
		}
		return interfaces;
	}
	
	public static String[] getInterfacesAddresses(ArrayList<RMEInterface> rmeInterfaceList) {
		String[] addresses = new String[rmeInterfaceList.size()];
		for(int i=0; i<rmeInterfaceList.size(); i++) {
			addresses[i] = rmeInterfaceList.get(i).getIp();
		}
		return addresses;
	}
	
	public static String calculateBroadcast(String netmask, String ip) {
		int netm = Integer.parseInt(netmask);
		String ipBroadcast = "";
		String[] netIp = ip.split("\\.");
		if(netIp.length<4) {
			System.err.println("Bad Configuration file in peer.cfg check the IP");
			System.exit(0);
		}
		if((netm <= 32) && (netm >= 25)) {
			int lastaddr = ((((netm+1)-25)*32)-1);
			ipBroadcast = netIp[0]+"."+netIp[1]+"."+netIp[2]+"."+lastaddr;
		}
		else if((netm <= 24) && (netm >= 17)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipBroadcast = netIp[0]+"."+netIp[1]+"."+lastaddr+".255";
		}
		else if((netm <= 16) && (netm >= 9)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipBroadcast = netIp[0]+"."+lastaddr+".255.255";
		}
		else if((netm <= 8) && (netm >= 1)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipBroadcast = lastaddr+".255.255.255";
		}
		else {
			System.err.println("Bad Configuration file in peer.cfg check the IP and netmask");
			System.exit(0);
		}
		
		return ipBroadcast;
	}
	
	public static String calculateNetmask(String netmask) {
		int netm = Integer.parseInt(netmask);
		String ipNetmask = "";
		if((netm <= 32) && (netm >= 25)) {
			int lastaddr = ((((netm+1)-25)*32)-1);
			ipNetmask = "255.255.255."+lastaddr;
		}
		else if((netm <= 24) && (netm >= 17)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipNetmask = "255.255."+lastaddr+".0";
		}
		else if((netm <= 16) && (netm >= 9)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipNetmask = "255."+lastaddr+".0.0";
		}
		else if((netm <= 8) && (netm >= 1)) {
			int lastaddr = ((((netm+1)-17)*32)-1);
			ipNetmask = lastaddr+".0.0.0";
		}
		else {
			System.err.println("Bad Configuration file in peer.cfg check the netmask");
			System.exit(0);
		}
		
		return ipNetmask;
	}
	
	public static boolean checkNetwork(String localIp, String localNetmask, String remoteIP) {		
		String[] localSplitIP = localIp.split("\\.");
		String[] remoteSplitIP = remoteIP.split("\\.");
		String[] localSplitNetmask = localNetmask.split("\\.");
		
		for(int j=0; j<localSplitIP.length; j++) {
			if(!localSplitIP[j].equals(remoteSplitIP[j])) {
				if(localSplitNetmask[j].equals("0") || Integer.parseInt(localSplitNetmask[j])>Integer.parseInt(remoteSplitIP[j])) {
					return true;
				}
				else {
					return false;
				}
			}
		}
		return false;
	}
	

}
