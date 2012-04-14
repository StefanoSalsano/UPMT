package com.and.gui;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

import upmt.Default;
import upmt.client.UPMTClient;
import upmt.client.application.interfPolicy.InterfPolicy;
import upmt.client.application.interfPolicy.InterfPolicyFactory;
import upmt.client.application.manager.ApplicationManager;
import upmt.client.application.manager.ApplicationManagerFactory;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.application.manager.impl.PopUpListener;
import upmt.client.application.manager.impl.PopUpWindows;
import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorFactory;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.core.ConfigManager;
import upmt.client.core.InterfaceInfo;
import upmt.client.network.NetworkMonitor;
import upmt.client.network.NetworkMonitorFactory;
import upmt.client.network.NetworkMonitorListener;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;
import upmt.os.Module;
import android.util.Log;

public class ClientService implements NetworkMonitorListener, ApplicationManagerListener, ApplicationMonitorListener
{
	public final static int EVENT_START = 0;
	public final static int EVENT_INTERFACE_UP = 1;
	public final static int EVENT_INTERFACE_DOWN = 2;

	private static final String CRLF = System.getProperty("line.separator"); //TODO: sostituire "\n" con CRLF in tutte le stringhe
	/**
	 * conventional tid meant to indicate that the packet has to be dropped because no interface is OK for a given application or flow
	 */
	private static final int TID_DROP = -1;
	
	private static final String WIZARD_AN = "Anchor Node IP address (IP:PORT)";
	private static final String WIZARD_SIP = "Your Sip Address";
	private static final String WIZARD_DESC = "Wizard";


	private static ConfigManager cfg;
	private static String cfgFile;

	/** Hashtable (interface name-> InterfaceInfo) 
	 *  containing the detected network interfaces and associated ip addresses/interface gateway<BR>
	 *  it does not say if the interface can be used (e.g. if it provides connectivity toward the AN)
	 */
	private Hashtable<String, InterfaceInfo> availableIfs = new Hashtable<String, InterfaceInfo>();

	//Anchor Node
	private int maxANNumber;
	private Vector<String> cfgANList;
	private Hashtable<String, Integer> associatedANList;
	
	


	
	//POLICY********************	
	//config
	private Hashtable<String, String> cfgPolicy;
	private String cfgDefaultPolicy;
	private String cfgSignalingPolicy;

	//closed
	/**
	 * hashtable (application name -> policy )
	 * for the closed applications (i.e. without any open sockets)
	 */
	private Hashtable<String, InterfPolicy> closedPolicy;
	private Hashtable<String, String> closedInterface;
	private InterfPolicy defaultClosedPolicy;
	private String defaultClosedInterface;

	//FIXME mettere se si vogliono le regole temporanee per le app kiuse
//	private Hashtable<String, Vector<String>> tmpPolicy;//temp (va di paripasso con le closed ed e' usato quando un app si kiude e devo ricreare la closed.)
//	private Vector<String> tmpDefaultPolicy;//temp (va di paripasso con le closed ed e' usato quando un app si kiude e devo ricreare la closed.)

	//opened
	private InterfPolicy signalingPolicy;
	
	/**
	 * interface used for signaling
	 */
	private String signalingInterface;

	/**
	 * hashtable (application name -> policy )
	 * for the active applications (i.e. with open sockets)
	 */
	private Hashtable<String, InterfPolicy> openedPolicy;
	private Hashtable<String, String> openedInterface;
	/**app->socket*/
	private Hashtable<String, Vector<Socket>> socketForApp;
	/**socket->app*/
	private Hashtable<String, String> appForSocket;

	//exception
	private Vector <String> noUpmtApp; //TODO: COMPLETAMENTE DA FARE!!!! MODIFICARE L'APPMON E IL MODULO!!!!

	//app registered for each event
	private Hashtable<Integer, Vector<String>> eventRegister;
	//*************************
	

