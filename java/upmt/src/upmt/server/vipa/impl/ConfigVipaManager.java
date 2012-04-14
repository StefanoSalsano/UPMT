package upmt.server.vipa.impl;

import org.zoolu.tools.Configurable;
import org.zoolu.tools.Configure;
import org.zoolu.tools.Parser;

import upmt.Default;
import upmt.server.vipa.VipaManager;
import upmt.server.vipa.model.NumIp;
import upmt.signaling.message.AssociationReq;

public class ConfigVipaManager implements VipaManager, Configurable
{
	private static final NumIp DefaultStartAddress = new NumIp(Default.CONFIG_VIPA_MANAGER_START_ADDRESS);
	private static final NumIp DefaultEndAddress = new NumIp(Default.CONFIG_VIPA_MANAGER_END_ADDRESS);

	private NumIp startAddress = DefaultStartAddress;
	private NumIp endAddress = DefaultEndAddress;

	private NumIp currentAddress;

	public String getNewVipa(AssociationReq req)
	{
		if (endAddress.isLesserThan(currentAddress)) return null;
		String current = currentAddress.toString();
		currentAddress.increase();
		return current;
	}

	public void loadConfig(String file)
	{
		new Configure(this, file);
		if (endAddress.isLesserThan(startAddress))
		{
			startAddress = DefaultStartAddress;
			endAddress = DefaultEndAddress;
		}
		currentAddress = new NumIp(startAddress);
	}

	public void parseLine(String line)
	{
		String attribute;
		Parser par;
		int index = line.indexOf("=");
		if (index>0) {attribute = line.substring(0, index).trim(); par = new Parser(line, index+1);}
		else {attribute = line; par = new Parser("");}

		if (attribute.equals(Default.CONFIG_VIPA_MANAGER_START_ADDRESS_TAG)) startAddress = new NumIp(par.getString());
		else if (attribute.equals(Default.CONFIG_VIPA_MANAGER_END_ADDRESS_TAG)) endAddress = new NumIp(par.getString());
	}
}
