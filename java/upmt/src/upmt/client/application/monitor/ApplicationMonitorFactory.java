package upmt.client.application.monitor;

import upmt.client.application.monitor.impl.SocketApplicationMonitor;

public class ApplicationMonitorFactory
{
	private static final String PACKAGE = ApplicationMonitor.class.getPackage().getName() + ".impl.";
	private static final String SUFFIX = ApplicationMonitor.class.getSimpleName();
	
	static public ApplicationMonitor getApplicationMonitor(String type)
	{
		try {return (ApplicationMonitor) Class.forName(PACKAGE + type + SUFFIX).newInstance();}
		
		catch (ClassNotFoundException e)
			{System.err.println("Can not find a ApplicationMonitor implementation for the specified type ("+type+")");return null;}
		catch (InstantiationException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new ApplicationMonitor");return null;}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new ApplicationMonitor");return null;}
		catch (ClassCastException e)
			{System.err.println("Error in " + type + SUFFIX + ": The class is not a ApplicationMonitor");return null;}
	}
}
