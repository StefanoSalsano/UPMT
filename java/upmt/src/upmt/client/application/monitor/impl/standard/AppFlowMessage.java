package upmt.client.application.monitor.impl.standard;

public class AppFlowMessage implements AppMonMessage
{
	public int msgtype;
	public int command;
	public String daddr;
	public int tid;

	public AppFlowMessage(int command, String daddr, int tid)
	{
		this.msgtype = Constants.MSG_TYPE_APP_FLOW;
		this.command = command;
		this.daddr = daddr;
		this.tid = tid;
	}

	//Empty constructor, setters and getters for JSON serialization:
	public AppFlowMessage() {}
	public int getCommand() {return command;}
	public void setCommand(int command) {this.command = command;}
	public int getTid() {return tid;}
	public void setTid(int tid) {this.tid = tid;}
	public int getMsgtype() {return msgtype;}
	public void setMsgtype(int msgtype) {this.msgtype = msgtype;}
	public String getDaddr() {return daddr;}
	public void setDaddr(String daddr) {this.daddr = daddr;}
}
