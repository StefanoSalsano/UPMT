package au.songdi.javapc.tag.parser;

import au.songdi.javapc.ContextManager;

/**
 * For parsing the express in ifdef key == 123.
 * example:	#ifdef key == 123
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class IntegerParser extends BooleanParser {
	
	/**
	 * Judge whether the string has a available Integer format
	 * 
	 * @param value
	 *            the string of a Integer number
	 * 
	 * @return boolean If this is a Integer number, return true, or false
	 * 
	 */
	
	private boolean isInteger(String value)
	{  
		 return value.matches("[+-]?\\d+");
	}
	/**
	 * Judge whether the string has a available float format
	 * 
	 * @param value
	 *            the string of a float number
	 * 
	 * @return boolean If this is a float number, return true, or false
	 * 
	 */
	private boolean isFloat(String value)
	{
		 return value.matches("[+-]?\\d+(\\.\\d+)?");    
	}

	public boolean checkExpress(String[] express) {
		// TODO Auto-generated method stub
		if((express==null)||(express.length!=3))
			return false;
		String left = express[0];
		String compare = express[1];
		String right = express[2];
		String value = ContextManager.getContext().getDefineValue(left);
		
		if(value==null)
			return false;
		if((isInteger(value))&&(isInteger(right)))
		{
			if("==".equals(compare))
				return Integer.parseInt(value)==Integer.parseInt(right)?true:false;
			if("!=".equals(compare))
				return Integer.parseInt(value)!=Integer.parseInt(right)?true:false;
			if(">".equals(compare))
				return Integer.parseInt(value)>Integer.parseInt(right)?true:false;
			if(">=".equals(compare))
				return Integer.parseInt(value)>=Integer.parseInt(right)?true:false;
			if("<".equals(compare))
				return Integer.parseInt(value)<Integer.parseInt(right)?true:false;
			if("<=".equals(compare))
				return Integer.parseInt(value)<=Integer.parseInt(right)?true:false;
		}
		else if((isFloat(value))&&(isFloat(right)))
		{
			if("==".equals(compare))
				return Float.parseFloat(value)==Float.parseFloat(right)?true:false;
			if("!=".equals(compare))
				return Float.parseFloat(value)!=Float.parseFloat(right)?true:false;
			if(">".equals(compare))
				return Float.parseFloat(value)>Float.parseFloat(right)?true:false;
			if(">=".equals(compare))
				return Float.parseFloat(value)>=Float.parseFloat(right)?true:false;
			if("<".equals(compare))
				return Float.parseFloat(value)<Float.parseFloat(right)?true:false;
			if("<=".equals(compare))
				return Float.parseFloat(value)<=Float.parseFloat(right)?true:false;
		}
		return false;
	}

}
