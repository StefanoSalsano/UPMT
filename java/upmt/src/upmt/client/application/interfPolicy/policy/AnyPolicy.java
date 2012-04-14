package upmt.client.application.interfPolicy.policy;

import java.util.Hashtable;
import java.util.Random;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;

public class AnyPolicy extends GenericPolicy
{
	public boolean setParam(String[] param) {return true;}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String currentInterf, int event)
	{
		if (currentInterf!=null && ifList.contains(currentInterf)) return currentInterf;

		if(ifList.size()==0) {
			return null;
		}
		Object[] key = ifList.keySet().toArray();
		Random r = new Random(System.currentTimeMillis());
		return (String)key[(int)(r.nextDouble()*ifList.size())]; //TODO: controllare meglio l'uniformita'
	}

	public String getDesc()
	{
		return null;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_INTERFACE_UP || event == UPMTClient.EVENT_INTERFACE_DOWN || event == UPMTClient.EVENT_START);
	}
}
