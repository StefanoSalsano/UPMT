package upmt.client.rme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import upmt.client.UPMTClient;
import upmt.client.application.manager.impl.GUIApplicationManager;
import upmt.os.Module;
import upmt.os.Shell;

public class RoutingCheck implements Runnable {

	/**
	 * @throws IOException 
	 * @throws org.jsonref.JSONException 
	 */
	
	public static String blankIp = "0.0.0.0";
	
	
	private HashMap<String, String> addressIfnameTogateway = new HashMap<String, String>();
	private HashMap<String, ArrayList<String>> localIfnameToaddresses = new HashMap<String, ArrayList<String>>(); 
	private ArrayList<String> table = new ArrayList<String>();
	private UPMTClient upmtClient;
	private boolean interrupt;
	private Thread threadRouting;
	private OLSRChecker olsrChecker;
	
	public RoutingCheck(UPMTClient upmtClient) {
		this.upmtClient = upmtClient;
		this.table = new ArrayList<String>();
		this.interrupt = false;
		this.threadRouting = new Thread(this, "Thread di Routing");
		this.olsrChecker = new OLSRChecker();
	}
	
	/**
	 * Stop RME Routing Thread and delete netfilter rules
	 */
	public void stopRME() {
		stop();
		delRules();
	}
	
	public void startRME() {
		setRules();
		System.out.println("ROUTING_CHECK: Thread di Routing creato");
		threadRouting.start();
		this.olsrChecker.init();
	}
	
	/**
	 * Parse the main routing table
	 * @return HashMap
	 * @throws IOException
	 */
	public HashMap<String, ArrayList<Route>> routeHash() throws IOException {
		Process p = Runtime.getRuntime().exec("netstat -rn");
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		br.readLine();
		br.readLine();
		boolean check=true;
		int count=1;
		HashMap<String, ArrayList<Route>> routing = new HashMap<String, ArrayList<Route>>();
		String ifName="";
		String dest="";
		String gate="";
		ArrayList<String> iflist = new ArrayList<String>();
		ArrayList<String> destlist = new ArrayList<String>();
		ArrayList<String> gatelist = new ArrayList<String>();
		while(check) {
			String single;
			String line = br.readLine();
			if(line!=null) {
				StringTokenizer tok = new StringTokenizer(line, " ");
				while(tok.countTokens()!=0) {
					single=tok.nextToken();
					if (count%8==1) {
						dest=single;
						destlist.add(dest);
					}
					else if(count%8==2) {
						gate=single;
						gatelist.add(gate);
					}
					else if(count%8==0) {
						ifName=single;
						count++;
						iflist.add(ifName);
					}
					count++;
				}
			} else {
				check=false;
			}
			count=1;
		}
		boolean[] table = new boolean[iflist.size()];
		for(int i=0; i<iflist.size(); i++) {
			ArrayList<Route> routefinal = new ArrayList<Route>();
			for(int j=0; j<iflist.size(); j++) {
				if(iflist.get(i).equals(iflist.get(j)) && table[j]==false) {
					for(int k=0; k<UPMTClient.getRMEInterfacesList().size(); k++) {
						if(iflist.get(j).equals(UPMTClient.getRMEInterfacesList().get(k))) { //check interfaces
							if(!(destlist.get(j).equals(blankIp)) && !(gatelist.get(j).equals(blankIp))) { // check address
								Route route = new Route(destlist.get(j), iflist.get(j), gatelist.get(j));
								routefinal.add(route);
								table[j]=true;
							}
						}
					}
				}
			}
			if(routing.get(iflist.get(i))==null) {
				routing.put(iflist.get(i), routefinal);
			}
		}
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return routing;
	}
	
