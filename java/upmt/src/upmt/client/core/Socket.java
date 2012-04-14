package upmt.client.core;

//#ifndef ANDROID
import org.freedesktop.dbus.Tuple;
//#endif

public class Socket
//#ifndef ANDROID
extends Tuple
//#endif
{
	public String proto, vipa, dstIp;
	public int srcPort, dstPort;

	public Socket(){}
	public Socket(String proto, String vipa, String dstIp, int srcPort, int dstPort)
	{
		this.proto = proto;
		this.vipa = vipa;
		this.dstIp = dstIp;
		this.srcPort = srcPort;
		this.dstPort = dstPort;
	}

	public String id() {return proto+"-"+vipa+"-"+dstIp+"-"+srcPort+"-"+dstPort;}
	public String tostring() {return proto+" - "+vipa+" - "+dstIp+" - "+srcPort+" - "+dstPort;}

	public String getProto() {return proto;}
	public void setProto(String proto) {this.proto = proto;}

	public String getVipa() {return vipa;}
	public void setVipa(String vipa) {this.vipa = vipa;}

	public String getDstIp() {return dstIp;}
	public void setDstIp(String dstIp) {this.dstIp = dstIp;}

	public int getSrcPort() {return srcPort;}
	public void setSrcPort(int srcPort) {this.srcPort = srcPort;}

	public int getDstPort() {return dstPort;}
	public void setDstPort(int dstPort) {this.dstPort = dstPort;}
}
