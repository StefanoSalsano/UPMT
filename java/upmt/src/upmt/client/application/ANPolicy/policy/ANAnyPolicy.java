package upmt.client.application.ANPolicy.policy;

import java.util.Hashtable;

import java.util.Random;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;
import upmt.signaling.message.ANListResp;

public class ANAnyPolicy extends ANGenericPolicy
{
	public boolean setParam(String[] param) {return true;}
	
	@Override
	public String getActiveAN(Hashtable <String, Integer > associatedANList, String currentAN, int event)
	{
		if (currentAN!=null && associatedANList.contains(currentAN)) return currentAN;
		if(associatedANList.size()==0) {
			return null;
		}
		Object[] key = associatedANList.keySet().toArray();
		Random r = new Random(System.currentTimeMillis());
		return (String)key[(int)(r.nextDouble()*associatedANList.size())]; //TODO: controllare meglio l'uniformita'
	}

	public String getDesc()
	{
		return null;
	}

	public boolean isTriggeredBy(int event)
	{
		return (event == UPMTClient.EVENT_AN_DOWN || event == UPMTClient.EVENT_START);
	}
}
