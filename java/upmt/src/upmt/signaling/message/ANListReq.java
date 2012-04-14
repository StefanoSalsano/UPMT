package upmt.signaling.message;


public class ANListReq implements Signal {

	public String sipId;

	public ANListReq(){}


	public void setSipId(String sipId) {
		this.sipId = sipId;		
	}

	public String getSipId() {
		return sipId;
	}

}
	
	
