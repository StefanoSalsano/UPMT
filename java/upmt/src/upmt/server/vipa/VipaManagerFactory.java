package upmt.server.vipa;

public class VipaManagerFactory
{
	private static final String PACKAGE = VipaManager.class.getPackage().getName() + ".impl.";
	private static final String SUFFIX = VipaManager.class.getSimpleName();
	
	static public VipaManager getVipaManager(String policy, String file)
	{
		try
		{
			VipaManager manager = (VipaManager) Class.forName(PACKAGE + policy + SUFFIX).newInstance();
			manager.loadConfig(file);
			return manager;
		}
		
		catch (ClassNotFoundException e)
			{System.err.println("Can not find a VipaManager implementation for the specified policy ("+policy+")");System.exit(-1);}
		catch (InstantiationException e)
			{System.err.println("Error in " + policy + SUFFIX + ": Can not istanziate a new VipaManager");System.exit(-2);}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + policy + SUFFIX + ": Can not istanziate a new VipaManager");System.exit(-2);}
		catch (ClassCastException e)
			{System.err.println("Error in " + policy + SUFFIX + ": The class is not a VipaManager");System.exit(-3);}

		return null;
	}
}
