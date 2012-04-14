package au.songdi.javapc;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 *  This class can write content with lines into destination file.
 *  It can write with two ways. One is common line, the other is comment line.
 * 	@author Di SONG  
 *	@version 0.1
 */ 

public final class DestFileWriter {
	private File filename;
	private java.io.BufferedWriter writer;
	private String comment;
	
	/** 
	 * Construct function  
     * @param filename
     * the destination file as a file object   
     * @exception No exceptions are thrown   
     */ 
	
	public DestFileWriter(File filename)
	{
		this.filename = filename;
		System.out.println("Writing: "+filename);
		ContextManager context = ContextManager.getContext();
		this.comment = context.getCommentMark();
		
	}
	/**      
	* This method is to open a BufferedReader for the destination file.   
	* @throws 
	* 	FileNoFoundException
	*/ 
	public void openWriter() throws FileNotFoundException
	{
		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename)),1024);
	}
	
	/**      
	* This method is to close the BufferedReader for writer the source file.  
	*  
	* @throws 
	* 	IOException
	*/ 
	public void closeWriter() throws IOException
	{
		writer.flush();
		writer.close();
	}
	
	/**      
	* This method is to write a common line into the source file. And add one "\r\n" at the end of each line. 
	* @param line
	* a line for writing into destination file.  
	* @throws 
	* 	NO Exceptions are thrown
	*/ 
	
	public void writeln(String line)
	{
		try {
			writer.write(line);
			//writer.write("\r\n");
			writer.write(System.getProperty("line.separator"));
			writer.flush();
		} catch (IOException e) {
			// TODO change into log method 
			e.printStackTrace();
		}
	}
	
	/**      
	* This method is to write a comment line into the source file. And add one "//" in the front of each line, and one "\r\n" at the end of each line.   
	* @throws 
	* 	NO Exceptions are thrown
	*/ 
	public void writeCommentln(String line)
	{
		try {
			writer.write(this.comment);
			writer.write(line);
			//writer.write("\r\n");
			writer.write(System.getProperty("line.separator"));
			writer.flush();
		} catch (IOException e) {
			// TODO change into log method 
			e.printStackTrace();
		}
	}
	
	public void writeDeCommentln(String line)
	{
		try {
			line = line.replaceFirst(comment, "");
			writer.write(line);
			//writer.write("\r\n");
			writer.write(System.getProperty("line.separator"));
			writer.flush();
		} catch (IOException e) {
			// TODO change into log method 
			e.printStackTrace();
		}
	}
}
