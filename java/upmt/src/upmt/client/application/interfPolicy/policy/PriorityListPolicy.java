package upmt.client.application.interfPolicy.policy;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.NoSuchElementException;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class PriorityListPolicy extends GenericPolicy 
{
	private String[] interfList;

	public boolean setParam(String[] param)
	{
		if (param.length==0) return false;
		this.interfList = param;
		return true;
	}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event) {
		for (String interf : interfList) {
			if (interf.equals("any")) {
				String toBeReturned = null;
				try {
					toBeReturned = ifList.keys().nextElement();
				} catch (NoSuchElementException e) {
				}
				return toBeReturned;
			} else if(ifList.containsKey(interf)) {
				return interf;
			}
		}
		return null;
	}

	public String getDesc()
	{
		String par = Arrays.toString(interfList);
		return "PriorityList " + par.substring(1, par.length()-1).replace(", ", " ");
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_UP || event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
