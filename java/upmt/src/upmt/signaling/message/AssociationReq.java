package upmt.signaling.message;

/**Upmt message: (MH --> AN) or (MH --> FH).*/
public class AssociationReq implements Signal
{
	public String sipId;
	public String vipaToken;
	public String[] addressInUse;
	public String vipa;

	public AssociationReq(){}
	public AssociationReq(String sipId, String vipaToken, String[] addressInUse, String vipa)
	{
		this.sipId = sipId;
		this.vipaToken = vipaToken;
		this.addressInUse = addressInUse;
		this.vipa = vipa;
	}

	public String getVipa() {
		return vipa;
	}
	public void setVipa(String vipa) {
		this.vipa = vipa;
	}
	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public void setVipaToken(String vipaToken) {this.vipaToken = vipaToken;}
	public String getVipaToken() {return vipaToken;}

	public void setAddressInUse(String[] addressInUse) {this.addressInUse = addressInUse;}
	public String[] getAddressInUse() {return addressInUse;}
}
