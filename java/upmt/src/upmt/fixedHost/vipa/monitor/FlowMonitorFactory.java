package upmt.fixedHost.vipa.monitor;

public class FlowMonitorFactory
{
	private static final String PACKAGE = FlowMonitor.class.getPackage().getName() + ".impl.";
	private static final String SUFFIX = FlowMonitor.class.getSimpleName();
	
	static public FlowMonitor getFlowMonitor(String type)
	{
		try {return (FlowMonitor) Class.forName(PACKAGE + type + SUFFIX).newInstance();}
		
		catch (ClassNotFoundException e)
			{System.err.println("Can not find a FlowMonitor implementation for the specified type ("+type+")");return null;}
		catch (InstantiationException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new FlowMonitor");return null;}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new FlowMonitor");return null;}
		catch (ClassCastException e)
			{System.err.println("Error in " + type + SUFFIX + ": The class is not a FlowMonitor");return null;}
	}
}
