package au.songdi.javapc.tag;

import java.util.Iterator;
import java.util.StringTokenizer;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;

/* For processing /**<% key %> **/
/**
 * @author Di SONG
 * @version 0.1
 */

public final class OutputProcessor extends TagProcessor {

	void doExportProcess(Iterator it,DestFileWriter writer) {
		// TODO Auto-generated method stub
		String tagline = (String) it.next();
		// sample: // #<< SCREEN_WIDTH
		 StringTokenizer st = new StringTokenizer(tagline);
		   if(st.countTokens() == 3)
		    {
			   int index=0;
			   String key=null;
			   while(st.hasMoreTokens())
			   {
				   String tmp = st.nextToken();
				   if(index == 2){
					  key = tmp;
					  break;
				   }
				   index++;
			   }
			String blank =  tagline.substring(0,tagline.indexOf("#<<"));
			ContextManager context = ContextManager.getContext();
	    	String value = context.getDefineValue(key);
	    	StringBuffer sb = new StringBuffer(128);
	    	sb.append(blank);
	    	//sb.append("System.out.println(\"[");
	    	//sb.append(key);
	    	//sb.append("] = ");
	    	sb.append(value);
	    	//sb.append("\");");
	    	writer.writeCommentln(sb.toString());
		}

	}
	
	void doNonExportProcess(Iterator it,DestFileWriter writer) {
		this.doExportProcess(it, writer);

	}

}
