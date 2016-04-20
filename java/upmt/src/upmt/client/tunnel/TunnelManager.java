package upmt.client.tunnel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.zoolu.tools.Log;

import upmt.Default;
import upmt.client.UPMTClient;
import upmt.client.core.ConfigManager;
import upmt.client.core.InterfaceInfo;
import upmt.client.core.LocalPortmgr;
import upmt.os.Module;
import upmt.os.Shell;

public class TunnelManager
{
	private static ConfigManager cfg = ConfigManager.instance();
	private String result; //TODO: Dopo ogni comando da shell andrebbe controllato il result.

	private int rtTablesIndex;
	public int startPort;
	public int portRange;
	public String noUpmtMark;
	public int extendedFilter;
	private final int rtTablesIndexStart;
	private LocalPortmgr realPortManager;
	private static UPMTClient upmtClient = null;
	
	private ArrayList<String> TemporaryTunnelSetup = new ArrayList<String>();
	
	private HashMap<String, String> markTable;
	
	private HashMap<Integer, String> tidToANTable;
	
	private static Hashtable<String, String> appToAN = new Hashtable<String, String>();

	/** Hash table: ifName+ANAddress -> tunnel ID<BR>  
	 * hash table for local Tids<BR>
	 * the remote TID table is stored in SipSignalManager.remoteTidTable 
	 */
	private static Hashtable<String, Integer> localTidTable;

	/**
	 * returns the tidTable for local tunnel IDs
	 * @return
	 */
	public static Hashtable<String, Integer> getTidTable() {
		return localTidTable;
	}



	/** Hash table: proto+srcPort+destIp+destPort -> AN address  */
	private Hashtable<String, String> anTable;
	//private static String defaultAN;

	/*public static String getDefaultAN() {
		return defaultAN;
	}*/

