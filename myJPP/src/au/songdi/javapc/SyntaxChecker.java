package au.songdi.javapc;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Stack;
import au.songdi.javapc.tag.ElseProcessor;
import au.songdi.javapc.tag.EndifProcessor;
import au.songdi.javapc.tag.IfdefProcessor;
import au.songdi.javapc.tag.IfndefProcessor;
import au.songdi.javapc.tag.TagProcessor;
import au.songdi.javapc.tag.TagSelector;
import au.songdi.javapc.tag.UnknownProcessor;

/**
 * Check Syntax, before processing a file.
 * 
 * @author Di SONG
 * @version 0.1
 */


public final class SyntaxChecker {

	private static String[] tokens = new String[] { 
		"#ifdef",
		"#ifndef",
		"#else", 
		"#endif" 
		};
	private static Class[] classes = new Class[] { 
		IfdefProcessor.class,
		IfndefProcessor.class,
		ElseProcessor.class,
		EndifProcessor.class 
		};
	private Stack keyword_tokens;
	private File src;
	private int number_line = 0;
	private String current_line;

	public SyntaxChecker(File srcfile) {
		this.src = srcfile;
		keyword_tokens = new Stack();
	}
	
	/**
	 * check method
	 *
	 * @return boolean
	 *	If the result is right, return true, or false
	 */

	public boolean check() throws SyntaxException, IOException {

		SourceFileReader reader = new SourceFileReader(src);
		try {
			reader.openReader();
			Iterator it = reader.iterator();
			while (it.hasNext()) {
				number_line++;
				current_line = (String) it.next();
				TagProcessor p = null;
				if ((p = TagSelector.getTagProcessor(current_line)) != null) {
//					if(p instanceof UnknownProcessor)
//						throw new SyntaxException(
//								createExceptionString(" Expect one  #ifdef ,  #ifndef ,  #else  or  #endif "));
					String token = this.getTokenString(p);
					if (token != null)
						this.analyseToken(token);
				}
			}
			if(!this.keyword_tokens.empty())
				throw new SyntaxException(
						createExceptionString(" Expect one  #ifdef ,  #ifndef ,  #else  or  #endif  before the current line"));
			return true;
		} catch (SyntaxException e) {
			throw e;

		} finally {
			reader.closeReader();
		}
	}
	
	/**
	 * Get the token string for corresponding TagProcessor class
	 * 
	 * @param p
	 *            the class object of TagProcessor
	 * @return String
	 *
	 */

	private String getTokenString(TagProcessor p) {
		Class class_p = p.getClass();
		for (int i = 0; i < classes.length; i++) {
			if (class_p == classes[i]) {
				return tokens[i];
			}
		}
		return null;
	}
	
	/**
	 * Analyse the token string
	 * 
	 * @param token
	 *            
	 * @return void
	 *
	 */

	private void analyseToken(String token) throws SyntaxException {
		if (token.equals("#ifdef") || token.equals("#ifndef"))
			process_Ifdef_Ifndef(token);
		else if (token.equals("#else")) {
			process_Else(token);
		} else if (token.equals("#endif")) {
			process_Endif(token);
		}
	}
	
	/**
	 * Push the token into stack
	 * 
	 * @param token
	 *            the token string of #ifdef or #ifndef
	 * @return void
	 *
	 */

	private void process_Ifdef_Ifndef(String token) {
		this.keyword_tokens.push(token);
	}
	
	/**
	 * Push the token into stack
	 * 
	 * @param token
	 *            the token string of #else
	 * @return void
	 *
	 */

	private void process_Else(String token) throws SyntaxException {
		if(this.keyword_tokens.empty())
			throw new SyntaxException(
					createExceptionString(" Expect one  #ifdef  or  #ifndef  before the current line"));
		String top_token = (String) this.keyword_tokens.peek();
		if (top_token.equals("#ifdef") || top_token.equals("#ifndef")) {
			this.keyword_tokens.push(token);
		} else {
			throw new SyntaxException(
					createExceptionString(" Expect one  #ifdef  or  #ifndef  before the current line"));
		}
	}
	
	/**
	 * Push the token into stack
	 * 
	 * @param token
	 *            the token string of #endif
	 * @return void
	 *
	 */

	private void process_Endif(String token) throws SyntaxException {
		if(this.keyword_tokens.empty())
			throw new SyntaxException(
					createExceptionString(" Expect one  #ifdef ,  #ifndef  or  #else  before the current line"));
		String top_token = (String) this.keyword_tokens.peek();
		if (top_token.equals("#ifdef") || top_token.equals("#ifndef")) {
			// pop ifdef or ifndef
			this.keyword_tokens.pop();
		} else if (top_token.equals("#else")) {
			// pop else
			this.keyword_tokens.pop();
			// check next top
			String next_top_token = (String) this.keyword_tokens.peek();
			if (next_top_token.equals("#ifdef")
					|| next_top_token.equals("#ifndef")) {
				// pop ifdef or ifndef
				this.keyword_tokens.pop();
			} else {
				throw new SyntaxException(
						createExceptionString("Expect one  #ifdef  or  #ifndef  before the current line"));
			}
		} else {
			throw new SyntaxException(
					createExceptionString("Expect one  #ifdef ,  #ifndef  or  #else  before the current line"));
		}
	}

	private String createExceptionString(String detail) {
		StringBuffer sb = new StringBuffer(128);
		String lineSeparator = System.getProperty("line.separator");

		sb.append(lineSeparator);
		sb.append("[SyntaxError]:"+lineSeparator);
		sb.append("[File]: ");
		sb.append(this.src.getAbsolutePath());
		sb.append(lineSeparator+"[Line]: ");
		sb.append(this.number_line);
		sb.append(lineSeparator+"[Content]: ");
		sb.append(this.current_line);
		sb.append(lineSeparator+"[Error]:");
		sb.append(detail);
		sb.append(lineSeparator);
		return sb.toString();

	}
}
