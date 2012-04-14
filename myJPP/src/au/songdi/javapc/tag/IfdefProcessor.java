package au.songdi.javapc.tag;

import java.io.File;
import java.util.Iterator;

import org.omg.CosNaming.IstringHelper;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.DestFileWriter;
import au.songdi.javapc.tag.parser.Parser;

/* For processing /** ifdef express **/
/**
 * @author Di SONG
 * @version 0.1
 */

public class IfdefProcessor extends TagProcessor {

	private static final String comment = ContextManager.getContext().getCommentMark();
	private static boolean ifdef;
	private static boolean condition;

	public IfdefProcessor(Parser p) {
		super(p);
	}

	void doExportProcess(Iterator it, DestFileWriter writer) {
		// TODO Auto-generated method stub
		this.doProcess(it, writer);
	}

	/**
	 * The core method for processing
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 *            Destination file writer
	 * @return void
	 * 
	 */

	protected void doProcess(Iterator it, DestFileWriter writer) {
		String tagline = (String) it.next();
		String[] express = this.parser.parseExpress(tagline);
		if (express == null)
			return;
		if (condition = this.parser.checkExpress(express)) {

			this.recordIfBlockOnly(it, writer, !(this instanceof IfndefProcessor));

		} else {

			this.recordElseBlockOnly(it, writer, !(this instanceof IfndefProcessor));
		}
	}

	/**
	 * Only process #ifdef block, and ignore #else block
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 *            Destination file writer
	 * @return void
	 * 
	 */

	protected void recordIfBlockOnly(Iterator it, DestFileWriter writer, boolean ifdef) {
		// if the value of the key is not defined
		// write following lines before else block into disfile
		String buff = ""; boolean allLinesAreAlreadyCommented = true; String[] buffArr;
		setIfdef(ifdef);
		while (it.hasNext()) {
			String line = (String) it.next();
			TagProcessor p = null;
			if ((p = TagSelector.getTagProcessor(line)) != null) {
				// if there is only one if block, no else block, use p
				// instanceof EndifProcessor to judge
				if (p instanceof EndifProcessor) {
					if (IfdefProcessor.isIfdef()) {
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
							else {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							}
						}
					} else {
						buff = buff.substring(0, buff.length()-1);
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							} else {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
						}
					}
					p.process(it, writer);
					return;
				}
				// if meet a else tag, jump off all lines are in else block
				if (p instanceof ElseProcessor) {
//					new NotNeedElseProcessor().process(it, writer);
					if (IfdefProcessor.isIfdef()) {
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
							else {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							}
						}
					} else {
						buff = buff.substring(0, buff.length()-1);
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							} else {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
						}
					}
					p.process(it, writer);
					return;
				}
				else if (p instanceof IfdefProcessor || p instanceof IfndefProcessor) {
					if (allLinesAreAlreadyCommented) {
						buff = buff.substring(0, buff.length()-1);
//						writer.writeln(buff);
						buffArr = buff.split(System.getProperty("line.separator"));
						for(String lineB : buffArr) {
							writer.writeDeCommentln(lineB);
						}
					} else {
						buffArr = buff.split(System.getProperty("line.separator"));
						for(String lineB : buffArr) {
							writer.writeCommentln(lineB);
						}
					}
				}
				p.process(it, writer);
			} else {
				// this is a line of source code and write it into destfile
//				writer.writeln(line);
				if(!line.trim().startsWith(comment)) {
					allLinesAreAlreadyCommented = false;
				}
				buff += line + System.getProperty("line.separator");
			}

		}
	}

	/**
	 * Only process #else block, and ignore #ifdef block
	 * 
	 * @param it
	 *            Iterator for reading each line
	 * @param writer
	 *            Destination file writer
	 * @return void
	 * 
	 */

	protected void recordElseBlockOnly(Iterator it, DestFileWriter writer, boolean ifdef) {
		boolean export = ContextManager.getContext().isExport();
		int count_if = 1;
		String buff = ""; boolean allLinesAreAlreadyCommented = true; String[] buffArr;
		setIfdef(ifdef);
		while (it.hasNext()) {
			String line = (String) it.next();

			TagProcessor p = null;
			if ((p = TagSelector.getTagProcessor(line)) != null) {

				if ((p instanceof ElseProcessor) && (count_if == 1)) {
					if (IfdefProcessor.isIfdef()) {
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
							else {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							}
						}
					} else {
						buff = buff.substring(0, buff.length()-1);
						buffArr = buff.split(System.getProperty("line.separator"));
						String lineB;
						for(int i=0; i<buffArr.length; i++) {
							lineB = buffArr[i];
							if(IfdefProcessor.isCondition()) {
								if(allLinesAreAlreadyCommented) writer.writeln(lineB);
								else writer.writeCommentln(lineB);
							} else {
								if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
								else writer.writeln(lineB);
							}
						}
					}
					p.process(it, writer);
					return;
				} else {
					//					if (!export)
					//						writer.writeln(line);
					if ((p instanceof IfdefProcessor)
							|| (p instanceof IfndefProcessor)) {
						count_if++;
					} else if (p instanceof EndifProcessor) {
						if (IfdefProcessor.isIfdef()) {
							buffArr = buff.split(System.getProperty("line.separator"));
							String lineB;
							for(int i=0; i<buffArr.length; i++) {
								lineB = buffArr[i];
								if(IfdefProcessor.isCondition()) {
									if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
									else writer.writeln(lineB);
								}
								else {
									if(allLinesAreAlreadyCommented) writer.writeln(lineB);
									else writer.writeCommentln(lineB);
								}
							}
						} else {
							buff = buff.substring(0, buff.length()-1);
							buffArr = buff.split(System.getProperty("line.separator"));
							String lineB;
							for(int i=0; i<buffArr.length; i++) {
								lineB = buffArr[i];
								if(IfdefProcessor.isCondition()) {
									if(allLinesAreAlreadyCommented) writer.writeln(lineB);
									else writer.writeCommentln(lineB);
								} else {
									if(allLinesAreAlreadyCommented) writer.writeDeCommentln(lineB);
									else writer.writeln(lineB);
								}
							}
						}
					}
					p.process(it, writer);
					return;
				}
				} else {

					if(!line.trim().startsWith(comment)) {
						allLinesAreAlreadyCommented = false;
					}
					buff += line + System.getProperty("line.separator");

					//				if (!export)
					//					writer.writeCommentln(line);
				}

		}
	}

	public static boolean isIfdef()
	{
		return ifdef;
	}

	private static void setIfdef(boolean ifdef)
	{
		IfdefProcessor.ifdef = ifdef;
	}

	public static boolean isCondition()
	{
		return condition;
	}
}
