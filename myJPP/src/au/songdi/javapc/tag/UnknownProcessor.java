package au.songdi.javapc.tag;

import java.util.Iterator;

import au.songdi.javapc.DestFileWriter;

/**
 * A NULL object pattern. Do NOHTING.
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class UnknownProcessor extends TagProcessor {

	void doExportProcess(Iterator it, DestFileWriter writer) {
		//do nothing here
	}

}
