package upmt.signaling.message;

/**Upmt message: (AN --> MH).
 * It is sent to notify the MH that it can communicate directly with a FH*/
public class TsRedirect implements Signal
{
	public String sipId;
	public String suggestedVipa;
	public String[] addressInUse;

	public TsRedirect(){}
	public TsRedirect(String sipId, String suggestedVipa, String[] addressInUse)
	{
		this.sipId = sipId;
		this.suggestedVipa = suggestedVipa;
		this.addressInUse = addressInUse;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public void setSuggestedVipa(String suggestedVipa) {this.suggestedVipa = suggestedVipa;}
	public String getSuggestedVipa() {return suggestedVipa;}

	public void setAddressInUse(String[] addressInUse) {this.addressInUse = addressInUse;}
	public String[] getAddressInUse() {return addressInUse;}
}
