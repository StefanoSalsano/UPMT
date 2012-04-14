package upmt.fixedHost.vipa.monitor;

public interface FlowMonitor
{
	/** Set a listener for any socket list changing */
	public void startListen(FlowMonitorListener listener);

	/** Remove the listener for the monitor */
	public void stop();

	public void addAN(String ANAddress);
	public void setVipaRange(String startAddress, String endAddress);
}
