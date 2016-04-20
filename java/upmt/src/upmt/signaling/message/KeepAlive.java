package upmt.signaling.message;


public class KeepAlive implements Signal {

	public String sipId;
	public int tunnelId;
	public int sipSignalingPort;
	public int lastDelay;
	public int numberRetry;
	public double EWMA_delay;
	public double EWMA_loss;
	
	
	public KeepAlive(){}	
	public KeepAlive(String sipId, int tunnelId, int sipSignalingPort)
	{
		this.sipId = sipId;
		this.tunnelId = tunnelId;
		this.sipSignalingPort = sipSignalingPort;
	}
	
	public KeepAlive(String sipId, int tunnelId, int sipSignalingPort, int lastDelay, int numberRetry, double EWMA_delay, double EWMA_loss)
	{
		this.sipId = sipId;
		this.tunnelId = tunnelId;
		this.sipSignalingPort = sipSignalingPort;
		this.lastDelay = lastDelay;
		this.numberRetry = numberRetry;
		this.EWMA_delay = EWMA_delay;
		this.EWMA_loss = EWMA_loss;
	}

	public void setSipId(String sipId) {this.sipId = sipId;}
	public String getSipId() {return sipId;}

	public int getTunnelId() {return tunnelId;}
	public void setTunnelId(int tunnelId) {this.tunnelId = tunnelId;}
	
	public int getSipSignalingPort() {return sipSignalingPort;}
	public void setSipSignalingPort(int sipSignalingPort) {this.sipSignalingPort = sipSignalingPort;}
	
	public int getLastDelay() {
		return lastDelay;
	}
	public void setLastDelay(int lastDelay) {
		this.lastDelay = lastDelay;
	}
	public int getNumberRetry() {
		return numberRetry;
	}
	public void setNumberRetry(int numberRetry) {
		this.numberRetry = numberRetry;
	}
	public double getEWMA_delay() {
		return EWMA_delay;
	}
	public void setEWMA_delay(double eWMA_delay) {
		EWMA_delay = eWMA_delay;
	}
	public double getEWMA_loss() {
		return EWMA_loss;
	}
	public void setEWMA_loss(double eWMA_loss) {
		EWMA_loss = eWMA_loss;
	}

}
