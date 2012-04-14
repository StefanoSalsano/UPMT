package au.songdi.javapc;

/**
 * A Exception for Syntax Error
 * 
 * @author Di SONG
 * @version 0.1
 */

public class SyntaxException extends Exception {

	private static final long serialVersionUID = 3812226787165452371L;

	public SyntaxException(String s)
	{
		super(s);
	}
}
