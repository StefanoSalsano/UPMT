package au.songdi.javapc;

import java.util.Iterator;
import java.io.*;

/**
 *  This class can read each line from sourcefile.And provide a interface of Iterator to user code.   
 * 	@author Di SONG  
 *	@version 0.1
 */  

public class SourceFileReader{

	private File filename;
	private java.io.BufferedReader reader;
	
	/** 
	 * Construct function  
     * @param filename
     * the source file as a file object   
     * @exception No exceptions are thrown   
     */ 
	
	public SourceFileReader(File filename)
	{
		this.filename = filename;
		
	}
	
	/**      
	* This method is to open a BufferedReader for the source file.   
	* @throws 
	* 	FileNoFoundException
	*/ 
	
	public void openReader() throws FileNotFoundException
	{
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)),1024);
	}
	
	/**      
	* This method is to close the BufferedReader for reading the source file.   
	* @throws 
	* 	IOException
	*/ 
	
	public void closeReader() throws IOException
	{
		reader.close();
	}
	
	/**      
	* This method is to get a Iterator for each line of the source file.   
	* @return Iterator
	*/ 
	
	public Iterator iterator()
	{
		return new Iterator(){
			String line;
			public boolean hasNext(){
				// TODO Auto-generated method stub
				try {
					if((line = reader.readLine())!=null)
						return true;
					else
						return false;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return false;
			}

			public Object next() {
				// TODO Auto-generated method stub
				return line;
			}
			public void remove() {
				// TODO Auto-generated method stub
				//do nothing
				
			}
		};
	}
	
}