package upmt.client.application.ANPolicy.policy;

import java.util.Arrays;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class ANPriorityListPolicy extends ANGenericPolicy 
{
	private String[] anchorList;

	
	public boolean setParam(String[] param)
	{
		if (param.length==0) return false;
		this.anchorList = param;
		return true;
	}

		public String getActiveAN(Hashtable<String, Integer> AssociatedANList, String currentAN, int event) {
					 
		for (String anchor : anchorList) {
			if (anchor.equals("any")) {
				String toBeReturned = null;
				try {
				Iterator<Entry<String, Integer>> iter= AssociatedANList.entrySet().iterator();
				Entry <String, Integer> entr = iter.next();
				
				toBeReturned = entr.getKey() + ":" +entr.getValue();
				} catch (NoSuchElementException e) {
				}
				return toBeReturned;
			} else if(AssociatedANList.containsKey(anchor.split(":")[0])) {				
				return anchor;
			}
		}
		return null;
	}

		
/**
 * 	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event) {
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
 */
		
		
	public String getDesc()
	{
		String par = Arrays.toString(anchorList);
		return "ANPriorityList " + par.substring(1, par.length()-1).replace(", ", " ");
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_AN_DOWN || event == UPMTClient.EVENT_START);
	}
}
