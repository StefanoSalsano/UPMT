package au.songdi.javapc.tag.parser;

import java.util.StringTokenizer;

import au.songdi.javapc.ContextManager;

/**
 * For parsing the express in ifdef key == true
 * example:	#ifdef key == true
 * 
 * @author Di SONG
 * @version 0.1
 */

public class BooleanParser extends Parser {

	public String[] parseExpress(String express) {
		// TODO Auto-generated method stub
		// sample: // #ifdef key == true
		StringTokenizer st = new StringTokenizer(express);
		int countTokens = st.countTokens();
		if (countTokens == 5) {
			int index = 0;
			String[] array_express = new String[3];
			while (st.hasMoreTokens()) {
				String tmp = st.nextToken();
				if (index == 2)
					array_express[0] = tmp;
				if (index == 3)
					array_express[1] = tmp;
				if (index == 4) {
					array_express[2] = tmp;
					break;
				}
				index++;
			}
			return array_express;
		}
		return null;
	}
	
	public boolean checkExpress(String[] express)
	{
		if((express==null)||(express.length!=3))
			return false;
		String left = express[0];
		String compare = express[1];
		String right = express[2];
		String value = ContextManager.getContext().getDefineValue(left);
		
		if(value==null)
			return false;
		
		if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value))
		{
			if("true".equalsIgnoreCase(right)||"false".equalsIgnoreCase(right))
			{
				if("==".equals(compare))
					return !(Boolean.parseBoolean(value)^Boolean.parseBoolean(right));
				if("!=".equals(compare))
					return (Boolean.parseBoolean(value)^Boolean.parseBoolean(right));
			}
		
		}
		return false;
	}

}
