package upmt.client.application.manager.impl;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;
import info.monitorenter.gui.chart.io.ADataCollector;


public class BandWidthCollector extends ADataCollector {
	private final long initialTime;
	private TracePoint2D point2d;

	public BandWidthCollector(ITrace2D trace, long latency) {
		super(trace, latency);
		this.initialTime = BandWidthGrapher.initialTime;
	}

	@Override
	public ITracePoint2D collectData() {
		return point2d;
	}

}
