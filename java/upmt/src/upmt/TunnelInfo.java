package upmt;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import upmt.client.UPMTClient;
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
	
	private int tid=0;
	
	private UPMTClient upmtClient;

	public TunnelInfo(int status, String natAddr, int natPort, double pastEWMA_Delay, double pastEWMA_Loss, int tid, UPMTClient upmtClient) {
		this.status = status;
		this.natAddr = natAddr;
		this.natPort = natPort;
		this.pastEWMA_Delay = pastEWMA_Delay;
		this.pastEWMA_Loss = pastEWMA_Loss;
		this.tid=tid;
		this.upmtClient=upmtClient;
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

	public boolean isWaitingForKA() {
		return waitingForKA;
	}
	
	public static void logFile(String filename, String value){
		try {
			Writer output;
			output = new BufferedWriter(new FileWriter(filename, true));
			output.append(value + "\n");
			output.close();
			System.out.println("I've logged the value " + value + "...");
		} catch (Exception e) {
			System.out.println("ERROR ---> I've logged the value " + value + "...");
			System.out.println(e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * Calculate value of the EWMA and of the rating loss
	 */
	private void processEWMA(int delay){
		double numberRetry;
		//if(delay >= EWMA_Delay) alfa = 0.8;
		//else alfa = 0.2;
		alfa = 0.2;
		numberRetry = (double) this.numberRetry / 1000; //ottengo così il loss in percentuale 100,00
		if(firstTime){
			EWMA_Delay=delay + pastEWMA_Delay; 
			EWMA_Loss=(numberRetry/(numberRetry+1))+ pastEWMA_Loss;
			firstTime=false; 
			lastTime=System.currentTimeMillis();
		}
		else{
			time=System.currentTimeMillis();
			double exp= (time-lastTime)/SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL;
			EWMA_Delay=Math.rint(((1-(Math.pow(1-alfa, exp)))*delay+(Math.pow(1-alfa, exp))*EWMA_Delay)*1000)/1000;
			EWMA_Loss = Math.rint((((1-(Math.pow(1-alfa, exp)))*((numberRetry)/(numberRetry+1)))+(Math.pow(1-alfa, exp))*EWMA_Loss)*1000)/1000;
			lastTime=time;
		}
		
		/*String filename = "/home/upmt/Desktop/out/asy_multi_" + SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL + ".dat";
		String value = lastTime + "|" + EWMA_Delay + "|" + delay;
		logFile(filename, value);
		System.out.println(" ---> RTT: " + delay);
		
		if(delay > 350){
			System.out.println("Valore anomalo di DELAY....");
			this.upmtClient.stop(); 
		}*/
		
		String filename = "/home/upmt/Desktop/out/client_loss_" + SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL + "_" + alfa + ".txt";
		String value = lastTime + "|" + EWMA_Loss + "|" + numberRetry;
		logFile(filename, value);
		System.out.println(" ---> LOSS CLIENT: " + numberRetry);
		
	}

	/**
	 * set the last delay and the last number retry, and make the average(/EWMA/)
	 */
	public void setLastDelayAndNumberRetry(int delay,int numberRetry){
		lastDelay=delay;
		this.numberRetry=numberRetry;
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

	public int getTid() {
		return tid;
	}

	public void setTid(int tid) {
		this.tid = tid;
	}

}
