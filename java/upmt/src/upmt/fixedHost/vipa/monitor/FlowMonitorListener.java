package upmt.fixedHost.vipa.monitor;

public interface FlowMonitorListener
{
	public void flowOpened(String srcAddress, int srcPort, String vipa, int vipaPort);
}