	/** Monitor to control the network interfaces */
	private NetworkMonitor networkMonitor;
	private SipSignalManager signaler;
	private TunnelManager tunnelManager;
	private ApplicationManager appManager;
	private ApplicationMonitor appMonitor;
	




	
	public static void start(String file)
	{
		file = file!=null?file:Default.CLIENT_CONFIG_FILE;
		UPMTClient.cfg = ConfigManager.instance();
		UPMTClient.cfgFile = file;
		cfg.ParseConfig(file);

		if (cfg.sipID==null || cfg.ANList.size()==0) new PopUpWindows(new String[]{WIZARD_AN, WIZARD_SIP}, WIZARD_DESC, new PopUpListener()
		{
			public void done(Vector<String> ret)
			{
				if (ret==null || ret.size()!=2) quitGui();
				if (!ret.get(0).contains(":")) quitGui();
				try{Integer.parseInt(ret.get(0).split(":")[1].trim());}
				catch(NumberFormatException e) {quitGui();}

				cfg.ANList = new Vector<String>(Arrays.asList(new String[]{ret.get(0)}));
				cfg.writeTag(Default.ANList_TAG, ret.get(0));
				cfg.sipID = ret.get(1);
				cfg.writeTag(Default.sipID_Tag, ret.get(1));

				instance().run();
			}
		}).setVisible(true);
		else instance().run();
	}

	// Singleton
	private static UPMTClient instance;
	public static UPMTClient instance()
	{
		if (instance == null) instance = new UPMTClient();
		return instance;
	}

	private UPMTClient()
	{
		Module.setVipaFix(cfg.vipaFix);
		logger = new Logger(new Log(cfg.logFile, cfg.logLevel));
		tunnelManager = new TunnelManager();
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
		associatedANList = new Hashtable<String, Integer>();

		cfgDefaultPolicy = Arrays.toString(cfg.defaultAppPolicy.toArray(new String[]{})).replace("[", "").replace("]", "").replace(",", "");
		cfgSignalingPolicy = cfg.signalingPolicy==null?cfgDefaultPolicy:Arrays.toString(cfg.signalingPolicy.toArray(new String[]{})).replace("[", "").replace("]", "").replace(",", "");;
		cfgPolicy = new Hashtable<String, String>();
		for (String appName : cfg.applicationPolicy.keySet())
			cfgPolicy.put(appName, Arrays.toString(cfg.applicationPolicy.get(appName).toArray(new String[]{})).replace("[", "").replace("]", "").replace(",", ""));	

		eventRegister = new Hashtable<Integer, Vector<String>>();
		eventRegister.put(EVENT_START, new Vector<String>());
		eventRegister.put(EVENT_INTERFACE_UP, new Vector<String>());
		eventRegister.put(EVENT_INTERFACE_DOWN, new Vector<String>());
		InterfPolicyFactory.setEventRegister(eventRegister);
		
		signalingPolicy = InterfPolicyFactory.getPolicy(null, cfgSignalingPolicy);
		defaultClosedPolicy = InterfPolicyFactory.getPolicy(null, cfgDefaultPolicy);
		closedPolicy = new Hashtable<String, InterfPolicy>();
		for (String app : cfgPolicy.keySet()) closedPolicy.put(app, InterfPolicyFactory.getPolicy(app, cfgPolicy.get(app)));
		socketForApp = new Hashtable<String, Vector<Socket>>();
		closedInterface = new Hashtable<String, String>();
		openedPolicy = new Hashtable<String, InterfPolicy>();
		openedInterface = new Hashtable<String, String>();
		appForSocket = new Hashtable<String, String>();
		
		noUpmtApp = new Vector<String>(Arrays.asList(cfg.noUpmtApp)); //TODO: passare all'appmon
	}

