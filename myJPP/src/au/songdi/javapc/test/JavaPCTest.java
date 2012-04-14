package au.songdi.javapc.test;

import java.util.StringTokenizer;

import junit.framework.Assert;

import org.junit.Test;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.JavaPC;
import au.songdi.javapc.tag.parser.Parser;
import au.songdi.javapc.tag.parser.StringParser;



public class JavaPCTest {
	
	@Test
	public void testPorcess()
	{

		ContextManager context = ContextManager.getContext();
		context.setExport(true);
		try{
		JavaPC.preprocess("../sample","/tmp/tmp/");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		context.showAllValues();
	}
	
	@Test
	public void testDefine()
	{
		//String tagline = "/** #define PARAM \"I am a    little boy\"   **/";
		//String tagline = "/** #define PARAM \"true\"   **/";
		String tagline = "/** #define SCREEN_WIDTH \"176\"  **/";
		StringTokenizer st = new StringTokenizer(tagline);
		   if(st.countTokens() >= 5)
		    {
			   int index=0;
			   String key=null;
			   while(st.hasMoreTokens())
			   {
				   String tmp = st.nextToken();
				   if(index == 2){
					  key = tmp;
					  break;
				   }
				   index++;
			   }
			  int begin =  tagline.indexOf("\"");
			  int end = tagline.lastIndexOf("\"");
			  String value = tagline.substring(begin+1, end);
			  System.out.println("key = " + key);
			  System.out.println("value =" + value);
			
			ContextManager context = ContextManager.getContext();
			context.addDefinelValue(key, value);
	    }
		  
		  
	}
	
	@Test
	
	public void testGetJudgementExpress()
	{
		ContextManager context = ContextManager.getContext();
		context.addDefinelValue("SCREEN_WIDTH", "hahaha hahaha");
		//context.addDefinelValue("SCREEN_WIDTH", "-123");
		
		String line = "/** #ifdef SCREEN_WIDTH == \"hahaha hahaha\" **/";
		//String line = "/** #ifdef SCREEN_WIDTH <= -124 **/";
		//String line = "/** #ifdef SCREEN_WIDTH **/";
		Parser p = new StringParser();
		//IfdefIntegerProcessor p = new IfdefIntegerProcessor();
		String[] express = p.parseExpress(line);
		System.out.println("exp[0] = "+express[0]);
		System.out.println("exp[1] = "+express[1]);
		System.out.println("exp[2] = "+express[2]);
		Assert.assertEquals(true,p.checkExpress(express));
		
	}

}
