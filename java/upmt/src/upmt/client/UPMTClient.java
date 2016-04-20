package upmt.client;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.zoolu.sip.provider.SipProvider;
import org.zoolu.tools.Log;
import org.zoolu.tools.Logger;

//#ifdef ANDROID
//	import android.content.Context;
//	import android.widget.Toast;
//#endif










import upmt.Default;
import upmt.TunnelInfo;
import upmt.client.application.ANPolicy.ANPolicy;
import upmt.client.application.ANPolicy.ANPolicyFactory;
import upmt.client.application.interfPolicy.InterfPolicy;
import upmt.client.application.interfPolicy.InterfPolicyFactory;
import upmt.client.application.interfPolicy.policy.BlockPolicy;
import upmt.client.application.manager.ApplicationManager;
import upmt.client.application.manager.ApplicationManagerFactory;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.application.manager.impl.GUIApplicationManager;
import upmt.client.application.manager.impl.PopUpListener;
import upmt.client.application.manager.impl.PopUpWindows;
import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorFactory;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.core.ConfigManager;
import upmt.client.core.IfsHashTable;
import upmt.client.core.InterfaceInfo;
import upmt.client.core.Socket;
import upmt.client.network.NetworkMonitor;
import upmt.client.network.NetworkMonitorFactory;
import upmt.client.network.NetworkMonitorListener;
import upmt.client.rme.DDSQoSTunnelInfo;
import upmt.client.rme.DDSQosReceiver;
import upmt.client.rme.RMEInterface;
import upmt.client.rme.RoutingCheck;
import upmt.client.rme.VipaMeasures;
import upmt.client.rme.VipaTunnel;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;
import upmt.os.Module;
import upmt.os.Shell;
import upmt.server.UPMTServer;
import upmt.server.rme.RMEServer;
import upmt.server.rme.RMETunnelInfo;

public class UPMTClient implements NetworkMonitorListener, ApplicationManagerListener, ApplicationMonitorListener
{
	/** set to true when developing the GUI */
	public static final boolean ONLY_GUI = false;

	public static final boolean textMode = false;

	public static final boolean coreEmulator = false;

	public static final boolean crossTunnel = true;

	public static final boolean blockerAssociation = true; 

	public static final boolean grapher3D = true;

	public static boolean DDSQoSReceiver = true;

	public static boolean interfaceBalance = false;

	static{System.setProperty("java.net.preferIPv4Stack", "true");}

	public final static int EVENT_START = 0;
	public final static int EVENT_INTERFACE_UP = 1;
	public final static int EVENT_INTERFACE_DOWN = 2;
	public final static int EVENT_AN_UP = 3;
	public final static int EVENT_AN_DOWN = 4;

	private static final String CRLF = System.getProperty("line.separator"); //TODO: sostituire "\n" con CRLF in tutte le stringhe
	/**
	 * conventional tid meant to indicate that the packet has to be dropped because no interface is OK for a given application or flow
	 */
	public static final int TID_DROP = -1;

	private static final String WIZARD_AN = "Anchor Node IP address (IP:PORT)";
	private static final String WIZARD_Broker = "Anchor Node Broker IP address (IP:PORT)";
	private static final String WIZARD_SIP = "Your Sip Address";
	private static final String WIZARD_DESC = "Wizard";


	public static ConfigManager cfg;
	private static String cfgFile;

	/** Object to perform logging */
	private static Logger logger;

	/** Hashtable (interface name-> InterfaceInfo) 
	 *  containing the detected network interfaces and associated ip addresses/interface gateway<BR>
	 *  it does not say if the interface can be used (e.g. if it provides connectivity toward the AN)
	 */
	//	private static Hashtable<String, InterfaceInfo> availableIfs = new Hashtable<String, InterfaceInfo>();

	/** Hashtable (interface name-> InterfaceInfo) 
	 *  containing the detected network interfaces and associated ip addresses/interface gateway<BR>
	 *  it does not say if the interface can be used (e.g. if it provides connectivity toward the AN)
	 */
	private static IfsHashTable availableIfs = new IfsHashTable();

	public static IfsHashTable getAvailableIfs() {
		return availableIfs;
	}

	public static Hashtable<String, Integer> getAssociatedANs() {
		return associatedANList;
	}

	//Anchor Node
	private int maxANNumber;
	/**
	 * a vector of strings ipaddress:port of Anchor Nodes as read by the configuration files 
	 */
	private static Vector<String> cfgANList;
	/**
	 * 
	 * @return a vector of strings ipaddress:port of Anchor Nodes as read by the configuration files
	 */
	public static Vector<String> getCfgANList() {
		return cfgANList;
	}
	/**
	 * Add an AN to ANlist
	 */
	public static void addCfgAnlist(String anAddress) {
		cfgANList.add(anAddress);
	}

	/**
	 * hastable of active and associated ANs vs. their TSA port number
	 */
	private static Hashtable<String, Integer> associatedANList = new Hashtable<String, Integer>();

	/**
	 * hastable of ANs vs. their SIP port number
	 */
	private static Hashtable<String, Integer> ANtoSipPort= new Hashtable<String, Integer>();

	public final static int REDIRECT_DEPTH = 1; //used for redirect mechanism while downloading ANList from an ANBroker

	//Radio Multiple Eterogenee
	private static boolean rme;
	private static ArrayList<String> rmeInterfacesList;
	private static HashMap<String, SipProvider> signalers;
	private RoutingCheck routingCheck;
	public static ArrayList<RMEInterface> rmeAddresses = new ArrayList<RMEInterface>();
	public static String[] rmeAssocAddress;

	private DDSQosReceiver ddsQosReceiver;

	public static HashMap<String, RMETunnelInfo> RMEremoteTidStatusTable = new HashMap<String, RMETunnelInfo>(); 

	/**
	 * HashTable which contains all the tunnels <ifname, anAddress>
	 */
	public Hashtable<String, String> rmeTunnelIpInterfaceList = new Hashtable<String, String>();

	/**
	 * ArrayList which contains only the direct tunnel <anAddress:ifname>
	 */
	public ArrayList<String> rmeDirectTunnel = new ArrayList<String>();

	/**
	 * ArrayList which contains the RME servers
	 */
	private static ArrayList<RMEServer> servers = new ArrayList<RMEServer>();

	public static boolean firstTunnel = true; // provvisorio

	public static ArrayList<String> RMETunnelsToGUI = new ArrayList<String>(); 

	/**
	 * hashtable of policies (expressed as strings) for each application 
	 * it starts from cfg.rmeApplicationPolicy read from cfg file, then in UPMTClient.putRMEPoliciesToHastable
	 * 
	 */
	private HashMap<String, ArrayList<String>> rmeCfgPolicy = new HashMap<String, ArrayList<String>>(); 


	/**
	 * HashMap of tunnel list vs. Vipa
	 */
	public HashMap<String, ArrayList<VipaTunnel>> rmePerVipaTunnelList = new HashMap<String, ArrayList<VipaTunnel>>();

	/**
	 * HashMap of best tunnel for each Vipa
	 */
	public HashMap<String, TunnelInfo> rmePerVipaBestTunnel = new HashMap<String, TunnelInfo>();

	/**
	 * HashMap of best tunnel for each Vipa
	 */
	public HashMap<String, RMETunnelInfo> rmePerVipaBestTunnelServer = new HashMap<String, RMETunnelInfo>();

	/**
	 * HashMap of best tunnel for each Vipa
	 */
	public HashMap<String, DDSQoSTunnelInfo> rmePerVipaBestTunnelDDS = new HashMap<String, DDSQoSTunnelInfo>();

	/**
	 * HashMap DDS Vipa for Qos in millisecondi
	 */
	public HashMap<String, Integer> ddsQoS = new HashMap<String, Integer>();

	/**
	 * hashtable (application name -> policy )
	 * for the active applications (i.e. with open sockets)
	 */
	private HashMap<String, String> rmeOpenedPolicy = new HashMap<String, String>();

	/**
	 * hashtable (application name -> policy )<BR>
	 * for the closed applications (i.e. without any open sockets)
	 */
	private HashMap<String, String> rmeClosedPolicy = new HashMap<String, String>();

	/**
	 * HashMap (application opend -> tunnel)<BR>
	 * for the active applications (with assigned tunnel)
	 */
	private HashMap<String, HashMap<String, Integer>> rmeOpenedAppToTunnel = new HashMap<String, HashMap<String,Integer>>();

	/**
	 * List of closed rme applications which need a tunnel policy
	 */
	public HashMap<String, HashMap<String, Integer>> rmeClosedAppToTunnel = new HashMap<String, HashMap<String,Integer>>();


	/**
	 * List of rme policy event
	 */
	public final static int RME_EVENT_START = 0;
	public final static int RME_EVENT_INTERFACE_UP = 1;
	public final static int RME_EVENT_INTERFACE_DOWN = 2;
	public final static int RME_EVENT_TUN_UP = 3;
	public final static int RME_EVENT_TUN_DOWN = 4;
	public final static int RME_EVENT_TUN_UPDATE = 5;

	/**
	 * application registered for each event<BR>
	 * the rmeEventRegister is initialized in the constructor of UPMTClient with the events:<BR>
	 * RME_EVENT_START, RME_EVENT_INTERFACE_UP, RME_EVENT_INTERFACE_DOWN RME_EVENT_TUN_UP RME_EVENT_TUN_DOWN<BR>
	 * the set of events is retrieved with eventRegister.keySet()<BR>
	 * for each event a ArrayList of String contains the applications that needs to be
	 * notified of such event
	 */
	private static HashMap<Integer, ArrayList<String>> rmeEventRegister = new HashMap<Integer, ArrayList<String>>();

	private HashMap<String, String[]> rmeDiscoveredAddresses = new HashMap<String, String[]>();

	/**
	 * List of currents endpoint discovered by olsr
	 */
	public ArrayList<String> olsrDetectedEndPoint = new ArrayList<String>();

	public static HashMap<String, String> ipToVipa = new HashMap<String, String>();
	/**
	 * hashtable (ifname -> SipProvider) 
	 * stores the sipProviders used to send KA messages towards any AN for each given interface
	 */
	private Hashtable<Integer, SipProvider> tunnelProviders = new Hashtable<Integer, SipProvider>();

	//****************************************POLICY****************************************	

	/**
	 * hashtable of policies (expressed as strings) for each application 
	 * it starts from cfg.applicationPolicy read from cfg file, then in UPMTClient.putPoliciesToHastable
	 * it removes [ ] and , with ""
	 * 
	 */
	private Hashtable<String, String> cfgPolicy = new Hashtable<String, String>();
	/**
	 * String containing the default behaviour for unspecified applications	
	 */
	private String cfgDefaultPolicy;
	private String cfgSignalingPolicy;
	private String defaultAN;
	private String cfgDefaultANPolicy;

	//closed
	/**
	 * hashtable (application name -> policy )<BR>
	 * for the closed applications (i.e. without any open sockets)
	 */
	private Hashtable<String, InterfPolicy> closedPolicy= new Hashtable<String, InterfPolicy>();


	/**
	 * hashtable for the AN Policy read from cfg file
	 */
	private Hashtable<String, ANPolicy> anchorNodePolicy = new Hashtable<String, ANPolicy>();
	/**
	 * hashtable containing the mapping App--AN
	 */
	private Hashtable<String, String> anchorNodeTable =new Hashtable<String, String>();

	/**
	 * hashtable (application name -> interface name )<BR>
	 * for the closed applications (i.e. without any open sockets)<BR>
	 * closedInterface.get(app) returns null if no interface can be associated to the application
	 */
	private Hashtable<String, String> closedInterface = new Hashtable<String, String>();

	private InterfPolicy defaultClosedPolicy;

	/** default interface for Closed applications (i.e. without any open sockets)<BR>
	 * it is null if there is no available interface for closed applications
	 */
	private String defaultClosedInterface;

	//FIXME mettere se si vogliono le regole temporanee per le app kiuse
	//	private Hashtable<String, Vector<String>> tmpPolicy;//temp (va di paripasso con le closed ed e' usato quando un app si kiude e devo ricreare la closed.)
	//	private Vector<String> tmpDefaultPolicy;//temp (va di paripasso con le closed ed e' usato quando un app si kiude e devo ricreare la closed.)

	//opened
	private InterfPolicy signalingPolicy;

	/**
	 * interface used for signaling - it corresponds to SipSignalManager.activeInterfaceName
	 */
	private String signalingInterface;

	/**
	 * hashtable (application name -> policy )
	 * for the active applications (i.e. with open sockets)
	 */
	private Hashtable<String, InterfPolicy> openedPolicy = new Hashtable<String, InterfPolicy>();

	/** app -> current interface <BR>
	 * if no active interface can be used for an application,<BR>
	 * openedInterface.get(app) returns null
	 */
	private Hashtable<String, String> openedInterface = new Hashtable<String, String>();

	/** Hash table app-> vector of sockets*/
	private Hashtable<String, Vector<Socket>> socketForApp= new Hashtable<String, Vector<Socket>>();

	/** hash table socket->app*/
	private Hashtable<String, String> appForSocket= new Hashtable<String, String>();

	//exception
	private Vector <String> noUpmtApp= new Vector<String>(Arrays.asList(cfg.noUpmtApp)); //TODO: COMPLETAMENTE DA FARE!!!! MODIFICARE L'APPMON E IL MODULO!!!!

	/**
	 * application registered for each event<BR>
	 * the eventRegister is initialized in the constructor of UPMTClient with the events:<BR>
	 * EVENT_START, EVENT_INTERFACE_UP, EVENT_INTERFACE_DOWN EVENT_AN_UP EVENT_AN_DOWN<BR>
	 * the set of events is retrieved with eventRegister.keySet()<BR>
	 * for each event a Vector of String contains the applications that needs to be
	 * notified of such event
	 */
	private static Hashtable<Integer, Vector<String>> eventRegister = new Hashtable<Integer, Vector<String>>();
	//*************************


	public static Hashtable<Integer, Vector<String>> getEventRegister() {
		return eventRegister;
	}

	/** Monitor to control the network interfaces */
	private NetworkMonitor networkMonitor;
	private SipSignalManager signaler;
	private TunnelManager tunnelManager;
	private ApplicationManager appManager;
	private ApplicationMonitor appMonitor;

	private static String statusMsg;

