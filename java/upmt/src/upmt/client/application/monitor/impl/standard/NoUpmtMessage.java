package upmt.client.application.monitor.impl.standard;

public class NoUpmtMessage implements AppMonMessage
{
	public int msgtype;
	public int command;
	public String appname;
	
	public NoUpmtMessage(int command, String appname)
	{
		this.msgtype = Constants.MSG_TYPE_NO_UPMT;
		this.command = command;
		this.appname = appname;
	}

	//Empty constructor, setters and getters for JSON serialization:
	public NoUpmtMessage() {}
	public int getCommand() {return command;}
	public void setCommand(int command) {this.command = command;}
	public String getAppname() {return appname;}
	public void setAppname(String appname) {this.appname = appname;}
	public int getMsgtype() {return msgtype;}
	public void setMsgtype(int msgtype) {this.msgtype = msgtype;}
}
