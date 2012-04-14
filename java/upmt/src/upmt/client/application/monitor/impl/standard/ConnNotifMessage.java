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

	//Empty constructor, setters and getters for JSON serialization:
	public ConnNotifMessage() {}
	public Socket getKey() {return key;}
	public void setKey(Socket key) {this.key = key;}
	public String getAppname() {return appname;}
	public void setAppname(String appname) {this.appname = appname;}
	public int getTid() {return tid;}
	public void setTid(int tid) {this.tid = tid;}
}
