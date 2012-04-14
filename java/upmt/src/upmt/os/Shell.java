package upmt.os;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;

public class Shell
{
	public static boolean insertStringInFile(String filePath, int lineno, String lineToBeInserted)
	{
		try
		{
			//#ifdef ANDROID
//			 executeRootCommand(new String[]{"mount -o remount,rw -t yaffs2 /dev/block/mtdblock3 /system"});
			//#endif
			File inFile = new File(filePath), outFile = new File(filePath+"-new");
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
			PrintWriter out = new PrintWriter(new FileOutputStream(outFile));
			
			for(String line = in.readLine();line != null;line = in.readLine())
				if(line.equals(lineToBeInserted)) {in.close();out.close();outFile.delete();return false;} //linea gia' presente
				else out.println(((lineno-- == 0)?lineToBeInserted+"\n":"")+line);

			out.flush(); out.close(); in.close();

			inFile.delete();
			outFile.renameTo(inFile);
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
		return true;
	}

	public static boolean removeStringInFile(String filePath, String lineToBeRemoved)
	{
		try
		{
			File inFile = new File(filePath), outFile = new File(filePath+"-new");
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
			PrintWriter out = new PrintWriter(new FileOutputStream(outFile));
			
			for(String line = in.readLine();line != null;line = in.readLine())
				if(line.startsWith(lineToBeRemoved)) continue;
				else out.println(line);

			out.flush(); out.close(); in.close();

			inFile.delete();
			outFile.renameTo(inFile);
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
		return true;
	}

	public static String executeCommand(String[] command)
	{
		try
		{
			String resultStr = "", errStr = "";
			Process p = Runtime.getRuntime().exec(command);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			
			for(String line = br.readLine(); line != null; line = br.readLine()) resultStr += line + "\r\n";
			for(String line = bre.readLine(); line != null; line = bre.readLine()) errStr += line + "\r\n";

			String outStr = "";
			for(String cmq : command) outStr += cmq + " ";
			
			//#ifdef ANDROID
//			System.out.println("Executing COMMAND: -" + outStr + "-");			
			//#endif
			
			p.waitFor();
			if (p.exitValue() != 0)
			{
				System.out.println(outStr);
				System.out.println(resultStr);
				System.out.println(errStr);
			}


			
			return resultStr;
		}
		catch (Exception e) {e.printStackTrace();return null;}
	}
	
	//#ifdef ANDROID
//	public static String executeRootCommand(String[] command)
//    {
//		String resultStr = "", errStr = "", outStr = "";
//		Process p = null;
//		try
//		{
//			p = Runtime.getRuntime().exec( "su -c sh" );
//			DataOutputStream out = new DataOutputStream( p.getOutputStream() );
//			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
//			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//			for(String cmq : command) outStr += cmq + " ";
//			outStr += "\n";
//			System.out.println("Executing COMMAND: -" + outStr + "-");			
//			out.writeBytes(outStr);
//			out.writeBytes( "exit\n" );
//			
//			for(String line = br.readLine(); line != null; line = br.readLine()) resultStr += line + "\r\n";
//			for(String line = bre.readLine(); line != null; line = bre.readLine()) errStr += line + "\r\n";
//			
//			p.waitFor();
//			if (p.exitValue() != 0)
//			{
//				System.out.println(command);
//				System.out.println(resultStr);
//				System.out.println(errStr);
//			}
//			
//		    out.flush();
//		    out.close();
//		}
//		catch (IOException e) {e.printStackTrace();}
//		catch (InterruptedException e) {e.printStackTrace();}
//	    return resultStr;
//    }
	//#endif
}
