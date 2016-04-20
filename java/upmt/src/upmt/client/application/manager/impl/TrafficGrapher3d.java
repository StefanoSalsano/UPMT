package upmt.client.application.manager.impl;


import info.monitorenter.gui.chart.ITracePoint2D;


import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Hashtable;
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

public class TrafficGrapher3d extends JFrame
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static long initialTime;
	private  DefaultCategoryDataset defaultcategorydataset;
	private ChartPanel jpanel;
	private JFreeChart jfreechart;
	private final int LATENCY = 10000;
	private Timer timer ;
	private ArrayList<String> interf;
	private ITracePoint2D point2d;
	private boolean firstTime=true;
	private Integer xValue;
	
	private Hashtable<String, Traffic3dCollector> collectors;
	private int numberXpointVisible=15;
	
	
	
	
	

	public TrafficGrapher3d() {
		collectors = new Hashtable<String, Traffic3dCollector>();
		
		initialTime = System.currentTimeMillis();
        defaultcategorydataset = new DefaultCategoryDataset();
        interf = new ArrayList<String>();
        xValue = 0;
        this.jfreechart = ChartFactory.createLineChart3D("UPMT Traffic Monitor", "Time [sec]", "Up + down rate (kb/s)", defaultcategorydataset, PlotOrientation.VERTICAL, true, true, false);
        this.jfreechart.setAntiAlias(true);
        
        jpanel = new ChartPanel(jfreechart);
       
        CategoryPlot plot = (CategoryPlot) jfreechart.getPlot();
          
        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        axis.setAutoRange(true);
        setTitle("UPMT Traffic Monitor");
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
	
	public void startListen(Vector<String> ifList) {
		for (String iface : ifList){
				addTrace(iface);
		}
		setVisible(false);
	}

	public void addInterface(String ifName) {
		addTrace(ifName);
	}
	
	public void removeInterface(String ifName) {
		removeTrace(ifName);
	}

	
	public void addTrace(String ifName) {
		if(!interf.contains(ifName)){
			this.interf.add(ifName);

			Traffic3dCollector collector = new Traffic3dCollector(LATENCY);
			collector.setInterf(ifName);
			collectors.put(ifName, collector);
			if(firstTime){
				firstTime=false;
				timer = new Timer(0, new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						for(int i=0; i< interf.size(); i++){
							Traffic3dCollector collector;

							collector=collectors.get(interf.get(i));

							point2d = collector.collectData();
							xValue = (int) (point2d.getX() / 1000.0);
							defaultcategorydataset.addValue(point2d.getY()/1000, interf.get(i), xValue.toString());
							
							if(defaultcategorydataset.getColumnCount() == numberXpointVisible ){
								defaultcategorydataset.removeColumn(0);
								
							}
							
						}


						


					}  	
				});
				timer.setInitialDelay(0);
				timer.setDelay(1000);
				timer.start();	
			}
		}
	}

	

	
	
	public void removeTrace(String ifName) {
		System.out.println("traccia rimossa per " + ifName);
		if(interf.contains(ifName)){
			this.interf.remove(ifName);
		}
		if(collectors.contains(ifName)){
			collectors.remove(ifName);
		}
		
		int index = defaultcategorydataset.getRowIndex(ifName);
        	if (index >= 0)
        		defaultcategorydataset.removeRow(index);
	}
	
	
	public ArrayList<String> getListInterf(){
		return this.interf;
	}
	
	public DefaultCategoryDataset getDataset(){
		return this.defaultcategorydataset;
	}
}
