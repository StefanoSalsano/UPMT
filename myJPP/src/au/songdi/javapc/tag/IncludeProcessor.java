package au.songdi.javapc.tag;

import java.util.Iterator;
import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;
import au.songdi.javapc.JavaPC;

/* For processing /** include "file name" **/
/**
 * @author Di SONG
 * @version 0.1
 */

public final class IncludeProcessor extends TagProcessor {

	void doExportProcess(Iterator it, DestFileWriter writer) {
		// TODO Auto-generated method stub
		String tagline = (String) it.next();
		// sample: /** #include "D:/A Word.txt" **/

		int begin = tagline.indexOf("\"");
		int end = tagline.lastIndexOf("\"");
		String include = tagline.substring(begin + 1, end);

		ContextManager context = ContextManager.getContext();
		context.backupContext();
		try {
			JavaPC.preprocess(include, context.getBaseDest());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			context.restoreContext();
		}
	}
}
