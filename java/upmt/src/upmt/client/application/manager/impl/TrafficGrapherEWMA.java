package upmt.client.application.manager.impl;

import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.io.ADataCollector;
import info.monitorenter.gui.chart.traces.Trace2DLtd;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import javax.swing.JFrame;

import upmt.TunnelInfo;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.sip.SipSignalManager;

public class TrafficGrapherEWMA extends JFrame{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Chart2D chart;
	private Color[] colors = new Color[]{Color.RED, Color.BLUE, Color.BLACK, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.DARK_GRAY,Color.YELLOW};
	public static long initialTime;
	private final int POINTS = 20;
	private final int LATENCY = 10000;
	private Vector<String> [] ifsAssignedToColors;
	private Hashtable<String,Integer> ifNameToColor = new Hashtable<String,Integer>();
	private int colorIndex = 0;
	private Hashtable<String, ADataCollector> collectors;
	private Hashtable<String, ITrace2D> traces;
	
	@SuppressWarnings("deprecation")
	public TrafficGrapherEWMA(){
		initialTime = System.currentTimeMillis();
		ifsAssignedToColors = new Vector[colors.length];
		
		setSize(600, 400);
		setLocation(610, 0);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setTitle("UPMT EWMA Delay Monitor");
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
		axisYTitle.setTitle("Delay (ms)");
		Font myFont = axisYTitle.getTitleFont();
		if (myFont==null){
			myFont = chart.getFont();
		}
		myFont = (myFont.deriveFont((float) 15));
		axisYTitle.setTitleFont(myFont);
		getContentPane().add(chart);
		setVisible(true);
	}
	
	public void startListen(Vector<String> ANList, Vector<String> interfList) {
		
		for (String AN : ANList) {
			for(String interf : interfList){
				synchronized(SipSignalManager.getRemoteTidStatusTable()){
				if(SipSignalManager.getRemoteTidStatusTable().containsKey(AN+":"+interf)&&SipSignalManager.getRemoteTidStatusTable().get(AN+":"+interf).getStatus()==TunnelInfo.TUNNEL_SETUP)
		             addTrace(AN,interf);
				}
			}
		}
		
		setVisible(true);
	}
	
	public void addANinterf(String AN,String interf) {
		addTrace(AN,interf);
	}
	
	public void removeInterface(String AN,String interf) {
		removeTrace(AN,interf);
	}
	
	private void addTrace(String AN,String interf) {
		ITrace2D trace = new Trace2DLtd(POINTS);
		
		Color color = getColorForIfName(AN+":"+interf);
		
		trace.setColor(color);
		trace.setName(AN+":"+interf);
		traces.put(AN+":"+interf, trace);
		ADataCollector collector = new TrafficCollectorEWMA(trace, LATENCY, AN+":"+interf);
		chart.addTrace(trace);
		collectors.put(AN+":"+interf, collector);
		collector.start();
		// very ugly workaround...
		Dimension d = getSize();
		setSize((int)d.getWidth()+1, (int)d.getHeight()+1);
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
		if (!ifsAssignedToColors[i].contains(ANinterf)){
			ifsAssignedToColors[i].add(ANinterf);
		}

		ifNameToColor.put(ANinterf, i);	
		colorIndex = (colorIndex+1)% colors.length;
		return colors [i];
	}
	
	public void removeTrace(String AN,String interf) {
		System.out.println("traccia rimossa per " + AN+":"+interf);
		markAssociateColorUnused(AN+":"+interf);
		chart.removeTrace(traces.get(AN+":"+interf));
		ADataCollector collector = collectors.remove(AN+":"+interf);
		collector.stop();
		traces.remove(AN+":"+interf);
		// very ugly workaround...
		Dimension d = getSize();
		setSize((int)d.getWidth()-1, (int)d.getHeight()-1);
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
