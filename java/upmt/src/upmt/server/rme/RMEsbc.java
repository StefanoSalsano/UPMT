package upmt.server.rme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

import local.sbc.ExtendedSipProvider;
import local.sbc.SessionBorderController;
import local.sbc.SessionBorderControllerProfile;
import local.server.ServerProfile;

import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;

import upmt.Default;
import upmt.client.rme.RMEInterface;
import upmt.client.rme.RoutingCheck;

/**Stub class. It extends the SessionBorderController adding some println for debug use.*/
public class RMEsbc extends SessionBorderController
{
	private static RMEConfigManager RMEcfg;
	public static ArrayList<RMEInterface> rmeAddresses = new ArrayList<RMEInterface>();

	public RMEsbc(ExtendedSipProvider sip_provider, ServerProfile server_profile, SessionBorderControllerProfile sbc_profile)
	{super(sip_provider, server_profile, sbc_profile);
	}

	public void onReceivedMessage(SipProvider provider, Message msg) {
		// System.out.println("\n[" + new Date() + "]\nReceived message! from "
		// + msg.getRemoteAddress() + "\n" + msg.getDump() + msg.getBody() +
		// "\t");
		super.onReceivedMessage(provider, msg);
	}

	/** When a new request message is received for a remote UA */
	public void processRequestToRemoteUA(Message req) {
		//		System.out.println("Handler: processRequestToRemoteUA\n\n");
		super.processRequestToRemoteUA(req);
	}

	/** When a new request message is received for a locally registered user */
	public void processRequestToLocalUser(Message req) {
		//		System.out.println("Handler: processRequestToLocalUser\n\n");
		super.processRequestToLocalUser(req);
	}

	/** When a new response message is received */
	public void processResponse(Message resp) {
		//		System.out.println("Handler: processResponse\n\n");
		super.processResponse(resp);
	}

	/** When a new request request is received for the local server */
	public void processRequestToLocalServer(Message msg) {
		//		System.out.println("Handler: processRequestToLocalServer\n\n");
		super.processRequestToLocalServer(msg);
	}

	@SuppressWarnings({ "unused", "rawtypes", "unchecked" })
	public static void startRMEsbc(String[] args) {
		System.out.println("RMEsbc Up and Running");

		String file = Default.RMESBC_CONFIG_FILE;

		String fileServer = Default.PEER_CONFIG_FILE;
		RMEsbc.RMEcfg = RMEConfigManager.instance();
		RMEcfg.ParseConfig(fileServer);
		rmeAddresses = RMEInterface.parseConfiguration(RMEcfg.rmeNet, RMEcfg.adhocwlan);
		//		stopNetworkManager();
		for(int h=0; h<rmeAddresses.size(); h++) {
			checkRMEAddresses(rmeAddresses.get(h).getRmeInterface(), h);
		}

		boolean memory_debugging = false;

		int first_port=0;
		int last_port=0;

		for(int i=0; i<args.length; i++)
		{
			if (args[i].equals("-f") && args.length>(i+1))
			{
				file = args[++i];
				continue;
			}
			if(args[i].equals("-d"))
			{
				memory_debugging = true;
				continue;
			}
			if(args[i].equals("-m")) // set the local media ports
			{
				first_port = Integer.parseInt(args[++i]);
				last_port = Integer.parseInt(args[++i]);
				continue;
			}
			if(args[i].equals("-h"))
			{
				System.out.println("usage:\n   java SessionBorderController [options]");
				System.out.println("   options:");
				System.out.println("   -f <config_file>   specifies a configuration file");
				System.out.println("   -m <fist_port> <last_port> interval of media ports");
				System.exit(0);
			}
		}

		SipStack.init(file);
		ServerProfile server_profile = new ServerProfile(file);
		SessionBorderControllerProfile sbc_profile = new SessionBorderControllerProfile(file);

		if(first_port>0 && last_port>=first_port)
		{
			Vector media_ports = new Vector();
			for(int i=first_port; i<=last_port; i+=2) media_ports.addElement(new Integer(i));
			sbc_profile.media_ports = media_ports;
		}
		// create a new ExtendedSipProvider
		long keepalive_aggressive_time = (sbc_profile.keepalive_aggressive)? sbc_profile.keepalive_time : 0;

		for(int i=0; i<rmeAddresses.size(); i++) {
			ExtendedSipProvider extended_provider = new ExtendedSipProvider(rmeAddresses.get(i).getIp(), 5066, SipStack.default_transport_protocols,rmeAddresses.get(i).getIp(), sbc_profile.binding_timeout, keepalive_aggressive_time);
			// create and start the SBC
			new RMEsbc(extended_provider,server_profile,sbc_profile);
		}
	}

