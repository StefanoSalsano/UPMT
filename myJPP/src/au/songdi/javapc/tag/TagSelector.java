package au.songdi.javapc.tag;

import java.util.regex.Pattern;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.tag.parser.BooleanParser;
import au.songdi.javapc.tag.parser.DefineGlobalNonStringParser;
import au.songdi.javapc.tag.parser.DefineGlobalStringParser;
import au.songdi.javapc.tag.parser.DefineNonStringParser;
import au.songdi.javapc.tag.parser.DefineStringParser;
import au.songdi.javapc.tag.parser.FloatParser;
import au.songdi.javapc.tag.parser.IntegerParser;
import au.songdi.javapc.tag.parser.OnlyKeyParser;
import au.songdi.javapc.tag.parser.StringParser;

/**
 * Provide a TagProcesser Object with the result of the regex express checking
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class TagSelector {
	
	private static final String comment = ContextManager.getContext().getCommentMark();
	
	private static final String[] patterns = new String[]{
		// #define bool true
		"^\\s*("+ comment +")*\\s*#define\\s+[A-Za-z_]+\\w*\\s+((true)|(True)|(TRUE)|(false)|(False)|(FALSE)){1}\\s*$",
		// #define int 123
		"^\\s*("+ comment +")*\\s*#define\\s+[A-Za-z_]+\\w*\\s+[+-]?\\d+\\s*$",
		// #define float 123.4
		"^\\s*("+ comment +")*\\s*#define\\s+[A-Za-z_]+\\w*\\s+[+-]?\\d+(\\.?\\d+){1}\\s*$",
		// #define str "hello world"
		"^\\s*("+ comment +")*\\s*#define\\s+[A-Za-z_]+\\w*\\s+\".+\"\\s*$",
		
		// #define global bool true
		"^\\s*("+ comment +")*\\s*#define\\s+global\\s+[A-Za-z_]+\\w*\\s+((true)|(True)|(TRUE)|(false)|(False)|(FALSE)){1}\\s*$",
		// #define global int 123
		"^\\s*("+ comment +")*\\s*#define\\s+global\\s+[A-Za-z_]+\\w*\\s+[+-]?\\d+\\s*$",
		// #define global float 123.4
		"^\\s*("+ comment +")*\\s*#define\\s+global\\s+[A-Za-z_]+\\w*\\s+[+-]?\\d+(\\.?\\d+){1}\\s*$",
		// #define global str "hello world"
		"^\\s*("+ comment +")*\\s*#define\\s+global\\s+[A-Za-z_]+\\w*\\s+\".+\"\\s*$",
		
		// #ifdef bool == true
		"^\\s*("+ comment +")*\\s*#ifdef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=))+\\s+((true)|(True)|(TRUE)|(false)|(False)|(FALSE)){1}){1}\\s*$",
		// #ifdef int == 123
		"^\\s*("+ comment +")*\\s*#ifdef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+[+-]?\\d+){1}\\s*$",
		// #ifdef float == 123.4
		"^\\s*("+ comment +")*\\s*#ifdef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+[+-]?\\d+(\\.\\d+){1}){1}\\s*$",
		// #ifdef str == "hello world"
		"^\\s*("+ comment +")*\\s*#ifdef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+\".+\"){1}\\s*$",
		// #ifdef param
		"^\\s*("+ comment +")*\\s*#ifdef\\s+[A-Za-z_]+\\w*\\s*$",
		
		// #ifndef bool == true
		"^\\s*("+ comment +")*\\s*#ifndef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=))+\\s+((true)|(True)|(TRUE)|(false)|(False)|(FALSE)){1}){1}\\s*$",
		// #ifndef int == 123
		"^\\s*("+ comment +")*\\s*#ifndef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+[+-]?\\d+){1}\\s*$",
		// #ifndef float == 123.4
		"^\\s*("+ comment +")*\\s*#ifndef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+[+-]?\\d+(\\.\\d+){1}){1}\\s*$",
		// #ifndef str == "hello world"
		"^\\s*("+ comment +")*\\s*#ifndef\\s+[A-Za-z_]+\\w*(\\s+((==)|(!=)|(<=)|(>=)|(<)|(>)){1}\\s+\".+\"){1}\\s*$",
		// #ifndef param
		"^\\s*("+ comment +")*\\s*#ifndef\\s+[A-Za-z_]+\\w*\\s*$",
		
		// #else
		"^\\s*("+ comment +")*\\s*#else\\s*$",
		// #endif
		"^\\s*("+ comment +")*\\s*#endif\\s*$",
		// #<< param
		"^\\s*("+ comment +")*\\s*#<<\\s+[A-Za-z_]+\\w*\\s*$",
		// #include file
		"^\\s*("+ comment +")*\\s*#include\\s+\".+\"\\s*$"
		// #whatever else
//		"^\\s*("+ comment +")*\\s*#.+\\s*$"
	} ;
	
	private static final TagProcessor processor = new DefineProcessor(new DefineNonStringParser());
	private static final TagProcessor gprocessor = new DefineGlobalProcessor(new DefineGlobalNonStringParser());
	private static final TagProcessor[] processors = new TagProcessor[]{
		processor,
		processor,
		processor,
		new DefineProcessor(new DefineStringParser()),
		
		gprocessor,
		gprocessor,
		gprocessor,
		new DefineGlobalProcessor(new DefineGlobalStringParser()),
		
		new IfdefProcessor(new BooleanParser()),
		new IfdefProcessor(new IntegerParser()),
		new IfdefProcessor(new FloatParser()),
		new IfdefProcessor(new StringParser()),
		new IfdefProcessor(new OnlyKeyParser()),
		
		new IfndefProcessor(new BooleanParser()),
		new IfndefProcessor(new IntegerParser()),
		new IfndefProcessor(new FloatParser()),
		new IfndefProcessor(new StringParser()),
		new IfndefProcessor(new OnlyKeyParser()),
		
		new ElseProcessor(),
		new EndifProcessor(),
		new OutputProcessor(),
		new IncludeProcessor(),
//		new UnknownProcessor()
	};
	
	public static TagProcessor getTagProcessor(String sample)
	{
		for(int i=0;i<patterns.length;i++)
		{
			 if (Pattern.matches(patterns[i],sample))
			 {
				 return processors[i];
			 }
		}
		return null;
	}

}