	private void run()
	{
		printLog("Scanning network interface and perform UPMT associations!!!", Log.LEVEL_LOW);

		Hashtable<String, InterfaceInfo> initialInterf = networkMonitor.getInterfaceList();
		if(initialInterf.size() != 0) {
			for (String ifName : initialInterf.keySet()) {
				InterfaceInfo newIf = initialInterf.get(ifName);
				printLog("Detected ifs: " + ifName + " (IP:" + newIf.ipAddress + " - gw:" + newIf.defaultGw+")", Log.LEVEL_HIGH);
				availableIfs.put(ifName, newIf);
				tunnelManager.addInterface(ifName, newIf);
				System.out.println("INITIAL START " + newIf.id);
			}

			//TODO SS here the signaling interface is chosen without checking if the interface 
			//provides connectivity
			signalingInterface = signalingPolicy.getActiveInterf(availableIfs, null, EVENT_START);
			if (signalingInterface != null) {
				signaler.changeDefaultInterface(signalingInterface, availableIfs.get(signalingInterface));
			}

			printLog(signalingInterface == null?"WARNING! No-interf-for-signaling!!!":"Chosen "+signalingInterface+" as signaling interface!\n", Log.LEVEL_HIGH);


			for (String ANAddress : cfgANList) {
				if(associatedANList.size()<maxANNumber) {
					String[] token = ANAddress.split(":");
					addAN(token[0].trim(), Integer.parseInt(token[1].trim()));
				}
			}

			if (signaler.getDefaultAN()!=null) {

				tunnelManager.setDefaultAN(signaler.getDefaultAN());
				
				// for all the applications in the closedPolicy set 
				// it chooses the interface to be used and signals the appMonitor
				for (String appName : closedPolicy.keySet()) {
					InterfPolicy policy = closedPolicy.get(appName);
					String newInterf = policy.getActiveInterf(policy.filter(availableIfs), null, EVENT_START);
	
					appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
					closedInterface.put(appName, newInterf==null?"":newInterf);
				}
	
				defaultClosedInterface = defaultClosedPolicy.getActiveInterf(availableIfs, null, EVENT_START);
				appMonitor.setDefault(defaultClosedInterface==null?TID_DROP:tunnelManager.getTid(defaultClosedInterface, signaler.getDefaultAN()));
	
			} else {
				printLog("No AN contacted!!!", Log.LEVEL_HIGH);
			}

		} else {
			printLog("No network interface detected!!!", Log.LEVEL_HIGH);
		}

		networkMonitor.startListen(this); //per le variazioni di interfacce
		appManager.startListen(this); //per i comandi dati dall'utente
		appMonitor.startListen(this); //per l'apertura o chiusura di applicazioni
		signaler.startListen(this); //per le variazioni di AN (fixed-host o end-to-end)

		printLog("END OF SETUP!!! Start listening event...\n", Log.LEVEL_LOW);
	}
	
