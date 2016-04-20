package upmt;

import java.util.Arrays;

public class Default //TODO: refactorare tutto x mettere i nomi maiuscoli e con la classe ke li usa
{
	/** The separator char for config list*/
	public static final char[] delim={' ', ','};

	/** The accepted form for "yes" in boolean value*/
	private static final String[] yes = new String[]{"yes", "y", "true"};

	public static boolean isYes(String param){
		return Arrays.asList(yes).contains(param);
		}


	//#ifndef ANDROID
	public static final String CLIENT_CONFIG_FILE = "cfg/client/client.cfg";
	//#else
//	public static final String CLIENT_CONFIG_FILE = "cfg/client/client_android.cfg";
	//#endif
	public static final String SBC_CONFIG_FILE = "cfg/server/sbc.cfg";
	public static final String SERVER_CONFIG_FILE = "cfg/server/server.cfg";
	public static final String RT_TABLE_PATH = "/etc/iproute2/rt_tables";
	
	//RME
	public static final String RMECLIENT_CONFIG_FILE = "cfg/client/RMEclient.cfg";
	public static final String RMESBC_CONFIG_FILE = "cfg/server/RMEsbc.cfg";
	public static final String RMESERVER_CONFIG_FILE = "cfg/server/RMEserver.cfg";
	public static final String  PEER_CONFIG_FILE = "cfg/peer/peer.cfg";



	/****************************************************************************************/
	/*****************************************SERVER*****************************************/
	/****************************************************************************************/
	public static final String SERVER_VIPA_TAG = "server_vipa";
	public static final String SERVER_VIPA = "1.1.1.1";

	public static final String SERVER_IFNAME_TAG = "server_iface";
	public static final String SERVER_IFNAME = "eth0";

	public static final String SERVER_TSA_PORT_TAG = "server_ifaces_and_ports";
	public static final int SERVER_TSA_PORT = 50000;

	public static final String SERVER_MARK_TAG = "server_mark";
	public static final int SERVER_MARK = 7;

	public static final String SERVER_VIPA_MANAGER_POLICY_TAG = "vipa_manager_policy";
	public static final String SERVER_VIPA_MANAGER_POLICY = "Config";
	
	public static final String SERVER_SIP_PORT_TAG = "host_port";
	public static final int SERVER_SIP_PORT = 5060;

	public static final String SERVER_IDENTIFIER_TAG = "anchor_node_identifier";

	
	public static final String SERVER_MAX_MH_TAG = "Max_MHs_allowed";
	
	public static final String BLACK_LIST_TAG = "Black_List";
	
	public static final String CONFIG_VIPA_MANAGER_START_ADDRESS_TAG = "vipa_start_addr";
	public static final String CONFIG_VIPA_MANAGER_START_ADDRESS = "1.2.3.100";

	public static final String CONFIG_VIPA_MANAGER_END_ADDRESS_TAG = "vipa_end_addr";
	public static final String CONFIG_VIPA_MANAGER_END_ADDRESS = "1.2.3.255";
	
	public static final String CONFIG_KEEPALIVE_PERIOD_TAG = "keepalive_period";
	public static final int CONFIG_KEEPALIVE_PERIOD = 10000;
	// RME
	public static final String CONFIG_KEEPALIVE_KERNEL = "keepalive_kernel";
	public static final String CONFIG_KEEPALIVE_TIMEOUT = "keepalive_timeout";



	/****************************************************************************************/
	/*****************************************CLIENT*****************************************/
	/****************************************************************************************/
	
	
	////////////////////////////////////////////
	//////////////////UPMTClient////////////////
	////////////////////////////////////////////
	private static final String sipConfigFile = "sip_config_path"; //TODO: vedi UPMTClient e separazione file di config.
	private static final String virtualSipPortStart = "virtual_reserved_port_for_sip_start"; //TODO: hardcodati?
	private static final String virtualSipPortRange = "virtual_reserved_port_for_sip_range";

	//Anchor Node
	public static final String ANList_TAG = "anchor_node_list"; //No default

	public static final String maxANNumber_TAG = "anchor_node_limit";
	public static final int maxANNumber = 2;
	
	//Anchor Node Broker
	public static final String ANBrokerList_TAG = "anchor_node_broker_list";
	public static final String isAN_TAG = "is_an";
	public static final String isBroker_TAG = "is_broker";
	public static final String isFH_TAG = "is_fh";
	
