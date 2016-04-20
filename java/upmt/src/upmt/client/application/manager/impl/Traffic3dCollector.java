package upmt.client.application.manager.impl;

import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;
import upmt.os.Shell;


public class Traffic3dCollector {

	private final long initialTime;
	private long lastTime;
	private double lastValue;
	private String iface;
	private boolean firstTime;
	private ITracePoint2D point2d;
	
	
	public Traffic3dCollector(final int latency){
			
		
		// this.initialTime = System.currentTimeMillis();
		this.initialTime = TrafficGrapher3d.initialTime;	
		this.lastTime = System.currentTimeMillis();
		this.lastValue = 0.0;
		this.firstTime = true;
	}
	
	public ITracePoint2D collectData(){
		String bytes = Shell.executeCommand( new String[] { "sh", "-c", "ifconfig " + iface + " | grep bytes" }).trim();
		int rxBegin = bytes.indexOf(":") + 1;
		int rxEnd = bytes.indexOf(" ", rxBegin);
		String rxS = bytes.substring(rxBegin, rxEnd);
		/**
		 * received bytes
		 */
		long rx = Long.parseLong(rxS);

		int txBegin = bytes.indexOf(":", rxEnd) + 1;
		int txEnd = bytes.indexOf(" ", txBegin);
		String txS = bytes.substring(txBegin, txEnd);
		/**
		 * transmitted bytes
		 */
		long tx = Long.parseLong(txS);

		long now = System.currentTimeMillis();

		double speed = 0;

		if (firstTime) {
			firstTime = false;
		} else {
			double value = tx + rx - lastValue;
			double timeInterval = now - lastTime;
			speed = value*8000 / timeInterval; //speed is in b/s
		}
		lastValue = tx + rx;
		lastTime = now;

		double t = now - initialTime;
		point2d = new TracePoint2D(t,speed);
		return point2d;
	}
	
	public void setInterf(String interf){
		this.iface = interf;
	}

}
