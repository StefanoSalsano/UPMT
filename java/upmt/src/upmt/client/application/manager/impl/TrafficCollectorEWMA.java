package upmt.client.application.manager.impl;

import upmt.TunnelInfo;
import upmt.client.sip.SipSignalManager;
import upmt.os.Shell;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;
import info.monitorenter.gui.chart.io.ADataCollector;

public class TrafficCollectorEWMA extends ADataCollector{
	
	private final long initialTime;
	private long lastTime;
	private double lastValue;
	private String ANinterf;
	private boolean firstTime;

	
	public TrafficCollectorEWMA(final ITrace2D trace, final int latency, String ANinterf) {
		super(trace, latency);
		this.ANinterf = ANinterf;
		// this.initialTime = System.currentTimeMillis();
		this.initialTime = TrafficGrapherEWMA.initialTime;
		this.lastTime = System.currentTimeMillis();
		this.lastValue = 0.0;
		this.firstTime = true;
	}
	
	@Override
	public ITracePoint2D collectData() {
		synchronized(SipSignalManager.getRemoteTidStatusTable()){
		long now = System.currentTimeMillis();
		TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(ANinterf);
		double ewma=0.0;
		if(ti!=null){
			   ewma=ti.getEWMA_Delay();
		}
		
		double t = now - initialTime;
		return new TracePoint2D(t, ewma);
		//return new TracePoint2D(t, speed);
		}
	}
}