	//Radio Mulitple Eterogenee
	public static final String rme_app_policy_TAG = "rme_app_policy";
	public static final String vepa_TAG = "rme_vepa";
	public static final String rmeNet_TAG = "rme_net";
	public static final String rmeOlsrd_TAG = "rme_olsrd";
	public static final String rmeTsa_TAG = "rme_tsa";
	public static final String adhocwlan_TAG = "adhoc_wlan";
	public static final String ddsQosPort_TAG = "dds_qos_port";
	public static final String interfaceBalance_TAG = "interface_balance";



	//Logger
	public static final String logFile_TAG = "log_file";
	public static final String logFile = "log/client.log";

	public static final String logLevel_TAG = "log_level";
	public static final int logLevel = 2;


	//Policy
	public static final String no_upmt_app_TAG = "no_upmt_app";
	public static final String[] no_upmt_app = {};
	
	public static final String default_app_policy_TAG = "default_app_policy";
	public static final String[] default_app_policy = {"Random"}; //{"Customizable", "eth0", "wlan0"}
	
	public static final String signaling_policyTAG = "signaling_policy"; //default: same-of-default_app_policy
	
	public static final String app_policy_TAG = "app_policy";
	public static final String applicationPolicy_TAG = "app_policy";


	//UPMTClient-->NetworkMonitor
	public static final String no_upmt_interf_TAG = "no_upmt_ifs";
	public static final String[] skipIF = {};
	
	public static final String network_monitor_TAG = "net_monitor";
//#ifndef ANDROID
	public static final String network_monitor = "LinuxDBus";
//#else
//	public static final String network_monitor = "Android";
//#endif
	
	//UPMTClient-->Module
	public static final String vipaFix_TAG = "vipa_fix";
	public static final String vipaFix = "5.6.7.8";

	public static final String mtu_override_TAG = "mtu_override";
	public static final int mtuOverride=0;

	
	//Application Manager
	public static final String app_manager_TAG = "app_manager";
	public static final String app_manager = "GUI";

	//Application Monitor
	public static final String app_monitor_TAG = "app_monitor";
	public static final String app_monitor = "Default";

	public static final String ext_filter_TAG = "extended_filter";
	public static final int extended_filter = 0; 

	////////////////////////////////////////////
	///////////////SipSignalManager/////////////
	////////////////////////////////////////////
	public static final String sipID_Tag = "sip_id_name";
	
	public static final String upmtServerPortTag = "upmt_server_port";
	public static final int upmtServerPort = 5066;
	
	public static final String portForTunnelledSipTag = "port_for_tunnelled_sip";
	public static final int portForTunnelledSip = 40000;


	

	////////////////////////////////////////////
	////////////////TunnelManager///////////////
	////////////////////////////////////////////
	public static final String startPortTAG = "start_port";
	public static final int startPort = 50000;
	
	public static final String portRangeTAG = "range_port";
	public static final int portRange = 100;
	
	public static final String noUpmtMarkTAG = "no_upmt_packet_mark";
	public static final String noUpmtMark = "0xfafafafa";


	public static final String rtTablesIndex_TAG = "rt_tables_index";
	public static final int rtTablesIndex = 100;



	/****************************************************************************************/
	/***************************************FIXED_HOST***************************************/
	/****************************************************************************************/
	public static final String FH_VIPA_TAG = "fixed_host_vipa";
	public static final String FH_VIPA = "1.1.1.1";

	public static final String FH_IFNAME_TAG = "fixed_host_iface";
	public static final String FH_IFNAME = "eth0";

	public static final String FH_TSA_PORT_TAG = "fixed_host_tsa_port";
	public static final int FH_TSA_PORT = 50000;

	public static final String FH_NSA_PORT_TAG = "fixed_host_nsa_port";
	public static final int FH_NSA_PORT = 80;

	public static final String FH_MARK_TAG = "fixed_host_mark";
	public static final int FH_MARK = 7;

	public static final String FH_FLOW_MONITOR_TAG = "fixed_host_monitor";
	public static final String FH_FLOW_MONITOR = "Default";

	public static final String FH_AN_LIST = "anchor_node_list";
	
	//to change in FH_VIPA_RANGE (fh_vipa_range = 1.2.3.100:1.2.3.255)
	public static final String FH_VIPA_RANGE_TAG = "fh_vipa_range";
	public static final String FH_VIPA_RANGE_START = "1.2.3.100";
	public static final String FH_VIPA_RANGE_END = "1.2.3.255";
}
