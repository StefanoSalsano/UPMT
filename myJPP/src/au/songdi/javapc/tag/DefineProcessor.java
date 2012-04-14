package au.songdi.javapc.tag;

import java.util.Iterator;



import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;
import au.songdi.javapc.tag.parser.Parser;

/* For processing /** define Key value **/
/** 
 * @author Di SONG
 * @version 0.1
 */

public final class DefineProcessor extends TagProcessor {

	public DefineProcessor(Parser p) {
		// TODO Auto-generated constructor stub
		super(p);
	}

	void doExportProcess(Iterator it, DestFileWriter writer) {
		// TODO Auto-generated method stub
		String express = (String) it.next();
		String[] key_value = this.parser.parseExpress(express);
		if(key_value!=null)
		{
			ContextManager context = ContextManager.getContext();
			context.addDefinelValue(key_value[0], key_value[1]);
		}
	}

}