	public TunnelManager(UPMTClient upmtClient)
	{
		TunnelManager.upmtClient = upmtClient;
		

		//XXX put this somewhere in the GUI or in the conf file
		extendedFilter = cfg.extendedFilter;

		//Settings from file (or from default)
		startPort = cfg.startPort;
		portRange = cfg.portRange;
		noUpmtMark = cfg.noUpmtMark;
		rtTablesIndex = cfg.rtTablesIndex;

		rtTablesIndexStart = rtTablesIndex;
		realPortManager = new LocalPortmgr(startPort, portRange);
		localTidTable = new Hashtable<String, Integer>();
		anTable = new Hashtable<String, String>();
		markTable = new HashMap<String, String>();
		tidToANTable = new HashMap<Integer, String>();

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"sh", "-c", "ifconfig -a | grep upmt0"});
		//#else
		//				result = Shell.executeCommand(new String[]{"sh", "-c", "netcfg | grep upmt0"});
		//#endif

		
		if(result.length()>0)
		{	
			if(!UPMTClient.getRME()) { // in rme il modulo viene caricato lato server
				//#ifndef ANDROID
				/*versione prima del kernel 3.8*/
//				result = Shell.executeCommand(new String[]{"modprobe", "-r", "xt_UPMT_ex"});
//				result = Shell.executeCommand(new String[]{"modprobe", "-r", "xt_UPMT"});
//				result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt"});
				
				/*versione con kernel 3.8*/
				try {
					result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt_"+InetAddress.getLocalHost().getHostName()});
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//#else
				//						result = Shell.executeRootCommand(new String[]{"rmmod", "xt_UPMT_ex"});
				//						result = Shell.executeRootCommand(new String[]{"rmmod", "xt_UPMT"});
				//						result = Shell.executeRootCommand(new String[]{"rmmod", "upmt"});
				//#endif
			}
			
			if(cfg.keepaliveKernel) {
				//set keepalive interval and timeout in kernel
				String[] par = new String[]{"-k", ""+cfg.keepalivePeriod, "-T", ""+cfg.keepaliveTimeout};
				String moduleResult = Module.upmtconf(par);
			}
			
		}

		//#ifndef ANDROID
		if (extendedFilter == 0) {
//			 xt_UPMT modules have been merged into upmt.ko (Sander)
//			result = Shell.executeCommand(new String[]{"modprobe", "xt_UPMT"});
		}
		else {
//			 xt_UPMT modules have been merged into upmt.ko (Sander)
//			result = Shell.executeCommand(new String[]{"modprobe", "xt_UPMT_ex"});
			try {
				result = Shell.executeCommand(new String[]{"modprobe", "upmt_"+InetAddress.getLocalHost().getHostName()});
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		result = Shell.executeCommand(new String[]{"ifconfig", "upmt0", Module.getVipaFix()});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"insmod", "/system/lib/modules/upmt.ko"});
		//				result = Shell.executeRootCommand(new String[]{"insmod", "/system/lib/modules/compat_xtables.ko"});
		//				if (extendedFilter == 0) {
		//					result = Shell.executeRootCommand(new String[]{"insmod", "/system/lib/modules/xt_UPMT.ko"});
		//				} else {
		//					result = Shell.executeRootCommand(new String[]{"insmod", "/system/lib/modules/xt_UPMT_ex.ko"});
		//				}
		//				result = Shell.executeRootCommand(new String[]{"ifconfig", "upmt0", Module.getVipaFix()});
		//#endif

		if (cfg.mtuOverride!=0) {
			printLog("overriding upmt0 mtu, new value: " + Integer.toString((cfg.mtuOverride)), Log.LEVEL_HIGH);
			result = Shell.executeCommand(new String[]{"ifconfig", "upmt0", "mtu", Integer.toString(cfg.mtuOverride)});
		}

		Module.upmtconf(new String[]{"-V", "off"}); //Modalita' verbosa

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});
		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-A", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});

		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});
		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-A", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});

		result = Shell.executeCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		result = Shell.executeCommand(new String[]{"iptables", "-A", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});

		result = Shell.executeCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		result = Shell.executeCommand(new String[]{"iptables", "-A", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-A", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});
		//		
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-A", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});
		//		
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-A", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//		
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-A", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//#endif

		if (!addTable("upmt0"))	printLog("upmt0 already present in rt_tables", Log.LEVEL_HIGH);

		result = Shell.executeCommand(new String[]{"sh", "-c", "ip rule | grep 30000:"});
		//#ifndef ANDROID
		if(result.length()==0) result = Shell.executeCommand(new String[]{"ip", "rule", "add", "priority", "30000", "table", "upmt0_table"});
		//#else
		//				if(result.length()==0) result = Shell.executeRootCommand(new String[]{"ip", "rule", "add", "priority", "30000", "table", "upmt0_table"});
		//#endif
		else printLog("ip rule already present (prio:30000)", Log.LEVEL_HIGH);

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"ip", "route", "flush", "table", "upmt0_table"});
		result = Shell.executeCommand(new String[]{"ip", "route", "add", "default", "dev", "upmt0", "table", "upmt0_table"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "flush", "table", "upmt0_table"});
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "add", "default", "dev", "upmt0", "table", "upmt0_table"});
		//#endif

		result = Shell.executeCommand(new String[]{"sh", "-c", "ip rule | grep 29999:"});
		//#ifndef ANDROID
		if(result.length()==0) result = Shell.executeCommand(new String[]{"ip", "rule", "add", "fwmark", noUpmtMark, "priority", "29999", "table", "main"});
		//#else
		//				if(result.length()==0) result = Shell.executeRootCommand(new String[]{"ip", "rule", "add", "fwmark", noUpmtMark, "priority", "29999", "table", "main"});
		//#endif
		else printLog("ip rule already present (prio:29999)", Log.LEVEL_HIGH);

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"iptables", "-t", "nat", "-D", "POSTROUTING", "-j", "MASQUERADE"});
		if(!UPMTClient.getRME()) {
			result = Shell.executeCommand(new String[]{"iptables", "-t", "nat", "-A", "POSTROUTING", "-j", "MASQUERADE"});
		}
		//#else
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "nat", "-D", "POSTROUTING", "-j", "MASQUERADE"});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "nat", "-A", "POSTROUTING", "-j", "MASQUERADE"});
		//#endif

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"sh", "-c", "echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter"});
		if(UPMTClient.getRME()) result = Shell.executeCommand(new String[]{"sh", "-c", "echo 1 > /proc/sys/net/ipv4/ip_forward"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter"});
		//#endif
	}

	public void stop()
	{
		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});
		result = Shell.executeCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});
		result = Shell.executeCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		result = Shell.executeCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		result = Shell.executeCommand(new String[]{"iptables", "-t", "nat", "-D", "POSTROUTING", "-j", "MASQUERADE"});
		result = Shell.executeCommand(new String[]{"ip", "rule", "del", "priority", "29999"});
		result = Shell.executeCommand(new String[]{"ip", "rule", "del", "priority", "30000"});
		result = Shell.executeCommand(new String[]{"sh", "-c", "echo 1 > /proc/sys/net/ipv4/conf/all/rp_filter"});
		result = Shell.executeCommand(new String[]{"ip", "r", "flush", "table", "upmt0_table"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "-p", "udp", "--source-port", "5060", "-j", "MARK", "--set-mark", noUpmtMark});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "mangle", "-D", "OUTPUT", "--destination", "127.0.0.1", "-j", "MARK", "--set-mark", noUpmtMark});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "udp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-D", "OUTPUT", "-p", "tcp", "-o", "upmt0", "-m", "conntrack", "--ctstate", "NEW", "-j", "UPMT"});
		//				result = Shell.executeRootCommand(new String[]{"iptables", "-t", "nat", "-D", "POSTROUTING", "-j", "MASQUERADE"});
		//				result = Shell.executeRootCommand(new String[]{"ip", "rule", "del", "priority", "29999"});
		//				result = Shell.executeRootCommand(new String[]{"ip", "rule", "del", "priority", "30000"});
		//				result = Shell.executeRootCommand(new String[]{"echo 1 > /proc/sys/net/ipv4/conf/all/rp_filter"});
		//				result = Shell.executeRootCommand(new String[]{"ip", "r", "flush", "table", "upmt0_table"});
		//#endif

		Shell.removeStringInFile(Default.RT_TABLE_PATH, "100\t");
		for(rtTablesIndex--; rtTablesIndex>rtTablesIndexStart; rtTablesIndex--)
		{
			//#ifndef ANDROID
			result = Shell.executeCommand(new String[]{"ip", "rule", "del", "priority", ""+rtTablesIndex});
			//#else
			//						result = Shell.executeRootCommand(new String[]{"ip", "rule", "del", "priority", ""+rtTablesIndex});
			//#endif
			Shell.removeStringInFile(Default.RT_TABLE_PATH, rtTablesIndex + "\t");
		}

		//#ifndef ANDROID
		
