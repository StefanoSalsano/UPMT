package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

/**
 * For parsing the express in define global statement.
 * example:	#define global key "hello world"
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class DefineGlobalStringParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #define global SCREEN_WIDTH "176 good man"
		StringTokenizer st = new StringTokenizer(express);
		if (st.countTokens() >= 4) {
			int index = 0;
			String key = null;
			String value = null;
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 3) {
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
