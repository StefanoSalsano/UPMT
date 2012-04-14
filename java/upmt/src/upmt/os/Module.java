package upmt.os;

public class Module
{
	static
	{
		System.out.print("Loading upmtconfig library... ");
		System.loadLibrary("upmtconf");
		System.out.println("library successfully loaded!");
	}
	
	private static String vipaFix;
	public static String getVipaFix() {return vipaFix;}
	public static void setVipaFix(String vipaFix) {Module.vipaFix = vipaFix;}

	private static String serverIfName;
	public static String getServerIfName() {return serverIfName;}
	public static void setServerIfName(String serverIfName) {Module.serverIfName = serverIfName;}

	public static native String upmtconf(String[] param);

	public static int getUpmtParameter(String upmtRes, String parameter)
	{
		String markIdentifier = parameter+":";
		int markStartIndex = upmtRes.indexOf(markIdentifier) + markIdentifier.length();
		int markEndIndex = upmtRes.indexOf("\n",markStartIndex);
		if(markStartIndex < markIdentifier.length()) return -1;
		try {return Integer.parseInt(upmtRes.substring(markStartIndex, markEndIndex).trim());}
		catch(NumberFormatException ex) {return -1;}
	}

	public static String[] getEntryList(String upmtRes)
	{return upmtRes.split("[^0-9]*",2)[1].split("\n[^0-9]", 2)[0].split("\n");}
}