//		result = Shell.executeCommand(new String[]{"modprobe", "-r", "xt_UPMT"});
//		result = Shell.executeCommand(new String[]{"modprobe", "-r", "xt_UPMT_ex"});
//		result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt"});
		
		try {
			result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt_"+InetAddress.getLocalHost().getHostName()});
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//#else
		//				result = Shell.executeRootCommand(new String[]{"rmmod", "xt_UPMT"});
		//				result = Shell.executeRootCommand(new String[]{"rmmod", "xt_UPMT_ex"});
		//				result = Shell.executeRootCommand(new String[]{"rmmod", "upmt"});
		//#endif
	}



	/**
	 * Signal UPMT module to control a network interface<BR>
	 * it is not yet sure if this interface can be used (i.e. if it provides connectivity 
	 * towards any AN or fixed host)
	 * @param ifName - The name of the interface to be controlled
	 * @param defaultGW - The default GateWay for ifName
	 */
	public void addInterface(String ifName, InterfaceInfo newIf)
	{
		//#ifndef ANDROID
		Shell.executeCommand(new String[]{"ip", "route", "add", newIf.prefix +"/" + newIf.netmask, "dev", ifName, "table", "upmt0_table"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "add", newIf.prefix +"/" + newIf.netmask, "dev", ifName, "table", "upmt0_table"});
		//#endif

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"route", "del", "default", "dev", ifName});
		result = Shell.executeCommand(new String[]{"route", "add", "default", "gw", newIf.defaultGw, "dev", ifName});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "del", "default", "dev", ifName});
		//				result = Shell.executeRootCommand(new String[]{"route", "add", "default", "gw", newIf.defaultGw, "dev", ifName});
		//#endif

		//TODO PROVA ANDROID
		//		result = Shell.executeRootCommand(new String[]{"upmtconf", "-a", "dev", "-i", ifName});
		result = Module.upmtconf(new String[]{"-a","dev","-i",ifName});

		int mark = Module.getUpmtParameter(result, "Mark");
		
		//XXX MARCO: if for any possible reason mark is not returned, abort everything or retry
		//because in this case the tunnel over this interface wouldn't work at all TODO
		//This is actually a much bigger problem. addInterface (as many other functions in this program)
		//should check all return values and itself should return something, or throw exceptions.... TODO

		if (!addTable(ifName)) printLog(ifName+" already present in rt_tables", Log.LEVEL_HIGH);;

		result = Shell.executeCommand(new String[]{"sh", "-c", "ip rule | grep "+(rtTablesIndex-1)+":"});

		//#ifndef ANDROID
		if(result.length()==0) {
			result = Shell.executeCommand(new String[]{"ip","rule","add","fwmark",mark+"","priority",""+(rtTablesIndex-1),"table",ifName+"_table"});
			this.markTable.put(ifName, ""+(rtTablesIndex-1));
		}
		
		//#else
		//				if(result.length()==0) result = Shell.executeRootCommand(new String[]{"ip","rule","add","fwmark",mark+"","priority",""+(rtTablesIndex-1),"table",ifName+"_table"});
		//#endif
		else {
			printLog("ip rule already present (prio:"+(rtTablesIndex-1)+")", Log.LEVEL_HIGH);
		}

		//Shell.executeCommand(new String[] {"sudo","ip","rule", "add","default", "dev", "ifName", "table", ifName.trim()+"_table"});
		
		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"ip", "route", "show", "table", ifName+"_table"});
		if(result.length()==0) {
			result = Shell.executeCommand(new String[]{"ip", "route", "add", "default", "via", newIf.defaultGw, "dev", ifName, "table", ifName+"_table"});
		}
		
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "show", "table", ifName+"_table"});
		//		
		//				if(result.length()==0) result = Shell.executeRootCommand(new String[]{"ip", "route", "add", "default", "via", newIf.defaultGw, "dev", ifName, "table", ifName+"_table"});
		//#endif
		else {
			printLog("ip route already present (table:"+ifName+"_table)", Log.LEVEL_HIGH);
		}

		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"sh", "-c", "echo 0 > /proc/sys/net/ipv4/conf/"+ifName+"/rp_filter"});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"echo 0 > /proc/sys/net/ipv4/conf/"+ifName+"/rp_filter"});
		//#endif
	}

	public void removeInterface(String ifName, InterfaceInfo oldIf, Set<String> ANList)	
	{
		//pcerqua
		if(oldIf == null){
			System.out.println("NULL interface removal -> return;");
			return;
		}

		System.out.println("ip route del "+ oldIf.prefix +"/" + oldIf.netmask + " dev " + ifName);
		//#ifndef ANDROID
		result = Shell.executeCommand(new String[]{"ip", "route", "del", oldIf.prefix +"/" + oldIf.netmask, "dev", ifName});
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "route", "del", oldIf.prefix +"/" + oldIf.netmask, "dev", ifName});
		//#endif
		//System.out.println (result);

		String[] par = new String[]{"-x", "dev", "-i", ifName};
		for (String ANAddress : ANList){
			System.out.println("DEBUG, (list) of AN addresse(s) :" + ANAddress);
		}
		//TODO PROVA
		//		par[0] = "upmtconf " + par[0];
		//		result = Shell.executeRootCommand(par);
		result = Module.upmtconf(par);

		for (String ANAddress : ANList)
		{
			int tid = localTidTable.remove(ifName+":"+ANAddress);
			realPortManager.releaseport(ifName+ANAddress);
			tidToANTable.remove(new Integer(tid));
			
		}
		//remove ip rule. In this case I don't care about the return value
		//#ifndef ANDROID
		Shell.executeCommand(new String[]{"ip", "rule", "del", "table", ifName +"_table"});
		
		if(removeTable(ifName)) {
			this.markTable.remove(ifName);
		}
		//#else
		//				result = Shell.executeRootCommand(new String[]{"ip", "rule", "del", "table", ifName +"_table"});
		//#endif
	}


	public int addTunnel(String vipa, String ifName, String ANAddress, int AnTsa)
	{
		int port = realPortManager.getport(ifName+ANAddress);

		String[] par = new String[]{"-a","tun","-S",vipa,"-D",Module.getVipaFix(),"-i",ifName,"-d",ANAddress,"-l",""+port,"-r",""+AnTsa};
		
//		System.err.println("YYYYYY__" + "-a"+ " " +"tun"+ " " +"-S" + " " + vipa+ " " +"-D"+ " " +Module.getVipaFix()+ " " +"-i"+ " " +ifName+ " " +"-d"+ " " +ANAddress+ " " +"-l"+ " " +""+port+ " " +"-r"+ " " +""+AnTsa);
		
		//TODO PROVA
		//		par[0] = "upmtconf " + par[0];
		//		result = Shell.executeRootCommand(par);
		
//		result = Module.upmtconf(par); // chiamata tramite JNI
		result = Module.upmtconf(par, false); // se impostata a true chiamata a JNI altrimenti chiamata di sistema
		
//		System.err.println(result);

		int localTid = Module.getUpmtParameter(result, "TID");
		if (localTid == 0) {printLog("TUNNEL NON CREATO", Log.LEVEL_HIGH);return 0;}

		localTidTable.put(ifName+":"+ANAddress, new Integer(localTid));
		UPMTClient.addRMETunnelsToGUI(ANAddress);
		tidToANTable.put(new Integer(localTid), ANAddress);
		
		return localTid;
	}

	public int removeTunnel(String ifName, String ANAddress) {

		Integer tid = localTidTable.remove(ifName+":"+ANAddress);

		if(tid == null)
			return -1;

		printLog("removing local tunnel " + tid);

		String[] par = new String[]{"-x", "tun", "-n", ""+tid};
		//TODO PROVA
		//		par[0] = "upmtconf " + par[0];
		//		result = Shell.executeRootCommand(par);
		result = Module.upmtconf(par);
		
		tidToANTable.remove(new Integer(tid));

		realPortManager.releaseport(ifName+ANAddress);
		return 0;
	}
	
	public int removeTunnel(String ifName, String ANAddress, Integer tid) {

		if(localTidTable.containsKey(ifName+":"+ANAddress)) {
			localTidTable.remove(ifName+":"+ANAddress);
		}
		
//		Integer tid = localTidTable.remove(ifName+":"+ANAddress);

		if(tid == null)
			return -1;

		printLog("removing local tunnel " + tid);

		String[] par = new String[]{"-x", "tun", "-n", ""+tid};
		//TODO PROVA
		//		par[0] = "upmtconf " + par[0];
		//		result = Shell.executeRootCommand(par);
		result = Module.upmtconf(par);

		tidToANTable.remove(new Integer(tid));
		
		realPortManager.releaseport(ifName+ANAddress);
		return 0;
	}

	/*public void setDefaultAN(String ANAddress)
	{
		defaultAN = ANAddress;
	}*/

	/**
	 * assigns the protocol + the triple of srcPort, destIP, destPort
	 * to the tunnel toward the ANAddress over the ifName 
	 * @param proto
	 * @param srcPort
	 * @param destIp
	 * @param destPort
	 * @param ANAddress
	 * @param ifName
	 */
	public void assignSocketToTunnel(String proto, int srcPort, String destIp, int destPort, String ANAddress, String ifName)
	{
		Integer tidObj = localTidTable.get(ifName+":"+ANAddress);
		if (tidObj==null){
			printLog("ERROR: no Tunnel for " +ifName+":"+ANAddress, Log.LEVEL_HIGH);
			return;
		}

		int tid = tidObj;
		String[] param = new String[]{"-a","rule","-p",proto,"-s",Module.getVipaFix(),"-d",destIp,"-l",""+srcPort,"-r",destPort+"","-n",""+tid};
		
//		System.err.println("-a"+"rule"+"-p"+proto+"-s"+Module.getVipaFix()+"-d"+destIp+"-l"+""+srcPort+"-r"+destPort+""+"-n"+""+tid);
		printLog("Socket handover on local tunnel " + tid+ " (AN:"+ANAddress+ "-if:"+ifName+") requested for src port "+ srcPort + " dest "+ANAddress+":"+destPort, Log.LEVEL_MEDIUM);
		//TODO PROVA
		//		param[0] = "upmtconf " + param[0];
		//		result = Shell.executeRootCommand(param);
		result = Module.upmtconf(param);
		
		//Response from kernel:
		//System.out.println(result);
		//System.out.println(Module.upmtconf(new String[]{"-l", "rule"}));

		anTable.put(proto+srcPort+destIp+destPort, ANAddress);
	}

	public void moveSocketToInterf(String protocol, int localPort, String destAddress, int destPort, String interf, String appName)
	{
		String ANAddress = anTable.get(protocol+localPort+destAddress+destPort);
		if (ANAddress==null) ANAddress = upmtClient.getDefaultAN(); //per le app nel tunnel di default
		
		appToAN.put(appName, ANAddress);
				
		assignSocketToTunnel(protocol, localPort, destAddress, destPort, ANAddress, interf);
	}
	
	public static String getAppAN(String appName){
		if(appToAN.get(appName)!=null)
			return appToAN.get(appName);
		else
			return upmtClient.getDefaultAN();
	}

	private boolean addTable(String ifName)
	{
		String row = rtTablesIndex++ + "\t" + ifName + "_table";
		return Shell.insertStringInFile(Default.RT_TABLE_PATH,3,row);
	}
	
	private boolean removeTable(String ifName) {
		String row = markTable.get(ifName) + "\t" + ifName + "_table";
		
//		System.err.println("remove line "+row);
		
		return Shell.removeStringInFile(Default.RT_TABLE_PATH, row);
	}

	/** returns TID_DROP if the localTidTable hashtable does not contain the key ifName+ANAddress */
	public synchronized int getTid(String ifName, String ANAddress) {
		String toCheck = ifName+":"+ANAddress;
		Iterator<String> keyIter = localTidTable.keySet().iterator();
		while(keyIter.hasNext()) {
			String found = keyIter.next();
			if(found.equals(toCheck)) {
				return localTidTable.get(ifName+":"+ANAddress);
			}
		}
		return UPMTClient.TID_DROP;
//		if (localTidTable.containsKey(ifName+":"+ANAddress)){
//			return localTidTable.get(ifName+":"+ANAddress);
//		} else {
//			return UPMTClient.TID_DROP;
//		}
	}
	
	public String getTidToAn(int tid) {
		if(tidToANTable.containsKey(new Integer(tid))) {
			return tidToANTable.get(new Integer(tid));
		} else {
			return null;
		}
	}
	
	/**
	 * assigns the protocol + the triple of srcPort, destIP, destPort
	 * to the tunnel toward the ANAddress over the ifName 
	 * @param proto
	 * @param srcPort
	 * @param destIp
	 * @param destPort
	 * @param ANAddress
	 * @param ifname
	 */
	public void assignSocketToTunnelRME(String proto, int srcPort, String destIp, int destPort, int tid)
	{
		String[] param = new String[]{"-a","rule","-p",proto,"-s",Module.getVipaFix(),"-d",destIp,"-l",""+srcPort,"-r",destPort+"","-n",""+tid};
		printLog("Socket handover on local tunnel " + tid+ " requested for src port "+ srcPort + " dest port "+ destPort +" and dest address: "+destIp,  Log.LEVEL_MEDIUM);
		result = Module.upmtconf(param);
	}

	public ArrayList<String> getTemporaryTunnelSetup() {
		return TemporaryTunnelSetup;
	}

	public void setTemporaryTunnelSetup(ArrayList<String> temporaryTunnelSetup) {
		TemporaryTunnelSetup = temporaryTunnelSetup;
	}

	//******************LOG METHODS******************************************************************************
	private void printLog(String text, int logLevel) {UPMTClient.printGenericLog(this, text, logLevel);}
	private void printLog(String text) {UPMTClient.printGenericLog(this, text, 0);}

}
