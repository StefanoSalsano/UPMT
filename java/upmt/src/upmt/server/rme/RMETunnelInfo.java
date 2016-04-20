package upmt.server.rme;

import org.jsonref.JSONArray;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;
import org.zoolu.tools.Log;

import upmt.client.UPMTClient;
import upmt.client.sip.SipSignalManager;
import upmt.server.gui.TunnelInfo;

public class RMETunnelInfo extends TunnelInfo {
	
	public final static int NO_TUNNEL = 0;
	public final static int TUNNEL_SETUP = 1;
	public final static int CLOSED = 2;

	private int status ;

	private static boolean firstTime = true;

	// Exponential Smoothing of the EWMA
	private double alfa;
	
	// Values for EWMA
	private double EWMA_Delay;
	private double EWMA_Loss;
	private double pastEWMA_Delay;
	private double pastEWMA_Loss;
	
	private double time;
	private double lastTime;

	private int lastDelay;
	private int numberRetry;
	private double EWMA_delay;
	private double EWMA_loss;
	private int tid;
	private int keepaliveNumber;
	
	private static long INTERVAL = 5000;


	public RMETunnelInfo(int serverTunnelID, String yourAddress, int yourPort, String sipId, String vipa, long lastAck, int status, int tid) {
		super(serverTunnelID, yourAddress, yourPort, sipId, vipa, lastAck);
		this.status = status;
		this.lastDelay = 0;
		this.numberRetry = 0;
		this.pastEWMA_Delay = 0;
		this.pastEWMA_Loss = 0;
		this.EWMA_Delay=0;
		this.EWMA_Loss=0;
		this.tid = tid;
		this.keepaliveNumber=0;
	}

	/**
	 * @return the status
	 */
	public int getStatus() {
		return status;
	}
	
	public void setFirstTime(boolean bool) {
		firstTime= bool;
	}
	
	public boolean getFirstTime() {
		return firstTime;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(int status) {
		this.status = status;
		if(status==CLOSED) 
			firstTime=true;
	}

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
		return this.EWMA_delay;
	}

	public void setEWMA_delay(double eWMA_delay) {
		this.EWMA_delay = eWMA_delay;
	}

	public double getEWMA_loss() {
		return EWMA_loss;
	}

	public void setEWMA_loss(double eWMA_loss) {
		this.EWMA_loss = eWMA_loss;
	}

	public int getTid() {
		return tid;
	}

	public void setTid(int tid) {
		this.tid = tid;
	}
	
	/**
	 * set the last delay and the last number retry, and make the average(/EWMA/)
	 */
	public void setLastDelayAndNumberRetry(int delay,int numberRetry){
		this.lastDelay=delay;
		this.numberRetry=numberRetry;
			processEWMA(delay);
	}
	
	public int getKeepaliveNumber() {
		return keepaliveNumber;
	}

	public void setKeepaliveNumber(int keepaliveNumber) {
		this.keepaliveNumber = keepaliveNumber;
	}
	
	public void incrementKeepaliveNumber() {
		if(this.keepaliveNumber==Integer.MAX_VALUE-2) {
			this.keepaliveNumber=3;
		}
		this.keepaliveNumber++;
	}

	@SuppressWarnings("unused")
	public static JSONObject getRmeTunnelInfo(String msgBody) {
		if (msgBody==null) {
			printLog("RMEServer: keepalive msgBody is null ", Log.LEVEL_LOWER);
			return null;
		}
		try {
			JSONArray array = null ;
			try {
				array = new JSONArray(msgBody);
			} catch (NullPointerException e) {
				printLog("NullPointerException, msgBody: " + msgBody, Log.LEVEL_LOWER);
			}

			for(int i=0; i<array.length(); i++) {
				JSONObject signalContainer = array.getJSONObject(i);
				JSONObject jsonSignal = signalContainer.getJSONObject("MContent");
				return jsonSignal;
			}
		}
		catch (JSONException e) {return null;}
		return null;
	}
	
	
	private static void printLog(String text, int logLevel) {
		UPMTClient.printStaticLog("[UpmtSignal] "+text, logLevel);
	}
	
	/**
	 * Calculate value of the EWMA and of the rating loss
	 */
	private void processEWMA(int delay){
		double numberRetry;
		alfa = 0.2;
		numberRetry = (double) this.numberRetry / 1000; //ottengo così il loss in percentuale 100,00
		if(firstTime){
			this.EWMA_Delay=delay + pastEWMA_Delay; 
			this.EWMA_Loss=(numberRetry/(numberRetry+1))+ pastEWMA_Loss;
			firstTime=false; 
			lastTime=System.currentTimeMillis();
		}
		else{
			time=System.currentTimeMillis();
			double exp= (time-lastTime)/INTERVAL;
			if((int)exp==0) {
				return;
			}
			this.EWMA_Delay=Math.rint(((1-(Math.pow(1-alfa, exp)))*delay+(Math.pow(1-alfa, exp))*this.EWMA_Delay)*1000)/1000;
			this.EWMA_Loss = Math.rint((((1-(Math.pow(1-alfa, exp)))*((numberRetry)/((numberRetry+1))))+(Math.pow(1-alfa, exp))*this.EWMA_Loss)*1000)/1000;
			setEWMA_delay(EWMA_Delay);
			lastTime=time;
		}
		
		String filename = "/home/upmt/Desktop/out/server_loss_" + SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL + "_" + alfa + ".txt";
		String value = lastTime + "|" + EWMA_Loss + "|" + numberRetry;
		upmt.TunnelInfo.logFile(filename, value);
		System.out.println(" ---> LOSS SERVER: " + numberRetry);
		
	}

}