	@SuppressWarnings({ "unused", "rawtypes", "unchecked" })
	public static void main(String[] args)
	{
		String file = Default.SBC_CONFIG_FILE;

		String fileServer = Default.PEER_CONFIG_FILE;
		RMEsbc.RMEcfg = RMEConfigManager.instance();
		RMEcfg.ParseConfig(fileServer);
		rmeAddresses = RMEInterface.parseConfiguration(RMEcfg.rmeNet, RMEcfg.adhocwlan);
		stopNetworkManager();
		for(int h=0; h<rmeAddresses.size(); h++) {
			checkRMEAddresses(rmeAddresses.get(h).getRmeInterface(), h);
		}

		boolean memory_debugging = false;

		int first_port=0;
		int last_port=0;

		for(int i=0; i<args.length; i++)
		{
			if (args[i].equals("-f") && args.length>(i+1))
			{
				file = args[++i];
				continue;
			}
			if(args[i].equals("-d"))
			{
				memory_debugging = true;
				continue;
			}
			if(args[i].equals("-m")) // set the local media ports
			{
				first_port = Integer.parseInt(args[++i]);
				last_port = Integer.parseInt(args[++i]);
				continue;
			}
			if(args[i].equals("-h"))
			{
				System.out.println("usage:\n   java SessionBorderController [options]");
				System.out.println("   options:");
				System.out.println("   -f <config_file>   specifies a configuration file");
				System.out.println("   -m <fist_port> <last_port> interval of media ports");
				System.exit(0);
			}
		}

		SipStack.init(file);
		ServerProfile server_profile = new ServerProfile(file);
		SessionBorderControllerProfile sbc_profile = new SessionBorderControllerProfile(file);

		if(first_port>0 && last_port>=first_port)
		{
			Vector media_ports = new Vector();
			for(int i=first_port; i<=last_port; i+=2) media_ports.addElement(new Integer(i));
			sbc_profile.media_ports = media_ports;
		}
		// create a new ExtendedSipProvider
		long keepalive_aggressive_time = (sbc_profile.keepalive_aggressive)? sbc_profile.keepalive_time : 0;

		for(int i=0; i<rmeAddresses.size(); i++) {
			ExtendedSipProvider extended_provider = new ExtendedSipProvider(rmeAddresses.get(i).getIp(), 5066, SipStack.default_transport_protocols,rmeAddresses.get(i).getIp(), sbc_profile.binding_timeout, keepalive_aggressive_time);
			// create and start the SBC
			new RMEsbc(extended_provider,server_profile,sbc_profile);
		}

	}

	public static void checkRMEAddresses(String devName, int pos) {
		if (rmeAddresses.get(pos).getRmeInterface().equals(devName)) {
			String ip = rmeAddresses.get(pos).getIp();
			String netmask = rmeAddresses.get(pos).getNetMask();
			if(!ip.equals(getIP(devName))) {
				try {
					Runtime.getRuntime().exec("sudo ifconfig "+devName+" "+ip+" netmask "+netmask);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void stopNetworkManager() {
		try {
			Runtime.getRuntime().exec("sudo service network-manager stop");
		} catch (IOException e) {
			e.printStackTrace();
		}
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

}
