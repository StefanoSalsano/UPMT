package upmt.server.gui;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import local.sbc.ExtendedSipProvider;
import local.sbc.SessionBorderControllerProfile;
import local.server.ServerProfile;

import org.zoolu.net.IpAddress;
import org.zoolu.sip.provider.SipStack;

import upmt.Default;
import upmt.os.Module;
import upmt.os.Shell;
import upmt.server.UPMTServer;
import upmt.server.UPMTsbc;

public class Gui {

	private static UPMTsbc uSbc = null;
	private static UPMTServer uServer = null;
	private static Boolean start = false;



	public Gui(){}
	
	public static void main(String[] args)
	{
		Gui g = new Gui();
		g.start();
	}

	public void start(){
		System.out.println("initializing Gui " + Default.SERVER_CONFIG_FILE);
		
		byte[] bo = new byte[100];
		String[] cmd = {"bash", "-c", "echo $PPID"};
		Process p;
		try {
			p = Runtime.getRuntime().exec(cmd);
			p.getInputStream().read(bo);

			System.out.println("Gui pid: " + new String(bo));

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//SBC
		String file = Default.SBC_CONFIG_FILE;
		File f = new File(Default.SERVER_CONFIG_FILE);
		System.out.println("config file absolute path " + f.getAbsolutePath());

		int first_port=0;
		int last_port=0;

		SipStack.init(file);
		ServerProfile server_profile = new ServerProfile(file);
		SessionBorderControllerProfile sbc_profile = new SessionBorderControllerProfile(file);

		if(first_port>0 && last_port>=first_port)
		{
			Vector<Integer> media_ports = new Vector<Integer>();
			for(int i=first_port; i<=last_port; i+=2) media_ports.addElement(new Integer(i));
			sbc_profile.media_ports = media_ports;
		}
		// create a new ExtendedSipProvider
		long keepalive_aggressive_time = (sbc_profile.keepalive_aggressive)? sbc_profile.keepalive_time : 0;
		ExtendedSipProvider extended_provider = new ExtendedSipProvider(file,sbc_profile.binding_timeout,keepalive_aggressive_time);

		// create and start the SBC
		uSbc = new UPMTsbc(extended_provider,server_profile,sbc_profile);
		
		//SERVER
		uServer = new UPMTServer(Default.SERVER_CONFIG_FILE);
		
		synchronized(start){
			start = true;
		}
	}
	

	public HashMap<String, AssociationInfo> getAssociations(){
		if(uServer != null)
			return uServer.getAssociations();
		else
			return null;
	}

	public HashMap<Integer, TunnelInfo> getTunnels(){
		if(uServer != null)
			return uServer.getTunnels();
		else
			return null;
	}
	
	public HashMap<String, Long> getRegisteredANs(){
		if(uServer != null)
			return uServer.getRegisteredANs();
		else
			return null;
	}

	public String reset(){
		String res = Module.upmtconf(new String[]{"-f", "all"});
		if(uServer != null){
			uServer.clearTunnels();
			uServer.clearAssociations();
			uServer.clearRegisteredANs();
		}
		start();
		return res;
	}
	
	public String raw(){
		
		String res =  "<br>";
		
		String[] tun = Shell.executeCommand(new String[] {"upmtconf", "-l", "tun"}).split("\n");	
		tun[0] = "upmtconf -l tun";
		tun[1] = "";
		
		//TABLE 1
		if(tun.length > 3)
			tun[2] = "";
		res += "\n<table border=\"1\">\n";
				
		for(String t : tun){
			res += "<tr>";
			t = t.replaceAll((char)9+"", " ");
			t = t.replaceAll((char)13+"", " ");
			t = t.replaceAll((char)32+"", " ");
			t = t.replaceAll("   ", " ");
			t = t.replaceAll("  ", " ");
			
			String [] cell = null;
			
			/*byte[] b = t.getBytes();
			for(int i = 0; i < b.length; i++){
				System.out.println((int)b[i] + "->" + (char)b[i]);
			}*/
				
			if(t.indexOf(" ") != -1)
				cell = t.split(" ");
			else if(t.indexOf("\t") != -1)
				cell = t.split("\t");
			else
				cell = t.split(" ");

			if(cell.length == 8){
				for(String c : cell)
					if(!c.equals(""))
						res += "<td>"+ c + "</td>";
			}
			else
				res += t;

			res += "</tr>\n";
		}
		
		if(tun.length > 3)
			res += "</table><br><br>\n";
		//TABLE 1 END
		
		String[] rule= Shell.executeCommand(new String[] {"upmtconf", "-l", "rule"}).split("\n");
		rule[0] = "upmtconf -l rule";
		rule[1] = "";
		
		//TABLE 2
		if(rule.length > 3)
			rule[2] = "";
		res += "<table border=\"1\">\n";				
		
		
		for(String r : rule){
			res += "<tr>";
			r = r.replaceAll((char)9+"", " ");
			r = r.replaceAll((char)13+"", " ");
			r = r.replaceAll((char)32+"", " ");
			r = r.replaceAll("   ", " ");
			r = r.replaceAll("  ", " ");
			
			String [] cell = null;
				
			if(r.indexOf(" ") != -1)
				cell = r.split(" ");
			else if(r.indexOf("\t") != -1)
				cell = r.split("\t");
			else
				cell = r.split(" ");

			if(cell.length == 8){
				for(String c : cell)
					if(!c.equals(""))
						res += "<td>"+ c + "</td>";
			}
			else
				res += r;

			res += "</tr>\n";
		}
		
		
		
		if(rule.length > 3)
			res += "</table>\n";
		//TABLE 2 END		
		
		res += "<br><br>";

		
		String[] tsa= Shell.executeCommand(new String[] {"upmtconf", "-l", "tsa"}).split("\n");
		tsa[0] = "upmtconf -l tsa";
		tsa[1] = "";
		
		//TABLE 3
		if(tsa.length > 3)
			tsa[2] = "";
		res += "<table border=\"1\">\n";				
		
		
		for(int i = 0; i < tsa.length ; i++){
			res += "<tr>";
			if(i == 0){
				res += tsa[i];
				continue;
				}
			
			String r = tsa[i];
			
			r = r.replaceAll((char)9+"", " ");
			r = r.replaceAll((char)13+"", " ");
			r = r.replaceAll((char)32+"", " ");
			r = r.replaceAll("   ", " ");
			r = r.replaceAll("  ", " ");
			
			String [] cell = null;
				
			if(r.indexOf(" ") != -1)
				cell = r.split(" ");
			else if(r.indexOf("\t") != -1)
				cell = r.split("\t");
			else
				cell = r.split(" ");

			//if(cell.length == 8){
				for(String c : cell)
					if(!c.equals(""))
						res += "<td>"+ c + "</td>";
			//}
			//else
			//	res += r;

			res += "</tr>\n";
		}
		
		
		
		if(tsa.length > 3)
			res += "</table>\n";
		//TABLE 3 END		
		res += "<br><br>";

		
		String[] dev= Shell.executeCommand(new String[] {"upmtconf", "-l", "dev"}).split("\n");
		dev[0] = "upmtconf -l dev";
		dev[1] = "";
		
		//TABLE 3
		if(dev.length > 3)
			dev[2] = "";
		res += "<table border=\"1\">\n";				
		
		
		for(int i = 0; i < dev.length ; i++){
			res += "<tr>";
			if(i == 0){
				res += dev[i];
				continue;
				}
			
			String r = dev[i];
			
			r = r.replaceAll((char)9+"", " ");
			r = r.replaceAll((char)13+"", " ");
			r = r.replaceAll((char)32+"", " ");
			r = r.replaceAll("   ", " ");
			r = r.replaceAll("  ", " ");
			
			String [] cell = null;
				
			if(r.indexOf(" ") != -1)
				cell = r.split(" ");
			else if(r.indexOf("\t") != -1)
				cell = r.split("\t");
			else
				cell = r.split(" ");

			//if(cell.length == 8){
				for(String c : cell)
					if(!c.equals(""))
						res += "<td>"+ c + "</td>";
			//}
			//else
			//	res += r;

			res += "</tr>\n";
		}
		
		
		
		if(dev.length > 3)
			res += "</table>\n";
		//TABLE 3 END
	
		res += "<br>";
		
		return res;
	}
	
	public String flush(){
		
		String res = "";
		
		res = res + Shell.executeCommand(new String[] {"upmtconf", "-f", "tun"}) + "<br>";
		res = res + Shell.executeCommand(new String[] {"upmtconf", "-f", "rule"}) + "<br>";
	
		return res;
	}
	
	public static boolean isRunning(){
		synchronized(start){
			return start;
		}
	}
	
	public boolean getIsAN(){
		return uServer.getIsAN();
	}
	
	public boolean getIsBroker(){
		return uServer.getIsBroker();
	}
	
	public String getMyIP(){
		return IpAddress.getLocalHostAddress().toString();
	}
	
	public int getTSAPort(){
		return uServer.getTsaPort();
	}
	
	
	public void stop(){
		
		String res = "";
		res = res + Shell.executeCommand(new String[] {"upmtconf", "-f", "all"}) + "<br>";
		uServer.stop();
				
		synchronized(start){
			start = false;
		}
		
		synchronized(start){
			start = false;
		}
	}

}
