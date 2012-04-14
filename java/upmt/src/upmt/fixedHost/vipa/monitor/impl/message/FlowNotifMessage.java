package upmt.fixedHost.vipa.monitor.impl.message;

/**Java representation of the message sent by FlowMon.	<br>
The corresponding struct in C is:						<p><b><pre>
struct upmt_flow_notif_data {
	TODO
};														</pre></b></p>*/
public class FlowNotifMessage
{
	public String srcAddress;
	public int srcPort;
	public String vipa;
	public int vipaPort; //TODO: si mette statica nel config? Tanto pu� essere sempre la stessa xk� il vipa cambia. Controllare se poi ci sono vari flussi da stesso vipa!!

	//Empty constructor, setters and getters for JSON serialization:
	public FlowNotifMessage(){}
	public String getSrcAddress() {return srcAddress;}
	public void setSrcAddress(String srcAddress) {this.srcAddress = srcAddress;}
	public int getSrcPort() {return srcPort;}
	public void setSrcPort(int srcPort) {this.srcPort = srcPort;}
	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}
	public int getVipaPort() {return vipaPort;}
	public void setVipaPort(int vipaPort) {this.vipaPort = vipaPort;}
}