	//#ifdef ANDROID
	//		private static Context context;
	//		
	//		public static void start(String file, Context cnt)
	//		{
	//			statusMsg = "Initializing...";
	//			context = cnt;
	//#else
	public static void start(String file)
	{
		statusMsg = "Initializing...";
		//#endif
		if(rme) {
			file = file!=null?file:Default.PEER_CONFIG_FILE;
		}
		else {
			file = file!=null?file:Default.CLIENT_CONFIG_FILE;
		}
		UPMTClient.cfg = ConfigManager.instance();
		UPMTClient.cfgFile = file;
		cfg.ParseConfig(file);

		if (ONLY_GUI) {
			instance().run();
			return;
		}


		if(rme) {

			instance().run();
		}
		else {
			//#ifndef ANDROID		
			if (cfg.sipID==null || (cfg.ANList.size()==0 && cfg.ANBrokerList.size() == 0))
				new PopUpWindows(new String[]{WIZARD_AN, WIZARD_Broker, WIZARD_SIP}, new String[]{cfg.ANList.size() >= 1 ? cfg.ANList.get(0) : "", cfg.ANBrokerList.size() >= 1 ? cfg.ANBrokerList.get(0) : "", cfg.sipID},  WIZARD_DESC, new PopUpListener() {
					public void done(Vector<String> ret) {

						boolean quit0 = false;
						boolean quit1 = false;

						if (ret==null || ret.size()!=3) quit0 = quit1 = true;

						if (!ret.get(0).contains(":")) quit0 = true;
						try{Integer.parseInt(ret.get(0).split(":")[1].trim());}
						catch(Exception e) {quit0 = true;}

						if (!ret.get(1).contains(":")) quit1 = true;
						try{Integer.parseInt(ret.get(1).split(":")[1].trim());}
						catch(Exception e) {quit1 = true;}

						if(quit0 && quit1)
							quitGui();

						if(!quit0){
							cfg.ANList = new Vector<String>(Arrays.asList(new String[]{ret.get(0)}));
							cfg.writeTag(Default.ANList_TAG, ret.get(0));
						}
						if(!quit1){
							cfg.ANBrokerList = new Vector<String>(Arrays.asList(new String[]{ret.get(1)}));
							cfg.writeTag(Default.ANBrokerList_TAG, ret.get(1));
						}
						cfg.sipID = ret.get(2);
						cfg.writeTag(Default.sipID_Tag, ret.get(2));

						instance().run();
					}
				}).setVisible(true);
			else {
				instance().run();
			}
		}
		//#else
		//				instance().run();
		//#endif		

	}

	// Singleton
	private static UPMTClient instance;

	public static UPMTClient instance()
	{
		if (instance == null) instance = new UPMTClient();
		return instance;
	}

	private UPMTClient() {

		if (ONLY_GUI){
			appManager = ApplicationManagerFactory.getApplicationManager(cfg.applicationManager);
			return;
		}

		if(rme) {
			Module.setVipaFix(cfg.vepa);
		} else {
			Module.setVipaFix(cfg.vipaFix);
		}
		//#ifndef ANDROID
		logger = new Logger(new Log(cfg.logFile, cfg.logLevel));
		//#else
		//				logger = new Logger(new Log("/sdcard/upmt/" + cfg.logFile, cfg.logLevel));
		//#endif
		tunnelManager = new TunnelManager(this);
		signaler = new SipSignalManager(cfgFile, tunnelManager, this);
		//TODO: file passato per configurazioni SIP. Separare i 2 file? (SIP e UPMT).
		//TODO: ma perche' si passa il client? NN basta prenderlo con Instance?

		networkMonitor = NetworkMonitorFactory.getMonitor(cfg.networkMonitor);
		networkMonitor.setInterfToSkip(cfg.interfToSkip);

		if (!coreEmulator) appManager = ApplicationManagerFactory.getApplicationManager(cfg.applicationManager);
		appMonitor = ApplicationMonitorFactory.getApplicationMonitor(cfg.applicationMonitor);
		appMonitor.setClient(this);

		//Anchor Node
		maxANNumber = cfg.anNumber;
		//Radio Multiple Eterogenee 
		if(rme) {
			interfaceBalance = cfg.interfaceBalance;
			if (!coreEmulator) ((GUIApplicationManager) this.appManager).setLoadBalance();
			ddsQosReceiver = new DDSQosReceiver(cfg.vepa, cfg.ddsQosPort, this);
			rmeAddresses = RMEInterface.parseConfiguration(cfg.rmeNet, cfg.adhocwlan);
			rmeInterfacesList = new ArrayList<String>(Arrays.asList(RMEInterface.getInterfacesNames(rmeAddresses)));
			rmeAssocAddress = RMEInterface.getInterfacesAddresses(rmeAddresses);
			ArrayList<String> olsrdConf = cfg.olsrdConf;

			cfgANList = new Vector<String>();

			rmeEventRegister.put(RME_EVENT_START, new ArrayList<String>());
			rmeEventRegister.put(RME_EVENT_INTERFACE_UP, new ArrayList<String>());
			rmeEventRegister.put(RME_EVENT_INTERFACE_DOWN, new ArrayList<String>());
			rmeEventRegister.put(RME_EVENT_TUN_UP, new ArrayList<String>());
			rmeEventRegister.put(RME_EVENT_TUN_DOWN, new ArrayList<String>());
			rmeEventRegister.put(RME_EVENT_TUN_UPDATE,  new ArrayList<String>());
			for(RMEServer server: servers) {
				server.setUpmtClient(this);
			}

		} else {
			cfgANList = cfg.ANList; //TODO: meglio fare una copia per eventuali modificke
			//initialize the policies
			cfgDefaultPolicy = Arrays.toString(cfg.defaultAppPolicy.toArray(new String[] {}))
					.replace("[", "").replace("]", "").replace(",", "");

			System.err.println("\n*********************************\n\nDefault_app_policy in cfg file: " + cfg.defaultAppPolicy + "\n\n*********************************");

			//marco  SET DEFAULT AN POLICY IF PRESENT


			defaultClosedPolicy = InterfPolicyFactory.getPolicy(null,cfgDefaultPolicy);


			eventRegister.put(EVENT_START, new Vector<String>());
			eventRegister.put(EVENT_INTERFACE_UP, new Vector<String>());
			eventRegister.put(EVENT_INTERFACE_DOWN, new Vector<String>());
			eventRegister.put(EVENT_AN_UP, new Vector<String>());
			eventRegister.put(EVENT_AN_DOWN, new Vector<String>());
		}
		setSignalingPolicy();
		if(cfg.keepaliveKernel) {
			if(cfg.keepalivePeriod>cfg.keepaliveTimeout) {
				printLog("Check your keepalive kernel configuration in peer.cfg, timeout must be greater or almost equals then period", Log.LEVEL_HIGH);
				stop();
				
			}
		}
		//InterfPolicyFactory.setEventRegister(eventRegister);

	}


	//marco
	/**
	 * for all applications listed in cfg files, it sets the policy into hashtable, register the application
	 * to events (in the InterfPolicyFactory.getPolicy method) and adds the application to closedPolicy hash map
	 * then it tells the appmonitor which tunnel to use for new connections
	 */
	private void putPoliciesToHashtable() {

		for (String appName : cfg.applicationPolicy.keySet())
			cfgPolicy.put( appName,	Arrays.toString( cfg.applicationPolicy.get(appName).toArray(
					new String[] {})).replace("[", "").replace("]", "").replace(",", ""));

		for (String app : cfgPolicy.keySet()) {
			if (cfgPolicy.get(app).contains("-AN=")){
				//EXTRACT AN POLICY FROM STRING
				String policy = cfgPolicy.get(app).replace("-AN=","");
				String PerANPolicy =policy.substring(policy.indexOf("\"")+1,policy.lastIndexOf("\""));
				policy=policy.replace ("\"" + PerANPolicy + "\"", "" );

				ANPolicy anPolicy = ANPolicyFactory.getPolicy(app, PerANPolicy);

				if(anPolicy!=null) {
					anchorNodePolicy.put(app, anPolicy);
					String newAnchor= anPolicy.getActiveAN(associatedANList, null, EVENT_START);
					printLog(CRLF + "APP: " +app+ "  -  Selected Anchor= " + newAnchor + CRLF,Log.LEVEL_HIGH);
					if (newAnchor!=null)
						anchorNodeTable.put(app, newAnchor);

				}

				InterfPolicy ifPolicy = InterfPolicyFactory.getPolicy(app, policy);
				closedPolicy.put(app, (ifPolicy !=null ? ifPolicy : defaultClosedPolicy ));
			}
			else {
				InterfPolicy ifPolicy = InterfPolicyFactory.getPolicy(app, cfgPolicy.get(app));
				closedPolicy.put(app, (ifPolicy !=null ? ifPolicy : defaultClosedPolicy ));

			}
		}

		for (String appName : closedPolicy.keySet()) {
			InterfPolicy policy = closedPolicy.get(appName);
			String newInterf = policy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), null, EVENT_START);

