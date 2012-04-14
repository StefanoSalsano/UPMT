package upmt.client.network;

public class NetworkMonitorFactory
{
	private static final String PACKAGE = NetworkMonitor.class.getPackage().getName() + ".impl.";
	private static final String SUFFIX = NetworkMonitor.class.getSimpleName();
	
	static public NetworkMonitor getMonitor(String os)
	{
		try {return (NetworkMonitor) Class.forName(PACKAGE + os + SUFFIX).newInstance();}
		
		catch (ClassNotFoundException e)
			{System.err.println("Can not find a NetworkMonitor implementation for the specified OS (\""+os+"\")"); System.exit(-1);}
		catch (InstantiationException e)
			{System.err.println("Error in " + os + SUFFIX + ": Can not istanziate a new Monitor"); System.exit(-2);}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + os + SUFFIX + ": Can not istanziate a new Monitor"); System.exit(-2);}
		catch (ClassCastException e)
			{System.err.println("Error in " + os + SUFFIX + ": The class is not a Monitor"); System.exit(-3);}

		return null;
	}
}
