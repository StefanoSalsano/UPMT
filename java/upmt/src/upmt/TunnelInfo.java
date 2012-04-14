package upmt;


import upmt.client.sip.SipSignalManager;

public class TunnelInfo {

	public final static int NO_TUNNEL = 0;
	public final static int TUNNEL_SETUP = 1;
	public final static int CLOSED = 2;

	private int status ;

	private String natAddr = null;
	private int natPort = -1;

	private boolean waitingForKA = false;
	private boolean firstTime=true;

	// Exponential Smoothing of the EWMA
	private double alfa;

	// Values for EWMA
	private double EWMA_Delay=0;
	private double EWMA_Loss=0;
	private double pastEWMA_Delay = 0;
	private double pastEWMA_Loss = 0;


	private double time;
	private double lastTime;

	private int lastDelay=0;
	private int numberRetry = 0;

	private Integer[] arrayDelay = new Integer[6];
	private int arrayIterator = 0;
	private double average = 0;



	public TunnelInfo(int status, String natAddr, int natPort, double pastEWMA_Delay, double pastEWMA_Loss) {
		this.status = status;
		this.natAddr = natAddr;
		this.natPort = natPort;
		this.pastEWMA_Delay = pastEWMA_Delay;
		this.pastEWMA_Loss = pastEWMA_Loss;
	}

	/**
	 * make average of the last 6 delay
	 */
	private void average(){
		int somma=0;

		for(int i=0;i<arrayDelay.length;i++){
			if(arrayDelay[i] != null){
				somma += arrayDelay[i];
			}
		}
		average= ((int)((somma/arrayDelay.length)*1000))/1000;
	}

	/**
	 * @return the average of the last 6 delay
	 */
	public double getAverage() {
		return average;
	}

	public double getEWMA_Delay(){
		return EWMA_Delay;
	}

	/**
	 * @return rating-loss of the last keepALive in the SIP
	 */
	public double getEWMA_Loss(){
		return EWMA_Loss * 100;
	}

	/**
	 * @return the last delay
	 */
	public int getLastDelay() {
		return lastDelay;
	}

	public String getNatAddr() {
		return natAddr;
	}

	public int getNatPort() {
		return natPort;
	}

	/**
	 * @return numberRetry of the last keepALive
	 */
	public int getNumberRetry() {
		return numberRetry;
	}

	/**
	 * @return the status
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * insert the last delay in the array delay
	 */
	private void insertToArray(int delay){
		arrayDelay[arrayIterator]=delay;
		arrayIterator++;
		if(arrayIterator>5)
			arrayIterator=0;
	}

	public boolean isWaitingForKA() {
		return waitingForKA;
	}

	/**
	 * Calculate value of the EWMA and of the rating loss
	 */
	private void processEWMA(int delay){
		if(firstTime){
			alfa=0.1; 
			EWMA_Delay=average + pastEWMA_Delay; 
			EWMA_Loss=(numberRetry/(numberRetry+1)) + pastEWMA_Loss; 
			firstTime=false; 
			lastTime=System.currentTimeMillis();
		}
		else{
			time=System.currentTimeMillis();

			double exp= (time-lastTime)/SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL;
			EWMA_Delay=Math.rint(((1-(Math.pow(1-alfa, exp)))*delay+(Math.pow(1-alfa, exp))*EWMA_Delay)*1000)/1000;
			EWMA_Loss = Math.rint((((1-(Math.pow(1-alfa, exp)))*(((double)numberRetry)/((double)(numberRetry+1))))+(Math.pow(1-alfa, exp))*EWMA_Loss)*1000)/1000;

			lastTime=time;
		}
	}

	/**
	 * set the last delay and the last number retry, and make the average(/EWMA/)
	 */
	public void setLastDelayAndNumberRetry(int delay,int numberRetry){
		lastDelay=delay;
		this.numberRetry=numberRetry;
		insertToArray(delay);
		average();
		processEWMA(delay);
	}

	public void setNatAddr(String natAddr) {
		this.natAddr = natAddr;
	}
	public void setNatPort(int natPort) {
		this.natPort = natPort;
	}
	/**
	 * @param status the status to set
	 */
	public void setStatus(int status) {
		this.status = status;
		if(status==CLOSED) 
			firstTime=true;
	}

	public void setWaitingForKA(boolean waitingForKA) {
		this.waitingForKA = waitingForKA;
	}

}