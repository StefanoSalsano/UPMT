package upmt.client.application.interfPolicy;

import java.util.Hashtable;
import upmt.client.core.InterfaceInfo;

/**
 * important assumption: we assume that a policy will not return null when getActiveInterface is called if there was a good interface and 
 * the interface is still ok when the getActiveInterface is called
 * if the new interface that has been added is not good... it should return the previous interface<BR>
 * if we remove this assumption we should decide if we block the application or if we consider it as an error of the policy
 */
public interface InterfPolicy
{
	/** Set the specific parameter for each policy
	 * @return true if the policy is correctly configured*/
	public boolean setParam(String[] param);

	/** Interrogate the policy to obtain the preferred interface to use.<BR> 
	 * Either it returns an interface or it returns null
	 * @param ifList the list of available interface
	 * @param currentInterf the current used interface. If this value is null 
	 * it means that the application is actually blocked
	 * @param event the event occurred*/
	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event);

	/**For START and INTERFACE_DOWN must always return true.*/
	public boolean isTriggeredBy(int event);
	public String getDesc();

//	/**
//	 * it is used to filter the available interfaces so that only the ones that are
//	 * usable are returned
//	 * @param availableIfs
//	 * @param anchorNode TODO
//	 * @return
//	 */
//	public Hashtable<String, InterfaceInfo> filterOnCanUseTunnel(
//			Hashtable<String, InterfaceInfo> availableIfs, String anchorNode);
//
//	public Hashtable<String, InterfaceInfo> filterOnSignalingOK(
//			Hashtable<String, InterfaceInfo> availableIfs, String anchorNode);

}
