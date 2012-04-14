package upmt.client.application.interfPolicy.policy;

import java.util.Hashtable;
import java.util.Map.Entry;

import upmt.client.application.interfPolicy.InterfPolicy;
import upmt.client.core.InterfaceInfo;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;

public class GenericPolicy implements InterfPolicy {

	@Override
	public boolean setParam(String[] param) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList,
			String currentInterf, int event) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isTriggeredBy(int event) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getDesc() {
		// TODO Auto-generated method stub
		return null;
	}

}
