package upmt.client.application.ANPolicy.policy;

import java.util.Hashtable;
import java.util.Map.Entry;

import upmt.client.application.ANPolicy.ANPolicy;
import upmt.client.application.interfPolicy.InterfPolicy;
import upmt.client.core.InterfaceInfo;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;
import upmt.signaling.message.ANListResp;

public class ANGenericPolicy implements ANPolicy {

	@Override
	public boolean setParam(String[] param) {
		// TODO Auto-generated method stub
		return false;
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

	public String getActiveAN(Hashtable<String, Integer> associatedANList,
			String currentAN, int event) {
		// TODO Auto-generated method stub
		return null;
	}
}
