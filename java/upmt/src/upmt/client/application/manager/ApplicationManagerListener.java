package upmt.client.application.manager;

import java.util.Hashtable;
import java.util.Vector;

import upmt.client.core.Socket;

public interface ApplicationManagerListener
{
	public void stop();

	public Vector<String> getInterfacesList();
	public String getVipaFix();
	public int getNumOfConnectedAN();
	public String getCfgSipID();
	public String getSipID();
	public String getDefaultAN();
	public String getCfgDefaultAN();
	public String getCurrentPolicy(String appName);
//	public String getCurrentPolicy(Socket socket);
	public String getStoredPolicy(String appName);
	public Hashtable<String, String> getAllStoredPolicy();
	public String getDefaultPolicy();
	public Vector<String> getNoUpmtApp();
	public Vector<String> getNoUpmtInterf();
	public String getCurrentInterf(String appName);
	public String getCurrentInterf(Socket socket);
	
	
	public void setCurrentPolicy(String appName, String policyVector);

	/**Stores a SIP ID in the config, but it has effect from the next UPMT restart*/
	public void setCfgSipID(String sipID);
	/**deletes all AN in the config and stores the new one, but it has effect from the next UPMT restart*/
	public void setDefaultAN(String sipID);

	public void cfgPolicyAdding(String appName, String policy);
	public void cfgPolicyEdit(String appName, String policy);
	public void cfgPolicyRemoval(String appName);

	public void defPolicyEdit(String defPolicy);

	public void cfgNoAppRemoval(String appName);
	public void cfgNoAppAdding(String appName);

	public void cfgNoInterfRemoval(String ifName);
	public void cfgNoInterfAdding(String ifName);
}