	/**
	 * Sets the new RME route for direct tunnels
	 * @param routeHash
	 */
	public void manageTable(HashMap<String, ArrayList<Route>> routeHash) {
		Iterator<String> destination = routeHash.keySet().iterator();
		while(destination.hasNext()) {
			String ifNameDest = destination.next();
			for(int i=0; i<routeHash.get(ifNameDest).size(); i++) {
				String dest = routeHash.get(ifNameDest).get(i).getIp();
				String destgate = routeHash.get(ifNameDest).get(i).getGateway();
//				System.err.println("destination: "+dest+" gateway: "+destgate);
				if(dest!=null && ifNameDest!=null && dest!=null) {
					if(!existRoute(dest, destgate, ifNameDest)) {
						Shell.executeCommand(new String[]{"sudo", "ip", "route", "add", dest, "via", destgate, "dev", ifNameDest, "table", ifNameDest+"_table"});
						table.add(dest+"-"+destgate+"-"+ifNameDest);
						String result = Module.upmtconf(new String[] { "-a", "dev", "-i", ifNameDest});
						int mark = Module.getUpmtParameter(result, "Mark");
						String sourceip = "";//= getIP(ifNameDest);
						for(int z=0; z<UPMTClient.rmeAddresses.size(); z++) {
							if(UPMTClient.rmeAddresses.get(z).getRmeInterface().equals(ifNameDest)) {
								sourceip = UPMTClient.rmeAddresses.get(z).getIp();
								break;
							}
						}
						upmtClient.getRmeDirectTunnels().add(dest+":"+ifNameDest);
						if(!localIfnameToaddresses.containsKey(ifNameDest)) {
							localIfnameToaddresses.put(ifNameDest, new ArrayList<String>());
						}
						localIfnameToaddresses.get(ifNameDest).add(dest);
						upmtClient.getOlsrDetectedEndPoint().add(dest);
						Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-A", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", dest, "-j", "MARK", "--set-mark", ""+mark});
						System.out.println("[RoutingCheck] detected new RME Server Node "+ dest);
						upmtClient.updateMsg("Detected new RME Server Node: "+dest);
						if(!UPMTClient.textMode) {
							((GUIApplicationManager) upmtClient.getApplicationManager()).refreshGui();
						}
						setPeerMode(dest, ifNameDest, destgate);
					}
				}				
			}
		}
	}
	
	/**
	 * Sets the new RME route for cross tunnels
	 * @param routeHash
	 */
	public void crossTunnel(String endPoint, String ifname, String gateway) {
		if(UPMTClient.ipToVipa.containsKey(endPoint)) {
			String VIPA = UPMTClient.ipToVipa.get(endPoint);
			String[] addressesInUseVipa = this.upmtClient.getDiscoveredaddresses(VIPA);
			if(addressesInUseVipa!=null) {
				for(String address: addressesInUseVipa) {
					if(!address.equals(endPoint)) {
						if(!existRoute(address, gateway, ifname)) {
							Shell.executeCommand(new String[]{"sudo", "ip", "route", "add", address, "via", gateway, "dev", ifname, "table", ifname+"_table"});
							table.add(address+"-"+gateway+"-"+ifname);
							String result = Module.upmtconf(new String[] { "-a", "dev", "-i", ifname});
							int mark = Module.getUpmtParameter(result, "Mark");
							String sourceip = "";// = getIP(ifname); //FIXME
							for(int z=0; z<UPMTClient.rmeAddresses.size(); z++) {
								if(UPMTClient.rmeAddresses.get(z).getRmeInterface().equals(ifname)) {
									sourceip = UPMTClient.rmeAddresses.get(z).getIp();
									break;
								}
							}
							Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-A", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", address, "-j", "MARK", "--set-mark", ""+mark});
							setPeerMode(address, ifname, gateway);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Function that cleans the table from obsolete addresses
	 * @param routeHash
	 */
	public void clearTable(HashMap<String, ArrayList<Route>> routeHash) {
		for(int i=0; i <table.size(); i++) {
			boolean foundDest = false;
			boolean foundGate = false;
			StringTokenizer tok = new StringTokenizer(table.get(i), "-");
			String dest = tok.nextToken();
			String gate = tok.nextToken();
			Iterator<String> itNet = routeHash.keySet().iterator();
			while(itNet.hasNext()) {
				String ifName = itNet.next();
				for(int j=0; j<routeHash.get(ifName).size(); j++) {
					String ip = routeHash.get(ifName).get(j).getIp();
					if(dest.equals(ip)) {
						foundDest = true;
					}
					if(gate.equals(ip)) {
						foundGate = true;
					}
				}
			}
			if(foundDest==false || foundGate==false) {
				String ifName = tok.nextToken();
				Shell.executeCommand(new String[]{"sudo", "ip", "route", "del", dest, "via", gate, "dev", ifName, "table", ifName+"_table"});
//				System.err.println("Rimuovo la tripla -> "+dest+"-"+gate+"-"+ifName);
				String result = Module.upmtconf(new String[] { "-a", "dev", "-i", ifName });
				int mark = Module.getUpmtParameter(result, "Mark");
				for(int k=0; k<UPMTClient.getRMEInterfacesList().size(); k++) {
					if(UPMTClient.getRMEInterfacesList().get(k).equals(ifName)) {
//						String sourceip = getIP(ifName); 
						//FIXME
						String sourceip = "";
						for(int z=0; z<UPMTClient.rmeAddresses.size(); z++) {
							if(UPMTClient.rmeAddresses.get(z).getRmeInterface().equals(ifName)) {
								sourceip = UPMTClient.rmeAddresses.get(z).getIp();
							}
						}
						if(upmtClient.getOlsrDetectedEndPoint().contains(dest)) {
							upmtClient.getOlsrDetectedEndPoint().remove(dest);
						}
						Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", dest, "-j", "MARK", "--set-mark", ""+mark});
//						System.err.println("sudo iptables -t mangle -D OUTPUT -p udp --source "+sourceip+" --dest "+dest+" -j MARK --set-mark "+mark);
						break;
					}
				}
				table.remove(i);
				i=i-1;
			}
		}
	}
	
	/**
	 * Function that checks whether a triple [destination-gateway-interface] has already been inserted in the table 
	 * @param destip
	 * @param ip
	 * @param ifName
	 * @return boolean
	 */
	public boolean existRoute(String destip, String ip, String ifName) {
		for(int i=0; i<table.size(); i++) {
			if(table.get(i).equals(destip+"-"+ip+"-"+ifName)) {
				if(!upmtClient.getOlsrDetectedEndPoint().contains(destip)) {
					upmtClient.getOlsrDetectedEndPoint().add(destip);
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Function that checks if the two IP addresses "dest" and "gates" are of the same VIPA
	 * @param dest
	 * @param gate
	 * @return boolean
	 */
	public boolean trueRoute(String dest, String gate) {
		if(UPMTClient.ipToVipa.containsKey(dest) && UPMTClient.ipToVipa.containsKey(dest)) {
			String vipaDest = UPMTClient.ipToVipa.get(dest);
			String vipaGate = UPMTClient.ipToVipa.get(gate);
			if(vipaDest.equals(vipaGate)) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	/**
	 * Function that returns the integer on ip address
	 * @param ip
	 * @return
	 */
	public static int intFromIP(String ip) {
		long intIP=0;
		StringTokenizer tok = new StringTokenizer(ip, ".");
		int cont=0;
		while(tok.hasMoreTokens()) {
			long app=Integer.parseInt(tok.nextToken());
			app*=(int)Math.pow((double)256, (double)cont);
			cont++;
			intIP=intIP+app;
		}
		return (int)intIP;
	}
	
	/**
	 * Function that returns the IP address from the interface name
	 * @param ifName
	 * @return String
	 */
	public static String getIP(String ifName) {
		String ip = "";
		try {
			Process p = Runtime.getRuntime().exec("ip address show "+ifName);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			br.readLine(); 
			br.readLine(); 
			ip = br.readLine();
			p.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(ip==null || ip.length()<9) {
			ip = RoutingCheck.blankIp;
		}
		else {
			StringTokenizer tok = new StringTokenizer(ip.substring(9));
			ip = tok.nextToken("/");
		}
		
		return ip;
	}
	
	/**
	 * Set the Netfilter tables (mangle, nat and filter) the rules used to forward packets
	 */
	public void setRules() {
		for(int i=0; i<UPMTClient.getRMEInterfacesList().size(); i++) {
			String sourceip = getIP(UPMTClient.getRMEInterfacesList().get(i));
			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-A", "PREROUTING", "-p", "udp", "!", "--dest", sourceip, "-j", "MARK", "--set-mark", "0xfafafafa"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "nat", "-A", "PREROUTING", "!", "--dest", sourceip, "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "nat", "-A", "POSTROUTING", "!", "--dest", sourceip, "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "filter", "-A", "FORWARD", "-i", UPMTClient.getRMEInterfacesList().get(i), "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "filter", "-A", "FORWARD", "-o", UPMTClient.getRMEInterfacesList().get(i), "-j", "ACCEPT"});
		}
	}
	
	/**
	 * Delete from Netfilter tables(mangle, nat and filter) the rules used to mark and forward packets
	 */
	public void delRules() {
		for(int i=0; i<UPMTClient.getRMEInterfacesList().size(); i++) {
			String sourceip = getIP(UPMTClient.getRMEInterfacesList().get(i));
			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-D", "PREROUTING", "-p", "udp", "!", "--dest", sourceip, "-j", "MARK", "--set-mark", "0xfafafafa"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "nat", "-D", "PREROUTING", "!", "--dest", sourceip, "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "nat", "-D", "POSTROUTING", "!", "--dest", sourceip, "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "filter", "-D", "FORWARD", "-i", UPMTClient.getRMEInterfacesList().get(i), "-j", "ACCEPT"});
//			Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "filter", "-D", "FORWARD", "-o", UPMTClient.getRMEInterfacesList().get(i), "-j", "ACCEPT"});
			for(int k=0; k<table.size(); k++) {
				StringTokenizer tok = new StringTokenizer(table.get(k), "-");
				String dest = tok.nextToken(); tok.nextToken(); /* ex String gate*/ 
				String ifname = tok.nextToken();
				if(ifname.equals(UPMTClient.getRMEInterfacesList().get(i))) {
					String result = Module.upmtconf(new String[] { "-a", "dev", "-i", UPMTClient.getRMEInterfacesList().get(i) });
					int mark = Module.getUpmtParameter(result, "Mark");
					Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", dest, "-j", "MARK", "--set-mark", ""+mark});
				}
			}
		}
	}
	
	public synchronized void setPeerMode(String aNAddress, String ifNameDest, String gateway) {
		if(UPMTClient.ipToVipa.containsKey(aNAddress)) {
			String VIP = UPMTClient.ipToVipa.get(aNAddress);
			PeerMode peerMode = new PeerMode(this.upmtClient, VIP, aNAddress, UPMTClient.cfg.vepa, ifNameDest);
			peerMode.setPeerMode();
			if(UPMTClient.crossTunnel) {
				this.crossTunnel(aNAddress, ifNameDest, gateway);
			}
		}
		else {
			synchronized (upmtClient.getTunnelProviders()) {
				if (!UPMTClient.getCfgANList().contains(aNAddress)) {
					if(UPMTClient.blockerAssociation) {
						synchronized (upmtClient.getSignaler().getCurrentAssociations()) {
							if(!upmtClient.getSignaler().getCurrentAssociations().contains(aNAddress+":"+ifNameDest)) {
								//					UPMTClient.addCfgAnlist(aNAddress);
//								System.err.println("AN----_>  "+aNAddress);
								this.upmtClient.setANSipPort(aNAddress, new Integer(5060));
								this.upmtClient.rmeAnAssociation(aNAddress, ifNameDest);
							}
						}
					}
					else {
						if(!upmtClient.getSignaler().getCurrentAssociations().contains(aNAddress+":"+ifNameDest)) {
							//					UPMTClient.addCfgAnlist(aNAddress);
							this.upmtClient.setANSipPort(aNAddress, new Integer(5060));
							this.upmtClient.rmeAnAssociation(aNAddress, ifNameDest);
						}
					}
				}
			}
			if(UPMTClient.blockerAssociation) {
//				setPeerMode(aNAddress, ifNameDest, gateway);
				tryToSetPeerMode(aNAddress, ifNameDest, gateway);
			}
			else {
				tryToSetPeerMode(aNAddress, ifNameDest, gateway);
			}
		}
	}
	
	public void tryToSetPeerMode(final String aNAddress,final String ifNameDest,final String gateway) {
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				setPeerMode(aNAddress, ifNameDest, gateway);
			}
//		}, SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL);
		}, 5000);
	}
	
	
	public synchronized void setPeerMode(String aNAddress) {

		if(UPMTClient.ipToVipa.containsKey(aNAddress)) {
			String VIP = UPMTClient.ipToVipa.get(aNAddress);
			PeerMode peerMode = new PeerMode(this.upmtClient, VIP, aNAddress, UPMTClient.cfg.vepa);
			peerMode.setPeerMode();
		}
	}
	
	public boolean isCrossTunnelAvailable(String address, String ifName) {
		boolean isAvailable = false;
		if(upmtClient.getOlsrDetectedEndPoint().contains(address)) {
			String vipa = upmtClient.getVipa(address);
			if(localIfnameToaddresses.containsKey(ifName)) {
				for(String remoteIP: localIfnameToaddresses.get(ifName)) {
					String vipaCheck = upmtClient.getVipa(remoteIP);
					if(vipa!=null && vipaCheck!=null && vipa.equals(vipaCheck) && upmtClient.getOlsrDetectedEndPoint().contains(remoteIP)) {
						isAvailable = true;
						break;
					}
				}
			}
		}
		return isAvailable;
	}

	public UPMTClient getUpmtClient() {
		return this.upmtClient;
	}

	public void setUpmtClient(UPMTClient upmtClient) {
		this.upmtClient = upmtClient;
	}

	public HashMap<String, String> getAddressIfnameTogateway() {
		return addressIfnameTogateway;
	}

	public void setAddressIfnameTogateway(HashMap<String, String> addressIfnameTogateway) {
		this.addressIfnameTogateway = addressIfnameTogateway;
	}

	public OLSRChecker getOlsrChecker() {
		return olsrChecker;
	}

	public void setOlsrChecker(OLSRChecker olsrChecker) {
		this.olsrChecker = olsrChecker;
	}

	@Override
	public void run() {
		try {
			while(!interrupt) {
				try {
					HashMap<String, ArrayList<Route>> routehash = routeHash();
					clearTable(routehash);
					manageTable(routehash);
				} catch (IOException e) {
					e.printStackTrace();
				}
				Thread.sleep(10000);
			}
		}
		catch (InterruptedException e) {
			System.out.println("ROUTING_CHECK: Thread di Routing interrotto");
		}
		System.out.println("ROUTING_CHECK: Uscita Thread di Routing");
		
	}
	
	/**
	 * Stops the RME routing thread
	 */
	public void stop() {
		this.interrupt=true;
	}

}