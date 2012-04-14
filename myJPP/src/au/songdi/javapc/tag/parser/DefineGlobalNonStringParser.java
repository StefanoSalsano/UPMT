package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

/**
 * For parsing the express in define global statement.
 * example:	#define global key 123
 *				#define global key 123.0
 *				#define global key true
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class DefineGlobalNonStringParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample // #define global BOOL TRUE
		StringTokenizer st = new StringTokenizer(express);
		if (st.countTokens() == 5) {
			int index = 0;
			String key = null;
			String value = null;
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 3) {
					key = tmp;
				}
				if(index == 4)
				{
					value = tmp.toLowerCase();
				}
				index++;
			}
			return new String[]{key,value};
		}
		return null;
	}

}
