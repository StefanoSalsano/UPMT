package au.songdi.javapc.tag;

import java.util.Iterator;

import au.songdi.javapc.DestFileWriter;

/**
 *  For processing the else block that does not need
 *  
 * @author Di SONG
 * @version 0.1
 */

public final class NotNeedElseProcessor extends TagProcessor {

	void doExportProcess(Iterator it, DestFileWriter writer) {
		// TODO Auto-generated method stub
		while (it.hasNext()) {
			String line2 = (String) it.next();
			TagProcessor p2 = null;
			if (((p2 = TagSelector.getTagProcessor(line2)) != null)
					&& (p2 instanceof EndifProcessor)) {
				p2.process(it, writer);
				return;
			}
		}

	}

	void doNonExportProcess(Iterator it, DestFileWriter writer) {
		// TODO Auto-generated method stub
		writer.writeln((String) it.next());
		while (it.hasNext()) {
			String line2 = (String) it.next();
			TagProcessor p2 = null;
			if (((p2 = TagSelector.getTagProcessor(line2)) != null)
					&& (p2 instanceof EndifProcessor)) {
				p2.process(it, writer);
				return;
			}
			else
				writer.writeCommentln(line2);
		}

	}

}
