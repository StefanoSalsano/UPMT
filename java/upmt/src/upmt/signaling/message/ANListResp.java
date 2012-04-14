package upmt.signaling.message;

import upmt.server.AnchorNode;

public class ANListResp implements Signal {

	public String sipId;
	public String[] anList;
	public boolean broker;
//	public String[] ip;
//	public boolean isAN;
//	public boolean isFH;
//	public int freeSlots;
//	public boolean isBroker;

	

	

	public String getSipId() {
		return sipId;
	}
	
	public void setSipId(String sipId) {
		this.sipId = sipId;
	}

	/*	
	public String[] getIp() {
		return ip;
	}
	
	public void setIp(String[] ip) {
		this.ip = ip;
	}
	
	
	
	public boolean isAN() {
		return isAN;
	}
	
	public void setAN(boolean isAN) {
		this.isAN = isAN;
	}
	
	public boolean isFH() {
		return isFH;
	}
	
	public void setFH(boolean isFH) {
		this.isFH = isFH;
	}	
	
	public int getFreeSlots() {
		return freeSlots;
	}
	
	public void setFreeSlots(int freeSlots) {
		this.freeSlots = freeSlots;
	}
	*/
	public String[] getAnList() {
		return anList;
	}
	
	public void setAnList(String[] anList) {
		this.anList = anList;
	}
	


	
	public boolean isBroker() {
		return broker;
	}
	
	public void setBroker(boolean broker) {
		this.broker = broker;
	}
}
	
	
