package upmt.client.application.interfPolicy.policy;

import java.util.Hashtable;
import java.util.NoSuchElementException;
import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class StaticPolicy extends GenericPolicy
{
	private String selectedInterf;

	public boolean setParam(String[] param)
	{
		if (param.length!=1) return false;
		selectedInterf = param[0].toLowerCase();
		return true;
	}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event)
	{
		if (selectedInterf=="any") {
			String toBeReturned = null;
			try {
				toBeReturned = ifList.keys().nextElement();
			} catch (NoSuchElementException e) {
			}
			return toBeReturned;
		} else if(ifList.containsKey(selectedInterf)) {
			return selectedInterf;
		}
		return null;

	}

	public String getDesc()
	{
		return "Static " + selectedInterf;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_UP || event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
