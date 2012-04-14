package upmt.signaling.message;

public class TunnelSetupReq implements Signal
{
	public int sipSignalingPort;
	public String vipa;//Per ora è usato. Ma è inutile? Si puù ricavare sul server dal sipID?
	public String sipId;

	public TunnelSetupReq(){}
	public TunnelSetupReq(String sipId, int sipSignalingPort, String vipa)
	{
		this.sipId = sipId;
		this.sipSignalingPort = sipSignalingPort;
		this.vipa = vipa;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public int getSipSignalingPort() {return sipSignalingPort;}
	public void setSipSignalingPort(int sipSignalingPort) {this.sipSignalingPort = sipSignalingPort;}

	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}
}
