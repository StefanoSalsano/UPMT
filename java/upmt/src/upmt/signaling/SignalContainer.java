package upmt.signaling;

import upmt.signaling.message.Signal;

public class SignalContainer
{
	private String mType;
	private Signal mContent;

	public SignalContainer(Signal signal)
	{
		this.mContent = signal;
		this.mType = signal.getClass().getSimpleName();
	}

	public void setMType(String mType) {this.mType = mType;}
	public String getMType(){return mType;}

	public void setMContent(Signal mContent) {this.mContent = mContent;}
	public Signal getMContent() {return mContent;}
}
