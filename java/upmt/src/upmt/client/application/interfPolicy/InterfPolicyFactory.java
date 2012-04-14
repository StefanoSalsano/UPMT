package upmt.client.application.interfPolicy;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

import upmt.client.UPMTClient;

/**
 * It used to instantiate a policy for an application
 */

public class InterfPolicyFactory
{
	private static final String PACKAGE = InterfPolicyFactory.class.getPackage().getName() + ".policy.";
	private static final String SUFFIX = "Policy";
	private static Hashtable<Integer, Vector<String>> reg;

//	public static void setEventRegister(Hashtable<Integer, Vector<String>> eventRegister) {InterfPolicyFactory.reg = eventRegister;}

	/**
	 * Instantiates an Interface Policy by using Class for name from the name of the policy<BR>
	 * (the name of the policy is the first in the configVector String)<BR>
	 * the String appName (if not null) is used for registering an application to events
	 * so it updates the eventRegister in UPMTClient 
	 */
	public static InterfPolicy getPolicy(String appName, String configVector) {
		return getPolicy(appName, new Vector<String>(Arrays.asList(configVector.split(" "))));
	}

	public static InterfPolicy getPolicy(String appName, Vector<String> configVector)
	{
		if (configVector==null || configVector.size()==0) return null;
		configVector = new Vector<String>(configVector);
		String policy = configVector.remove(0);

		
		try {
			String[] param = configVector.toArray(new String[configVector.size()]);
			InterfPolicy ret = (InterfPolicy) Class.forName(PACKAGE + policy + SUFFIX).newInstance();

			if(policy.contains("SG")){
				configVector.add(appName);
				param = configVector.toArray(new String[configVector.size()]);
			}

				if(!ret.setParam(param)) return null;

			if (appName!=null) {
				Hashtable<Integer, Vector<String>> eventRegister = UPMTClient.getEventRegister();
				synchronized (eventRegister) { 
					for (int event : eventRegister.keySet()) {
						if (/*!eventToSkip.contains(event)&&*/ret.isTriggeredBy(event)) {
							eventRegister.get(event).add(appName);
						}
					}
				}
			}
			return ret;
		}
		catch (ClassNotFoundException e)
			{System.err.println("Can not find the specified policy (" + policy + SUFFIX + ") implementation.");
			System.err.println("PACKAGE : " + PACKAGE + "  SUFFIX "+ SUFFIX);
			System.err.println("appname : " + appName);
			
			return null;}

		catch (InstantiationException e)
			{System.err.println("Error in " + policy + SUFFIX + ": Can not instantiate a new Policy");return null;}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + policy + SUFFIX + ": Can not instantiate a new Policy");return null;}
		catch (ClassCastException e)
			{System.err.println("Error in " + policy + SUFFIX + ": The class is not a Policy");return null;}
	}
}
