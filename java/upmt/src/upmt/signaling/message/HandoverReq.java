package upmt.signaling.message;

import upmt.client.core.Socket;

public class HandoverReq implements Signal
{
	public String sipId;
	public Socket socket;
	public String vipa;
	public int tunnelId;

	public HandoverReq(){}	
	public HandoverReq(String sipId, Socket socket, String vipa, int tunnelId)
	{
		this.sipId = sipId;
		this.socket = socket;
		this.vipa = vipa;
		this.tunnelId = tunnelId;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public Socket getSocket() {return socket;}
	public void setSocket(Socket socket) {this.socket = socket;}

	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}

	public int getTunnelId() {return tunnelId;}
	public void setTunnelId(int tunnelId) {this.tunnelId = tunnelId;}
}
