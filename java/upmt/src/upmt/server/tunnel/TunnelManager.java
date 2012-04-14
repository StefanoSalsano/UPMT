package upmt.server.tunnel;

import java.util.Vector;

import upmt.os.Module;
import upmt.os.Shell;

public class TunnelManager
{
	public TunnelManager(Vector<String> tsas, int serverMark)
	{
		String result;
		if(Shell.executeCommand(new String[]{"sh", "-c", "ifconfig -a | grep upmt0"}).length()>0)
			result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt"});

		result = Shell.executeCommand(new String[]{"modprobe", "upmt"});
		result = Shell.executeCommand(new String[]{"ifconfig", "upmt0", Module.getVipaFix()});
		//System.out.println("Starting server on interface: " + Module.getServerIfName());
		//result = Module.upmtconf(new String[]{"-a", "dev", "-i", Module.getServerIfName()});
		//result = Module.upmtconf(new String[]{"-a", "tsa", "-i", Module.getServerIfName(), "-l", ""+tsaPort});
		
		for(int i = 0; i < tsas.size(); i++){
			result = Module.upmtconf(new String[]{"-a", "dev", "-i", tsas.get(i).split(":")[0]});
			if(result.contains("Device does not exist")){
				System.err.println("Executing command:\nupmtconf -a dev -i "+ tsas.get(i).split(":")[0]);
				System.err.println(result);
				System.err.println("BAD Configuration. Stopping server.");
				System.exit(0);
			}
			
			result = Module.upmtconf(new String[]{"-a", "tsa", "-i", tsas.get(i).split(":")[0], "-l", ""+tsas.get(i).split(":")[1]});
			//System.out.println(result);	
		}
		
		result = Module.upmtconf(new String[]{"-m", "an", "-M", ""+serverMark});

		result = Shell.executeCommand(new String[]{"sh", "-c", "echo 1 > /proc/sys/net/ipv4/ip_forward"});
		result = Shell.executeCommand(new String[]{"iptables", "-D", "POSTROUTING", "-t", "nat", "-m", "mark", "--mark", ""+serverMark, "-j", "MASQUERADE"});
		result = Shell.executeCommand(new String[]{"iptables", "-A", "POSTROUTING", "-t", "nat", "-m", "mark", "--mark", ""+serverMark, "-j", "MASQUERADE"});
	}

	public int getTunnelID(String MhVipa, int applicationPort)
	{
		String resp = Module.upmtconf(new String[]{"-l","rule"});
		//System.out.println(resp);
		String[] rules = Module.getEntryList(resp);
		//System.out.println("Rules length: " + rules.length);
		for (String rule : rules)
		{
			String[] ruleTokens = rule.split("[ \t]+");
			if(ruleTokens[4].equals(MhVipa) && ruleTokens[5].equals(applicationPort+""))
				return Integer.parseInt(ruleTokens[6]);
		}
		return -1;
	}

	public String getTunnelEndAddress(int tunnelID)
	{
		String resp = Module.upmtconf(new String[]{"-l","tun"});
		String[] tunnels = Module.getEntryList(resp);

		for (String tunnel : tunnels)
		{
			String[] tunnelTokens = tunnel.split("[ \t]+");
			if(tunnelTokens[0].equals(tunnelID+"")) return tunnelTokens[3] + ":" + tunnelTokens[4];
		}
		return null;
	}

	public void assignSocketToTunnel(String proto, String srcIp, int srcPort, String destIp, int destPort, int tid)
	{
		String[] param = new String[]{"-a","rule","-p",proto,"-s",srcIp,"-d",destIp,"-l",""+srcPort,"-r",destPort+"","-n",""+tid};
		String res = Module.upmtconf(param);
	}
	
	
	
	public int removeTunnel(int tid) {
			
		System.out.println("removing local tunnel " + tid);
		String[] par = new String[]{"-x", "tun", "-n", ""+tid};
		Module.upmtconf(par);

		return 0;
	}
}
