package upmt.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

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
import upmt.client.rme.RoutingCheck;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;
import upmt.os.Module;
import upmt.os.Shell;

public class UPMTClient implements NetworkMonitorListener, ApplicationManagerListener, ApplicationMonitorListener
{
	/** set to true when developing the GUI */
	public static final boolean ONLY_GUI = false;

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


	private static ConfigManager cfg;
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
	private static String rmeConfig;
	private static ArrayList<String> rmeInterfacesList;


	//****************************************POLICY****************************************	

	/**
	 * hashtable of policies (expressed as strings) for each application as read from cfg file
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

	/**p
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
		file = file!=null?file:Default.CLIENT_CONFIG_FILE;
		UPMTClient.cfg = ConfigManager.instance();
		UPMTClient.cfgFile = file;
		cfg.ParseConfig(file);

		if (ONLY_GUI) {
			instance().run();
			return;
		}

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

		Module.setVipaFix(cfg.vipaFix);
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

		appManager = ApplicationManagerFactory.getApplicationManager(cfg.applicationManager);
		appMonitor = ApplicationMonitorFactory.getApplicationMonitor(cfg.applicationMonitor);

		//Anchor Node
		maxANNumber = cfg.anNumber;
		cfgANList = cfg.ANList; //TODO: meglio fare una copia per eventuali modificke

		//Radio Multiple Eterogenee 
		rme = cfg.rme;
		if(rme) {
			rmeConfig = cfg.rmeConfig;
			rmeInterfacesList = new ArrayList<String>(Arrays.asList(cfg.rmeInterfaces));
			//cfg.rmeInterfaces = rmeInterfacesList.toArray(new String[]{});
		}

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

		setSignalingPolicy();

		//InterfPolicyFactory.setEventRegister(eventRegister);

	}


	//marco
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
		
		if(rme) {
			RoutingCheck.initialize();
		}

		if (ONLY_GUI) {
			appManager.startListen(this);
			return;
		}

		appManager.startListen(this);

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
			}

			if(rme) {
				RoutingCheck.runMH();
			}

			downloadANList();

			//for each anchor node and for all the availableIfs
			//it checks on which interfaces it is possible to contact
			//the anchor node

			networkMonitor.startListen(this); //monitor changes of network interfaces
			//GUIApplicationManager for commands given by the user on the GUI
			//like save policy, apply policy
			appManager.startWorking(); 	
			appMonitor.startListen(this); 	//per l'apertura o chiusura di applicazioni

			updateMsg("Trying to contact Anchor Node(s)...");

			while(associatedANList.size() == 0)
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

			//#ifndef ANDROID
			((GUIApplicationManager) appManager).startGraphers();
			//#endif


			configureDefaultPolicy();
			putPoliciesToHashtable();

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
		signaler.startListen(this); 

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
		((GUIApplicationManager)appManager).refreshStatusBarAndFirstRow();
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
		if (rme) RoutingCheck.stopMH(); //Radio Multiple Eterogenee
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

			for (String ANAddress : associatedANList.keySet()) {
				long result = tunnelSetup(ifName, ANAddress, associatedANList.get(ANAddress));
			}

			checkAllPolicy(EVENT_INTERFACE_UP);
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

			checkAllPolicy(EVENT_INTERFACE_DOWN);

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
	private void checkAllPolicy(int event) {
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

		appManager.onPolicyCheck();
	}

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
	private boolean addANonIf(String ANAddress, String interf, boolean blocking) {
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
					((GUIApplicationManager)getGui()).refreshGui();
					//#endif

				}
			}
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
	public long tunnelSetup(String ifName, String ANAddress, int tsaPort) {		
		String vipaForThisAN = signaler.getVipaForAN(ANAddress);
		int result = tunnelManager.addTunnel(vipaForThisAN, ifName, ANAddress, tsaPort);
		printLog("LocalTunnelID: " + result,Log.LEVEL_HIGH );
		if (result==0) {
			printLog("ATTENTION: LocalTunnelID should not be 0 ",Log.LEVEL_HIGH );
			return result;
		}

		//blocking call
		int result1 = signaler.createTunnel(ifName, ANAddress);

		if (result1==0) {
			tunnelManager.removeTunnel(ifName, ANAddress);
			return 0;
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
				System.err.println("\nSending " + appName + " traffic on interface " + currentIf + "\n");
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

		appManager.addApp(appName);
	}

	public void socketOpened(String appName, Socket socket) {
		synchronized(socketForApp) {
			if(!socketForApp.containsKey(appName)) socketForApp.put(appName, new Vector<Socket>(Arrays.asList(new Socket[]{socket})));
			else socketForApp.get(appName).add(socket);
			appForSocket.put(socket.id(), appName);
			appManager.addSocket(appName, socket);
		}
	}

	//TODO: fatto al volo ma va verificato xk� x adesso nel modulo ancora nn c'� la garbage collection
	public void socketClosed(Socket socket)
	{
		synchronized(socketForApp)
		{
			socketForApp.get(appForSocket.remove(socket.id())).remove(socket);
			appManager.rmvSocket(socket);
		}
	}

	//TODO!!! Da fare xk� x adesso nel modulo ancora nn c'� la garbage collection.
	//Quando un app si kiude si elimina la politica dalla lista delle app aperte e si crea 
	//una nuova politica nella lista delle kiuse a partire dalla stringa del config (come all'avvio)
	public void appClosed(String appName)
	{

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

		appManager.onPolicyCheck();
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
		if(signaler.getDefaultAN() != null && cfgDefaultANPolicy != null ){
			if(cfgDefaultANPolicy.contains(signaler.getDefaultAN())){
				defaultAN = signaler.getDefaultAN();
				System.err.println("NEW DEFAULT AN AFTER AN REMOVAL: " + defaultAN);
			}
		}
		else{
			defaultAN = null;
			System.err.println("NEW DEFAULT AN AFTER AN REMOVAL: " + defaultAN);
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
		return openedPolicy.get(appName).getDesc();
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
		cfg.writeTag(Default.ANList_TAG, addr);
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
		appManager.onPolicyCheck();
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
		appManager.onPolicyCheck();
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
		appManager.onPolicyCheck();
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

	//#ifndef ANDROID
	//*********************** Main method ***************************
	public static void main(String[] args)
	{
		String file = null;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) printUsageAndQuit();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];

		//RoutingCheck.initialize(); // serve per il mh package

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

		System.err.println("Trying complete restoration of association and tunnels to " + ANAddress);

		associatedANList.remove(ANAddress);

		HashSet<String> availableIfsCopy = null;

		synchronized(availableIfs) {
			availableIfsCopy = new HashSet<String>(availableIfs.keySet());
		}

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

	public static boolean getRME() {
		return rme;
	}

	public static String getRMEConfig() {
		return rmeConfig;
	}
	
	public static ArrayList<String> getRMEInterfacesList() {
		return rmeInterfacesList;
	}

	public void flushTables() {
		for (String ifName : availableIfs.keySet()) {
			Shell.executeCommand(new String[]{"sudo", "ip", "route", "flush", "table", ifName+"_table"});
		}
	}

	//#ifdef ANDROID
	//		public static Context getContext()
	//		{
	//			return context;
	//		}
	//#endif
}
