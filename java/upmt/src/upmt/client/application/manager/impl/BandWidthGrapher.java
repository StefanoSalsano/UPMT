package upmt.client.application.manager.impl;

import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.io.ADataCollector;
import info.monitorenter.gui.chart.traces.Trace2DLtd;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

import upmt.TunnelInfo;
import upmt.client.sip.SipSignalManager;

public class BandWidthGrapher extends JFrame{
	private static final long serialVersionUID = -8792251048644506125L;
	public Chart2D chart;
	private Color[] colors = new Color[]{Color.RED, Color.BLUE, Color.BLACK, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.DARK_GRAY,Color.YELLOW};
	public static long initialTime;
	private final int POINTS = 20;
	private final int LATENCY = 10000;
	private Vector<String> [] ifsAssignedToColors;
	private Hashtable<String,Integer> ifNameToColor = new Hashtable<String,Integer>();
	private int colorIndex = 0;
	private Hashtable<String, ADataCollector> collectors;
	private Hashtable<String, ITrace2D> traces;
	
	public BandWidthGrapher() {
		setBounds(100, 100, 695, 340);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				setVisible(false);
			}
		});
		
		initialTime = System.currentTimeMillis();
		ifsAssignedToColors = new Vector[colors.length];
		setTitle("UPMT BandWidth Monitor");
		traces = new Hashtable<String, ITrace2D>();
		collectors = new Hashtable<String, ADataCollector>();
		chart = new Chart2D();
		chart.setUseAntialiasing(true);
		AxisTitle axisYTitle = chart.getAxisY().getAxisTitle();
		axisYTitle.setTitle("Inserire un etichetta");
		AxisTitle axisXTitle = chart.getAxisX().getAxisTitle();
		axisXTitle.setTitle("Inserire un etichetta");
		
		Font myFont1 = axisXTitle.getTitleFont();
		if(myFont1==null)
			myFont1 = chart.getFont();
		myFont1 = (myFont1.deriveFont((float) 15));
		axisXTitle.setTitleFont(myFont1);
		
		Font myFont = axisYTitle.getTitleFont();
		if (myFont==null){
			myFont = chart.getFont();
		}
		myFont = (myFont.deriveFont((float) 15));
		axisYTitle.setTitleFont(myFont);
		
		JPanel menuPanel = new JPanel();
		getContentPane().add(menuPanel, BorderLayout.NORTH);
		GridBagLayout gbl_menuPanel = new GridBagLayout();
		gbl_menuPanel.columnWidths = new int[]{0, 0};
		gbl_menuPanel.rowHeights = new int[]{0, 0};
		gbl_menuPanel.columnWeights = new double[]{0.0, Double.MIN_VALUE};
		gbl_menuPanel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		menuPanel.setLayout(gbl_menuPanel);
		
		JMenuBar menuBar = new JMenuBar();
		GridBagConstraints gbc_menuBar = new GridBagConstraints();
		gbc_menuBar.gridx = 0;
		gbc_menuBar.gridy = 0;
		menuPanel.add(menuBar, gbc_menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		JMenuItem mntmExit = new JMenuItem("item 1");
		mnMenu.add(mntmExit);
		JMenuItem mntmNewMenuItem = new JMenuItem("Exit");
		mntmNewMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		mnMenu.add(mntmNewMenuItem);
		
		JPanel buttonPanel = new JPanel();
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		
		JButton btnNewButton = new JButton("Exit");
		btnNewButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				setVisible(false);
			}
		});
		buttonPanel.add(btnNewButton);
		
		JPanel graphPanel = new JPanel();
		getContentPane().add(graphPanel, BorderLayout.CENTER);
		getContentPane().add(chart);
	}
	public void addANinterf(String AN,String interf) {
		addTrace(AN,interf);
	}
	public void startListen(Vector<String> ANList, Vector<String> interfList){
	}
	public void removeInterface(String AN,String interf) {
		removeTrace(AN,interf);
	}
	public Chart2D getChart2D(){
		return this.chart;
	}
	private void addTrace(String AN,String interf) {
		ITrace2D trace = new Trace2DLtd(POINTS);
		Color color = getColorForIfName(AN+":"+interf);
		trace.setColor(color);
		trace.setName(AN+":"+interf);
		traces.put(AN+":"+interf, trace);
		ADataCollector collector = new BandWidthCollector(trace,LATENCY);
		chart.addTrace(trace);
		collectors.put(AN+":"+interf, collector);
		collector.start();
		Dimension d = getSize();
		setSize((int)d.getWidth()+1, (int)d.getHeight()+1);
	}
	public void removeTrace(String AN,String interf) {
		markAssociateColorUnused(AN+":"+interf);
		chart.removeTrace(traces.get(AN+":"+interf));
		ADataCollector collector = collectors.remove(AN+":"+interf);
		collector.stop();
		traces.remove(AN+":"+interf);
		Dimension d = getSize();
		setSize((int)d.getWidth()-1, (int)d.getHeight()-1);
	}
	private Color getColorForIfName (String ANinterf) {
		int i;
		Integer color = ifNameToColor.get(ANinterf);
		if (color != null) { // the interface ifName is already associated to a color
			i = color;
		} else { //the interface ifName is not associated to a color
			boolean foundUnassignedColor = false;
			for (i=colorIndex; i<colorIndex+colors.length; i++){
				if (ifsAssignedToColors[i % colors.length] == null || ifsAssignedToColors[i % colors.length].isEmpty()) {
					foundUnassignedColor = true;
					break;
				}
			}
			if (!foundUnassignedColor) {
				//looks for a color that is assigned but not used
				for (i=colorIndex; i<colorIndex+colors.length; i++){
					for (String ifNameTemp : ifsAssignedToColors[i % colors.length]) {
						color = ifNameToColor.get(ifNameTemp);
						if (color != null) {
							if (color>=colors.length) {
								ifsAssignedToColors[i % colors.length].remove(ifNameTemp);
								ifNameToColor.remove(ifNameTemp);
								break;
							}
						}
					}
				}
			}
		}
		i = i % colors.length;
		if (ifsAssignedToColors[i]==null) {
			ifsAssignedToColors[i] = new Vector<String>();
		}
		if (!ifsAssignedToColors[i].contains(ANinterf)){
			ifsAssignedToColors[i].add(ANinterf);
		}

		ifNameToColor.put(ANinterf, i);	
		colorIndex = (colorIndex+1)% colors.length;
		return colors [i];
	}
	private void markAssociateColorUnused (String ANinterf) {
		Integer color = ifNameToColor.get(ANinterf);
		if (color != null) {
			ifNameToColor.put(ANinterf, (color%colors.length)+colors.length);
		}
	}
	public void flushGraph() {
		getContentPane().remove(chart);
		Enumeration<ADataCollector> cs = collectors.elements();
		while (cs.hasMoreElements()) {
			cs.nextElement().stop();
		}
		chart.removeAllTraces();
		
		traces = new Hashtable<String, ITrace2D>();
		collectors = new Hashtable<String, ADataCollector>();

		chart = new Chart2D();
		chart.setUseAntialiasing(true);
		getContentPane().add(chart);
	}


}
