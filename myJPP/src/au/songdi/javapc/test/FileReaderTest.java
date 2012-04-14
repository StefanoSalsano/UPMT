package au.songdi.javapc.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import org.junit.Test;

import au.songdi.javapc.SourceFileReader;


public class FileReaderTest {
	
	@Test
	public void testFileNotExist()
	{
		File file = new File("manual.txt");
		SourceFileReader reader = new SourceFileReader(file);
		try {
			reader.openReader();
			Iterator it = reader.iterator();
			while(it.hasNext())
			{
				System.out.println(it.next());
			}
			reader.closeReader();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	

}
