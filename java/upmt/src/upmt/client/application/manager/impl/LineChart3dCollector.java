package upmt.client.application.manager.impl;

import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.sip.SipSignalManager;
import upmt.server.rme.RMETunnelInfo;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;

public class LineChart3dCollector {

	private String ANinterf;
	private final long initialTime;
	private ITracePoint2D point2d;
	private double time, ewma;
	
	public LineChart3dCollector(final int latency){
		this.initialTime = LineChart3d.initialTime;	
	}
	
	public ITracePoint2D collectData(){
		if(!UPMTClient.getRME()){
			synchronized(SipSignalManager.getRemoteTidStatusTable()){
				long now = System.currentTimeMillis();
				TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(ANinterf);
				this.ewma=0.0;
				if(ti!=null){
					ewma=ti.getEWMA_Delay();
				}
				this.time = now - initialTime;
				point2d = new TracePoint2D(time,ewma);	
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
					this.time = now - initialTime;
					point2d = new TracePoint2D(time,ewma);	
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
					this.time = now - initialTime;
					point2d = new TracePoint2D(time,ewma);	
					return point2d;
					
				}
			}
		}
	}
	public void setANinterf(String ANinterf){
		this.ANinterf = ANinterf;
	}

}
