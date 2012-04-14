package upmt.fixedHost;

import java.util.Hashtable;

import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.tools.Configurable;
import org.zoolu.tools.Configure;
import org.zoolu.tools.Log;
import org.zoolu.tools.Parser;

import upmt.Default;
import upmt.fixedHost.tunnel.TunnelManager;
import upmt.fixedHost.vipa.monitor.FlowMonitor;
import upmt.fixedHost.vipa.monitor.FlowMonitorFactory;
import upmt.fixedHost.vipa.monitor.FlowMonitorListener;
import upmt.os.Module;
import upmt.signaling.BaseUpmtEntity;
import upmt.signaling.UpmtSignal;
import upmt.signaling.message.AssociationReq;
import upmt.signaling.message.AssociationResp;
import upmt.signaling.message.HandoverReq;
import upmt.signaling.message.HandoverResp;
import upmt.signaling.message.SetHandoverModeReq;
import upmt.signaling.message.SetHandoverModeResp;
import upmt.signaling.message.TsaBinding;
import upmt.signaling.message.TunnelSetupReq;
import upmt.signaling.message.TunnelSetupResp;

public class UPMTFixedHost extends BaseUpmtEntity implements Configurable, FlowMonitorListener
{
	private Hashtable<String,String> srcToVipa;

	private FlowMonitor flowMonitor;
	private String flowMonitorImpl = Default.FH_FLOW_MONITOR;
	private String vipaRangeStart = Default.FH_VIPA_RANGE_START;
	private String vipaRangeEnd = Default.FH_VIPA_RANGE_END;

	private TunnelManager tunnelManager;
	private int tsaPort = Default.FH_TSA_PORT;
	private int mark = Default.FH_MARK;
	private int nsaPort = Default.FH_NSA_PORT;

	public UPMTFixedHost(String file)
	{
		super(file);
		new Configure(this, file);		
		flowMonitor = FlowMonitorFactory.getFlowMonitor(flowMonitorImpl);
		flowMonitor.setVipaRange(vipaRangeStart, vipaRangeEnd);
		flowMonitor.startListen(this);
		tunnelManager = new TunnelManager(tsaPort, nsaPort, mark);
	}
	public void parseLine(String line)
	{
		int index = line.indexOf("=");
		String attribute = index>0 ? line.substring(0, index).trim() : line;
		Parser par = index>0 ? new Parser(line, index+1) : new Parser("");

		//FlowMonitor
		if (attribute.equals(Default.FH_FLOW_MONITOR_TAG)) flowMonitorImpl = par.getString();
		else if (attribute.equals(Default.FH_AN_LIST)) for (String ANAddress : par.getWordArray(Default.delim)) flowMonitor.addAN(ANAddress);
		else if (attribute.equals(Default.FH_VIPA_RANGE_TAG))
		{
			String[] token = par.getString().split(":");
			vipaRangeStart= token[0].trim();
			vipaRangeEnd = token[1].trim();
		}

		//TunnelManager
		else if (attribute.equals(Default.FH_TSA_PORT_TAG)) tsaPort = par.getInt();
		else if (attribute.equals(Default.FH_NSA_PORT_TAG)) nsaPort = par.getInt();
		else if (attribute.equals(Default.FH_MARK_TAG)) mark = par.getInt();

		//Module
		else if (attribute.equals(Default.FH_VIPA_TAG)) Module.setVipaFix(par.getString());
		else if (attribute.equals(Default.FH_IFNAME_TAG)) Module.setServerIfName(par.getString());
	}
	protected void printLog(String str, int level){System.out.println("UPMTServer: "+str); super.printLog("UPMTServer: "+str, level);}
	protected void printLog(String str){printLog(str, Log.LEVEL_LOWER);}

	public void flowOpened(String srcAddress, int srcPort, String vipa, int vipaPort)
	{
		printLog("Starting MH_2_FH procedure by AN:" + srcAddress, Log.LEVEL_MEDIUM);
		srcToVipa.put(srcAddress+":"+srcPort, vipa+":"+vipaPort);

		//ReqMessage creation
		NameAddress anchorNode = new NameAddress("sip:" + srcAddress + ":"+SipStack.default_port);
		NameAddress from = new NameAddress("sip:" + sip_provider.getInterfaceAddress());
		TsaBinding req = new TsaBinding("sip:" + sip_provider.getInterfaceAddress(), srcAddress, srcPort);
		Message message = newUpmtMessage(anchorNode, from, req);

		//DoTransaction
		startNonBlockingTransaction(message, "flowOpenedResp", srcAddress);}
		public void flowOpenedResp(Message resp, String ANAddress) {

		//RespMessage handling
		printLog("MH_2_FH message sent to AN:" + ANAddress, Log.LEVEL_MEDIUM);		
	}

	protected AssociationResp handleAssociation(AssociationReq req)
	{
		printLog("ASSOCIATION for " + req.sipId);

		String[] token = srcToVipa.get(req.vipaToken).split(":");
		String vipa = token[0].trim();
		int vipaPort = Integer.parseInt(token[0].trim()); //TODO: c'� anke la porta!!!!!!!!!!!!!!
		
		AssociationResp resp = (AssociationResp)UpmtSignal.createResp(req);
		resp.associationSuccess = true;
		resp.vipa = vipa;

		printLog("Vipa assigned: "+vipa);
		return resp;
	}
	protected TunnelSetupResp handleTunnelSetup(TunnelSetupReq req)
	{
		return null;
	}
	protected HandoverResp handleHandover(HandoverReq req)
	{
		return null;
	}
	protected SetHandoverModeResp handleSetHandoverMode(SetHandoverModeReq req)
	{
		return null;
	}



	// ****************************** MAIN *****************************
	public static void main(String[] args)
	{
		String file = null;

		for (int i = 0; i < args.length; i++)
			if (args[i].equals("-h")) quitAndPrintUsage();
			else if (args[i].equals("-f") && i+1 < args.length) file = args[++i];
		
		new UPMTFixedHost(file);
	}

	private static void quitAndPrintUsage()
	{
		System.err.println("usage:\n\tjava UPMTFixedHost [options]\n\toptions:" +
				"\n\t-f <config_file> specifies a configuration file");
		System.exit(-1);
	}
}