			appMonitor.setApp(appName, newInterf == null ? TID_DROP : tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));

			if (newInterf!=null) {
				closedInterface.put(appName, newInterf);
			} else {
				closedInterface.remove(appName);
			}

		}
	}

	//marco
	/**
	 * @param app
	 * @return the policy for the given application if present in the anchorNodePolicy hashtable, otherwise the default one
	 */
	ANPolicy getANPolicy(String app) {

		ANPolicy anpolicy = null;
		//if(app==null){
		//System.out.println("NO APP GIVEN");
		//}

		if (anchorNodePolicy.containsKey(app)) {
			anpolicy = anchorNodePolicy.get(app);
			System.out.println( "PRE-CHARGED POLICY FOR THE APP "+app +" is: "+ anpolicy );
		}

		else {
			anpolicy = ANPolicyFactory.getPolicy(null, cfgDefaultANPolicy);
			System.out.println("APP " +app+" NOT IN TABLE ---->> DEFAULT POLICY: " + anpolicy);
		}

		return anpolicy;	
	}



	//MARCO TODO
	public String getSelectedAppAN(String appName) {

		String newAnchor =null;

		if(anchorNodeTable.containsKey(appName)){
			newAnchor =anchorNodeTable.get(appName);
			//			System.out.println( CRLF + "APP: " +appName+ " in anchorNodeTable =--->> Selected Anchor Node = " + newAnchor + CRLF);
		}			

		else {
			newAnchor= defaultAN;
			//			System.out.println( CRLF + "APP: " +appName+ " not in anchorNodeTable =--->> Default Anchor Node = " + newAnchor + CRLF);
		}

		System.err.println("APP: " +appName+ " sending traffic to AN " + newAnchor);

		return newAnchor;
	}



	private void setSignalingPolicy(){

		cfgSignalingPolicy = cfg.signalingPolicy == null ? cfgDefaultPolicy: Arrays.toString 
				(cfg.signalingPolicy.toArray(new String[] {})).replace("[", "").replace("]", "").replace(",", "");
		signalingPolicy = InterfPolicyFactory.getPolicy(null,cfgSignalingPolicy);

		signalingInterface = signalingPolicy.getActiveInterf(availableIfs.filterOnSignalingOK(defaultAN), null, EVENT_START);

		if (signalingInterface != null) {
			signaler.changeDefaultInterface(signalingInterface, availableIfs.get(signalingInterface));
		}

		printLog(signalingInterface == null ? 
				"WARNING! No-interf-for-signaling!!!" : "Chosen " + signalingInterface + " as signaling interface!" + CRLF,
				Log.LEVEL_HIGH);

	}

	/**
	 * it sets the default AN and then a default policy to be used if applications do not have a policy
	 * then it tells to the appmonitor which is the default tunnel to be used at connection setup
	 */
	private void configureDefaultPolicy(){
		if (cfgDefaultPolicy.contains("-AN=")){

			//extract the AN Policy
			String defaultpolicy = cfgDefaultPolicy.replace("-AN=","");
			cfgDefaultANPolicy =defaultpolicy.substring(defaultpolicy.indexOf("\"")+1, defaultpolicy.lastIndexOf("\""));
			cfgDefaultPolicy=defaultpolicy.replace ("\"" + cfgDefaultANPolicy + "\"", "" );

			System.err.println(CRLF+ "Default AN Policy:" + cfgDefaultANPolicy + CRLF+ "Default Interface Policy:" + cfgDefaultPolicy+ CRLF);

			ANPolicy defAnPolicy = ANPolicyFactory.getPolicy(null, cfgDefaultANPolicy);
			if (defAnPolicy!=null){
				defaultAN=defAnPolicy.getActiveAN(associatedANList, null, EVENT_START);	
			}
			else defaultAN=signaler.getDefaultAN();

		}

		else {
			//			ANPolicy defAnPolicy = ANPolicyFactory.getPolicy(null, "Any");
			//			defaultAN=defAnPolicy.getActiveAN(associatedANList, null, EVENT_START);
			defaultAN=signaler.getDefaultAN();
		}

		System.err.println(CRLF + "defaultAN is : " + defaultAN + CRLF);
		defaultClosedPolicy = InterfPolicyFactory.getPolicy(null,cfgDefaultPolicy);


		defaultClosedInterface = defaultClosedPolicy.getActiveInterf(availableIfs.filterOnCanUseTunnel(defaultAN), null, EVENT_START);
		appMonitor.setDefault(defaultClosedInterface == null ? 
				TID_DROP :
					tunnelManager.getTid(defaultClosedInterface,defaultAN));


	}


	private void run() {

		if (ONLY_GUI) {
			appManager.startListen(this);
			return;
		}

		if (!coreEmulator) appManager.startListen(this);


		printLog("Scanning network interface and performing UPMT associations!!!", Log.LEVEL_LOW);

		Hashtable<String, InterfaceInfo> initialInterf = networkMonitor.getInterfaceList();

		if (initialInterf.size() != 0) {
			for (String ifName : initialInterf.keySet()) {
				InterfaceInfo newIf = initialInterf.get(ifName);
				printLog("Detected ifs: " + ifName + " (IP:"
						+ newIf.ipAddress + " - gw:" + newIf.defaultGw
						+ ")", Log.LEVEL_HIGH);
				availableIfs.put(ifName, newIf);
				tunnelManager.addInterface(ifName, newIf);
				System.out.println("INITIAL START " + newIf.id);

				if(rme) runOlsrd(ifName);
			}

			if(!rme) downloadANList();

			//for each anchor node and for all the availableIfs
			//it checks on which interfaces it is possible to contact
			//the anchor node

			networkMonitor.startListen(this); //monitor changes of network interfaces
			//GUIApplicationManager for commands given by the user on the GUI
			//like save policy, apply policy
			if(!UPMTClient.textMode) {
				appManager.startWorking(); 	
			}
			appMonitor.setClient(this);
			appMonitor.startListen(this); 	//per l'apertura o chiusura di applicazioni

			if(rme) {
				putRMEPoliciesToHashtable();
				routingCheck = new RoutingCheck(this);
				routingCheck.startRME();
				ddsQosReceiver.startDDSQosThread();

			} else {
				updateMsg("Trying to contact Anchor Node(s)...");
				while(associatedANList.size() == 0) {
					for (String ANAddress : cfgANList) {
						if (associatedANList.size() < maxANNumber) {
							String[] token = ANAddress.split(":");
							synchronized(availableIfs) {
								for (String interf : availableIfs.keySet()) {
									if(addANonIf(token[0].trim(), interf, true)){					
										createAllTunnelsToAN(token[0].trim(), associatedANList.get(token[0].trim()));
										signaler.startKeepALiveThread();
										break;
									}
								}
							}
						}
					}
				}
			}

			if(!textMode) {
				//#ifndef ANDROID
				((GUIApplicationManager) appManager).startGraphers();
				//#endif
			}


			if(!rme) {
				configureDefaultPolicy();
				putPoliciesToHashtable();
			}

			//MARCO cambiata ipotesi del'if
			//if (defaultAN != null) {
			if (associatedANList.size()>0) {
				updateMsg("Checking policies...");
			} 

			else {
				updateMsg("Unable to contact any Anchor Node.");

				printLog("No AN contacted!!!", Log.LEVEL_HIGH);
			}

		} else {
			printLog("No network interface detected!!!", Log.LEVEL_HIGH);
		}

		//used by SipSignalManager
		//it notifies UPMT client when an AN is added (endAddAN)
		//it performs a registration procedure
		//		signaler.startListen(this); 

		printLog("END OF SETUP!!! Start listening events..."+ CRLF, Log.LEVEL_LOW);

		if (associatedANList.size()>0){
			updateMsg("Up and running.");
		} else {
			updateMsg("Unable to contact any Anchor Node.");
		}

	}

	/** update the message in the status bar 
	 * @param msg
	 */

	public void updateMsg (String msg) {
		statusMsg = msg;
		//#ifndef ANDROID
		if(!textMode) {
			((GUIApplicationManager)appManager).refreshStatusBarAndFirstRow();
		}
		//#else
		//				Toast.makeText(getContext(), statusMsg, Toast.LENGTH_SHORT).show();
		//#endif
	}

	public void forceRuleByTheInside(String appName, int tunnel){
		appMonitor.setApp(appName, tunnel);
	}

	public void downloadANList(){
		//TODO MARCO
		System.out.println("Anchor Node Broker List size: " + cfg.ANBrokerList.size());

		Vector<String> newANList = new Vector<String>();

		for(int i = 0; i < cfg.ANBrokerList.size(); i++){
			//System.out.println("Anchor Node Broker: " + cfg.ANBrokerList.get(i));

			String[] brokerList = null;

			HashSet<String> availableIfsCopy = null;

			synchronized(availableIfs) {
				availableIfsCopy = new HashSet<String>(availableIfs.keySet());
			}


			for (String interf : availableIfsCopy) {
				brokerList = signaler.ANListReq(cfg.ANBrokerList.get(i).split(":")[0], Integer.parseInt(cfg.ANBrokerList.get(i).split(":")[1]), interf, availableIfs.get(interf), REDIRECT_DEPTH);

				if(brokerList != null){
					break;
				}
			}

			if(brokerList != null){
				for(String s : Arrays.asList(brokerList)){
					if(!newANList.contains(s))
						newANList.add(s);
				}
			}
		}

		for(String an : cfgANList){
			if(!newANList.contains(an))
				newANList.add(an);
		}

		System.out.println("AN List received from Brokers:");
		for(String s : newANList){
			System.out.println(s);
		}

		if(newANList.size() > 0){
			cfgANList = newANList;
		}

		ANtoSipPort.clear();

		for(String AN : cfgANList){
			if(AN.contains(":"))
				ANtoSipPort.put(AN.split(":")[0], new Integer(AN.split(":")[1]));
		}
	}

	public void cleanDeadANs(String AN){
		Vector<String> temp = new Vector<String>();

		for(int i = 0; i < cfgANList.size(); i++)
			if(cfgANList.get(i).startsWith(AN)){
				System.out.println("Removing probably dead AN " + cfgANList.get(i));
				temp.add(cfgANList.get(i));
			}

		cfgANList.removeAll(temp);
	}

	public SipSignalManager getSignaler(){
		return signaler;
	}

	public TunnelManager getTunnelManager(){
		return tunnelManager;
	}

	public ApplicationManager getGui(){
		return appManager;
	}

	public void stop()
	{
		if (rme) routingCheck.stopRME(); //Radio Multiple Eterogenee
		if (rme) {
			try {
				Runtime.getRuntime().exec("sudo killall olsrd");
				ddsQosReceiver.stopDDSQosThread();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (availableIfs!=null) this.flushTables();
		if (appMonitor!=null) appMonitor.stop();
		if (tunnelManager!=null) tunnelManager.stop();
		if (networkMonitor!=null) networkMonitor.stop();
		if (signaler!=null) signaler.stop();

		//#ifndef ANDROID
		System.exit(0);
		//#endif
	}


	/**
	 * Adds a new ip interface.
	 * @param ifName the name of the new interface (e.g. eth0, ppp5, etc.).
	 * @param newIf the interface descriptor that contains the local ip address and the ip address of the remote gw for this interface.
	 */
	private void addInterface(String ifName, InterfaceInfo newIf) {
		printLog("Detected ifs: " + ifName + " (IP:" + newIf.ipAddress + " - gw:" + newIf.defaultGw+")", Log.LEVEL_HIGH);
		synchronized(availableIfs) {
			if(appManager!=null) appManager.addInterface(ifName);

			availableIfs.put(ifName, newIf);

			tunnelManager.addInterface(ifName, newIf);
			System.out.println("UPMTCLIENT addinterface " + newIf.id);


			if(rme) {
				String resultTsa;
				resultTsa = Module.upmtconf(new String[]{"-a", "tsa", "-i", ifName, "-l", "50000"});
				System.out.println(resultTsa);
				for (String ANAddress : associatedANList.keySet()) {
					long result = tunnelSetup(ifName, ANAddress, associatedANList.get(ANAddress));
					String ePVipa = getVipa(ANAddress);
					checkAllRMEPolicy(ePVipa, RME_EVENT_TUN_UP);
				}
			}
			else {
				for (String ANAddress : associatedANList.keySet()) {
					long result = tunnelSetup(ifName, ANAddress, associatedANList.get(ANAddress));

				}
				checkAllPolicy(EVENT_INTERFACE_UP);
			}
		}
	}

	/** Removes an ip interface.
	 *  @param ifName the interface to be removed. */
	private void removeInterface(String ifName) {
		printLog("Lost ifs: " + ifName, Log.LEVEL_HIGH);
		if(ifName.startsWith("tty")) {
			ifName = "ppp0";
		}
		synchronized(availableIfs) {
			if(appManager!=null) appManager.removeInterface(ifName);
			printLog("removed: " + ifName + " from appManager", Log.LEVEL_HIGH);
			InterfaceInfo oldIf = availableIfs.remove(ifName);

			if(rme) {
				for (String ANAddress : associatedANList.keySet()) {
					String ePVipa = getVipa(ANAddress);
					checkAllRMEPolicy(ePVipa, RME_EVENT_TUN_UP);
				}
			}
			else {
				checkAllPolicy(EVENT_INTERFACE_DOWN);
			}

			tunnelManager.removeInterface(ifName, oldIf, associatedANList.keySet());
		}
	}

	private void changeSignalingInterf(String newInterf) {
		signalingInterface = newInterf;
		if (newInterf!=null) {
			printLog("Chosen " + newInterf + " as default signaling interface!\n", Log.LEVEL_HIGH);
			signaler.changeDefaultInterface(newInterf, availableIfs.get(newInterf));
		} else {
			printLog("No inteface available as default signaling interface!\n", Log.LEVEL_HIGH);
			signaler.changeDefaultInterface(newInterf, null);
		}
	}

	/**
	 * evaluates all the policies following a given event, to check if it is needed to change the
	 * interface for an application 
	 */
	public void checkAllPolicy(int event) {
		if (signalingPolicy.isTriggeredBy(event)) { 
			String newInterf = signalingPolicy.getActiveInterf(availableIfs.filterOnSignalingOK(defaultAN), signalingInterface, event);

			if (!(      (newInterf==null&&signalingInterface==null) || (newInterf!=null && newInterf.equals(signalingInterface))    ) ) {
				changeSignalingInterf(newInterf);
			}
		}

		if (defaultClosedPolicy.isTriggeredBy(event)) {
			String newInterf = defaultClosedPolicy.getActiveInterf(availableIfs.filterOnCanUseTunnel(defaultAN), defaultClosedInterface, event);
			if (  !(  (newInterf==null&&defaultClosedInterface==null) || (newInterf!=null && newInterf.equals(defaultClosedInterface)) )         ) {
				defaultClosedInterface = newInterf;
				appMonitor.setDefault(tunnelManager.getTid(newInterf, defaultAN));
			}
		}

		//SS ADDED SYNC ON EVENT REGISTER
		synchronized (eventRegister.get(event)) {
			for (String appName : eventRegister.get(event)) { 
				checkSinglePolicy(appName, event);
			}
		}

		if (!coreEmulator) appManager.onPolicyCheck();
	}

	/**
	 * applica la politica associata ad una applicazione quando si e' verificato un certo evento
	 * applies the policy associated to an application when a given event has happened
	 * @param appName
	 * @param event the event that has happened
	 *           (EVENT_START / EVENT_INTERFACE_UP / EVENT_INTERFACE_DOWN / EVENT_AN_UP / EVENT_AN_DOWN )
	 */
	public void checkSinglePolicy(String appName, int event) {
		if (openedPolicy.containsKey(appName)) { //APP OPENED (i.e. it has open sockets)
			InterfPolicy policy = openedPolicy.get(appName);
			String currentInterf = openedInterface.get(appName);
			String newInterf = policy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), currentInterf, event);
			if ((newInterf==null&&currentInterf==null) || (newInterf!=null && newInterf.equals(currentInterf))) return;

			if (newInterf!=null) {
				openedInterface.put(appName, newInterf);
			} else {
				openedInterface.remove(appName);
			}
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));
			moveAppToInterf(appName, newInterf);
		} else { //APP CLOSED (i.e. with no open sockets)
			InterfPolicy policy = closedPolicy.get(appName);
			String currentInterf = closedInterface.get(appName);
			String newInterf = policy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), currentInterf, event);
			if ((newInterf==null&&currentInterf==null) || (newInterf!=null && newInterf.equals(currentInterf))) return;
			if (newInterf!=null) {
				closedInterface.put(appName, newInterf);
			} else {
				closedInterface.remove(appName);
			}
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));
		}
	}

	private void addFH(String FHAddress, int tsaPort) {
		addAN(FHAddress, tsaPort);
		appMonitor.setAppFlow(FHAddress, tunnelManager.getTid(defaultClosedInterface, FHAddress));
		//TODO: cosi' (currentDefaultAppIf) non ci sono politike verso i fixedHost.
		//Tokkerebbe modificare pesantemente il modulo (perke' all'interno dei pakketti diretti verso il FH bisogna distinguere l'app).
	}

	/** 
	 * tries to associate to an Anchor Node over a given interface,
	 * performing a blocking association Request to obtain the VIPA, then
	 * it adds the tunnels for each interface<BR>
	 * (endAddAN is called when the association Request is completed and it creates
	 * the tunnels for each interface)
	 * @param ANAddress
	 * @param sipPort
	 */
	public boolean addANonIf(String ANAddress, String interf, boolean blocking) {
		InterfaceInfo ifInfo;
		ifInfo = availableIfs.get(interf);
		if (ifInfo!=null){
			//#ifndef ANDROID
			updateMsg("Trying to associate to "+ ANAddress);
			//#endif
			return signaler.signalAssociationOnIf(blocking, ANAddress, interf, ifInfo);
			//			if(signalingInterface != null) {
			//				signaler.signalAssociation(ANAddress, tsaPort);
			//			}

		}
		else 
			return false;
	} 


	/** 
	 * OLD VERSION adds an Anchor Node, performing the association Request to obtain the VIPA, then
	 * it adds the tunnels for each interface<BR>
	 * (endAddAN is called when the association Request is completed and it creates
	 * the tunnels for each interface)
	 * @param ANAddress
	 * @param tsaPort
	 */
	private void addAN(String ANAddress, int tsaPort) {
		if(signalingInterface != null) {
			signaler.signalAssociation(ANAddress, tsaPort);
		}
	} 

	/** 
	 * it is called when the association Request towards an AN is completed<BR>
	 * it tries to create a tunnel for each interface which is also valid for out-of-tunnel signaling<BR>
	 * currently, it is blocking
	 * @param ANAddress
	 * @param tsaPort
	 */
	public void createAllTunnelsToAN(String ANAddress, int tsaPort) {
		//#ifndef ANDROID
		updateMsg("Trying to create tunnels toward "+ ANAddress);
		//#endif
		if (signaler.getVipaForAN(ANAddress) != null) {
			synchronized(availableIfs) {
				for (String ifName : availableIfs.filterOnSignalingOK(ANAddress).keySet()) {
					//creates a tunnel towards the AN for each interface
					//it is blocking, as the signaler.createTunnel is blocking
					long result = tunnelSetup(ifName, ANAddress, tsaPort);

					//#ifndef ANDROID
					if(!textMode) {
						((GUIApplicationManager)getGui()).refreshGui();
					}
					//#endif

				}
			}
		}
		if(!textMode) {
			((GUIApplicationManager)appManager).refreshGui();
		}

	}


	/** 
	 * OLD VERSION it is called when the association Request towards an AN is completed<BR>
	 * it tries to create a tunnel for each interface
	 * @param ANAddress
	 * @param tsaPort
	 */
	public void endAddAN(String ANAddress, int tsaPort) {
		updateMsg("Trying to create tunnels...");
		if (signaler.getVipaForAN(ANAddress) != null) {
			synchronized(availableIfs) {
				for (String ifName : availableIfs.keySet()) {
					//creates a tunnel towards the AN for each interface
					long result = tunnelSetup(ifName, ANAddress, tsaPort);
				}
			}
		}
	}





	//kiamato qnd viene aggiunta un interfaccia o un AN
	public synchronized long tunnelSetup(String ifName, String ANAddress, int tsaPort) {		

		//		System.err.println("-----------------------------------------------");
		//		System.err.println("TUNNEL SETUP FOR "+ANAddress+" on INTERFACE "+ifName+" with port: "+tsaPort);
		//		System.err.println("thread---> "+Thread.currentThread().getId());
		//		System.err.println("-----------------------------------------------");


		String vipaForThisAN = signaler.getVipaForAN(ANAddress);
		if(rme) {
			if(tunnelManager.getTid(ifName, ANAddress)!=UPMTClient.TID_DROP) {
				return 0;
			}
			if(tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+ANAddress)) {
				return 0;
			}
			else {
				tunnelManager.getTemporaryTunnelSetup().add(ifName+":"+ANAddress);
			}
		}
		int result = tunnelManager.addTunnel(vipaForThisAN, ifName, ANAddress, tsaPort);

		printLog("LocalTunnelID: " + result,Log.LEVEL_HIGH );
		if (result==0) {
			if(rme) {
				if(tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+ANAddress)) {
					tunnelManager.getTemporaryTunnelSetup().remove(ifName+":"+ANAddress);
				}
			}
			printLog("ATTENTION: LocalTunnelID should not be 0 ",Log.LEVEL_HIGH );
			return result;
		}

		//blocking call
		int result1 = signaler.createTunnel(ifName, ANAddress, result);

		if (result1==0) {
			tunnelManager.removeTunnel(ifName, ANAddress);
			if(rme) {
				if(tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+ANAddress)) {
					tunnelManager.getTemporaryTunnelSetup().remove(ifName+":"+ANAddress);
				}
			}
			return 0;
		}
		else {
			if(cfg.keepaliveKernel) {
				String[] par = new String[]{"-k", "on", "-n", ""+result};
				String moduleResult = Module.upmtconf(par);
				if(rme) {
					if(tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+ANAddress)) {
						tunnelManager.getTemporaryTunnelSetup().remove(ifName+":"+ANAddress);
					}
				}
			}
		}
		if(!textMode) {
			((GUIApplicationManager) appManager).refreshGui();

		}

		return ((long)result1)<<32 + (long)result;

	}

	//usata solo nella rimozione di AN xke' nella rimozione di interf si levano tutti in automatico.
	private void tunnelRemove(String ifName, String ANAddress) {
		//TODO:
	}

	private void moveAppToInterf(String appName, String ifName) {

		System.out.println("MOVE " + appName + " to " + ifName);

		synchronized(socketForApp) {
			Vector<Socket> sockVect = socketForApp.get(appName);
			if (sockVect==null) {
				printLog("[UPMTClient.moveAppToInterf]: socketForApp.get( \"" +appName + "\" ) returns null!!",Log.LEVEL_HIGH );
				return;
			} else {			
				for (Socket socket : socketForApp.get(appName)) {
					moveSocketToInterf(socket, ifName, appName);
				}
			}
		}
	}

	private void moveSocketToInterf(Socket socket, String ifName, String appName) {
		//Se ifName e' null vuol dire ke devo bloccare! grande TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		if(ifName==null) {
			printLog("[UPMT client.moveSocketToInterf]: iFName NULL!!!" ,Log.LEVEL_HIGH );
			return;
		}
		//FORSE (DA TESTARE!!!!) SI PUO' LASCIARE TUTTO COM'E' TANTO IL BLOCCO AVVIENE SOLO IN SEGUITO A RIMOZIONE E QUINDI 
		//NEL MODULO VIENE KIAMATO "DELETE TUN" KE LEVA LE REGOLE DELLA PAFT! INVECE PER IL SERVER TRASMETTERA' SU UN TUNNEL TAPPATO.
		//MI PUO' ANDARE BENE STO COMPORTAMENTO?



		tunnelManager.moveSocketToInterf(socket.proto, socket.srcPort, socket.dstIp, socket.dstPort, ifName, appName);

		//TODO removed explicit signaling
		//signaler.handover(socket, getDefaultAN(), ifName);

		//alla fine segnalo anke alla finestrella i cambiamenti di tutti i socket e dell'applicazione in generale
		//(rappresenta dove andranno gli eventuali socket aperti in futuro)
	}	





	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////EVENT HANDLER//////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//**********************networkMonitor****************************
	public void onNetworkInterfaceAdding(String ifName, InterfaceInfo newIf) {addInterface(ifName, newIf);}
	public void onNetworkInterfaceRemoval(String ifName) {removeInterface(ifName);}



	//***********************ApplicationMonitor***************************
	/** an application has started */
	public void appOpened(String appName) {
		if(rme) {
			if(rmeClosedPolicy.containsKey(appName)) {
				//the application was closed (i.e. no sockets) and its policy was already set in the rmeClosedPolicy 
				rmeOpenedPolicy.put(appName, rmeClosedPolicy.remove(appName));
				if(rmeClosedAppToTunnel.containsKey(appName)) {
					HashMap<String, Integer> vipaToTunnel = rmeClosedAppToTunnel.remove(appName);
					if(vipaToTunnel.size()!=0) {
						if(!rmeOpenedAppToTunnel.containsKey(appName)) {
							rmeOpenedAppToTunnel.put(appName, new HashMap<String, Integer>());
						}
						rmeOpenedAppToTunnel.put(appName, vipaToTunnel);
						for(String vipa: vipaToTunnel.keySet()) {
							int currentTid = vipaToTunnel.get(vipa);
							System.out.println("\nSending " + appName + " traffic on tunnel TID: " + currentTid + " for VIPA: "+vipa+"\n");
						}
					} else {
						//lo leva dalla lista se non ha una lista di vipa con tid già settato ma non è corretto da --->vedere
						rmeOpenedAppToTunnel.remove(appName);	
					}
				}
				else {
					rmeOpenedAppToTunnel.remove(appName);
				}
			} else {
				// the application was not included in the rmeclosedPolicy dropped
				printLog("Unknown "+appName+", application shall be dropped", Log.LEVEL_HIGH);
				appMonitor.setApp(appName, TID_DROP);
			}

		} else {
			if (closedPolicy.containsKey(appName)) {
				//the application was closed (i.e. no sockets) and its policy was already set in the closedPolicy 
				openedPolicy.put(appName, closedPolicy.remove(appName));

				//TODO new code
				//String currentIf = openedPolicy.get(appName).getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAN(appName)),null, EVENT_START); 
				//closedInterface.remove(appName);

				//TODO old code
				String currentIf = closedInterface.remove(appName);

				if (currentIf !=null){
					openedInterface.put(appName, currentIf);
					System.out.println("\nSending " + appName + " traffic on interface " + currentIf + "\n");
				} else {
					openedInterface.remove (appName);
				}
			} else { 
				// the application was not included in the closedPolicy

				//InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, tmpDefaultPolicy!=null? tmpDefaultPolicy:cfgDefaultPolicy);//FIXME
				InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, cfgDefaultPolicy);
				if (policy==null) {
					printLog("Policy null for app: "+appName, Log.LEVEL_HIGH);
					return;
				}
				openedPolicy.put(appName, policy);

				synchronized (availableIfs) {
					String activeIf = policy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)),null, EVENT_START); 
					if (activeIf!=null) {
						openedInterface.put(appName, activeIf);
						System.err.println("\nSending " + appName + " traffic on interface " + activeIf + "\n");
					} else {
						openedInterface.remove(appName);
					}
				}
			}
		}
		if (!coreEmulator) appManager.addApp(appName);
	}

	public void socketOpened(String appName, Socket socket) {
		boolean found = false;
		synchronized(socketForApp) {
			if(!socketForApp.containsKey(appName)) socketForApp.put(appName, new Vector<Socket>(Arrays.asList(new Socket[]{socket})));
			else {
				Iterator<Socket> iterSocket = socketForApp.get(appName).iterator();
				while(iterSocket.hasNext() && !found) {
					Socket socketCheck = iterSocket.next();
					//					if((socketCheck.getProto()).equals(signal.socket.getProto()) &&
					//							(socketCheck.getVipa()).equals(signal.socket.getVipa()) &&
					//							(socketCheck.getDstIp()).equals(signal.socket.getDstIp()) && 
					//							(socketCheck.getDstPort())==(signal.socket.getDstPort()) && 
					//							(socketCheck.getSrcPort())==(signal.socket.getSrcPort())) {
					//						found = true;
					//					}
					if(socketCheck.id().equals(socket.id())) {
						found = true;
						break;
					}
				}
				if(!found) {
					socketForApp.get(appName).add(socket);
				}

			}
			if(!found) {
				appForSocket.put(socket.id(), appName);
				if (!coreEmulator) {
					appManager.addSocket(appName, socket);
				}
			}
		}
	}

	//Aggiunto metodo per ricavare appName dalla socket.id() (bonus)
	public String getAppnameFromSocket(Socket socket){
		String appName = null;
		synchronized(appForSocket){
			appName = appForSocket.get(socket.id());
		}
		return appName;
	}

	// controllo se il vector<Socket> relativo a un appname è vuoto (bonus)
	public boolean isActiveApp(String appName){
		synchronized(socketForApp){
			if(socketForApp.containsKey(appName))
				if(!socketForApp.get(appName).isEmpty())
					return true;
		}
		return false;
	}

	//TODO: fatto al volo ma va verificato xk� x adesso nel modulo ancora nn c'� la garbage collection
	//modificata da valerio ora funziona
	public void socketClosed(Socket socket)
	{
		
		System.err.println("arrivata notifica di rimozione socket -----> "+socket.id());
		synchronized(socketForApp) {
			String appName = appForSocket.remove(socket.id());
			Vector<Socket> sockVect = socketForApp.get(appName);
			if (sockVect==null) {
				return;
			} else {
				Iterator<Socket> socketIterator = socketForApp.get(appName).iterator();
				while(socketIterator.hasNext()) {
					Socket foundSocket = socketIterator.next();
					if(foundSocket.id().equals(socket.id())) {
						socketIterator.remove();
						if (!coreEmulator) appManager.rmvSocket(socket);
						break;
					}
				}
			}
		}
//		
//		
//		
//		synchronized(socketForApp)
//		{
//			socketForApp.get(appForSocket.remove(socket.id())).remove(socket);
//			if (!coreEmulator) appManager.rmvSocket(socket);
//		}
	}

	//TODO!!! Da fare xke x adesso nel modulo ancora nn c'e la garbage collection.
	//rifatta da valerio ora funziona
	//Quando un app si kiude si elimina la politica dalla lista delle app aperte e si crea 
	//una nuova politica nella lista delle kiuse a partire dalla stringa del config (come all'avvio)
	public void appClosed(String appName) {

		if(rme){

			if(rmeOpenedAppToTunnel.containsKey(appName)) {
				//rimuovo l applicazione dalla lista delle app aperte
				String policy = rmeOpenedPolicy.remove(appName);
				//creo una nuova politica a partire dalla stringa del config e l aggiungo nella lista delle applicazioni chiuse
				rmeClosedPolicy.put(appName, policy);
				//copio tute i vipa e i tunnel che utilizzavano quella applicazione in rmeClosedAppToTunnel
				HashMap<String, Integer> vipaToTunnel = rmeOpenedAppToTunnel.remove(appName);
				rmeClosedAppToTunnel.put(appName, vipaToTunnel);
				//infine rimuovo l applicazione dall albero posto nella GUIApplicationManager(scheda applications)
				if (!coreEmulator) appManager.removeApp(appName);
			}

			// only one peer
			//			if(!rmeOpenedAppToTunnel.containsKey(appName)) {
			//				//rimuovo l applicazione dalla lista delle app aperte
			//				String policy = rmeOpenedPolicy.remove(appName);
			//				//creo una nuova politica a partire dalla stringa del config e l aggiungo nella lista delle applicazioni chiuse
			//				rmeClosedPolicy.put(appName, policy);
			//				//infine rimuovo l applicazione dall albero posto nella GUIApplicationManager(scheda applications)
			//				appManager.removeApp(appName);
			//			}
		}
		else {
			// (bonus)
			if(!openedInterface.contains(appName)) {
				//rimuovo l applicazione dalla lista delle app aperte
				openedPolicy.remove(appName);
				//creo una nuova politica a partire dalla stringa del config e l aggiungo nella lista delle applicazioni chiuse
				InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, cfgDefaultPolicy);
				closedPolicy.put(appName, policy);
				//infine rimuovo l applicazione dall albero posto nella GUIApplicationManager(scheda applications)
				if (!coreEmulator) appManager.removeApp(appName);
			}
		}
	}

	//***********************ApplicationManager***************************

	public Vector<String> getInterfacesList() {
		return new Vector<String>(availableIfs.keySet());
	}

	public Vector<String> getNoUpmtApp() {
		return noUpmtApp;
	}

	public String getVipaFix() {
		return Module.getVipaFix();
	}

	public int getNumOfConnectedAN() {
		if (associatedANList != null) {
			return associatedANList.size();
		} else {
			return 0;
		}
	}

	/**
	 * we assume that the application is in the hash table of applications that has open sockets
	 * (as openedPolicy.get(appName) cannot return null)
	 */
	public void setCurrentPolicy(String appName, String policyVector) {
		printLog("change policy for " + appName + " to " + policyVector, Log.LEVEL_HIGH);

		if (cfgPolicy.get(appName).contains("-AN=")){
			//EXTRACT AN POLICY FROM STRING

			ANPolicy currentANPolicy = anchorNodePolicy.get(appName);

			policyVector = policyVector.replace("-AN=","");

			String PerANPolicy =policyVector.substring(policyVector.indexOf("\"")+1,policyVector.lastIndexOf("\""));
			policyVector=policyVector.replace ("\"" + PerANPolicy + "\"", "" );
			System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx new policy"+PerANPolicy);

			ANPolicy anPolicy = ANPolicyFactory.getPolicy(appName, PerANPolicy);
			System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx new policy"+anPolicy);
			if(anPolicy!=null && anPolicy!=currentANPolicy) {
				anchorNodePolicy.put(appName, anPolicy);
				String newAnchor= anPolicy.getActiveAN(associatedANList, null, EVENT_START);
				printLog(CRLF + "APPLICATION: " +appName+ "  - Selected New Anchor= " + newAnchor + CRLF,Log.LEVEL_HIGH);
				if (newAnchor!=null)
					anchorNodeTable.put(appName, newAnchor);
			}
		}


		synchronized (eventRegister.keySet()) {
			for (int event : eventRegister.keySet()) {

				//System.out.println(openedPolicy + "-" + event);

				//pcerqua 
				if(openedPolicy.get(appName) == null)
					this.appOpened(appName);

				//System.out.println(openedPolicy + "-" + event);

				if (openedPolicy.get(appName).isTriggeredBy(event)) {
					eventRegister.get(event).remove(appName);
				}
			}
		}


		// creates a new policy based on the string input by the user (policyVector)
		InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, policyVector);


		//pcerqua (start)
		if(policy == null){
			policy = new BlockPolicy();
			System.out.println("NULL POLICY DETECTED -> setting BLOCKPOLICY");
		}
		System.out.println("Applied policy description: " + policy.getDesc());
		//pcerqua (end)


		// and stores it in the openedPolicy hash table
		openedPolicy.put(appName, policy);

		String currentInterf = openedInterface.get(appName);
		String newInterf = policy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), currentInterf, EVENT_START);
		printLog("Old Interf: " + currentInterf + "; New Interf: " + newInterf, Log.LEVEL_HIGH);

		if (newInterf==null&&currentInterf==null)
			return;

		if (newInterf!=null) {
			openedInterface.put(appName, newInterf);

		} else {
			openedInterface.remove(appName);
		}
		//sends the local message that instructs the appmon to assign a TID to the
		//first packet of sockets that will be originated by this application
		appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));
		moveAppToInterf(appName, newInterf);

		if (!coreEmulator) appManager.onPolicyCheck();
	}

	public String getSipID() {
		return signaler.getSipID();
	}

	public String getCfgSipID() {
		return cfg.sipID;
	}

	public String getDefaultAN() {		
		return defaultAN;
	}

	public void updateDefaultAN(){
		if(rme) {
			if(signaler.getDefaultAN() != null) {
				defaultAN = signaler.getDefaultAN();
				printLog("New default AnchorNode: " + defaultAN, Log.LEVEL_HIGH);
			}
			else{
				defaultAN = null;
				printLog("New default AnchorNode: " + defaultAN, Log.LEVEL_HIGH);
			}
		} 
		else {
			if(signaler.getDefaultAN() != null && cfgDefaultANPolicy != null ){
				if(cfgDefaultANPolicy.contains(signaler.getDefaultAN())){
					defaultAN = signaler.getDefaultAN();
					printLog("New default AnchorNode: " + defaultAN, Log.LEVEL_HIGH);
				}
			}
			else{
				defaultAN = null;
				printLog("New default AnchorNode: " + defaultAN, Log.LEVEL_HIGH);
			}
		}
	}

	public String getCfgDefaultAN() {
		return cfg.ANList.get(0);
	}

	public String getCurrentInterf(String appName) {
		return openedInterface.get(appName);
	}

	public String getCurrentInterf(Socket socket) {
		return openedInterface.get(appForSocket.get(socket.id()));
	}

	public String getCurrentPolicy(String appName) {
		if(openedPolicy!=null && openedPolicy.contains(appName)) {
			if(openedPolicy.get(appName).getDesc()!=null) {
				return openedPolicy.get(appName).getDesc();
			}
			return null;
		}
		else {
			return null;
		}
	}

	public String getStoredPolicy(String appName) {
		return cfgPolicy.get(appName);
	}

	public Hashtable<String, String> getAllStoredPolicy() {
		return cfgPolicy;
	}

	public Vector<String> getNoUpmtInterf() {
		return new Vector<String>(Arrays.asList(cfg.interfToSkip));
	}

	public String getDefaultPolicy() {
		return cfgDefaultPolicy;
	}

	public void setCfgSipID(String sipID) {
		cfg.sipID = sipID; cfg.writeTag(Default.sipID_Tag, sipID);
	}

	public void setDefaultAN(String addr) {
		cfg.ANList = new Vector<String>(Arrays.asList(new String[]{addr}));
		if(!rme) {
			cfg.writeTag(Default.ANList_TAG, addr);
		}
	}

	//TODO: in add e edit ci vuole un controllo ke la policy sia fatta bene (nel senso nome e parametri corretti!)
	public void cfgPolicyAdding(String appName, String policy) {

		System.out.println("cfgPolicyAdding: "+appName+" -> "+policy+" (TODO)");
		cfgPolicy.put(appName, policy);
		cfg.writeTag(Default.applicationPolicy_TAG, appName+" "+policy);

		if (!openedPolicy.containsKey(appName)) { //APP CLOSED
			InterfPolicy interfPolicy = InterfPolicyFactory.getPolicy(appName, policy);
			closedPolicy.put(appName, interfPolicy);
			String newInterf = interfPolicy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), null, EVENT_START);
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));
			if (newInterf!=null) {
				closedInterface.put(appName, newInterf);
			} else {
				closedInterface.remove (appName);
			}
		}
		if (!coreEmulator) appManager.onPolicyCheck();
	}


	/**
	 * editing the policy for an application DIRECT IN THE CFG FILE 
	 */
	public void cfgPolicyEdit(String appName, String policy) {
		System.out.println("cfgPolicyEdit: "+appName+" -> "+policy+" (TODO)");

		cfgPolicy.put(appName, policy);
		cfg.writeTag(Default.applicationPolicy_TAG, appName+" "+policy);


		//SS: not still clear why we are doing this
		if (closedPolicy.containsKey(appName)) {

			InterfPolicy interfPolicy = null;
			synchronized (eventRegister.keySet()) {
				for (int event : eventRegister.keySet()) { // for each possible event
					if (closedPolicy.get(appName).isTriggeredBy(event)) { 
						//if the policy of the application is triggered by the event
						//we remove the application from the set of applications related to that event
						eventRegister.get(event).remove(appName);
					}
				}
				//MARCO ADDED AN CHECK

				if (cfgPolicy.get(appName).contains("-AN=")){
					//EXTRACT AN POLICY FROM STRING					
					ANPolicy currentANPolicy = anchorNodePolicy.get(appName);

					policy = policy.replace("-AN=","");

					String PerANPolicy =policy.substring(policy.indexOf("\"")+1,policy.lastIndexOf("\""));
					policy=policy.replace ("\"" + PerANPolicy + "\"", "" );

					ANPolicy anPolicy = ANPolicyFactory.getPolicy(appName, PerANPolicy);
					if(anPolicy!=null && anPolicy!=currentANPolicy) {
						anchorNodePolicy.put(appName, anPolicy);
						String newAnchor= anPolicy.getActiveAN(associatedANList, null, EVENT_START);
						if (newAnchor!=null)
							anchorNodeTable.put(appName, newAnchor);
					}
				}

				interfPolicy = InterfPolicyFactory.getPolicy(appName, policy);
			}

			closedPolicy.put(appName, interfPolicy);
			String currentInterf = closedInterface.get(appName);

			String newInterf = interfPolicy.getActiveInterf(availableIfs.filterOnCanUseTunnel(getSelectedAppAN(appName)), currentInterf, EVENT_START);

			if (newInterf==null&&currentInterf==null) 
				return;

			if (newInterf!=null) {
				closedInterface.put(appName, newInterf);
			} else {
				closedInterface.remove(appName);
			}

			if (newInterf==null){
				appMonitor.setApp(appName, TID_DROP);	
			}
			else
				appMonitor.setApp(appName,tunnelManager.getTid(newInterf, getSelectedAppAN(appName)));

		}
		if (!coreEmulator) appManager.onPolicyCheck();
	}


	public void cfgPolicyRemoval(String appName)
	{
		System.out.println("cfgPolicyRemoval: "+appName+" (TODO)");

		cfgPolicy.remove(appName);
		cfg.deleteTag(Default.applicationPolicy_TAG + "=" + appName);

		if (closedPolicy.containsKey(appName)) { //APP CLOSED WITH POLICY
			//SS ADDED SYNC ON EVENT REGISTER
			synchronized (eventRegister.keySet()) {
				for (int event : eventRegister.keySet()) {
					if (closedPolicy.get(appName).isTriggeredBy(event)) {
						eventRegister.get(event).remove(appName);
					}
				}
			}
			closedPolicy.remove(appName);
			appMonitor.rmApp(appName);
			closedInterface.remove(appName);
		}
		if (!coreEmulator) appManager.onPolicyCheck();
	}

	public void defPolicyEdit(String defPolicy)
	{
		System.out.println("defPolicyEdit: "+defPolicy+" (TODO)");
		cfgDefaultPolicy = defPolicy;
		cfg.writeTag(Default.default_app_policy_TAG, defPolicy);

		defaultClosedPolicy = InterfPolicyFactory.getPolicy(null, cfgDefaultPolicy);

		defaultClosedInterface = defaultClosedPolicy.getActiveInterf(availableIfs.filterOnCanUseTunnel(defaultAN), null, EVENT_START);

		appMonitor.setDefault(defaultClosedInterface==null?TID_DROP:tunnelManager.getTid(defaultClosedInterface, defaultAN));
	}

	public void cfgNoAppRemoval(String appName)
	{
		System.out.println("cfgNoAppRemoval: "+appName+" (TODO)");
		noUpmtApp.remove(appName); //TODO: rimuovere dall'appMon
		cfg.writeTag(Default.no_upmt_app_TAG, Arrays.toString(noUpmtApp.toArray()).replace("[", "").replace("]", "").replace(",", ""));
	}

	public void cfgNoAppAdding(String appName)
	{
		System.out.println("cfgNoAppAdding: "+appName+" (TODO)");
		noUpmtApp.add(appName); //TODO: passare all'appMon
		cfg.writeTag(Default.no_upmt_app_TAG, Arrays.toString(noUpmtApp.toArray()).replace("[", "").replace("]", "").replace(",", ""));
	}

	public void cfgNoInterfRemoval(String ifName)
	{
		System.out.println("cfgNoInterfRemoval: "+ifName+" (TODO)");
		Vector<String> newList = new Vector<String>(Arrays.asList(cfg.interfToSkip));
		newList.remove(ifName);
		cfg.interfToSkip = newList.toArray(new String[]{});
		cfg.writeTag(Default.no_upmt_interf_TAG, Arrays.toString(cfg.interfToSkip).replace("[", "").replace("]", "").replace(",", "") + ifName);
	}

	public void cfgNoInterfAdding(String ifName)
	{
		System.out.println("cfgNoInterfAdding: "+ifName+" (TODO)");
		Vector<String> newList = new Vector<String>(Arrays.asList(cfg.interfToSkip));
		newList.add(ifName);
		cfg.interfToSkip = newList.toArray(new String[]{});
		cfg.writeTag(Default.no_upmt_interf_TAG, Arrays.toString(cfg.interfToSkip).replace("[", "").replace("]", "").replace(",", "").replace(ifName+" ", ""));
	}






	// ****************************** Logs *****************************
	private void printLog(String text, int loglevel)
	{printGenericLog(this, text, loglevel);}

	public static void printGenericLog(Object element, String text, int loglevel)
	{
		if(logger != null)
			logger.println("["+element.getClass().getSimpleName() + "]: " + text, loglevel);
		if (loglevel!=Log.LEVEL_LOW) 
			System.out.println("["+element.getClass().getSimpleName() + "]: " + text);
	}

	/**
	 * in a static context it is not possible to call printGenericLog so I've added this
	 * method
	 * @param text
	 * @param loglevel
	 */
	public static void printStaticLog( String text, int loglevel)
	{
		logger.println(text, loglevel);
		if (loglevel!=Log.LEVEL_LOW) System.out.println(text);
	}

	public static void startRMEClient(String[] args, HashMap<String, SipProvider> RMESignalers, ArrayList<RMEServer> RMEServers, boolean isRme) {
		System.out.println("RMEClient Up and Running");

		String file = null;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) printUsageAndQuit();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];

		rme = isRme;
		setServers(RMEServers);
		setSignalers(RMESignalers);
		UPMTClient.start(file);
	}

	//#ifndef ANDROID
	//*********************** Main method ***************************
	public static void main(String[] args)
	{
		String file = null;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) printUsageAndQuit();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];

		UPMTClient.start(file);
	}
	//#endif

	private static void printUsageAndQuit()
	{
		System.err.println("usage:\n\tjava MobileManager [options]\n\toptions:" +
				"\n\t-f <config_file> specifies a configuration file\n\t-gui start with gui");
		System.exit(-1);
	}
	private static void quitGui()
	{
		System.out.println("Parametri non validi");
		System.exit(0);
	}

	public static String getStatusMsg() {
		return statusMsg;
	}

	public static void addAssociatedAN(String aNAddress, Integer tsaPort) {
		associatedANList.put(aNAddress, tsaPort);
	}

	public void removeAssociatedAN(String ANAddress) {
		associatedANList.remove(ANAddress);
	}

	public boolean removeAssociatedANAnTryReconnect(String ANAddress) {

		if(!rme) {
			associatedANList.remove(ANAddress);
		}

		HashSet<String> availableIfsCopy = null;

		//		synchronized(availableIfs) {
		availableIfsCopy = new HashSet<String>(availableIfs.keySet());
		if(rme) {
			if(this.rmeTunnelIpInterfaceList.containsKey(ANAddress) && this.olsrDetectedEndPoint.contains(ANAddress)) {
				String interf = this.rmeTunnelIpInterfaceList.get(ANAddress);
				System.out.println("Trying to connect to " + ANAddress + " from RMEList");
				updateMsg("Trying to create tunnels toward "+ ANAddress);
				if (SipSignalManager.getVipaForAN(ANAddress) != null) {
					for (String ifName : availableIfs.keySet()) {
						if(this.tunnelManager.getTid(ifName,  ANAddress)==TID_DROP && this.olsrDetectedEndPoint.contains(ANAddress)) {
							if(crossTunnel && routingCheck.isCrossTunnelAvailable(ANAddress, ifName)) {
								if(!tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+ANAddress)) {
//									System.err.println("--------------------------------------");
//									System.err.println("chiamata da tryreconnection11111111");
//									System.err.println("--------------------------------------");
									long tunSet = tunnelSetup(ifName, ANAddress, associatedANList.get(ANAddress));
									int localTID = tunnelManager.getTid(ifName, ANAddress);
									String VIPA = this.getVipa(ANAddress);
									if(localTID!=TID_DROP) {
										this.addRMETunnelToTunnelList(VIPA, ifName, ANAddress, localTID);
										checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
									}
									if(!textMode) {
										((GUIApplicationManager)getGui()).refreshGui();
									}
								}

							}
							else {
								if(rmeDirectTunnel.contains(ANAddress+":"+ifName)){
//									System.err.println("--------------------------------------");
//									System.err.println("chiamata da tryreconnection2222222222");
//									System.err.println("--------------------------------------");
									long tunSet = tunnelSetup(ifName, ANAddress, associatedANList.get(ANAddress));
									int localTID = tunnelManager.getTid(ifName, ANAddress);
									String VIPA = this.getVipa(ANAddress);
									if(localTID!=TID_DROP) {
										this.addRMETunnelToTunnelList(VIPA, ifName, ANAddress, localTID);
										checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
									}
									if(!textMode) {
										((GUIApplicationManager)getGui()).refreshGui();
									}
								}
							}
						}

					}
				}
				else {
					for (String ifName : availableIfs.keySet()) {
						if(!blockerAssociation) {
							if(!signaler.getCurrentAssociations().contains(ANAddress+":"+ifName)) {
								rmeAnAssociation(ANAddress, interf);
								if (SipSignalManager.getVipaForAN(ANAddress) != null) {
									break;
								}
							}
						}
						else {
							rmeAnAssociation(ANAddress, interf);
						}
					}

				}
				return true;
			}
		}
		else {
			for (String interf : availableIfsCopy) {
				if(associatedANList.size() < maxANNumber){
					System.out.println("Trying to connect to " + ANAddress + " from ANList");
					if(addANonIf(ANAddress, interf, true)){
						//#ifndef ANDROID
						updateMsg("Trying to create tunnels toward "+ ANAddress);
						//#endif
						createAllTunnelsToAN(ANAddress, associatedANList.get(ANAddress));
						return true;
					}					
				}
				else
					return true;
			}
		}
		//		}
		return false;

	}



	public Hashtable<String, TunnelInfo> getANStatuses(){
		return signaler.getRemoteTidStatusTable();
	}

	public Hashtable<String, Integer> getAssociatedANList(){
		return associatedANList;
	}

	public int getMaxANNumber(){
		return maxANNumber;
	}

	public int getANSipPort(String AN){
		return ANtoSipPort.get(AN);
	}

	public void setANSipPort(String an, Integer port) {
		if(!ANtoSipPort.containsKey(an))
			ANtoSipPort.put(an, port);
	}

	public static boolean getRME() {
		return rme;
	}

	public static ArrayList<String> getRMEInterfacesList() {
		return rmeInterfacesList;
	}

	public void flushTables() {
		for (String ifName : availableIfs.keySet()) {
			Shell.executeCommand(new String[]{"sudo", "ip", "route", "flush", "table", ifName+"_table"});
		}
	}

	public static HashMap<String, SipProvider> getSignalers() {
		return signalers;
	}

	public static ArrayList<RMEServer> getServers() {
		return servers;
	}

	public static void setSignalers(HashMap<String, SipProvider> signalers) {
		UPMTClient.signalers = signalers;
	}

	public static void setServers(ArrayList<RMEServer> servers) {
		UPMTClient.servers = servers;
	}

	//#ifdef ANDROID
	//		public static Context getContext()
	//		{
	//			return context;
	//		}
	//#endif

	public void rmeAnAssociation(final String anAddress, final String ifNameDest) {
		if(blockerAssociation) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					if(!UPMTClient.associatedANList.contains(anAddress) && SipSignalManager.getVipaForAN(anAddress)==null && olsrDetectedEndPoint.contains(anAddress)){
						//						synchronized(availableIfs) {
						//							synchronized (signaler.getCurrentAssociations()) {
						if(!signaler.getCurrentAssociations().contains(anAddress+":"+ifNameDest)) {
							signaler.getCurrentAssociations().add(anAddress+":"+ifNameDest);
							if(!addANonIf(anAddress, ifNameDest, blockerAssociation)) {
								rmeAnAssociation(anAddress, ifNameDest);
							}
						}
						//							}
						//						}
					}
				}
			}, "RME Association Thread").start();
		}
		else {
			if(!UPMTClient.associatedANList.contains(anAddress) && SipSignalManager.getVipaForAN(anAddress)==null && olsrDetectedEndPoint.contains(anAddress)){
				//				synchronized(availableIfs) {
				if(!addANonIf(anAddress, ifNameDest, blockerAssociation)) {
					if(!signaler.getCurrentAssociations().contains(anAddress+":"+ifNameDest)) {
						rmeAnAssociation(anAddress, ifNameDest);
					}
				}
				//				}
			}
		}
	}

	public void setRoutingPeerModeFromAssociation(String destination, String ifname) {
		if(this.routingCheck.getAddressIfnameTogateway().containsKey(destination+":"+ifname)) {
			String gateway = this.routingCheck.getAddressIfnameTogateway().get(destination+":"+ifname);
			this.routingCheck.setPeerMode(destination, ifname, gateway);
		}
	}

	public void createRmeSingleTunnel(final String anAddress, final String ifNameDest) {

		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				updateMsg("Trying to create tunnels toward "+ anAddress);
				if (SipSignalManager.getVipaForAN(anAddress) != null && olsrDetectedEndPoint.contains(anAddress)) {
					if(tunnelManager.getTid(ifNameDest, anAddress)==TID_DROP && routingCheck.isCrossTunnelAvailable(anAddress, ifNameDest)) {
						if(!tunnelManager.getTemporaryTunnelSetup().contains(ifNameDest+":"+anAddress)) {
//							System.err.println("--------------------------------------");
//							System.err.println("chiamata da creatermesingletunnel");
//							System.err.println("--------------------------------------");
							long tunSet = tunnelSetup(ifNameDest, anAddress, associatedANList.get(anAddress));
							int localTID = tunnelManager.getTid(ifNameDest, anAddress);
							String VIPA = getVipa(anAddress);

							if(localTID!=TID_DROP) {
								addRMETunnelToTunnelList(VIPA, ifNameDest, anAddress, localTID);
								checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
							}		
						}
					}
				}
				if(!textMode) {
					((GUIApplicationManager)appManager).refreshGui();
				}

			}
		}).start();
		signaler.startKeepALiveThread();
	}


	public void createRmeTunnels(final String anAddress, final String ifNameDest) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				updateMsg("Trying to create tunnels toward "+ anAddress);
				if (SipSignalManager.getVipaForAN(anAddress) != null && olsrDetectedEndPoint.contains(anAddress)) {
					//					synchronized(availableIfs) {
					for (String ifName : availableIfs.filterOnSignalingOK(anAddress).keySet()) {
						if(ifName.equals(ifNameDest)) {
							if(tunnelManager.getTid(ifNameDest, anAddress)==TID_DROP) {
								if(crossTunnel && routingCheck.isCrossTunnelAvailable(anAddress, ifName)) {
									if(!tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+anAddress)) {
										rmeTunnelIpInterfaceList.put(anAddress, ifName);
//										System.err.println("--------------------------------------");
//										System.err.println("chiamata da creatermetunnels11111");
//										System.err.println("--------------------------------------");
										long tunSet = tunnelSetup(ifName, anAddress, associatedANList.get(anAddress));
										int localTID = tunnelManager.getTid(ifName, anAddress);
										String VIPA = getVipa(anAddress);
										if(localTID!=TID_DROP) {
											addRMETunnelToTunnelList(VIPA, ifName, anAddress, localTID);
											checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
										}
									}
								}
								else {
									if(rmeDirectTunnel.contains(anAddress+":"+ifName)) {
										if(!tunnelManager.getTemporaryTunnelSetup().contains(ifName+":"+anAddress)) {
											rmeTunnelIpInterfaceList.put(anAddress, ifName);
//											System.err.println("--------------------------------------");
//											System.err.println("chiamata da creatermetunnels222222");
//											System.err.println("--------------------------------------");
											long tunSet = tunnelSetup(ifName, anAddress, associatedANList.get(anAddress));
											int localTID = tunnelManager.getTid(ifName, anAddress);
											String VIPA = getVipa(anAddress);
											if(localTID!=TID_DROP) {
												addRMETunnelToTunnelList(VIPA, ifName, anAddress, localTID);
												checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
											}
										}
									}
								}
							}
						}
					}
					//					}
				}
				if(!textMode) {
					((GUIApplicationManager)appManager).refreshGui();
				}

			}
		}).start();
		signaler.startKeepALiveThread();

	}


	//old version
	//	public void rmeAnAssociation(String anAddress, String ifNameDest) {
	////		while(!associatedANList.containsKey(anAddress)){
	//			synchronized(availableIfs) {
	//				if(addANonIf(anAddress, ifNameDest, true)) {
	//					updateMsg("Trying to create tunnels toward "+ anAddress);
	//					if (SipSignalManager.getVipaForAN(anAddress) != null) {
	//						synchronized(availableIfs) {
	//							for (String ifName : availableIfs.filterOnSignalingOK(anAddress).keySet()) {
	//								if(ifName.equals(ifNameDest)) {
	//									rmedirectunnel.put(anAddress, ifName);
	//									long tunSet = tunnelSetup(ifName, anAddress, associatedANList.get(anAddress));
	//									int localTID = tunnelManager.getTid(ifName, anAddress);
	//									String VIPA = this.getVipa(anAddress);
	//									if(firstTunnel && tunSet!=0 && localTID!=TID_DROP) {
	////										appMonitor.setDefault(localTID);
	////										setDefaultAN(anAddress);
	//										System.err.println("localtid: "+localTID);
	//										firstTunnel = false;
	//									}
	//									if(localTID!=TID_DROP) {
	//										this.addRMETunnelToTunnelList(VIPA, ifName, anAddress, localTID);
	//										checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UP);
	//									}
	//								}
	//							}
	//						}
	//					}
	//					((GUIApplicationManager)appManager).refreshGui();
	//					signaler.startKeepALiveThread();
	//					
	//				}
	//				else {
	//					rmeAnAssociation(anAddress, ifNameDest);
	//				}
	//			}
	////		}
	//	}

	public void rmeAnAssociation(String anAddress) {
		//		while(!associatedANList.containsKey(anAddress)){
		//			synchronized(availableIfs) {
		for (String interf : availableIfs.keySet()) {
			if(addANonIf(anAddress, interf, true)) {
				createAllTunnelsToAN(anAddress, associatedANList.get(anAddress));
				signaler.startKeepALiveThread();
				break;
			}
		}
		//			}
		//		}
	}

	public String getVipa(String anAddress) {
		if(UPMTClient.ipToVipa.containsKey(anAddress)) {
			return UPMTClient.ipToVipa.get(anAddress);
		}
		return null;
	}

	// old version using netconf.json
	//	public String getVipa(String anAddress) {
	//		
	//		for(String VIPA: this.routingCheck.getParse().getJmap().keySet()) {
	//			for(int i=0; i<this.routingCheck.getParse().getJmap().get(VIPA).size(); i++) {
	//				if(this.routingCheck.getParse().getJmap().get(VIPA).get(i).getIp().trim().equals(anAddress.trim())) {
	//					return VIPA;
	//				}
	//			}
	//		}
	//		return null;
	//	}

	public Hashtable<String, String> getRmeTunnelIpInterfaceList() {
		return rmeTunnelIpInterfaceList;
	}

	public ArrayList<String> getRmeDirectTunnels() {
		return rmeDirectTunnel;
	}

	public RoutingCheck getRoutingCheck() {
		return this.routingCheck;
	}

	public ApplicationManager getApplicationManager() {
		return this.appManager;
	}

	public Hashtable<Integer, SipProvider> getTunnelProviders() {
		return tunnelProviders;
	}

	public void setTunnelProviders(Hashtable<Integer, SipProvider> tunnelProviders) {
		this.tunnelProviders = tunnelProviders;
	}

	/**
	 * for all applications listed in cfg files, it sets the policy into hashtable, register the application
	 * to events (in the InterfPolicyFactory.getPolicy method) and adds the application to closedPolicy hash map
	 * then it tells the appmonitor which tunnel to use for new connections
	 */
	private void putRMEPoliciesToHashtable() {

		// salviamo le policy per applicazione trovate nel file cfg
		for(String app: cfg.rmeApplicationPolicy.keySet()) {
			rmeCfgPolicy.put(app, cfg.rmeApplicationPolicy.get(app));
			rmeClosedPolicy.put(app, cfg.rmeApplicationPolicy.get(app).get(0));
			for(int event: rmeEventRegister.keySet()) {
				rmeEventRegister.get(event).add(app);
			}
		}
	}

	/**
	 * evaluates all the policies following a given event, to check if it is needed to change the
	 * tunnel for an application
	 */
	public void checkAllRMEPolicy(String VIPA, int event) {
		if (signalingPolicy.isTriggeredBy(event)) { 
			String newInterf = signalingPolicy.getActiveInterf(availableIfs.filterOnSignalingOK(defaultAN), signalingInterface, event);

			if (!( (newInterf==null&&signalingInterface==null) || (newInterf!=null && newInterf.equals(signalingInterface))    ) ) {
				changeSignalingInterf(newInterf);
			}
		}


		for (String appName : rmeEventRegister.get(event)) {
			checkSingleRMEPolicy(VIPA, appName, event);
		}

		//		da cambiare anche nell interfaccia grafica----> mi raccomando quello vede un tante policy che devono scomparire per rme
		//		appManager.onPolicyCheck();
	}

	/**
	 * Applies the policy associated to an application when a given event has happened
	 * @param appName
	 * @param event the event that has happened
	 *           (RME_EVENT_START / RME_EVENT_INTERFACE_UP / RME_EVENT_INTERFACE_DOWN / RME_EVENT_TUN_UP / RME_EVENT_TUN_DOWN )
	 */
	public void checkSingleRMEPolicy(String VIPA, String appName, int event) {

		if (rmeOpenedPolicy.containsKey(appName)) { //APP OPENED (i.e. it has open sockets)
			String policy = rmeOpenedPolicy.get(appName);
			int newTid = TID_DROP;
			if((policy.trim()).equalsIgnoreCase("bestTunnel")) { // dovremmo scegliere una politica per il momento bestTunnel
				newTid = getBestTunnelForVipa(VIPA);
			}
			else {
				newTid = getBestTunnelForVipa(VIPA);
			}
			int currentTid;
			if(!rmeOpenedAppToTunnel.containsKey(appName)) {
				rmeOpenedAppToTunnel.put(appName, new HashMap<String, Integer>());
				rmeOpenedAppToTunnel.get(appName).put(VIPA, newTid);
				currentTid = newTid;
				appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
			}
			else {
				if(rmeOpenedAppToTunnel.get(appName).containsKey(VIPA)) {
					currentTid = rmeOpenedAppToTunnel.get(appName).get(VIPA);
				}
				else {
					rmeOpenedAppToTunnel.get(appName).put(VIPA, newTid);
					currentTid = newTid;
					appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
				}
			}
			if ((newTid==TID_DROP && currentTid==TID_DROP) || (newTid!=TID_DROP && newTid==currentTid)) return;
			if (newTid!=TID_DROP) {
				System.err.println("new tid diverso da ti to drop");
				rmeOpenedAppToTunnel.get(appName).put(VIPA, newTid);
			} else {
				System.out.println("[UPMTClient]: No connection available to the VIPA "+VIPA);
				rmeOpenedAppToTunnel.get(appName).put(VIPA, newTid);
				//				rmeOpenedAppToTunnel.remove(appName);
			}
			appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
			moveAppToTunnelRME(appName, newTid, VIPA);
		} else { //APP CLOSED (i.e. with no open sockets)
			String policy = rmeClosedPolicy.get(appName);
			int newTid;
			if((policy.trim()).equalsIgnoreCase("bestTunnel")) { // dovremmo scegliere una politica per il momento bestTunnel
				newTid = getBestTunnelForVipa(VIPA);
			}
			else {
				newTid = getBestTunnelForVipa(VIPA);
			}
			int currentTid;
			if(!rmeClosedAppToTunnel.containsKey(appName)) {
				rmeClosedAppToTunnel.put(appName, new HashMap<String, Integer>());
				rmeClosedAppToTunnel.get(appName).put(VIPA, newTid);
				currentTid = newTid;
				appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
			}
			else {
				if(rmeClosedAppToTunnel.get(appName).containsKey(VIPA)) {
					currentTid = rmeClosedAppToTunnel.get(appName).get(VIPA);
				}
				else {
					rmeClosedAppToTunnel.get(appName).put(VIPA, newTid);
					currentTid = newTid;
					appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
				}
			}
			if ((newTid==TID_DROP && currentTid==TID_DROP) || (newTid!=TID_DROP && newTid==currentTid)) return;
			if (newTid!=TID_DROP) {
				rmeClosedAppToTunnel.get(appName).put(VIPA, newTid);
			} else {
				System.err.println("[UPMTClient]: No connection available to the VIPA "+VIPA);
				rmeClosedAppToTunnel.get(appName).put(VIPA, newTid);
//				rmeClosedAppToTunnel.remove(appName);
			}
			appMonitor.rmeSetAppAndVIPA(appName, VIPA, newTid);
		}
		if(!textMode) {
			((GUIApplicationManager)appManager).refreshGui();
		}
	}

	/**
	 * Adds a tunnel to the hashmap of tunnelLists by VIPA, interface-name and end-point-address
	 * @param VIPA
	 * @param ifName
	 * @param endPointAddress
	 */
	public void addRMETunnelToTunnelList(String VIPA, String ifName, String endPointAddress) {
		VipaTunnel vt = new VipaTunnel(VIPA, ifName, endPointAddress, tunnelManager.getTid(ifName, endPointAddress));
		if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
			this.rmePerVipaTunnelList.get(VIPA).add(vt);
		} else {
			this.rmePerVipaTunnelList.put(VIPA, new ArrayList<VipaTunnel>());
			this.rmePerVipaTunnelList.get(VIPA).add(vt);
		}
	}

	/**
	 * Adds a tunnel to the hashmap of tunnelLists by VIPA, interface-name, end-point-address and tunnel ID
	 * @param VIPA
	 * @param ifName
	 * @param endPointAddress
	 * @param tid
	 */
	public void addRMETunnelToTunnelList(String VIPA, String ifName, String endPointAddress, int tid) {
		VipaTunnel vt = new VipaTunnel(VIPA, ifName, endPointAddress, tid);
		if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
			this.rmePerVipaTunnelList.get(VIPA).add(vt);
		} else {
			this.rmePerVipaTunnelList.put(VIPA, new ArrayList<VipaTunnel>());
			this.rmePerVipaTunnelList.get(VIPA).add(vt);
		}
	}

	/**
	 * Deletes a tunnel to the hashmap of tunnelLists by VIPA, interface-name and end-point-address
	 * @param VIPA
	 * @param ifName
	 * @param endPointAddress
	 */
	public void delRMETunnelToTunnelList(String VIPA, String ifName, String endPointAddress) {
		if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
			for(int i=0; i<this.rmePerVipaTunnelList.get(VIPA).size(); i++) {
				if(this.rmePerVipaTunnelList.get(VIPA).get(i).getIfName().equals(ifName) 
						&& this.rmePerVipaTunnelList.get(VIPA).get(i).getEndPointAddress().equals(endPointAddress)) {
					this.rmePerVipaTunnelList.get(VIPA).remove(i);
				}
			}
		}
	}
	
	public void delRmePerVipaTunnelList(String VIPA, int tid) {
		if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
			for(int i=0; i<this.rmePerVipaTunnelList.get(VIPA).size(); i++) {
				if(this.rmePerVipaTunnelList.get(VIPA).get(i).getTid()==tid) {
					this.rmePerVipaTunnelList.get(VIPA).remove(i);
				}
			}
		}
	}

	/**
	 * Deletes a tunnel to the hashmap of tunnelLists by VIPA, and tunnel ID
	 * @param VIPA
	 * @param ifname
	 */
	public void delRMETunnelToTunnelList(String VIPA, int tid) {
		delRmePerVipaTunnelList(VIPA, tid);
		delBestTunnelForVipa(VIPA, tid);
		delRmePerVipaBestTunnelDDS(VIPA, tid);
	}
	
	public String tryToDelRMETunnelToTunnelList(int tid, String ifName, String ANAddress) {
		String VIPA = getVipa(ANAddress);
		if(VIPA!=null) {
			delRmePerVipaTunnelList(VIPA, tid);
			delBestTunnelForVipa(VIPA, tid);
			delRmePerVipaBestTunnelDDS(VIPA, tid);
		}
		return VIPA;
	}
	
	/**
	 * 
	 * @param VIPA
	 * @param tid
	 */
	public void delRmePerVipaBestTunnelDDS(String VIPA, int tid) {
		if(this.rmePerVipaBestTunnelDDS.containsKey(VIPA)) {
			if(this.rmePerVipaBestTunnelDDS.get(VIPA).getTid()==tid) {
				this.rmePerVipaBestTunnelDDS.remove(VIPA);
			}
		}
	}

	public HashMap<String, ArrayList<VipaTunnel>> getRmePerVipaTunnelList() {
		return this.rmePerVipaTunnelList;
	}

	public ArrayList<String> getOlsrDetectedEndPoint() {
		return olsrDetectedEndPoint;
	}

	public void setOlsrDetectedEndPoint(ArrayList<String> olsrDetectedEndPoint) {
		this.olsrDetectedEndPoint = olsrDetectedEndPoint;
	}

	private void moveAppToTunnelRME(String appName, int tid, String vepa) {

		printLog("Tunnel handover for "+appName+" to tunnel with ID: "+tid, Log.LEVEL_HIGH);

		synchronized(socketForApp) {
			Vector<Socket> sockVect = socketForApp.get(appName);
			if (sockVect==null) {
				printLog("[UPMTClient.moveAppToInterf]: socketForApp.get( \"" +appName + "\" ) returns null!!",Log.LEVEL_HIGH );
				return;
			} else {			
				for (Socket socket : socketForApp.get(appName)) {
					moveSocketToTunnelRME(socket, tid, appName, vepa);
				}
			}
		}
	}

	private void moveSocketToTunnelRME(Socket socket, int tid, String appName, String vepa) {
		if(tid==TID_DROP) {
//			printLog("[UPMT client.moveSocketToInterf]: iFName NULL!!!" ,Log.LEVEL_HIGH );
			return;
		}
		if(vepa.equals(socket.dstIp)) {
			tunnelManager.assignSocketToTunnelRME(socket.proto, socket.srcPort, socket.dstIp, socket.dstPort, tid);
		}
	}


	/**
	 * Chooses the best tunnel for a determinated VIPA
	 * 
	 * @param VIPA
	 * @return the tid which refers to the best tunnel
	 */
	public int getBestTunnelForVipaOLD(String VIPA) {
		if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
			if(this.rmePerVipaTunnelList.get(VIPA).size()!=0) {
				// potrebbe creare problemi un rme la condizione prima dell'istruzione
				if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaTunnelList.get(VIPA).get(0).getTid());

				return this.rmePerVipaTunnelList.get(VIPA).get(0).getTid();
			}
		}
		return TID_DROP;
	}

	/**
	 * Chooses the best tunnel for a determinated VIPA
	 * 
	 * @param VIPA
	 * @return the tid which refers to the best tunnel
	 */
	public int getBestTunnelForVipa(String VIPA) {
		if(rmePerVipaBestTunnelDDS.containsKey(VIPA)){
			if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaBestTunnelDDS.get(VIPA).getTid());
			return this.rmePerVipaBestTunnelDDS.get(VIPA).getTid();
		}else{
			if(this.rmePerVipaBestTunnel.containsKey(VIPA)) {
				if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaBestTunnel.get(VIPA).getTid());
				return this.rmePerVipaBestTunnel.get(VIPA).getTid();
			}
			else if(this.rmePerVipaBestTunnelServer.containsKey(VIPA)) {
				if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaBestTunnelServer.get(VIPA).getTid());
				return this.rmePerVipaBestTunnelServer.get(VIPA).getTid();
			}
			else if(this.rmePerVipaTunnelList.containsKey(VIPA)) {
				if(this.rmePerVipaTunnelList.get(VIPA).size()!=0) {
					// potrebbe creare problemi un rme la condizione prima dell'istruzione				
					if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaTunnelList.get(VIPA).get(0).getTid());
					return this.rmePerVipaTunnelList.get(VIPA).get(0).getTid();
				}
			}
		}
		return TID_DROP;
	}

	/**
	 * Chooses the best tunnel for a determinated VIPA
	 * 
	 * @param VIPA
	 * @return the tid which refers to the best tunnel
	 */
	public void setBestTunnelForVipa(String ANAddress, TunnelInfo tInfo) {
		String VIPA = getVipa(ANAddress);
		if(ddsQoS.containsKey(VIPA) && DDSQoSReceiver) {
			reallocationDDSTunnel(VIPA);
		}else if(interfaceBalance){
			setBestTunnelwithIntefaceBalance(VIPA);
		}
		else {
			setBestTunnelWithBestDelay(VIPA);
		}


	}

	/**
	 * Chooses the best tunnel for a determinated VIPA
	 * 
	 * @param VIPA
	 * @return the tid which refers to the best tunnel
	 */
	public void setBestTunnelForVipaServer(String ANAddress, RMETunnelInfo tInfo) {
		String VIPA = getVipa(ANAddress);
		if(tInfo.getKeepaliveNumber()>=2) {
			if(ddsQoS.containsKey(VIPA) && DDSQoSReceiver) {
				reallocationDDSTunnel(VIPA);
			}else if(interfaceBalance){
				setBestTunnelwithIntefaceBalance(VIPA);
			}
			else {
				setBestTunnelWithBestDelay(VIPA);
			}
		}
	}




	public void delBestTunnelForVipa(String VIPA, int tid) {
		if(this.rmePerVipaBestTunnel.containsKey(VIPA)) {
			if(this.rmePerVipaBestTunnel.get(VIPA).getTid()==tid) {
				this.rmePerVipaBestTunnel.remove(VIPA);
			}
		}
		else if(this.rmePerVipaBestTunnelServer.containsKey(VIPA)) {
			if(this.rmePerVipaBestTunnelServer.get(VIPA).getTid()==tid) {
				this.rmePerVipaBestTunnelServer.remove(VIPA);
			}
		}
	}

	public HashMap<String, TunnelInfo> getRmePerVipaBestTunnel() {
		return rmePerVipaBestTunnel;
	}

	public void setRmePerVipaBestTunnel(HashMap<String, TunnelInfo> rmePerVipaBestTunnel) {
		this.rmePerVipaBestTunnel = rmePerVipaBestTunnel;
	}

	public ApplicationMonitor getAppMonitor() {
		return this.appMonitor;
	}

	public static ArrayList<String> getRMETunnelsToGUI() {

		ArrayList<String> tunnelToGui = new ArrayList<String>(RMETunnelsToGUI);

		return tunnelToGui;
	}

	public static void addRMETunnelsToGUI(String endPointAddress) {
		if(!RMETunnelsToGUI.contains(endPointAddress)) {
			RMETunnelsToGUI.add(endPointAddress);
		}
	}

	public static void delRMETunnelsToGUI(String endPointAddress) {
		if(RMETunnelsToGUI.contains(endPointAddress)) {
			RMETunnelsToGUI.remove(endPointAddress);
		}
	}


	public static HashMap<String, RMETunnelInfo> getRMERemoteTidStatusTable() {
		return RMEremoteTidStatusTable;

	}

	public static String getVipaUI(String anAddress) {

		if(UPMTClient.ipToVipa.containsKey(anAddress)) {
			return UPMTClient.ipToVipa.get(anAddress);
		}		
		return null;
	}


	// old version using netconf.json

	//	public static String getVipaUI(String anAddress) {
	//		
	//		
	//		
	//		
	//		ParseJson parse;
	//		try {
	//			parse = new ParseJson();
	//			for(String VIPA: parse.getJmap().keySet()) {
	//				for(int i=0; i<parse.getJmap().get(VIPA).size(); i++) {
	//					if(parse.getJmap().get(VIPA).get(i).getIp().trim().equals(anAddress.trim())) {
	//						return VIPA;
	//					}
	//				}
	//			}
	//		} catch (IOException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		} catch (JSONException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		}
	//		
	//		
	//		return null;
	//	}

	public static String getVepa() {
		return cfg.vepa;
	}

	public static void runOlsrd(String devName) {
		for(int i=0; i<rmeAddresses.size(); i++) {
			for(int j=0; j<cfg.olsrdConf.size(); j++) {
				String[] splitted = cfg.olsrdConf.get(j).split("/");
				String filename = splitted[(splitted.length)-1];
				String ifConf = filename.substring(0, filename.length()-5);
				if(rmeAddresses.get(i).getRmeInterface().equals(devName) && ifConf.equals(devName)) {
					try {
						Runtime.getRuntime().exec("sudo olsrd -f "+cfg.olsrdConf.get(j));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void addDiscoveredaddresses(String vepa, String[] addressesInUse) {
		synchronized (this.rmeDiscoveredAddresses) {
			if(!this.rmeDiscoveredAddresses.containsKey(vepa)) {
				this.rmeDiscoveredAddresses.put(vepa, addressesInUse);
				for(String address: addressesInUse) {
					if(!UPMTClient.ipToVipa.containsKey(address)) {
						UPMTClient.ipToVipa.put(address, vepa);
					}
				}
			}
		}
	}

	public void addSingleDiscoveredAddress(String vepa, String address) {
		synchronized (this.rmeDiscoveredAddresses) {
			if(!UPMTClient.ipToVipa.containsKey(address)) {
				UPMTClient.ipToVipa.put(address, vepa);
			}
		}
	}

	public String[] getDiscoveredaddresses(String vepa) {
		synchronized (this.rmeDiscoveredAddresses) {
			if(this.rmeDiscoveredAddresses.containsKey(vepa)) {
				return this.rmeDiscoveredAddresses.get(vepa);
			}
			else {
				return null;
			}
		}
	}
	/**
	 * Update the hash map for the DDS Qos and it do the reallocation
	 * @param key
	 * @param effectiveLatency
	 * @author Pierluigi Greto
	 */
	public void updateDDSQos(String key, int effectiveLatency) {
		String vipaKey = "";
		if(key.contains(":")){
			String[] array = key.split(":");
			vipaKey = array[0];
		}else {
			vipaKey = key;
		}

		if(!ddsQoS.containsKey(vipaKey) || ddsQoS.get(vipaKey)!=effectiveLatency) {
			ddsQoS.put(vipaKey, effectiveLatency);
			reallocationDDSTunnel(vipaKey);
		}


	}

	/**
	 * @param VIPA
	 * @return A list of all DDSQoSTunnelInfo (sorted by EWMA_delay) for the VIPA
	 * @author Pierluigi Greto
	 */
	public ArrayList<DDSQoSTunnelInfo> getTunnelInfoDDSForVipa(String VIPA) {
		ArrayList<DDSQoSTunnelInfo> tunnelInfoForDDS = new ArrayList<DDSQoSTunnelInfo>();
		if(this.rmePerVipaTunnelList.containsKey(VIPA) && this.rmeDiscoveredAddresses.containsKey(VIPA)) {
			for(String address: this.rmeDiscoveredAddresses.get(VIPA)) {
				for(String ifname: availableIfs.keySet()) {
					if(SipSignalManager.getRemoteTidStatusTable().containsKey(address+":"+ifname)) {
						TunnelInfo tunnelInfo = SipSignalManager.getRemoteTidStatusTable().get(address+":"+ifname);
						if(tunnelInfo.getStatus() == TunnelInfo.TUNNEL_SETUP){
							DDSQoSTunnelInfo ddsQoSTunnelInfo= new DDSQoSTunnelInfo(tunnelInfo.getTid(), tunnelInfo.getEWMA_Delay(), false, 0);
							tunnelInfoForDDS.add(ddsQoSTunnelInfo);
						}
					}
					else if(RMEremoteTidStatusTable.containsKey(address+":"+ifname)) {
						RMETunnelInfo tunnelInfo = RMEremoteTidStatusTable.get(address+":"+ifname);
						if(tunnelInfo.getStatus() == TunnelInfo.TUNNEL_SETUP){
							DDSQoSTunnelInfo ddsQoSTunnelInfo= new DDSQoSTunnelInfo(tunnelInfo.getTid(), tunnelInfo.getEWMA_delay(), true, tunnelInfo.getKeepaliveNumber());
							tunnelInfoForDDS.add(ddsQoSTunnelInfo);
						}
					}
				}
			}
		}
		Collections.sort(tunnelInfoForDDS, new Comparator<DDSQoSTunnelInfo>() {
			@Override
			public int compare(DDSQoSTunnelInfo arg0, DDSQoSTunnelInfo arg1) {
				return new Double(arg0.getEwma_delay()).compareTo(new Double(arg1.getEwma_delay()));

			}
		});
		return tunnelInfoForDDS;
	}
	/**
	 * 
	 * @return A HashTable with a list of DDSQoSTunnelInfo for any VIPA with a QoS value
	 * @author Pierluigi Greto
	 */
	public HashMap<String, ArrayList<DDSQoSTunnelInfo>> getForAllVipaDDSQoDTunnelInfo(){
		HashMap<String, ArrayList<DDSQoSTunnelInfo>> DDSQoDTunnelInfoForAllVipa = new HashMap<String, ArrayList<DDSQoSTunnelInfo>>();
		for(String VIPA :this.rmePerVipaTunnelList.keySet()){
			ArrayList<DDSQoSTunnelInfo> tunnelInfoDDSForVipa = getTunnelInfoDDSForVipa(VIPA);
			if(tunnelInfoDDSForVipa.size()!=0){
				DDSQoDTunnelInfoForAllVipa.put(VIPA, tunnelInfoDDSForVipa);
			}
		}
		return DDSQoDTunnelInfoForAllVipa;
	}


	/**
	 *  Reallocation of bestTunnel for all VIPA
	 * @param VIPA
	 * @author Pierluigi Greto
	 */
	public void reallocationDDSTunnel(String VIPA){
		if(DDSQoSReceiver && this.rmePerVipaTunnelList.containsKey(VIPA) ){
			HashMap<String, ArrayList<DDSQoSTunnelInfo>> DDSQoDTunnelInfoForAllVipa = getForAllVipaDDSQoDTunnelInfo();
			decisionEngine(DDSQoDTunnelInfoForAllVipa);
		}
	}

	/**
	 * Set for the Vipa the bestTunnel tunnelInfoForDDS
	 * @param VIPA
	 * @param tunnelInfoForDDS
	 * @author Pierluigi Greto
	 */
	public void setDDSQoSBestTunnel(String VIPA, DDSQoSTunnelInfo tunnelInfoForDDS) {
		if( (!tunnelInfoForDDS.isServer()) || (tunnelInfoForDDS.isServer() && tunnelInfoForDDS.getNumberKeepAlive()>=2) ) {
			this.rmePerVipaBestTunnelDDS.put(VIPA, tunnelInfoForDDS);
			printLog("New default Tunnel for "+VIPA+" with Tunnel ID "+this.rmePerVipaBestTunnelDDS.get(VIPA).getTid(), Log.LEVEL_MEDIUM);
			//aggiornamento interfaccia grafica
			if (!coreEmulator) ((GUIApplicationManager) this.appManager).setTunnelInUseForVipa(VIPA, this.rmePerVipaBestTunnelDDS.get(VIPA).getTid());
			checkAllRMEPolicy(VIPA, RME_EVENT_TUN_UPDATE);
		}
	}

	/**
	 * Calculate and set the best tunnel. It use the Reallocation Priotity
	 * @param DDSQoDTunnelInfoForAllVipa
	 * @author Pierluigi Greto
	 */
	public void decisionEngine(HashMap<String, ArrayList<DDSQoSTunnelInfo>> DDSQoDTunnelInfoForAllVipa) {
		ArrayList<VipaMeasures>	vipaMeasures = new ArrayList<VipaMeasures>();	
		for(String VIPA : DDSQoDTunnelInfoForAllVipa.keySet()){
			if(!this.rmePerVipaBestTunnelDDS.containsKey(VIPA)){
				if(this.rmePerVipaBestTunnel.containsKey(VIPA)){
					TunnelInfo tinfo = this.rmePerVipaBestTunnel.get(VIPA);
					this.rmePerVipaBestTunnelDDS.put(VIPA, new DDSQoSTunnelInfo(tinfo.getTid(), tinfo.getEWMA_Delay(), false, 0));
				} else if (this.rmePerVipaBestTunnelServer.containsKey(VIPA)){
					RMETunnelInfo tinfo = this.rmePerVipaBestTunnelServer.get(VIPA);
					this.rmePerVipaBestTunnelDDS.put(VIPA, new DDSQoSTunnelInfo(tinfo.getTid(), tinfo.getEWMA_delay(), true, tinfo.getKeepaliveNumber()));
				} else {
					this.setDDSQoSBestTunnel(VIPA, DDSQoDTunnelInfoForAllVipa.get(VIPA).get(0));
				}
			}
			if(!ddsQoS.containsKey(VIPA)){
				ddsQoS.put(VIPA, 0);
			}
			DDSQoSTunnelInfo tinfo = this.rmePerVipaBestTunnelDDS.get(VIPA);
			double measuredSpread = tinfo.getEwma_delay() - ddsQoS.get(VIPA);
			double potentialGain = tinfo.getEwma_delay() - DDSQoDTunnelInfoForAllVipa.get(VIPA).get(0).getEwma_delay();
			double reallocationPriority = measuredSpread + potentialGain;
			vipaMeasures.add(new VipaMeasures(VIPA, measuredSpread, potentialGain, reallocationPriority));
		}

		/* Order List potentialGain */
		Collections.sort(vipaMeasures,Collections.reverseOrder(new Comparator<VipaMeasures>() {

			@Override
			public int compare(VipaMeasures arg0, VipaMeasures arg1) {
				// TODO Auto-generated method stub
				return new Double(arg0.getReallocationPriority()).compareTo(new Double(arg1.getReallocationPriority()));
			}
		}));

		HashMap<String, ArrayList<DDSQoSTunnelInfo>> allVipaTunnelInfo = getForAllVipaDDSQoDTunnelInfo();
		HashMap<String, Integer> numberOfTunnelAllocabiliForInterface = new HashMap<String, Integer>();
		int R = allVipaTunnelInfo.size();
		int M = availableIfs.size();
		for(String nameinf :availableIfs.keySet()){
			numberOfTunnelAllocabiliForInterface.put(nameinf, 0);
		}		
		for(VipaMeasures vm: vipaMeasures){
			String vipa = vm.getVipa();
			boolean setted = false;
			for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
				String ifname = getInterfaceForVipa(vipa, ti.getTid());
				if(ifname != null){
					if(numberOfTunnelAllocabiliForInterface.get(ifname) < ((int)(R/M)) && ti.getEwma_delay() <= ddsQoS.get(vipa)){
						setted = true;
						int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
						numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
						if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid())){
							this.setDDSQoSBestTunnel(vipa, ti);					
						}
						break;
					}
				}
			}
			if(!setted){
				for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
					String ifname = getInterfaceForVipa(vipa, ti.getTid());
					if(ifname != null){
						if(numberOfTunnelAllocabiliForInterface.get(ifname) < (((int)(R/M))+1) && ti.getEwma_delay() <= ddsQoS.get(vipa) ){
							setted = true;
							int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
							numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
							if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid())){
								this.setDDSQoSBestTunnel(vipa, ti);					
							}
							break;
						}
					}
				}
			}
			if(!setted){
				for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
					String ifname = getInterfaceForVipa(vipa, ti.getTid());
					if(ifname != null){
						setted = true;
						int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
						numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
						if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || ((this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid()))){
							this.setDDSQoSBestTunnel(vipa, ti);					
						}
						break;
					}
				}
			}
		}

	}


	/**
	 * Set the best tunnel balancing the interface without the QoS
	 * @param Vipa
	 * @author Pierluigi Greto
	 */
	public void setBestTunnelwithIntefaceBalance(String Vipa){
		if(Vipa!=null){
			HashMap<String, ArrayList<DDSQoSTunnelInfo>> allVipaTunnelInfo = getForAllVipaDDSQoDTunnelInfo();
			HashMap<String, Integer> numberOfTunnelAllocabiliForInterface = new HashMap<String, Integer>();
			int R = allVipaTunnelInfo.size();
			int M = availableIfs.size();
			for(String nameinf :availableIfs.keySet()){
				numberOfTunnelAllocabiliForInterface.put(nameinf, 0);
			}
			for(String vipa: allVipaTunnelInfo.keySet()){
				boolean setted = false;
				for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
					String ifname = getInterfaceForVipa(vipa, ti.getTid());
					if(ifname != null){
						if(numberOfTunnelAllocabiliForInterface.get(ifname) < ((int)(R/M)) ){
							setted = true;
							int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
							numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
							if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid())){
								this.setDDSQoSBestTunnel(vipa, ti);					
							}
							break;
						}
					}
				}
				if(!setted){
					for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
						String ifname = getInterfaceForVipa(vipa, ti.getTid());
						if(ifname != null){
							if(numberOfTunnelAllocabiliForInterface.get(ifname) < (((int)(R/M))+1) ){
								setted = true;
								int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
								numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
								if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid())){
									this.setDDSQoSBestTunnel(vipa, ti);					
								}
								break;
							}
						}
					}
				}
				if(!setted){
					for(DDSQoSTunnelInfo ti: allVipaTunnelInfo.get(vipa)){
						String ifname = getInterfaceForVipa(vipa, ti.getTid());
						if(ifname != null){
							setted = true;
							int tunnel = numberOfTunnelAllocabiliForInterface.get(ifname)+1;
							numberOfTunnelAllocabiliForInterface.put(ifname, tunnel);
							if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti.getTid())){
								this.setDDSQoSBestTunnel(vipa, ti);					
							}
							break;
						}
					}
				}

			}
		}
	}

	/**
	 * Return the interface name of the tunnel tid
	 * @param vipa
	 * @param tid
	 * @return 
	 * @author Pierluigi Greto
	 */
	private String getInterfaceForVipa(String vipa, int tid) {
		for(String address: this.rmeDiscoveredAddresses.get(vipa)) {
			for(String ifname: availableIfs.keySet()) {
				if(SipSignalManager.getRemoteTidStatusTable().containsKey(address+":"+ifname)) {
					TunnelInfo tunnelInfo = SipSignalManager.getRemoteTidStatusTable().get(address+":"+ifname);
					if(tunnelInfo!=null ){
						if(tunnelInfo.getTid()==tid){
							return ifname;
						}						
					}
				}
				else if(RMEremoteTidStatusTable.containsKey(address+":"+ifname)) {
					RMETunnelInfo tunnelInfo = RMEremoteTidStatusTable.get(address+":"+ifname);
					if(tunnelInfo!=null ){
						if(tunnelInfo.getTid()==tid){
							return ifname;
						}						
					}
				}
			}
		}
		return null;
	}

	/**
	 * Return a hashMap with all interface, and for all interface the number of Best tunnel
	 * @return
	 * @author Pierluigi Greto
	 */
	public HashMap<String, Integer> getInterfaceNumberOfBestTunnel(){
		HashMap<String, Integer> interfaceNumberOfBestTunnel = new HashMap<String, Integer>();		
		for(String VIPA :this.rmePerVipaTunnelList.keySet()){
			if(this.rmeDiscoveredAddresses.containsKey(VIPA)){
				for(String address: this.rmeDiscoveredAddresses.get(VIPA)) {
					for(String ifname: availableIfs.keySet()) {
						if(!interfaceNumberOfBestTunnel.containsKey(ifname)){
							interfaceNumberOfBestTunnel.put(ifname, 0);
						}
						if(SipSignalManager.getRemoteTidStatusTable().containsKey(address+":"+ifname)) {
							TunnelInfo tunnelInfo = SipSignalManager.getRemoteTidStatusTable().get(address+":"+ifname);
							int bestTid = TID_DROP;
							if(!coreEmulator) bestTid =	((GUIApplicationManager) this.appManager).tunnelInUseForVipa.get(VIPA);
							if(tunnelInfo!=null && tunnelInfo.getStatus()==TunnelInfo.TUNNEL_SETUP ){
								int localTID = TunnelManager.getTidTable().get(ifname+":"+address);
								if(bestTid==localTID){
									int number =interfaceNumberOfBestTunnel.get(ifname);
									number++;
									interfaceNumberOfBestTunnel.put(ifname, number);
								}
							}
						}
						else if(RMEremoteTidStatusTable.containsKey(address+":"+ifname)) {
							RMETunnelInfo tunnelInfo = RMEremoteTidStatusTable.get(address+":"+ifname);
							int bestTid = TID_DROP;
							if(!coreEmulator) bestTid =	((GUIApplicationManager) this.appManager).tunnelInUseForVipa.get(VIPA);
							if(tunnelInfo!=null &&  tunnelInfo.getStatus()==RMETunnelInfo.TUNNEL_SETUP){
								int localTID = tunnelInfo.getServerTunnelID();
								if(bestTid==localTID){
									int number =interfaceNumberOfBestTunnel.get(ifname);
									number++;
									interfaceNumberOfBestTunnel.put(ifname, number);
								}
							}
						}
					}
				}
			}
		}
		return interfaceNumberOfBestTunnel;
	}


	private void setBestTunnelWithBestDelay(String Vipa) {
		if(Vipa != null){
			HashMap<String, ArrayList<DDSQoSTunnelInfo>> allVipaTunnelInfo = getForAllVipaDDSQoDTunnelInfo();
			for(String vipa: allVipaTunnelInfo.keySet()){
				DDSQoSTunnelInfo ti = allVipaTunnelInfo.get(vipa).get(0);
				if(ti!=null){
					if(!this.rmePerVipaBestTunnelDDS.containsKey(vipa) || (this.rmePerVipaBestTunnelDDS.get(vipa).getTid() != ti .getTid())) {
						this.setDDSQoSBestTunnel(vipa, ti);					
					}
				}
			}
		}
	}

	public void handleDelTun(int tid, String ifname, String remoteIP) {
		if(cfgANList.contains(remoteIP)) { //client
			signaler.removeTunnel(tid, ifname, remoteIP);
		}
		else { //server
			for(RMEServer server: servers) {
				if(server.getServerName().equals(ifname)) {
					server.handleDelTunnel(tid, ifname, remoteIP);
				}
			}
		}
		if(!textMode) {
			((GUIApplicationManager)appManager).refreshGui();
		}
	}

	public void handleInfoTunnel(int tid, String ifname, String remoteIP, int delay, int loss, int ewmadelay, int ewmaloss) {
		//String toPrint = "["+ System.currentTimeMillis() +"] "+"delay: "+delay + " loss: "+loss;
		//toPrint = "echo '" + System.currentTimeMillis() + "," + delay + "," + loss + "'" + " >> /home/upmt/Desktop/kp_measure.dat";
		//System.out.println(toPrint);
		//try {
		//	Runtime.getRuntime().exec(toPrint);
		//} catch (IOException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//}
		printMeasure(System.currentTimeMillis(), delay, loss);
		if(cfgANList.contains(remoteIP)) { //client			
			signaler.infoTunnel(remoteIP, ifname, delay, loss);
		}
		else { //server
			for(RMEServer server: servers) {
				if(server.getServerName().equals(ifname)) {
					server.handleInfoTunnel(tid, ifname, remoteIP, delay, loss, 0, 0);
				}
			}
		}
		if(!textMode) {
			((GUIApplicationManager)appManager).refreshGui();
		}
	}

	public void printMeasure(long millis, int delay, int loss) {
		try {
			String content = System.currentTimeMillis() + "," + delay + "," + loss + "\n";
			File file = new File("/home/upmt/Desktop/keepAlive_measures.dat");
			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(content);
			bw.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

}
