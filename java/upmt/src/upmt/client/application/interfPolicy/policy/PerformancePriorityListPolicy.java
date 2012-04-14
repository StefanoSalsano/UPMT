package upmt.client.application.interfPolicy.policy;

import upmt.TunnelInfo;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;

import java.util.Arrays;

import java.util.Hashtable;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class PerformancePriorityListPolicy extends GenericPolicy 
{
	private String[] interfList;
	private double delay;
	private double loss;
	private String appName;
	private boolean any = false;

	public boolean setParam(String[] param)
	{
		if (param.length < 3) 
			return false;

		appName = param[param.length -1];
		
		String[] thresholds =  param[param.length -2].split(":");

		if (thresholds.length < 2) 
			return false;

		delay = Integer.parseInt(thresholds[0]);
		loss = Integer.parseInt(thresholds[1]);

		this.interfList = new String[param.length -2];
		for(int i = 0; i < param.length -2; i++)
			this.interfList[i] = param[i];

		for(int i = 0; i < param.length; i++)
			if(param[i].equals("any"))
				any = true;

		System.err.print(getDesc());

		return true;
	}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event) {

		if(any){
			interfList = new String[ifList.keySet().size()];
			int i = 0;
			for(String iface : ifList.keySet()){
				interfList[i] = iface;
				i++;
			}
		}

		Hashtable<String, TunnelInfo> tidStatuStable= SipSignalManager.getRemoteTidStatusTable();

		String ANAddress = TunnelManager.getAppAN(appName);
		if(ANAddress == null)
			ANAddress = "";

		double[] delays = new double[interfList.length];
		double[] losses = new double[interfList.length];

		double[] ndelays = new double[interfList.length];
		double[] nlosses = new double[interfList.length];

		for(String s : tidStatuStable.keySet()){

			System.out.println("TID Status Entry " + s);

			for(int i =0; i < interfList.length; i++){
				if(s.endsWith(interfList[i]) && s.startsWith(ANAddress)){
					System.out.println("TID Status Entry " + s + " selected");
					if(tidStatuStable.get(s) != null){
						delays[i] += tidStatuStable.get(s).getEWMA_Delay();
						losses[i] += tidStatuStable.get(s).getEWMA_Loss();
						ndelays[i]++;
						nlosses[i]++;
					}
				}
			}	
		}

		for(int i = 0; i < delays.length; i++){
			delays[i] /= ndelays[i]; 
			losses[i] /= nlosses[i]; 
		}

		for(int i =0; i < interfList.length; i++){
			if(ifList.containsKey(interfList[i]) && delays[i] < delay && losses[i] < loss){
				System.out.println(delays[i] + "<" + delay + "&&" + losses[i] + "<" + loss);
				System.out.println("PerformancePriorityListSGPolicy returns " + interfList[i]);
				return interfList[i];
			}	
		}

		for(int i =0; i < interfList.length; i++){
			if(ifList.containsKey(interfList[i])){
				System.out.println("PerformancePriorityListSGPolicy returns with no threshold " + interfList[i]);
				return interfList[i];
			}	
		}

		System.out.println("PerformancePriorityListSGPolicy returns " + null);
		return null;
	}

	public String getDesc()
	{
		String par = Arrays.toString(interfList);
		return "PerformancePriorityListSGPolicy " + par.substring(1, par.length()-1).replace(", ", " ") + delay + ":" + loss + " for app: " + appName;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_UP || event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
