package upmt.client.application.manager.impl;

import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.IAxis;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.io.ADataCollector;
import info.monitorenter.gui.chart.traces.Trace2DLtd;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JFrame;

public class TrafficGrapher extends JFrame
{
	private Chart2D chart;
	private final int POINTS = 20;
	private final int LATENCY = 1000;
	private Hashtable<String, ITrace2D> traces;
	private Hashtable<String, ADataCollector> collectors;
	public static long initialTime;

	private Color[] colors = new Color[]{Color.RED, Color.BLUE, Color.BLACK, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.DARK_GRAY};
	private int colorIndex = 0;
	private Vector<String> [] ifsAssignedToColors; 

	/**
	 * <colors.length (0 to colors.length-1) means that the interface is active<BR>
	 * >=colors.lenght (colors.length to 2*colors.length-1) means that the interface is not active
	 */
	private Hashtable<String,Integer> ifNameToColor = new Hashtable<String,Integer>(); 

	@SuppressWarnings("deprecation")
	public TrafficGrapher() {
		initialTime = System.currentTimeMillis();
		ifsAssignedToColors = new Vector[colors.length];
		
		setSize(600, 400);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setTitle("UPMT Traffic Monitor");
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				flushGraph();
				dispose();
			}
		});
		
		traces = new Hashtable<String, ITrace2D>();
		collectors = new Hashtable<String, ADataCollector>();

		chart = new Chart2D();
		chart.setUseAntialiasing(true);
		AxisTitle axisYTitle = chart.getAxisY().getAxisTitle();
		axisYTitle.setTitle("Up + down rate (b/s)");
		Font myFont = axisYTitle.getTitleFont();
		if (myFont==null){
			myFont = chart.getFont();
		}
		myFont = (myFont.deriveFont((float) 15));
		axisYTitle.setTitleFont(myFont);
		getContentPane().add(chart);
	}
	
	public void startListen(Vector<String> ifList) {
		for (String iface : ifList) addTrace(iface);
		setVisible(true);
	}

	public void addInterface(String ifName) {
		addTrace(ifName);
	}
	
	public void removeInterface(String ifName) {
		removeTrace(ifName);
	}

	
	public void addTrace(String ifName) {
		ITrace2D trace = new Trace2DLtd(POINTS);
		
		Color color = getColorForIfName (ifName);
		
		trace.setColor(color);
		trace.setName(ifName);
		traces.put(ifName, trace);
		ADataCollector collector = new TrafficCollector(trace, LATENCY, ifName);
		chart.addTrace(trace);
		collectors.put(ifName, collector);
		collector.start();
		// very ugly workaround...
		Dimension d = getSize();
		setSize((int)d.getWidth()+1, (int)d.getHeight()+1);
	}

	/**
	 * this method tries to return the same color for the same interface<BR>
	 * when all colors have been assigned, it looks for a color that was assigned but it is currently
	 * unused (and removes the association between the interface that was using the color and the color)
	 * if no unused colors exists, a used color is reassigned
	 * @param ifName
	 * @return
	 */
	private Color getColorForIfName (String ifName) {
		int i;
		Integer color = ifNameToColor.get(ifName);
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
//				boolean foundUnusedColor = false;
				for (i=colorIndex; i<colorIndex+colors.length; i++){
					for (String ifNameTemp : ifsAssignedToColors[i % colors.length]) {
						color = ifNameToColor.get(ifNameTemp);
						if (color != null) {
							if (color>=colors.length) {
//								foundUnusedColor = true;
								ifsAssignedToColors[i % colors.length].remove(ifNameTemp);
								ifNameToColor.remove(ifNameTemp);
								break;
							}
						}
					}
				}
//				if (!foundUnusedColor) {
//				}
			}
		}
		i = i % colors.length;
		if (ifsAssignedToColors[i]==null) {
			ifsAssignedToColors[i] = new Vector<String>();
		}
		if (!ifsAssignedToColors[i].contains(ifName)){
			ifsAssignedToColors[i].add(ifName);
		}

		ifNameToColor.put(ifName, i);	
		colorIndex = (colorIndex+1)% colors.length;
		return colors [i];
	}

	private void markAssociateColorUnused (String ifName) {
		Integer color = ifNameToColor.get(ifName);
		if (color != null) {
			ifNameToColor.put(ifName, (color%colors.length)+colors.length);
		}
	}

	
	
	public void removeTrace(String ifName) {
		System.out.println("traccia rimossa per " + ifName);
		markAssociateColorUnused(ifName);
		chart.removeTrace(traces.get(ifName));
		ADataCollector collector = collectors.remove(ifName);
		collector.stop();
		traces.remove(ifName);
		// very ugly workaround...
		Dimension d = getSize();
		setSize((int)d.getWidth()-1, (int)d.getHeight()-1);
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
