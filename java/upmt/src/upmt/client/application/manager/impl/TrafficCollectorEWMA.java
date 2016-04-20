package upmt.client.application.manager.impl;

import java.util.Iterator;
import java.util.Vector;

import upmt.TunnelInfo;
import upmt.client.sip.SipSignalManager;
import upmt.os.Shell;
import upmt.server.rme.RMETunnelInfo;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;
import info.monitorenter.gui.chart.io.ADataCollector;
import upmt.client.UPMTClient;

public class TrafficCollectorEWMA extends ADataCollector{
	
	private final long initialTime;
	private long lastTime;
	private double lastValue;
	private String ANinterf;
	private TracePoint2D point2d;
	double ewma,t;

	
	public TrafficCollectorEWMA(final ITrace2D trace, final int latency, String ANinterf) {
		super(trace, latency);
		this.ANinterf = ANinterf;
		this.initialTime = TrafficGrapherEWMA.initialTime;
		this.lastTime = System.currentTimeMillis();
		this.lastValue = 0.0;	
	}
	
	@Override
	public ITracePoint2D collectData() {
		if(!UPMTClient.getRME()){
			synchronized(SipSignalManager.getRemoteTidStatusTable()){
				long now = System.currentTimeMillis();
				TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(ANinterf);
				this.ewma=0.0;
				if(ti!=null){
					ewma=ti.getEWMA_Delay();
				}
				this.t = now - initialTime;
				point2d = new TracePoint2D(t,ewma);	
				return point2d;
			}
		}else{
			String endPointAddress=ANinterf.substring(0, ANinterf.indexOf(":"));
			if(UPMTClient.getCfgANList().contains(endPointAddress)) {
				synchronized(SipSignalManager.getRemoteTidStatusTable()){
					long now = System.currentTimeMillis();
					TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(ANinterf);
					this.ewma=0.0;
					if(ti!=null){
						ewma=ti.getEWMA_Delay();
					}
					this.t = now - initialTime;
					point2d = new TracePoint2D(t,ewma);	
					return point2d;
				}
	
			}else{
				synchronized(UPMTClient.getRMERemoteTidStatusTable()){
					RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(ANinterf);
					long now = System.currentTimeMillis();
					
					this.ewma=0.0;
					if(ti!=null){
						ewma=ti.getEWMA_delay();
					}
					this.t = now - initialTime;
					point2d = new TracePoint2D(t,ewma);	
					return point2d;
					
				}
			}
		}
		
	}
	public TracePoint2D getPoint2D(){
		return point2d;
	}
}
