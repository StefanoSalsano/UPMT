package upmt.client.application.ANPolicy.policy;

import java.util.Hashtable;
import java.util.NoSuchElementException;
import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class ANStaticPolicy extends ANGenericPolicy
{
	private String selectedAN;

	public boolean setParam(String[] param)
	{
		if (param.length!=1) return false;
		selectedAN = param[0].toLowerCase();
		return true;
	}

	public String getActiveAN(Hashtable<String, Integer> associatedANList, String currentAN, int event)
	{
		if (selectedAN=="any") {
			String toBeReturned = null;
			try {
				toBeReturned = associatedANList.keys().nextElement();
			} catch (NoSuchElementException e) {
			}
			return toBeReturned;
		} else if(associatedANList.containsKey(selectedAN)) {
			return selectedAN;
		}
		return null;
		
	}

	public String getDesc()
	{
		return "Static " + selectedAN;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
