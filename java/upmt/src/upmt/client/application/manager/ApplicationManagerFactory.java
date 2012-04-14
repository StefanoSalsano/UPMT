package upmt.client.application.manager;

public class ApplicationManagerFactory
{
	//#ifndef ANDROID
	private static final String PACKAGE = ApplicationManager.class.getPackage().getName() + ".impl.";
	private static final String SUFFIX = ApplicationManager.class.getSimpleName();
	//#else
//	private static final String PACKAGE = "com.and.gui.activity.";
//	private static final String SUFFIX = "";
	//#endif
	
	static public ApplicationManager getApplicationManager(String type)
	{
		try {return (ApplicationManager) Class.forName(PACKAGE + type + SUFFIX).newInstance();}
		
		catch (ClassNotFoundException e)
			{System.err.println("Can not find a ApplicationManager implementation for the specified type ("+type+")");return null;}
		catch (InstantiationException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new ApplicationManager");return null;}
		catch (IllegalAccessException e)
			{System.err.println("Error in " + type + SUFFIX + ": Can not istanziate a new ApplicationManager");return null;}
		catch (ClassCastException e)
			{System.err.println("Error in " + type + SUFFIX + ": The class is not a ApplicationManager");return null;}
	}
}
