package upmt.server.vipa.impl;

import upmt.signaling.message.AssociationReq;

public class TestVipaManager implements upmt.server.vipa.VipaManager
{
	public void loadConfig(String file){}
	public String getNewVipa(AssociationReq req) {return "10.20.30.40";}
}
