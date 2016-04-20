package upmt.client.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import org.zoolu.tools.Configurable;
import org.zoolu.tools.Configure;
import org.zoolu.tools.Log;
import org.zoolu.tools.Parser;

import upmt.Default;
import upmt.client.UPMTClient;

public class ConfigManager implements Configurable
{
	private static ConfigManager instance;
	private ConfigManager(){}
	private String file;

	public static ConfigManager instance() {if (instance == null) instance = new ConfigManager(); return instance;}

	public void ParseConfig(String file)
	{
		//#ifdef ANDROID
		//		file = "/sdcard/upmt/" + file;
		//#endif
		this.file = file;
		new Configure(this, file);
	}

	//Module
	public String vipaFix = Default.vipaFix;
	public int mtuOverride = Default.mtuOverride;

	//Logger
	/** Specifies log level. <br> Possible values are 0 (little detailed log level),
	 * 1 (quite detailed log level) and 2 (very detailed log level). Default value is 2 </br> */
	public int logLevel = Default.logLevel;
	/** Specifies the log filename. If it is not specified or file cannot be opened, standard output is used for logging. */
	public String logFile = Default.logFile;

	//TunnelManager
	public int rtTablesIndex = Default.rtTablesIndex;
	public String noUpmtMark = Default.noUpmtMark;
	public int startPort = Default.startPort;
	public int portRange = Default.portRange;
	public int extendedFilter = Default.extended_filter; 

	//Signaler
	public String sipID = null;
	public int sipTunneledPort = Default.portForTunnelledSip;
	public int sbcSipPort = Default.upmtServerPort;
	public int keepalivePeriod = Default.CONFIG_KEEPALIVE_PERIOD;
	public int keepaliveTimeout = 0;
	public boolean keepaliveKernel = false;

	//NetworkMonitor
	public String networkMonitor = Default.network_monitor;
	public String[] interfToSkip = Default.skipIF;

	//ApplicationManager
	public String applicationManager = Default.app_manager;

	//ApplicationManager
	public String applicationMonitor = Default.app_monitor;

	public int extFilter = Default.extended_filter;

	//Anchor node
	public int anNumber = Default.maxANNumber;
	public Vector<String> ANList = new Vector<String>();

	//Anchor Node Broker

	public Vector<String> ANBrokerList = new Vector<String>();

	//Policy
	public Vector<String> defaultAppPolicy = new Vector<String>(Arrays.asList(Default.default_app_policy));
	public Vector<String> signalingPolicy = null;
	/**
	 * hashtables of policies for each application as read by the configuration file
	 */
	public Hashtable<String, Vector<String>> applicationPolicy = new Hashtable<String, Vector<String>>();
	public String[] noUpmtApp = Default.no_upmt_app;

	//Radio Multiple Eterogenee
	public String[] rmeNet = null;
	public String[] adhocwlan = null;
	public HashMap<String, ArrayList<String>> rmeApplicationPolicy = new HashMap<String, ArrayList<String>>();
	public String vepa = null;
	public int ddsQosPort;
	public ArrayList<String> olsrdConf = new ArrayList<String>();
	public boolean interfaceBalance = false;


