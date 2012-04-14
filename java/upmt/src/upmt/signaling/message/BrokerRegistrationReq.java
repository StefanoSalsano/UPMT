package upmt.signaling.message;

public class BrokerRegistrationReq implements Signal {

	private String sipId;
	private String[] ip;
	private int[] port;
	private String anchor_identifier;
	private boolean imFH;
	private int freeSlots;
	private boolean isBroker;
	private boolean imAN;

	public final boolean isImAN() {
		return imAN;
	}
	public final void setImAN(boolean imAN) {
		this.imAN = imAN;
	}
	public final String getSipId() {
		return sipId;
	}
	public final void setSipId(String sipId) {
		this.sipId = sipId;
	}
	public final String[] getIp() {
		return ip;
	}
	public final void setIp(String[] ip) {
		this.ip = ip;
	}
	public final int[] getPort() {
		return port;
	}
	public final void setPort(int[] port) {
		this.port = port;
	}
	public final String getAnchor_identifier() {
		return anchor_identifier;
	}
	public final void setAnchor_identifier(String anchor_identifier) {
		this.anchor_identifier = anchor_identifier;
	}
	public final boolean isImFH() {
		return imFH;
	}
	public final void setImFH(boolean isFH) {
		this.imFH = isFH;
	}
	public final int getFreeSlots() {
		return freeSlots;
	}
	public final void setFreeSlots(int freeSlots) {
		this.freeSlots = freeSlots;
	}
	public final boolean isBroker() {
		return isBroker;
	}
	public final void setBroker(boolean isBroker) {
		this.isBroker = isBroker;
	}




}


