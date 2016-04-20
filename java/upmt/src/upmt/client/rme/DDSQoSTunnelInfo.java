package upmt.client.rme;

public class DDSQoSTunnelInfo {


	private int tid;
	private double ewma_delay;
	private boolean isServer;
	private int numberKeepAlive;

	public DDSQoSTunnelInfo(int tid, double ewma_delay, boolean isServer, int numberKeepAlive) {
		this.ewma_delay = ewma_delay;
		this.tid = tid;
		this.isServer = isServer;
		this.numberKeepAlive = numberKeepAlive;
	}

	public int getTid() {
		return tid;
	}

	public void setTid(int tid) {
		this.tid = tid;
	}

	public double getEwma_delay() {
		return ewma_delay;
	}

	public void setEwma_delay(double ewma_delay) {
		this.ewma_delay = ewma_delay;
	}

	public boolean isServer() {
		return isServer;
	}

	public void setServer(boolean isServer) {
		this.isServer = isServer;
	}

	public int getNumberKeepAlive() {
		return numberKeepAlive;
	}

	public void setNumberKeepAlive(int numberKeepAlive) {
		this.numberKeepAlive = numberKeepAlive;
	}


}
