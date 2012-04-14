package au.songdi.javapc.tag.parser;

/**
 * Abstract class for all Parser.
 * 
 * @author Di SONG
 * @version 0.1
 */

public abstract class Parser {

	/**
	 * For parsing a express.
	 * 
	 * @param express
	 *            the express which will be parsed
	 * 
	 * @return String[] the items of the express will be stored into this String
	 *         array
	 * 
	 */
	public abstract String[] parseExpress(String express);

	/**
	 * For check whether is the express right.
	 * 
	 * @param express
	 *            the express which will be checked
	 * 
	 * @return boolean if right, return true, or false
	 * 
	 * 
	 */
	public boolean checkExpress(String[] express) {
		return false;
	}

}
