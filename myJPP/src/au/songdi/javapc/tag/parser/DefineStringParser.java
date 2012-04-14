package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

/**
 * For parsing the express in define statement.
 * example:	#define key "string"
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class DefineStringParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #define SCREEN_WIDTH "176 I am a boy"
		StringTokenizer st = new StringTokenizer(express);
		if (st.countTokens() >= 4) {
			int index = 0;
			String key = null;
			String value = null;
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 2) {
					key = tmp;
					break;
				}
				index++;
			}
			int begin = express.indexOf("\"");
			int end = express.lastIndexOf("\"");
			value = express.substring(begin + 1, end);
			return new String[]{key,value};
		}
		return null;
		
	}

}
