package upmt.client.rme;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import upmt.client.UPMTClient;
import upmt.os.Module;
import upmt.os.Shell;


public class RoutingCheck {

	/**
	 * @throws IOException 
	 * @throws org.jsonref.JSONException 
	 */
	public static ParseJson parse;
	public static ArrayList<String> Table = new ArrayList<String>();
	static ThreadRouting t;
	public static String blankIp = "0.0.0.0";
	
	public static void initialize() {
		parse = null;
		try {
			parse = new ParseJson();
		} catch (org.jsonref.JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void runMH() {
		t = new ThreadRouting();
	}
	
	public static void stopMH() {
		t.stop();
	}
	
	public static HashMap<String, ArrayList<Route>> routeHash() throws IOException {
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
	
	public static void manageTable(HashMap<String, ArrayList<Route>> routeHash) {
		Iterator<String> destination = routeHash.keySet().iterator();
		while(destination.hasNext()) {
			String ifNameDest = destination.next();
			for(int i=0; i<routeHash.get(ifNameDest).size(); i++) {
				String dest = routeHash.get(ifNameDest).get(i).getIp();
				String destgate = routeHash.get(ifNameDest).get(i).getGateway();
				Iterator<String> gateway = routeHash.keySet().iterator();
				while(gateway.hasNext()) {
					String ifNameGate = gateway.next();
					if(!ifNameDest.equals(ifNameGate)) {
						for(int j=0; j<routeHash.get(ifNameGate).size(); j++) {
							String gate = routeHash.get(ifNameGate).get(j).getGateway();
							if(!dest.equals(gate) && !ifNameDest.equals(ifNameGate)) {
								String sourceip = getIP(ifNameGate);
								if(!existRoute(dest, gate, ifNameGate)) { 
									if(trueRoute(destgate, gate)) { // to change trueRoute() ---> file json
										Shell.executeCommand(new String[]{"sudo", "ip", "route", "add", dest, "via", gate, "dev", ifNameGate, "table", ifNameGate+"_table"});
										Table.add(dest+"-"+gate+"-"+ifNameGate);
//										System.err.println("Aggiunta la tripla "+dest+"-"+gate+"-"+ifNameGate);
										String result = Module.upmtconf(new String[] { "-a", "dev", "-i", ifNameGate });
										int mark = Module.getUpmtParameter(result, "Mark");
										Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-A", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", dest, "-j", "MARK", "--set-mark", ""+mark});
//										System.err.println("sudo iptables -t mangle -A OUTPUT -p udp --source "+sourceip+" --dest "+dest+" -j MARK --set-mark "+mark);
									}
//									else {
//										System.err.println("La tripla -> "+dest+"-"+gate+"-"+ifNameGate+" <- rotta inesistente");
//									}
								}
//								else {
//									System.err.println("La tripla -> "+dest+"-"+gate+"-"+ifNameGate+" <- è già esistente");
//								}
							}
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
	public static void clearTable(HashMap<String, ArrayList<Route>> routeHash) {
		for(int i=0; i <Table.size(); i++) {
			boolean foundDest = false;
			boolean foundGate = false;
			StringTokenizer tok = new StringTokenizer(Table.get(i), "-");
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
				System.err.println("Rimuovo la tripla -> "+dest+"-"+gate+"-"+ifName);
				String result = Module.upmtconf(new String[] { "-a", "dev", "-i", ifName });
				int mark = Module.getUpmtParameter(result, "Mark");
				for(int k=0; k<UPMTClient.getRMEInterfacesList().size(); k++) {
					if(UPMTClient.getRMEInterfacesList().get(k).equals(ifName)) {
						String sourceip = getIP(ifName);
						Shell.executeCommand(new String[]{"sudo", "iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp","--source", sourceip, "--dest", dest, "-j", "MARK", "--set-mark", ""+mark});
						System.err.println("sudo iptables -t mangle -D OUTPUT -p udp --source "+sourceip+" --dest "+dest+" -j MARK --set-mark "+mark);
						break;
					}
				}
				Table.remove(i);
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
	public static boolean existRoute(String destip, String ip, String ifName) {
		for(int i=0; i<Table.size(); i++) {
			if(Table.get(i).equals(destip+"-"+ip+"-"+ifName)) {
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
	public static boolean trueRoute(String dest, String gate) {
		Iterator<String> itmap = parse.getJmap().keySet().iterator();
		while(itmap.hasNext()) {
			String vip = itmap.next();
			for(int j=0; j<parse.getJmap().get(vip).size(); j++) {
				String ip = parse.getJmap().get(vip).get(j).getIp();
				if(ip.equals(dest)) { // found destination in the json file
					for(int i=0; i<parse.getJmap().get(vip).size(); i++) {
						String ip2 = parse.getJmap().get(vip).get(i).getIp();
						if(!ip.equals(ip2) && ip2.equals(gate)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Function that returns the integere on ip address
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StringTokenizer tok = new StringTokenizer(ip.substring(9));
		ip = tok.nextToken("/");
		
		return ip;
	}
	
}
