package au.songdi.javapc.tag;

import java.util.Iterator;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;
import au.songdi.javapc.tag.parser.Parser;

/**
 * Abstract class for all TagProcessor.
 * 
 * @author Di SONG
 * @version 0.1
 */

public abstract class TagProcessor {
	
	Parser parser;
	
	public TagProcessor()
	{
		
	}
	public TagProcessor(Parser p)
	{
		this.parser = p;
	}
	
	/**
	 * A template method. This method must be called for processing.
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 * 				Destination file writer
	 */

	public void process(Iterator it,DestFileWriter writer) {
		// TODO Auto-generated method stub
		ContextManager context = ContextManager.getContext();
		if (context.isExport())
			this.doExportProcess(it, writer);
		else
			this.doNonExportProcess(it, writer);
	}
	
	/**
	 * process without export
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 * 				Destination file writer
	 *
	 */
	
	void doNonExportProcess(Iterator it,DestFileWriter writer){
		writer.writeln((String) it.next());
		this.doExportProcess(it, writer);
	}
	/**
	 * process with export
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 * 				Destination file writer
	 *
	 */
	void doExportProcess(Iterator it,DestFileWriter writer)
	{
		
	}

}
