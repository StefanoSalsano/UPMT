package upmt.fixedHost.vipa.monitor.impl.message;

/** Java representation of the message received by FlowMon.*/
public class FlowMonMessage
{
	public static final int CMD_ADD_AN = 0;
	public static final int CMD_SET_VIPA_RANGE = 1;

	public int command;
	public String data;

	public FlowMonMessage(int command, String data)
	{
		this.command = command;
		this.data = data;		
	}

	//Empty constructor, setters and getters for JSON serialization:
	public FlowMonMessage(){}
	public int getCommand() {return command;}
	public void setCommand(int command) {this.command = command;}
	public String getData() {return data;}
	public void setData(String data) {this.data = data;}
}
