package upmt.signaling.message;

public class SetHandoverModeResp implements Signal
{
	public String sipId;

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}
}
