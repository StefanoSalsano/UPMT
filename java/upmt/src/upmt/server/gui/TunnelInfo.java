package upmt.server.gui;

public class TunnelInfo {
	
	public int serverTunnelID;
	public String yourAddress;
	public int yourPort;
	public String sipId;
	public String vipa;
	public long lastAck;
	
	public TunnelInfo(int serverTunnelID, String yourAddress, int yourPort,
			String sipId, String vipa, long lastAck) {
		super();
		this.serverTunnelID = serverTunnelID;
		this.yourAddress = yourAddress;
		this.yourPort = yourPort;
		this.sipId = sipId;
		this.vipa = vipa;
		this.lastAck = lastAck;
	}
	
	public int getServerTunnelID() {
		return serverTunnelID;
	}
	
	public void setServerTunnelID(int serverTunnelID) {
		this.serverTunnelID = serverTunnelID;
	}
	
	public String getYourAddress() {
		return yourAddress;
	}
	
	public void setYourAddress(String yourAddress) {
		this.yourAddress = yourAddress;
	}
	
	public int getYourPort() {
		return yourPort;
	}
	
	public void setYourPort(int yourPort) {
		this.yourPort = yourPort;
	}
	
	public String getSipId() {
		return sipId;
	}
	
	public void setSipId(String sipId) {
		this.sipId = sipId;
	}
	
	public String getVipa() {
		return vipa;
	}
	public void setVipa(String vipa) {
		this.vipa = vipa;
	}
	
	public long getLastAck() {
		return lastAck;
	}
	
	public void setLastAck(long lastAck) {
		this.lastAck = lastAck;
	}
	

}