	public void stop()
	{
		appMonitor.stop();
		tunnelManager.stop();
		networkMonitor.stop();
		signaler.stop();
		System.exit(0);
	}

	
	/**Adds a new ip interface.
	 * @param ifName the name of the new interface (e.g. eth0, ppp5, etc.).
	 * @param newIf the interface descriptor that contains the local ip address and the ip address of the remote gw for this interface.*/
	private void addInterface(String ifName, InterfaceInfo newIf)
	{
		printLog("Detected ifs: " + ifName + " (IP:" + newIf.ipAddress + " - gw:" + newIf.defaultGw+")", Log.LEVEL_HIGH);
		synchronized(availableIfs)
		{
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
	private void removeInterface(String ifName)
	{
		printLog("Lost ifs: " + ifName, Log.LEVEL_HIGH);
		synchronized(availableIfs)
		{
			if(appManager!=null) appManager.removeInterface(ifName);
			InterfaceInfo oldIf = availableIfs.remove(ifName);
			
			checkAllPolicy(EVENT_INTERFACE_DOWN);

			tunnelManager.removeInterface(ifName, oldIf, associatedANList.keySet());
		}
	}

	private void changeSignalingInterf(String newInterf)
	{
		signalingInterface = newInterf;
		printLog("Chosen " + newInterf + " as default signaling interface!\n", Log.LEVEL_HIGH);
		signaler.changeDefaultInterface(newInterf, availableIfs.get(newInterf));
	}
	
	private void checkAllPolicy(int event)
	{
		if (signalingPolicy.isTriggeredBy(event))
		{
			String newInterf = signalingPolicy.getActiveInterf(availableIfs, signalingInterface, event);
			
			//TODO: not clear when signallingInterface can be 0
			if (!((newInterf==null&&signalingInterface.length()==0) || (newInterf!=null && newInterf.equals(signalingInterface))))
				changeSignalingInterf(newInterf);
		}

		if (defaultClosedPolicy.isTriggeredBy(event))
		{
			String newInterf = defaultClosedPolicy.getActiveInterf(availableIfs, defaultClosedInterface, event);
			if (!((newInterf==null&&defaultClosedInterface.length()==0) || (newInterf!=null && newInterf.equals(defaultClosedInterface))))
			{
				defaultClosedInterface = newInterf;
				appMonitor.setDefault(tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
			}
		}

		for (String appName : eventRegister.get(event))
			checkSinglePolicy(appName, event);

		appManager.onPolicyCheck();
	}
	
	public void checkSinglePolicy(String appName, int event) {
		if (openedPolicy.containsKey(appName)) { //APP OPENED (i.e. it has open sockets)
			InterfPolicy policy = openedPolicy.get(appName);
			String currentInterf = openedInterface.get(appName);
			String newInterf = policy.getActiveInterf(availableIfs, currentInterf, event);
			if ((newInterf==null&&currentInterf.length()==0) || (newInterf!=null && newInterf.equals(currentInterf))) return;

			openedInterface.put(appName, newInterf==null?"":newInterf);
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
			moveAppToInterf(appName, newInterf);
		} else { //APP CLOSED (i.e. with no open sockets)
			InterfPolicy policy = closedPolicy.get(appName);
			String currentInterf = closedInterface.get(appName);
			String newInterf = policy.getActiveInterf(availableIfs, currentInterf, event);
			if ((newInterf==null&&currentInterf.length()==0) || (newInterf!=null && newInterf.equals(currentInterf))) return;

			closedInterface.put(appName, newInterf==null?"":newInterf);
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
		}
	}
	
	private void addFH(String FHAddress, int tsaPort) {
		addAN(FHAddress, tsaPort);
		appMonitor.setAppFlow(FHAddress, tunnelManager.getTid(defaultClosedInterface, FHAddress));
		//TODO: cosi' (currentDefaultAppIf) non ci sono politike verso i fixedHost.
		//Tokkerebbe modificare pesantemente il modulo (perke' all'interno dei pakketti diretti verso il FH bisogna distinguere l'app).
	}
	
	
	/** 
	 * adds an Anchor Node, performing the association Request to obtain the VIPA
	 * endAddAN is called when the association Request is completed and it creates
	 * the tunnels for each interface
	 * @param ANAddress
	 * @param tsaPort
	 */
	private void addAN(String ANAddress, int tsaPort) {
		if(signalingInterface != null) {
			signaler.signalAssociation(ANAddress, tsaPort);
		}
	} 
	
	public void endAddAN(String ANAddress, int tsaPort) {
		if (signaler.getVipaForAN(ANAddress) != null) {
			synchronized(availableIfs) {
				associatedANList.put(ANAddress, tsaPort);
				for (String ifName : availableIfs.keySet()) {
					//creates a tunnel towards the AN for each interface
					long result = tunnelSetup(ifName, ANAddress, tsaPort);
				}
			}
		}
		System.out.println(); //TODO: Per Debug... rimuovere!!
	}

	private void removeAN(String ANAddress) {
		//TODO:
	}

	//kiamato qnd viene aggiunta un interfaccia o un AN
	private long tunnelSetup(String ifName, String ANAddress, int tsaPort) {
		String vipaForThisAN = signaler.getVipaForAN(ANAddress);
		int result = tunnelManager.addTunnel(vipaForThisAN, ifName, ANAddress, tsaPort);
		printLog("LocalTunnelID: " + result,Log.LEVEL_HIGH );
		if (result==0) {
			printLog("ATTENTION: LocalTunnelID should not be 0 ",Log.LEVEL_HIGH );
			return result;
		}
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
		synchronized(socketForApp) {
			for (Socket socket : socketForApp.get(appName)) {
				moveSocketToInterf(socket, ifName);
			}
		}
	}
	
	private void moveSocketToInterf(Socket socket, String ifName)
	{
		//Se ifName e' null vuol dire ke devo bloccare! grande TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		if(ifName==null) return;
		//FORSE (DA TESTARE!!!!) SI PUO' LASCIARE TUTTO COM'E' TANTO IL BLOCCO AVVIENE SOLO IN SEGUITO A RIMOZIONE E QUINDI 
		//NEL MODULO VIENE KIAMATO "DELETE TUN" KE LEVA LE REGOLE DELLA PAFT! INVECE PER IL SERVER TRASMETTERA' SU UN TUNNEL TAPPATO.
		//MI PUO' ANDARE BENE STO COMPORTAMENTO?
		
		tunnelManager.moveSocketToInterf(socket.proto, socket.srcPort, socket.dstIp, socket.dstPort, ifName);
		signaler.handover(socket, getDefaultAN(), ifName);
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
			openedPolicy.put(appName, closedPolicy.remove(appName));
			openedInterface.put(appName, closedInterface.remove(appName));
		} else {
			//InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, tmpDefaultPolicy!=null? tmpDefaultPolicy:cfgDefaultPolicy);//FIXME
			InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, cfgDefaultPolicy);

			openedPolicy.put(appName, policy);
			openedInterface.put(appName, policy.getActiveInterf(availableIfs, null, EVENT_START)); //TODO: sincronizzare availableIfs?
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
	public Vector<String> getInterfacesList() {return new Vector<String>(availableIfs.keySet());}
	public Vector<String> getNoUpmtApp() {return noUpmtApp;}
	public String getVipaFix() {return Module.getVipaFix();}
	public int getConnectedAN() {return associatedANList.size();}



	public void setCurrentPolicy(String appName, String policyVector)
	{
		printLog("change policy for " + appName + " to " + policyVector, Log.LEVEL_HIGH);
		
		for (int event : eventRegister.keySet()) if (openedPolicy.get(appName).isTriggeredBy(event)) eventRegister.get(event).remove(appName);
		InterfPolicy policy = InterfPolicyFactory.getPolicy(appName, policyVector);
		openedPolicy.put(appName, policy);
		
		String currentInterf = openedInterface.get(appName);
		String newInterf = policy.getActiveInterf(availableIfs, currentInterf, EVENT_START);
		if (newInterf.equals(currentInterf)) return;

		openedInterface.put(appName, newInterf);
		appMonitor.setApp(appName, tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
		moveAppToInterf(appName, newInterf);
		appManager.onPolicyCheck();
	}

	
	
	public String getSipID() {return signaler.getSipID();}
	public String getCfgSipID() {return cfg.sipID;}

	//TODO: gestire molteplicita' AN con AN di default
	public String getDefaultAN() {return signaler.getDefaultAN();}
	public String getCfgDefaultAN() {return cfg.ANList.get(0);}
	
	public String getCurrentInterf(String appName) {return openedInterface.get(appName);}
	public String getCurrentInterf(Socket socket) {return openedInterface.get(appForSocket.get(socket.id()));}
	
	
	public String getCurrentPolicy(String appName) {return openedPolicy.get(appName).getDesc();}

	public String getStoredPolicy(String appName) {return cfgPolicy.get(appName);}
	public Hashtable<String, String> getAllStoredPolicy() {return cfgPolicy;}

	public Vector<String> getNoUpmtInterf() {return new Vector<String>(Arrays.asList(cfg.interfToSkip));}
	public String getDefaultPolicy() {return cfgDefaultPolicy;}

	

	
	public void setCfgSipID(String sipID) {cfg.sipID = sipID; cfg.writeTag(Default.sipID_Tag, sipID);}
	public void setDefaultAN(String addr) {cfg.ANList = new Vector<String>(Arrays.asList(new String[]{addr})); cfg.writeTag(Default.ANList_TAG, addr);}


	//TODO: in add e edit ci vuole un controllo ke la policy sia fatta bene (nel senso nome e parametri corretti!)
	public void cfgPolicyAdding(String appName, String policy)
	{
		System.out.println("cfgPolicyAdding: "+appName+" -> "+policy+" (TODO)");

		cfgPolicy.put(appName, policy);
		cfg.writeTag(Default.applicationPolicy_TAG, appName+" "+policy);
		
		if (!openedPolicy.containsKey(appName)) { //APP CLOSED
			InterfPolicy interfPolicy = InterfPolicyFactory.getPolicy(appName, policy);
			closedPolicy.put(appName, interfPolicy);
			String newInterf = interfPolicy.getActiveInterf(availableIfs, null, EVENT_START);
			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
			closedInterface.put(appName, newInterf==null?"":newInterf);
		}
		appManager.onPolicyCheck();
	}

	public void cfgPolicyEdit(String appName, String policy)
	{
		System.out.println("cfgPolicyEdit: "+appName+" -> "+policy+" (TODO)");

		cfgPolicy.put(appName, policy);
		cfg.writeTag(Default.applicationPolicy_TAG, appName+" "+policy);
		
		if (closedPolicy.containsKey(appName)) //APP CLOSED WITH POLICY
		{
			for (int event : eventRegister.keySet()) if (closedPolicy.get(appName).isTriggeredBy(event)) eventRegister.get(event).remove(appName);

			InterfPolicy interfPolicy = InterfPolicyFactory.getPolicy(appName, policy);
			closedPolicy.put(appName, interfPolicy);
			
			String currentInterf = closedInterface.get(appName);
			String newInterf = interfPolicy.getActiveInterf(availableIfs, currentInterf, EVENT_START);
			if (newInterf.equals(currentInterf)) return;

			appMonitor.setApp(appName, newInterf==null?TID_DROP:tunnelManager.getTid(newInterf, signaler.getDefaultAN()));
			closedInterface.put(appName, newInterf==null?"":newInterf);
		}
		appManager.onPolicyCheck();
	}

	public void cfgPolicyRemoval(String appName)
	{
		System.out.println("cfgPolicyRemoval: "+appName+" (TODO)");

		cfgPolicy.remove(appName);
		cfg.deleteTag(Default.applicationPolicy_TAG + "=" + appName);
		
		if (closedPolicy.containsKey(appName)) //APP CLOSED WITH POLICY
		{
			for (int event : eventRegister.keySet()) if (closedPolicy.get(appName).isTriggeredBy(event)) eventRegister.get(event).remove(appName);

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

		defaultClosedInterface = defaultClosedPolicy.getActiveInterf(availableIfs, null, EVENT_START);
		appMonitor.setDefault(defaultClosedInterface==null?TID_DROP:tunnelManager.getTid(defaultClosedInterface, signaler.getDefaultAN()));
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
		logger.println("["+element.getClass().getSimpleName() + "]: " + text, loglevel);
		if (loglevel!=Log.LEVEL_LOW) System.out.println("["+element.getClass().getSimpleName() + "]: " + text);
	}



	//*********************** Main method ***************************
	public static void main(String[] args)
	{
		String file = null;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) printUsageAndQuit();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];

		UPMTClient.start(file);
	}

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
}
