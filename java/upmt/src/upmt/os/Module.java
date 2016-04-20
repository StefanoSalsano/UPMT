package upmt.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

	//public static native String upmtconf(String[] param);
	
	public static String upmtconf(String[] param){
		return upmtconf(param, false);
	}
	
	public static String upmtconf(String[] param, boolean isNative) {
		String response = "";
		if(!isNative) {
			try {
				String command ="upmtconf";
				for(String singleParam: param) { command+=" "+singleParam; }
//				System.err.println("richiesta a upmtconf ---> " + command);
				Process p = Runtime.getRuntime().exec(command);
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				boolean check = true;
				while(check) {
					String line = br.readLine();
					if(line!=null) {
						response += "\n"+line;
					}
					else {
						check = false;
					}
				}
//				System.err.println("risposta da upmtconf ---> "+response);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			response = upmtconf(param);
		}
		return response;
	}

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
