package upmt.client.application.interfPolicy.policy;

import java.util.Hashtable;

import upmt.client.core.InterfaceInfo;

public class WiredBeforeWirelessPolicy extends GenericPolicy
{
	public boolean setParam(String[] param)
	{
		return false;
	}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String actualInterf, int event)
	{
		return null;
	}

	public String getDesc()
	{
		return null;
	}

	public boolean isTriggeredBy(int event)
	{
		return false;
	}
}
