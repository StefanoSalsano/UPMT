package upmt.fixedHost.tunnel;

import upmt.os.Module;
import upmt.os.Shell;

public class TunnelManager
{
	public TunnelManager(int tsaPort, int nsaPort, int mark)
	{
		String result;
		if(Shell.executeCommand(new String[]{"sh", "-c", "ifconfig -a | grep upmt0"}).length()>0)
			result = Shell.executeCommand(new String[]{"modprobe", "-r", "upmt"});

		result = Shell.executeCommand(new String[]{"modprobe", "upmt"});
		result = Shell.executeCommand(new String[]{"ifconfig", "upmt0", Module.getVipaFix()});

		Module.upmtconf(new String[]{"-a", "dev", "-i", Module.getServerIfName()});
		Module.upmtconf(new String[]{"-a", "tsa", "-i", Module.getServerIfName(), "-l", ""+tsaPort});

		//TODO mettere NSA!!!!!!!!!!
		/*
		Module.upmtconf(new String[]{"-a", "tsa", "-i", Module.getServerIfName(), ?"-lettera"?, ""+nsaPort});
		Module.upmtconf(new String[]{"-m", "an", "-M", ""+mark});
		result = Shell.executeCommand(new String[]{"sh", "-c", "echo 1 > /proc/sys/net/ipv4/ip_forward"});
			
		result = Shell.executeCommand(new String[]{"iptables", "-D", "POSTROUTING", "-t", "nat", "-m", "mark", "--mark", ""+mark, "-j", "MASQUERADE"});
		result = Shell.executeCommand(new String[]{"iptables", "-A", "POSTROUTING", "-t", "nat", "-m", "mark", "--mark", ""+mark, "-j", "MASQUERADE"});
		*/
	}

	public int getTunnelID(String MhVipa, int applicationPort)
	{
		String resp = Module.upmtconf(new String[]{"-l","rule"});
		String[] rules = Module.getEntryList(resp);
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
}