	public void parseLine(String line)
	{
		int index = line.indexOf("=");
		String attribute = index>0 ? line.substring(0, index).trim() : line;
		Parser par = index>0 ? new Parser(line, index+1) : new Parser("");

		//Module
		if (attribute.equals(Default.vipaFix_TAG)) vipaFix = par.getString();
		if (attribute.equals(Default.mtu_override_TAG)) mtuOverride = par.getInt();

		//Logger
		else if (attribute.equals(Default.logLevel_TAG)) logLevel = par.getInt();
		else if (attribute.equals(Default.logFile_TAG)) logFile = par.getString();

		//TunnelManager
		else if (attribute.equals(Default.startPortTAG)) startPort = par.getInt();
		else if (attribute.equals(Default.portRangeTAG)) portRange = par.getInt();
		else if (attribute.equals(Default.noUpmtMarkTAG)) noUpmtMark = par.getString();
		else if (attribute.equals(Default.rtTablesIndex_TAG)) rtTablesIndex = par.getInt();	

		//Signaler
		else if (attribute.equals(Default.sipID_Tag)) sipID = par.getString();
		else if (attribute.equals(Default.portForTunnelledSipTag)) sipTunneledPort = par.getInt();
		else if (attribute.equals(Default.upmtServerPortTag)) sbcSipPort = par.getInt();
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_PERIOD_TAG)) keepalivePeriod = par.getInt();
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_TIMEOUT)) keepaliveTimeout = par.getInt();
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_KERNEL)) {
			String kernelkeepalive = par.getString();
			if(kernelkeepalive.equalsIgnoreCase("yes")) {
				keepaliveKernel = true;
			}
		}

		//NetworkMonitor
		else if (attribute.equals(Default.network_monitor_TAG)) networkMonitor = par.getString();
		else if (attribute.equals(Default.no_upmt_interf_TAG)) interfToSkip = par.getWordArray(Default.delim);

		//ApplicationManager
		else if (attribute.equals(Default.app_manager_TAG)) applicationManager = par.getString();

		//ApplicationMonitor
		else if (attribute.equals(Default.app_monitor_TAG)) applicationMonitor = par.getString();
		else if (attribute.equals(Default.ext_filter_TAG)) extendedFilter = par.getInt();

		//Anchor node
		else if (attribute.equals(Default.maxANNumber_TAG)) anNumber = par.getInt();
		else if (attribute.equals(Default.ANList_TAG)) ANList = par.getWordVector(Default.delim);

		//Anchor node Broker List
		else if (attribute.equals(Default.ANBrokerList_TAG)) ANBrokerList = par.getWordVector(Default.delim);

		//Policy
		else if (attribute.equals(Default.no_upmt_app_TAG)) noUpmtApp = par.getWordArray(Default.delim);
		else if (attribute.equals(Default.default_app_policy_TAG)) defaultAppPolicy = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.signaling_policyTAG)) signalingPolicy = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.applicationPolicy_TAG))
		{
			Vector<String> policy = par.getWordVector(Default.delim);
			applicationPolicy.put(policy.remove(0), policy);
		}

		//RME
		else if (attribute.equals(Default.rme_app_policy_TAG)) {
			ArrayList<String> policy = par.getWordArrayList(Default.delim);
			rmeApplicationPolicy.put(policy.remove(0), policy);
		}
		else if (attribute.equals(Default.vepa_TAG)) vepa = par.getString();
		else if (attribute.equals(Default.rmeNet_TAG)) rmeNet = par.getWordArray(Default.delim);
		else if (attribute.equals(Default.rmeOlsrd_TAG)) {
			String olsrdSinglePath = par.getString();
			olsrdConf.add(olsrdSinglePath);
		}
		else if(attribute.equals(Default.adhocwlan_TAG)) adhocwlan = par.getWordArray(Default.delim);
		else if(attribute.equals(Default.ddsQosPort_TAG)) ddsQosPort = Integer.parseInt(par.getString());
		else if(attribute.equals(Default.interfaceBalance_TAG)) {
			String balance = par.getString();
			if(balance.equalsIgnoreCase("yes")) {
				interfaceBalance = true;
			}
		}
	}

	//TODO: con i nomi delle variabili fatte bene si potrebbe usare la reflection invece di usare write e poi mettere il campo a mano (vedi "UPMTClient.start()")
	public void writeTag(String tag, String value) //value come stringa va bene anke nel caso array xk� si pu� usare una stringa cn gli spazi.
	{
		try
		{
			File inFile = new File(file), outFile = new File(file+"-new");
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
			PrintWriter out = new PrintWriter(new FileOutputStream(outFile));

			String row = tag + "=" + value;
			if (tag.equals(Default.app_policy_TAG)) tag = tag + "=" + value.split(" ",2)[0];

			boolean found = false;
			for(String line = in.readLine();line != null;line = in.readLine())
				if (line.startsWith(tag))
				{
					out.println(row);
					found = true;
				}
				else out.println(line);
			if (!found) out.println(row);

			out.flush(); out.close(); in.close();
			inFile.delete();
			outFile.renameTo(inFile);

			printLog("Writing in the config file:\n"+row, Log.LEVEL_HIGH);
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
	}

	public void deleteTag(String tag) {
		try {
			File inFile = new File(file), outFile = new File(file+"-new");
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
			PrintWriter out = new PrintWriter(new FileOutputStream(outFile));

			for(String line = in.readLine();line != null;line = in.readLine())
				if (!line.startsWith(tag)) out.println(line);

			out.flush(); out.close(); in.close();
			inFile.delete();
			outFile.renameTo(inFile);

			printLog("Removing in the config file:\n"+tag, Log.LEVEL_HIGH);
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
	}

	// ****************************** Logs *****************************
	private void printLog(String text, int loglevel) {UPMTClient.printGenericLog(this, text, loglevel);}
}
