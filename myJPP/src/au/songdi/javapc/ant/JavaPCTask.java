package au.songdi.javapc.ant;

import java.io.File;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.IReport;
import au.songdi.javapc.JavaPC;
import au.songdi.javapc.SyntaxException;

/**
 * Ant Task Class. It is called by Ant.
 * 
 * @author Di SONG
 * @version 0.1
 */

public final class JavaPCTask extends Task {

	private String srcdir;
	private String destdir;
	private boolean export = false;
	private String initfile = "global.def";

	public void execute() {

		if (srcdir == null) {
			throw new BuildException("No srcdir set.");
		}
		if (destdir == null) {
			throw new BuildException("No destdir set.");
		}
		File src = new File(srcdir);
		if (!src.exists()) {
			throw new BuildException("[srcdir] = " + this.srcdir
					+ " does not exist.");
		}
		File dest = new File(destdir);
		if (!dest.exists()) {
			throw new BuildException("[destdir] = " + this.destdir
					+ " does not exist.");
		}
		if (this.export) {
			ContextManager context = ContextManager.getContext();
			context.setExport(this.export);
		}
		try{
			JavaPC.setReport(new IReport(){
				public void report(String msg) {
					// TODO Auto-generated method stub
					log(msg);
				}
				
			});
			File init = new File(initfile);
			if (init.exists()) {
				log("loading initfile:" + init.getAbsolutePath());
				JavaPC.preprocess(init, dest);
			}
			else
			{
				log("[Warning] Fail to load initfile:" + init.getAbsolutePath());
			}
			JavaPC.preprocess(src, dest);
			log("Pre compile is completed.");
		}
		catch(SyntaxException e)
		{
			throw new BuildException(e.toString());
		}
		catch(Exception e)
		{
			log(e.getMessage());
		}
	}

	public void setSrcdir(String dir) {
		this.srcdir = dir;
	}

	public void setDestdir(String dir) {
		this.destdir = dir;
	}

	public void setExport(boolean b) {
		this.export = b;
	}

	public void setInitfile(String f) {
		this.initfile = f;
	}
	public void setCommentmark(String c)
	{
		ContextManager context = ContextManager.getContext();
		context.setCommentMark(c);
	}

}
