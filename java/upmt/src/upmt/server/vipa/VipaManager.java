package upmt.server.vipa;

import upmt.signaling.message.AssociationReq;

public interface VipaManager
{
	public void loadConfig(String file);
	public String getNewVipa(AssociationReq req);
}
