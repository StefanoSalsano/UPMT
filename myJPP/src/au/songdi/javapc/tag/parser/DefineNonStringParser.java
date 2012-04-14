package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

/**
 * For parsing the express in define statement.
 * example:	#define key 123
 *				#define key 123.0
 *				#define key true
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class DefineNonStringParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #define BOOL TRUE
		
		StringTokenizer st = new StringTokenizer(express);
		if (st.countTokens() == 4) {
			int index = 0;
			String key = null;
			String value = null;
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 2) {
					key = tmp;
				}
				if(index == 3)
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
