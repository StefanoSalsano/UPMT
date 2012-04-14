package upmt.signaling.message;

/**Upmt message: (FH --> AN). <br>
 * Is sent to notify the AN that the FH is UPMT aware and can handle an application flow without its help.*/
public class TsaBinding implements Signal
{
	public String anAddress;
	public int anPort;
	public String sipId;

	public TsaBinding(String sipId, String anAddress, int anPort)
	{
		this.sipId = sipId;
		this.anAddress = anAddress;
		this.anPort = anPort;
	}

	//Empty constructor, setters and getters for JSON serialization:
	public TsaBinding(){}
	public String getSipId() {return sipId;}
	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getAnAddress() {return anAddress;}
	public void setAnAddress(String anAddress) {this.anAddress = anAddress;}
	public int getAnPort() {return anPort;}
	public void setAnPort(int anPort) {this.anPort = anPort;}
}
