package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

import au.songdi.javapc.ContextManager;

/**
 * For parsing the express in ifdef key.
 * example:	#ifdef key
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class OnlyKeyParser extends Parser {

	private static final String comment = ContextManager.getContext().getCommentMark();

	public boolean checkExpress(String[] express)
	{
		if((express==null)||(express.length!=1))
			return false;
		String left = express[0];
		
		String value = ContextManager.getContext().getDefineValue(left);

		if (value == null)
			return false;
		else if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return Boolean.parseBoolean(value);
		}
		else
			return true;
	}

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #ifdef key
		express = express.replace(comment + "#", comment + " #");
		StringTokenizer st = new StringTokenizer(express);
		int countTokens = st.countTokens();
		if (countTokens == 3) {
			int index = 0;
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 2)
					return new String[]{tmp};
				index++;
			}
		}
		return null;
	}

}
