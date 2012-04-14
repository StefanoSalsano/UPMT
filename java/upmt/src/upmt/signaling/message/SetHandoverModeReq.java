package upmt.signaling.message;

import upmt.client.core.Socket;

public class SetHandoverModeReq implements Signal
{
	public String sipId;
	public Socket socket;
	public String vipa;
	public boolean staticRule;

	public SetHandoverModeReq(){}	
	public SetHandoverModeReq(String sipId, Socket socket, String vipa, boolean staticRule)
	{
		this.sipId = sipId;
		this.socket = socket;
		this.vipa = vipa;
		this.staticRule = staticRule;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public Socket getSocket() {return socket;}
	public void setSocket(Socket socket) {this.socket = socket;}

	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}

	public boolean getStaticRule() {return staticRule;}
	public void setStaticRule(boolean staticRule) {this.staticRule = staticRule;}
}
