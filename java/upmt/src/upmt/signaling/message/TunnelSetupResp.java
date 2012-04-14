package upmt.signaling.message;

public class TunnelSetupResp implements Signal
{
	public int serverTunnelID;
	public String yourAddress;
	public int yourPort;
	public String sipId;

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public int getServerTunnelID() {return serverTunnelID;}
	public void setServerTunnelID(int serverTunnelID) {this.serverTunnelID = serverTunnelID;}

	public String getYourAddress() {return yourAddress;}
	public void setYourAddress(String yourAddress) {this.yourAddress = yourAddress;}

	public int getYourPort() {return yourPort;}
	public void setYourPort(int yourPort) {this.yourPort = yourPort;}
}
