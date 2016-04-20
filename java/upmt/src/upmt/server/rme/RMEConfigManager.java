package upmt.server.rme;

import java.util.Vector;

import org.zoolu.tools.Configurable;
import org.zoolu.tools.Configure;
import org.zoolu.tools.Parser;

import upmt.Default;
import upmt.os.Module;

public class RMEConfigManager implements Configurable
{
	private static RMEConfigManager instance;
	private RMEConfigManager(){}
	private String file;

	public static RMEConfigManager instance() {if (instance == null) instance = new RMEConfigManager(); return instance;}

	public void ParseConfig(String file)
	{
		//#ifdef ANDROID
		//		file = "/sdcard/upmt/" + file;
		//#endif
		this.file = file;
		new Configure(this, file);
	}



	//Radio Multiple Eterogenee
	public String[] rmeNet = null;
	public String[] adhocwlan = null;
	public String vepa = null;
	public int rmeTsa;

	public String vipaManagerPolicy = Default.SERVER_VIPA_MANAGER_POLICY;
	public Vector<String> tsas = new Vector<String>();
	public int serverMark = Default.SERVER_MARK;
	public int sipPort = Default.SERVER_SIP_PORT;
	public int SERVER_KEEP_ALIVE_INTERVAL =  20000;
	public boolean isAN = false;
	public boolean isBroker = false;
	public boolean isFH = false;
	public Vector<String> ANBrokerList = new Vector<String>();
	public Vector<String> Black_List = new Vector<String>();
	public String anchor_identifier;
	public int maxMHs;
	public boolean keepaliveKernel = false;

	@SuppressWarnings("unchecked")
	public void parseLine(String line)
	{
		int index = line.indexOf("=");
		String attribute = index>0 ? line.substring(0, index).trim() : line;
		Parser par = index>0 ? new Parser(line, index+1) : new Parser("");
		if (attribute.equals(Default.SERVER_VIPA_MANAGER_POLICY_TAG)) vipaManagerPolicy = par.getString();
		else if (attribute.equals(Default.SERVER_TSA_PORT_TAG)) tsas = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.SERVER_MARK_TAG)) serverMark = par.getInt();
		else if (attribute.equals(Default.SERVER_SIP_PORT_TAG)) sipPort = par.getInt();
		else if (attribute.equals(Default.SERVER_VIPA_TAG)) Module.setVipaFix(par.getString());
		else if (attribute.equals(Default.SERVER_IFNAME_TAG)) Module.setServerIfName(par.getString());
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_PERIOD_TAG)) SERVER_KEEP_ALIVE_INTERVAL = par.getInt() * 2;

		//Anchor node Broker List for registration
		else if (attribute.equals(Default.ANBrokerList_TAG)) ANBrokerList = par.getWordVector(Default.delim);
		else if (attribute.equals(Default.isAN_TAG)) isAN = (par.getString().contains("yes") ? true : false);
		else if (attribute.equals(Default.isBroker_TAG)) isBroker = (par.getString().contains("yes") ? true : false);
		else if (attribute.equals(Default.isFH_TAG)) isFH = (par.getString().contains("yes") ? true : false);

		else if (attribute.equals(Default.SERVER_IDENTIFIER_TAG)) anchor_identifier = par.getString();

		else if (attribute.equals(Default.SERVER_MAX_MH_TAG)) maxMHs = par.getInt();
		else if (attribute.equals(Default.BLACK_LIST_TAG)) Black_List = par.getWordVector(Default.delim);


		//RME
		else if (attribute.equals(Default.vepa_TAG)) vepa = par.getString();
		else if (attribute.equals(Default.rmeNet_TAG)) rmeNet = par.getWordArray(Default.delim);
		else if (attribute.equals(Default.rmeTsa_TAG)) rmeTsa = par.getInt();
		else if(attribute.equals(Default.adhocwlan_TAG)) adhocwlan = par.getWordArray(Default.delim);
		else if (attribute.equals(Default.CONFIG_KEEPALIVE_KERNEL)) {
			String kernelkeepalive = par.getString();
			if(kernelkeepalive.equalsIgnoreCase("yes")) {
				keepaliveKernel = true;
			}
		}
	}

	public String getFile() {
		return this.file;
	}
}
