package au.songdi.javapc.tag;

import java.util.Iterator;

import au.songdi.javapc.DestFileWriter;
import au.songdi.javapc.tag.parser.Parser;

/* For processing /** ifndef express **/
/**
 * @author Di SONG
 * @version 0.1
 */

public final class IfndefProcessor extends IfdefProcessor {
	
	public IfndefProcessor(Parser p)
	{
		super(p);
	}
	
	/**
	 * The core method for processing
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 *            Destination file writer
	 * @return void
	 * 
	 */
	
	protected void doProcess(Iterator it, DestFileWriter writer,int type)
	{
		String tagline = (String) it.next();
		String[] express = this.parser.parseExpress(tagline);
		if(express==null)
			return;
		if (!this.parser.checkExpress(express)) {

			this.recordIfBlockOnly(it, writer, false);

		} else {

			this.recordElseBlockOnly(it, writer, false);
		}

	}
}
