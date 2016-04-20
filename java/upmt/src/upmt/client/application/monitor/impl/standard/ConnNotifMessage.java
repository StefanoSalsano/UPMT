package upmt.client.application.monitor.impl.standard;

import upmt.client.core.Socket;

/**Java representation of the message sent by AppMon.	<br>
The corresponding struct in C is:						<p><b><pre>
struct upmt_conn_notif_data {
	struct upmt_key key;
	char appname[MAX_APPNAME_LENGTH];
	int tid;
};														</pre></b></p>*/
public class ConnNotifMessage
{
	public Socket key;
	public String appname;
	public int tid;
	public String command;
	public String ifname;
	public String daddr;
	public int delay;
	public int loss;
	public int ewmadelay;
	public int ewmaloss;

	//Empty constructor, setters and getters for JSON serialization:
	public ConnNotifMessage() {}
	public Socket getKey() {return key;}
	public void setKey(Socket key) {this.key = key;}
	public String getAppname() {return appname;}
	public void setAppname(String appname) {this.appname = appname;}
	public int getTid() {return tid;}
	public void setTid(int tid) {this.tid = tid;}
	public String getCommand() {return command;}
	public void setCommand(String command) {this.command=command;}
	public String getIfname() {
		return ifname;
	}
	public void setIfname(String ifname) {
		this.ifname = ifname;
	}
	public String getDaddr() {
		return daddr;
	}
	public void setDaddr(String daddr) {
		this.daddr = daddr;
	}
	public int getDelay() {
		return delay;
	}
	public void setDelay(int delay) {
		this.delay = delay;
	}
	public int getLoss() {
		return loss;
	}
	public void setLoss(int loss) {
		this.loss = loss;
	}
	public int getEwmadelay() {
		return ewmadelay;
	}
	public void setEwmadelay(int ewmadelay) {
		this.ewmadelay = ewmadelay;
	}
	public int getEwmaloss() {
		return ewmaloss;
	}
	public void setEwmaloss(int ewmaloss) {
		this.ewmaloss = ewmaloss;
	}
}
