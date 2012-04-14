package au.songdi.javapc.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import au.songdi.javapc.ContextManager;
import au.songdi.javapc.IReport;
import au.songdi.javapc.JavaPC;

/**
 * GUI Class. It is called by JavaPC class.
 * 
 * @author Di SONG
 * @version 0.1
 */

public class MainFrame extends JFrame {

	private static final long serialVersionUID = 2680996520432106584L;
	private JPanel jContentPanel = null;
	private JPanel jConsolePanel = null;
	private JPanel jLogPanel = null;
	private JPanel jButtonPanel = null;
	private JTextArea log = null;
	private JFileChooser fc = null;
	private JTextField src_txt = null;
	private JTextField dest_txt = null;
	private JTextField global_txt = null;
	private JButton start = null;
	private JCheckBox export = null;
	private JTextField comment_txt = null; 
	private boolean selected_source = false;
	
	public MainFrame() {
		super();
		initialize();
	}

	private void initialize() {

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		this.setPreferredSize(new Dimension(600, 700));
		int frameWidth = this.getPreferredSize().width;
		int frameHeight = this.getPreferredSize().height;
		this.setSize(frameWidth, frameHeight);
		this.setLocation((screenSize.width - frameWidth) / 2,
				(screenSize.height - frameHeight) / 2);

		this.setContentPane(getJContentPane());
		this.setTitle("JavaPC GUI");
	}

	private JPanel getJContentPane() {
		if (jContentPanel == null) {
			jContentPanel = new JPanel();
			jContentPanel.setLayout(new BorderLayout());
			Border paneEdge = BorderFactory.createEmptyBorder(5,5,5,5);
			jContentPanel.setBorder(paneEdge);
			jContentPanel.add(this.getConsolePanel(), BorderLayout.NORTH);
			jContentPanel.add(this.getLogPanel(), BorderLayout.CENTER);
			jContentPanel.add(this.getBottomPanel(), BorderLayout.SOUTH);
		}
		return jContentPanel;
	}

	private JPanel getTextFieldPanel()
	{
		JPanel jTextFieldPanel = new JPanel();
		jTextFieldPanel.setLayout(new BoxLayout(jTextFieldPanel, BoxLayout.Y_AXIS));
		src_txt = new JTextField(20);
		src_txt.setEditable(false);
		JPanel panel1 = new JPanel();
		panel1.add(src_txt);
		jTextFieldPanel.add(panel1);
		
		dest_txt = new JTextField(20);
		dest_txt.setEditable(false);
		JPanel panel2 = new JPanel();
		panel2.add(dest_txt);
		jTextFieldPanel.add(panel2);
		
		global_txt = new JTextField(20);
		global_txt.setEditable(false);
		JPanel panel3 = new JPanel();
		panel3.add(global_txt);
		jTextFieldPanel.add(panel3);
		
		return jTextFieldPanel;
	}
	
	private JPanel getButtonPanel()
	{
		JPanel jButtonPanel = new JPanel();
		jButtonPanel.setLayout(new BoxLayout(jButtonPanel, BoxLayout.Y_AXIS));
		fc = new JFileChooser();
		fc.setDialogType(JFileChooser.OPEN_DIALOG); 
		fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES );
		fc.setCurrentDirectory(new File("/home/di/Data/02/Workplace/javapc/testsample"));

		JButton openSource = new JButton("        Source         ");
		openSource.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				int returnVal = fc.showOpenDialog(MainFrame.this);

