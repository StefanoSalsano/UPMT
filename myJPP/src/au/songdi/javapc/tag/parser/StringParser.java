package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

import au.songdi.javapc.ContextManager;

/**
 * For parsing the express in ifdef key == "Heelo world".
 * example:	#ifdef key == "Hello world"
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class StringParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #ifdef key == "Hello world"
		StringTokenizer st = new StringTokenizer(express);
		int countTokens = st.countTokens();
		if (countTokens >= 5) {
			int index = 0;
			String[] array_express = new String[3];
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 2)
					array_express[0] = tmp;
				if (index == 3) {
					array_express[1] = tmp;
					break;
				}
				index++;
			}
			int begin = express.indexOf("\"");
			int end = express.lastIndexOf("\"");
			array_express[2] = express.substring(begin + 1, end);
			return array_express;
		}
		return null;
	}

	public boolean checkExpress(String[] express) {
		if ((express == null) || (express.length != 3))
			return false;
		String left = express[0];
		String compare = express[1];
		String right = express[2];
		String value = ContextManager.getContext().getDefineValue(left);

		if (value == null)
			return false;

		if ("==".equals(compare))
			return value.compareTo(right) == 0 ? true : false;
		if ("!=".equals(compare))
			return value.compareTo(right) != 0 ? true : false;
		if (">".equals(compare))
			return value.compareTo(right) > 0 ? true : false;
		if (">=".equals(compare))
			return value.compareTo(right) >= 0 ? true : false;
		if ("<".equals(compare))
			return value.compareTo(right) < 0 ? true : false;
		if ("<=".equals(compare))
			return value.compareTo(right) <= 0 ? true : false;
		return false;
	}

}
