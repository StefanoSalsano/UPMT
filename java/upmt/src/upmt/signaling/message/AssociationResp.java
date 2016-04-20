package upmt.signaling.message;

/**Upmt message: (AN --> MH) or (FH --> MH).*/
public class AssociationResp implements Signal
{
	public boolean associationSuccess;
	public String vipa;
	public String sipId;
	public int tsa;
	public String reason;
	public String[] addressesInUse;

	public String getReason() {
		return reason;
	}
	public void setReason(String reason) {
		this.reason = reason;
	}
	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public boolean isAssociationSuccess() {return associationSuccess;}
	public void setAssociationSuccess(boolean associationSuccess) {this.associationSuccess = associationSuccess;}

	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}
	
	public int getTsa() {return tsa;}
	public void setTsa(int tsa) {this.tsa = tsa;}
	
	public String[] getAddressesInUse() {
		return addressesInUse;
	}
	
	public void setAddressesInUse(String[] addressesInUse) {
		this.addressesInUse=addressesInUse;
	}
	
}
