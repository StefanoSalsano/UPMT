package upmt.client.application.ANPolicy;

import java.util.Hashtable;

import upmt.client.core.InterfaceInfo;
import upmt.signaling.message.ANListResp;

public interface ANPolicy {

	public boolean setParam(String[] param);

	/** Interrogate the policy to obtain the preferred anchor node to use.<BR> 
	 * Either it returns an Anchor Node or it returns null
	 * @param <associatedANList>
	 * @param ANList the list of available anchor nodes
	 * @param currentAN the current used AN. If this value is null 
	 * it means that the application is actually blocked
	 * @param event the event occurred*/


	/**For START and AN_DOWN must always return true.*/

	
	public boolean isTriggeredBy(int event);
	public String getDesc();

	String getActiveAN(Hashtable<String, Integer> associatedANList,
			String currentAN, int event);
						

	}
