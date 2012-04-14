package upmt.server.gui;

public class AssociationInfo {
	
	private String vipa;
	private long lastAck;
	
	public AssociationInfo(String vipa, long lastAck) {
		super();
		this.vipa = vipa;
		this.lastAck = lastAck;
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
