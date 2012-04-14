package upmt.client.application.interfPolicy.policy;

import java.util.Arrays;
import java.util.Hashtable;

import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;

public class VoipPerformancePolicy extends GenericPolicy 
{
	private String[] interfList;
	private String appName;
	private boolean any = false;

	public boolean setParam(String[] param)
	{
		if (param.length<2) return false;

		appName = param[param.length -1 ];

		this.interfList = new String[param.length-1];

		for(int i = 0; i < param.length - 1; i++){
			this.interfList[i] = param[i];

			if(param[i].equals("any"))
				any = true;
		}

		System.out.println(getDesc());

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

		Hashtable<String, TunnelInfo> tidStatuStable = SipSignalManager.getRemoteTidStatusTable();

		String ANAddress = TunnelManager.getAppAN(appName);
		if(ANAddress == null)
			ANAddress = "";

		// calcolo degli score
		double[] scores = new double[interfList.length];
		double[] count = new double[interfList.length];

		for(String s : tidStatuStable.keySet()){

			System.out.println("TID Status Entry " + s);

			for(int i =0; i < interfList.length; i++){
				if(s.endsWith(interfList[i]) && s.startsWith(ANAddress)){
					System.out.println("TID Status Entry " + s + " selected");

					if(tidStatuStable.get(s) != null){
						scores[i] += evaluateScore(tidStatuStable.get(s).getEWMA_Delay()/2, tidStatuStable.get(s).getEWMA_Loss()*100);
						count[i]++;
					}
				}
			}	
		}

		for(int i = 0; i < scores.length; i++){
			scores[i] /= count[i]; 
			System.out.println("Score: " + scores[i] + " count: " + count[i] + " for interface " + interfList[i]);
		}
		// calcolo degli score terminato

		double min = Double.MAX_VALUE;
		double lower_bound = Double.MIN_VALUE;

		while(true){

			int position = -1;

			for(int i = 0; i < scores.length; i++){
				if(scores[i] <= min && scores[i] > lower_bound){
					System.out.println("\n\n\nmin score, if: " + interfList[i] + ", score: " + scores[i] + ", pos: " + i);
					min = scores[i];
					position = i;
				}
			}

			lower_bound = min;

			if(position == -1){
				System.out.println("PerformanceSGPolicy returns iface: " + null);
				return null;
			}

			if(ifList.containsKey(interfList[position])){
				System.out.println("PerformanceSGPolicy returns iface: " + interfList[position]);
				return interfList[position];
			}

		}

	}

	private double evaluateScore(double ewma_Delay, double ewma_Loss) {
		if(ewma_Delay < 100){
			return ewma_Loss;
		}
		else if(ewma_Delay >= 100 && ewma_Delay <=200){
			return ewma_Loss + (ewma_Delay - 100)/2;
		}
		else{
			return Math.sqrt(ewma_Delay*ewma_Delay + ewma_Loss*ewma_Loss) + 55;
		}
	}


	public String getDesc()
	{
		String par = Arrays.toString(interfList);
		return "PerformanceSGPolicy " + par.substring(1, par.length()-1).replace(", ", " ") + " for app: " + appName;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_UP || event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
