package upmt.client.application.manager.impl;


import info.monitorenter.gui.chart.ITracePoint2D;


import java.awt.BorderLayout;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ArrayList;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import javax.swing.Timer;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;

import org.jfree.data.category.DefaultCategoryDataset;


import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.sip.SipSignalManager;

public class LineChart3d extends JFrame {

	private static final long serialVersionUID = 8369439755217560336L;
	public static long initialTime;
	private  DefaultCategoryDataset defaultcategorydataset;
	private ChartPanel jpanel;
	private JFreeChart jfreechart;
	private final int LATENCY = 10000;
	private Timer timer ;
	private ArrayList<String> ANinterf;
	private ITracePoint2D point2d;
	private LineChart3dCollector collector;
	private Integer xValue;
	private String vepa="";
	
	private int numberXpointVisible=15;
	
	public LineChart3d(){
		initialTime = System.currentTimeMillis();
        defaultcategorydataset = new DefaultCategoryDataset();
        ANinterf = new ArrayList<String>();
        xValue = 0;
        this.jfreechart = ChartFactory.createLineChart3D("EWMA Delay Monitor", "Time [sec]", "Delay [msec]", defaultcategorydataset, PlotOrientation.VERTICAL, true, true, false);
        this.jfreechart.setAntiAlias(true);
        
        jpanel = new ChartPanel(jfreechart);
       
        CategoryPlot plot = (CategoryPlot) jfreechart.getPlot();
          
        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        axis.setAutoRange(true);
       
        getContentPane().add(jpanel,BorderLayout.CENTER);
		 
		 JPanel buttonPanel = new JPanel();
		 GridBagLayout gbc= new GridBagLayout();
		 gbc.columnWidths = new int[]{10};
		 gbc.rowHeights = new int[]{20};
		 buttonPanel.setLayout(gbc);
		 getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		 JButton exit = new JButton("Close");
		 GridBagConstraints gbc_settingsButton = new GridBagConstraints();
			gbc_settingsButton.insets = new Insets(5, 5, 5, 5);
			gbc_settingsButton.fill = GridBagConstraints.BOTH;
			gbc_settingsButton.gridx = 1;
			gbc_settingsButton.gridy = 0;
			buttonPanel.add(exit,gbc_settingsButton);
			exit.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					if(isVisible())
						setVisible(false);		
				}
			});
	     pack();
	     setVisible(false);
	}
	
	public void setdataset(DefaultCategoryDataset set, ITracePoint2D point2d){
		this.point2d = point2d;
	}

	public void startListen(Vector<String> ANList, Vector<String> interfList) {
		// TODO Auto-generated method stub
		for (String AN : ANList) {
			for(String interf : interfList){
				synchronized(SipSignalManager.getRemoteTidStatusTable()){
				if(SipSignalManager.getRemoteTidStatusTable().containsKey(AN+":"+interf)&&SipSignalManager.getRemoteTidStatusTable().get(AN+":"+interf).getStatus()==TunnelInfo.TUNNEL_SETUP)
		             addGraph(AN,interf);
				}
			}
		}
		
	}

	public void addGraph(String AN, String interf) {
		// TODO Auto-generated method stub
		this.ANinterf.add(AN + ":"+ interf);
		
		collector = new LineChart3dCollector(LATENCY);
		timer = new Timer(0, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					// TODO Auto-generated method stub
					for(int i=0; i< ANinterf.size(); i++){
						collector.setANinterf(ANinterf.get(i));
						point2d = collector.collectData();
						xValue =(int) ( point2d.getX() / 1000.0);
						String interf = ANinterf.get(i);
						defaultcategorydataset.addValue(point2d.getY(), interf, xValue.toString());		
					}
					if(defaultcategorydataset.getColumnCount()== numberXpointVisible){
						defaultcategorydataset.removeColumn(0);
					}
					if(UPMTClient.getRME()){
						chartVisibleForVepa(vepa);
					}
					
				}  	
	        });
	     timer.setInitialDelay(0);
	     timer.setDelay(10000);
	     timer.start();	
	}
	
	public void remove3dInterface(String AN, String interf){
		String ANinterface = AN + ":" + interf;
		this.ANinterf.remove(ANinterface);
			int index = defaultcategorydataset.getRowIndex(ANinterface);
	        	if (index >= 0)
	        		defaultcategorydataset.removeRow(index);
	}
	
	public void add3dInterface(String AN, String interf){
		String ANinterface = AN + ":" + interf;
		this.ANinterf.add(ANinterface);
		defaultcategorydataset.addValue(0.0, ANinterface, this.xValue.toString());

	}
	
	public ArrayList<String> getListANInterf(){
		return this.ANinterf;
	}
	
	public DefaultCategoryDataset getDataset(){
		return this.defaultcategorydataset;
	}

	public void chartVisibleForVepa(String vepa) {
		if(!vepa.equals("")){
			this.vepa=vepa;
			CategoryPlot plot = (CategoryPlot) jfreechart.getPlot();
			for(int i=0; i< ANinterf.size();i++){
				plot.getRenderer().setSeriesVisible(i, false);
			}
			for(String endPointAddress: UPMTClient.getRMETunnelsToGUI()) {
				if(UPMTClient.getVipaUI(endPointAddress).equals(vepa)){
					for (String interf : UPMTClient.getAvailableIfs().keySet()) {
						int index=ANinterf.indexOf(endPointAddress+":"+interf);
						if(index!=-1){
							plot.getRenderer().setSeriesVisible(index, true);
							
						}
					}
				}
			}

		}
	}
        
	

	
	
}

