package upmt.server;

import org.zoolu.net.IpAddress;


public class AnchorNode  {
	
	public String anchor_identifier;
	public String anchor_IPaddress;
	public int port;
	public boolean isAN;
	public boolean isBroker;
	public boolean isFH;
	public int freeSlots;
	
	

	public AnchorNode(String anchor_identifier, IpAddress anchor_IPaddress,
			int port, boolean isAN, boolean isBroker, boolean isFH,
			int freeSlots) {
		this.anchor_identifier = anchor_identifier;
		this.anchor_IPaddress = anchor_IPaddress.toString();
		this.port = port;
		this.isAN = isAN;
		this.isBroker = isBroker;
		this.isFH = isFH;
		this.freeSlots = freeSlots;
	}



	public String getAnchor_identifier() {
		return anchor_identifier;
	}



	public void setAnchor_identifier(String anchor_identifier) {
		this.anchor_identifier = anchor_identifier;
	}



	public String getAnchor_IPaddress() {
		return anchor_IPaddress;
	}

	public void setAnchor_IPaddress(IpAddress anchor_IPaddress) {
		this.anchor_IPaddress = anchor_IPaddress.toString();
	}



	public int getPort() {
		return port;
	}



	public void setPort(int port) {
		this.port = port;
	}



	public boolean isAN() {
		return isAN;
	}



	public void setAN(boolean isAN) {
		this.isAN = isAN;
	}



	public boolean isBroker() {
		return isBroker;
	}



	public void setBroker(boolean isBroker) {
		this.isBroker = isBroker;
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
	
	
	
}
