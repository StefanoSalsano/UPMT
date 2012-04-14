package au.songdi.javapc.test;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import au.songdi.javapc.SyntaxChecker;
import au.songdi.javapc.SyntaxException;

public class SyntaxCheckTest {

	@Test
	public void testCheck() {
		
		SyntaxChecker checker  = new SyntaxChecker(new File("manual.txt"));
		try {
			checker.check();
		} catch (SyntaxException e) {
			// TODO Auto-generated catch block
			System.out.println(e.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
