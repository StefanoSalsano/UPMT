package upmt.client.application.monitor.impl.standard;

public class AppMessage implements AppMonMessage
{
	public int msgtype;
	public int command;
	public String appname;
	public int tid;
	public String VIPA; //for RME


	public AppMessage(int command, String appname, int tid)
	{
		this.msgtype = Constants.MSG_TYPE_APP;
		this.command = command;
		this.appname = appname;
		this.tid = tid;
		this.VIPA = null;
	}

	public AppMessage(int command, String VIPA, String appname, int tid)
	{
		this.msgtype = Constants.MSG_TYPE_APP;
		this.command = command;
		this.appname = appname;
		this.tid = tid;
		this.VIPA = VIPA;
	}

	
	//Empty constructor, setters and getters for JSON serialization:
	public AppMessage() {}
	public int getCommand() {return command;}
	public void setCommand(int command) {this.command = command;}
	public String getAppname() {return appname;}
	public void setAppname(String appname) {this.appname = appname;}
	public int getTid() {return tid;}
	public void setTid(int tid) {this.tid = tid;}
	public int getMsgtype() {return msgtype;}
	public void setMsgtype(int msgtype) {this.msgtype = msgtype;}
	public String getVIPA() { return VIPA; }
	public void setVIPA(String vIPA) { VIPA = vIPA; }


}