	            if (returnVal == JFileChooser.APPROVE_OPTION) {
	                File file = fc.getSelectedFile();
	                src_txt.setText(file.getAbsolutePath());
	                global_txt.setText(file.getAbsolutePath()+File.separator+"global.def");
	                selected_source = true;
	            }
			}
			
		});
		JPanel panel1 = new JPanel();
		panel1.add(openSource);
		jButtonPanel.add(panel1);
		
		JButton openDest = new JButton("     Destination    ");
		openDest.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				int returnVal = fc.showOpenDialog(MainFrame.this);
	            if (returnVal == JFileChooser.APPROVE_OPTION) {
	                File file = fc.getSelectedFile();
	                dest_txt.setText(file.getAbsolutePath());
	                if(selected_source)
	                	start.setEnabled(true);
	            }
			}
		});
		
		
		JPanel panel2 = new JPanel();
		panel2.add(openDest);
		jButtonPanel.add(panel2);
		
		
		JButton openGlobal = new JButton("Global Definition");
		openGlobal.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				int returnVal = fc.showOpenDialog(MainFrame.this);
	            if (returnVal == JFileChooser.APPROVE_OPTION) {
	                File file = fc.getSelectedFile();
	                global_txt.setText(file.getAbsolutePath());
	            }
			}
		});
		
		
		JPanel panel3 = new JPanel();
		panel3.add(openGlobal);
		jButtonPanel.add(panel3);
		
		return jButtonPanel;
	}
	
	private JPanel getExtendPanel()
	{
		JPanel jExtendPanel = new JPanel();
		jExtendPanel.setLayout(new BoxLayout(jExtendPanel, BoxLayout.Y_AXIS));
		jExtendPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
		
		JLabel comment = new JLabel("Comment: ");
		comment_txt = new JTextField("//",3);
		JPanel panel1 = new JPanel();
		panel1.add(comment);
		panel1.add(comment_txt);
		jExtendPanel.add(panel1);
		
		export = new JCheckBox("Export");
		export.setSelected(false);

		JPanel panel2 = new JPanel();
		panel2.add(export);
		jExtendPanel.add(panel2);
		
		return jExtendPanel;
	}
	
	private JPanel getConsolePanel() {
		if (jConsolePanel == null) {
			jConsolePanel = new JPanel();
			jConsolePanel.setLayout(new BoxLayout(jConsolePanel, BoxLayout.X_AXIS));
			jConsolePanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
			
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
			panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
			panel.add(this.getTextFieldPanel());
			panel.add(this.getButtonPanel());
			jConsolePanel.add(panel);
			jConsolePanel.add(this.getExtendPanel());			
			
		}
		return jConsolePanel;

	}

	private JPanel getLogPanel() {
		if (jLogPanel == null) {
			jLogPanel = new JPanel();
			jLogPanel.setLayout(new BorderLayout());
			log = new JTextArea(5, 20);
			log.setMargin(new Insets(5, 5, 5, 5));
			log.setEditable(false);
			JScrollPane logScrollPane = new JScrollPane(log);
			jLogPanel.add(logScrollPane,BorderLayout.CENTER);

		}
		return jLogPanel;
	}

	private JPanel getBottomPanel() {
		if (jButtonPanel == null) {
			jButtonPanel = new JPanel();
			jButtonPanel.setLayout(new FlowLayout());
			start = new JButton("start");
			start.setEnabled(false);
			start.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent arg0) {
					// TODO Auto-generated method stub
					
					Runnable thread = new Runnable()
					{

						public void run() {
							// TODO Auto-generated method stub
							try {
								
								ContextManager context = ContextManager.getContext();
								context.renew();
								context.setExport(export.isSelected());
								
								if(comment_txt.getText()!=null)
									context.setCommentMark(comment_txt.getText());
								
								File init = new File(src_txt.getText()+File.separator+"global.def");
								File src = new File(src_txt.getText()).getCanonicalFile();
								File dest = new File(dest_txt.getText()).getCanonicalFile();
								
								JavaPC.setReport(new IReport(){
									public void report(String msg) {
										// TODO Auto-generated method stub
										log.append(msg+"\n");
										log.setCaretPosition(log.getDocument().getLength());
										}
									}
									);
								if (init.exists()) {
									log.append("loading initfile:" + init.getAbsolutePath()+"\n");
									JavaPC.preprocess(init,dest);
								} else {
									log.append("[Warning] Fail to load initfile:"
											+ init.getAbsolutePath()+"\n");
								}
								JavaPC.preprocess(src, dest);
								log.append("Pre compile is completed.\n");
							} catch (Exception e) {
								log.append(e.toString()+"\n");
							}
							finally
							{
								start.setEnabled(true);
							}
						}
					};
					Thread thd = new Thread(thread);
					thd.start();
					start.setEnabled(false);	
				}
				
			});
			JButton close = new JButton("close");
			close.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent arg0) {
					// TODO Auto-generated method stub
					System.exit(EXIT_ON_CLOSE);
				}
				
			});
			jButtonPanel.add(start);
			jButtonPanel.add(close);

		}
		return jButtonPanel;
	}

}
