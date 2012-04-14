package au.songdi.javapc.tag;

import java.util.Iterator;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;

/* For processing /** else **/
/** 
 * @author Di SONG
 * @version 0.1
 */

public final class ElseProcessor extends TagProcessor {

	private static final String comment = ContextManager.getContext().getCommentMark();
	
	void doExportProcess(Iterator it,DestFileWriter writer) {
		// TODO Auto-generated method stub
		// sample: /** #else **/
		String buff = ""; boolean allLinesAreAlreadyCommented = true; String[] buffArr;
		while (it.hasNext()) {
			String line = (String)it.next();
			TagProcessor p = null;
			if ((p = TagSelector.getTagProcessor(line)) != null) {
				// do with a status process class
//				p.process(it,writer);
				if (p instanceof EndifProcessor) {
					if (IfdefProcessor.isIfdef()) {
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							}
							else {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
						}
					} else {
						buff = buff.substring(0, buff.length()-1);
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							} else {
							if(allLinesAreAlreadyCommented) writer.writeln(lineB);
							else writer.writeCommentln(lineB);
							}
						}
					}
					p.process(it,writer);
					return;
				}
				} else {
				
					if(!line.trim().startsWith(comment)) {
						allLinesAreAlreadyCommented = false;
					}
					buff += line + System.getProperty("line.separator");
	//				// write to destfile
	//				writer.writeDeCommentln(line);
				}
		}
	}
}
