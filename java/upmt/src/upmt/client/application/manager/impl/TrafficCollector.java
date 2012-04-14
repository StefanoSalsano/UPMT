package upmt.client.application.manager.impl;

import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.TracePoint2D;
import info.monitorenter.gui.chart.io.ADataCollector;
import upmt.os.Shell;

public class TrafficCollector extends ADataCollector {
	private final long initialTime;
	private long lastTime;
	private double lastValue;
	private String iface;
	private boolean firstTime;

	public TrafficCollector(final ITrace2D trace, final int latency, String iface) {
		super(trace, latency);
		this.iface = iface;
		// this.initialTime = System.currentTimeMillis();
		this.initialTime = TrafficGrapher.initialTime;
		this.lastTime = System.currentTimeMillis();
		this.lastValue = 0.0;
		this.firstTime = true;
	}

	@Override
	public ITracePoint2D collectData() {
		// ifconfig eth0 | grep bytes
		// RX bytes:75174 (73.4 KB) TX bytes:21982 (21.4 KB)

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
		return new TracePoint2D(t, speed);
	}
}
