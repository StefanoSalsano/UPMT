package upmt.client.application.interfPolicy.policy;

import java.util.Hashtable;

import upmt.client.core.InterfaceInfo;

public class BlockPolicy extends GenericPolicy
{
	public boolean setParam(String[] param)
	{return true;}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String actualInterf, int event)
	{
		return null;
	}

	public String getDesc()
	{
		return "Block";
	}

	public boolean isTriggeredBy(int event)
	{
		return false;
	}
}
