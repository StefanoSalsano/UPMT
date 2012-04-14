package upmt.signaling.message;


public class KeepAlive implements Signal {

	public String sipId;
	public int tunnelId;
	public int sipSignalingPort;


	public KeepAlive(){}	
	public KeepAlive(String sipId, int tunnelId, int sipSignalingPort)
	{
		this.sipId = sipId;
		this.tunnelId = tunnelId;
		this.sipSignalingPort = sipSignalingPort;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public int getTunnelId() {return tunnelId;}
	public void setTunnelId(int tunnelId) {this.tunnelId = tunnelId;}
	
	public int getSipSignalingPort() {return sipSignalingPort;}
	public void setSipSignalingPort(int sipSignalingPort) {this.sipSignalingPort = sipSignalingPort;}

}
