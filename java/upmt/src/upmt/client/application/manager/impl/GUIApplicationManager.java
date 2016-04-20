package upmt.client.application.manager.impl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.zoolu.tools.Log;

import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.application.manager.ApplicationManager;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.core.Socket;
import upmt.client.sip.SipSignalManager;
import upmt.client.tunnel.TunnelManager;
import upmt.server.gui.Gui;
import upmt.server.rme.RMEServer;
import upmt.server.rme.RMETunnelInfo;
//come si aggiungono i socket (modificare l'interfaccia aggiungendo addSocket)
//cosa succede qnd si rimuove un'interfaccia
//come accorgersi ke e' stata rimossa un'applicazione (o socket)
public class GUIApplicationManager extends JFrame implements ApplicationManager
{

	private static final long serialVersionUID = -247976287606177664L;
	private static String VIPA;
	private static final String DEFAULT_ANCHOR_NODE = "Default AN: ";
	private static final String CONNECTED_ANCHOR_NODES = "Connected AN(s): ";
	private static final String CURRENT_POLICY = "Current policy: ";
	private static final String CURRENT_INTERFACE = "Current interface: ";
	private static final String STORED_POLICY = "Stored Policy: ";
	private static final String CURRENT_AN="Current Anchor Node: ";
	private static final String SETTINGS = "Settings";
	private static final String BANDWIDTH = "Show bandwidth graph";
	private static final String NETWORK_GRAPH = "Show network graph";
	private static final String NETWORK_GRAPH_EWMA = "Show EWMA delay graph";
	private static final String EXIT = "Exit";
	private static final String STORED_FLAG = "Stored -> ";
	private static final String CURRENT_FLAG = "Current -> ";
	private static final String NO_STORED_POLICY = "-";
	
	private JPanel buttonPanel, menuPanel, centerPanel, statusPanel, applicationsPanel;
	private JButton settingsButton, networkButton, ewmaButton, exitButton, btnApply, btnSave, bandWidthButton, DDSQoSReceiverButton, loadBalanceButton;
	private JMenu menu, toolMenu;
	private JMenuBar menuBar;
	private JMenuItem bandWidthMenuItem,settingsMenuItem, networkMenuItem, ewmaMenuItem, exitMenuItem, captureTrafficGrapher,captureEWMAGrapher;
	private JTabbedPane tabbedPane;
	private JLabel lblFirstRow, lblStatus, lblInterface, lblCurrPolicy, lblStoredPolicy;
	private JScrollPane scrollPane;
	private JComboBox CurrentPolicy;
	private MyTableModel myData;
	private SaveImage saveImage;
	private int countImage;

	private JTree tree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode rootNode;
	private Object selectedItem;
	
	private JFrame listFrame;
	private DefaultListModel listVepaForFrame;
	
	private ApplicationManagerListener listener;
	private JLabel lblAnchorNode;
	/**
	 * number of connected Anchor Nodes
	 */
	private int numOfConnectedAN;
	private String defaultAN;
	private String vipaFix;
	private Vector<String> ifList;
	private boolean skipAction;
	

	//private MyTableModel myData ;

	private JTable table;

	/**appName -> treeNode*/
	private Hashtable<String, DefaultMutableTreeNode> appList = new Hashtable<String, DefaultMutableTreeNode>();

	/**socketID -> treeNode*/
	private Hashtable<String, DefaultMutableTreeNode> socketList = new Hashtable<String, DefaultMutableTreeNode>();

	/**socketID -> appName*/
	private Hashtable<String, String> appForSocket = new Hashtable<String, String>();

	private TrafficGrapher grapher;
	private TrafficGrapher3d grapher3d;
	private static TrafficGrapherEWMA grapherEWMA;
	private static LineChart3d linechart3d;
	private BandWidthGrapher bandWidthGrapher;
	
	
	private Vector<String> colNameVector; //marco bonuglia

	public GUIApplicationManager() {
		if(!UPMTClient.getRME()) {
			VIPA = "Local VIPA: ";
		}else{
			VIPA = "Local VEPA: ";
		}
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		countImage = 0;
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e){
				listener.stop();
				e.getWindow().dispose();
			}
			public void windowClosed(WindowEvent e){
				System.exit(0);
			}
		});
		setTitle("Upmt Manager");
		setBounds(100,100,900,500);
		skipAction = true;
		
		colNameVector = new Vector<String>();
		if(UPMTClient.getRME()) {
			colNameVector.add("VEPA");
			colNameVector.add("End Point");
			
		}
		else {
			colNameVector.add("Anchor Node");
			colNameVector.add("VIPA");
		}
	
		//Create buttons and his layout
		buttonPanel = new JPanel();
		getContentPane().add(buttonPanel,BorderLayout.WEST);
		GridBagLayout gbc= new GridBagLayout();
		gbc.columnWidths = new int[]{0, 0, 0};
		gbc.rowHeights = new int[]{20, 70, 70, 70, 70, 70, 0};
		gbc.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		gbc.rowWeights = new double[]{0.0, 1.0, 1.0, 1.0, 1.0, 1.0, Double.MIN_VALUE};
		buttonPanel.setLayout(gbc);
		
		/*bandWidthButton = new JButton("<html>BandWidth Graph</hmtl>");
		bandWidthButton.setPreferredSize(new Dimension(110,70));
		bandWidthButton.setFont(new Font("Helvetica", Font.BOLD, 11));
		GridBagConstraints gbc_bandwidthButton = new GridBagConstraints();
		gbc_bandwidthButton.insets = new Insets(0, 10, 15, 0);
		gbc_bandwidthButton.fill = GridBagConstraints.BOTH;
		gbc_bandwidthButton.gridx = 0;
		gbc_bandwidthButton.gridy = 1;
		bandWidthButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(bandWidthGrapher != null)
					if(!bandWidthGrapher.isVisible())
						bandWidthGrapher.setVisible(true);
			}
		});
		buttonPanel.add(bandWidthButton,gbc_bandwidthButton);
		*/
		
		if(UPMTClient.getRME()) {
			loadBalanceButton = new JButton("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Active</html>");
			loadBalanceButton.setPreferredSize(new Dimension(120, 70));

			if(UPMTClient.interfaceBalance){
				loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Active</html>");
			} else {
				loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Disabled</html>");
			}
			

			loadBalanceButton.setFont(new Font("Helvetica",Font.BOLD, 11));
			GridBagConstraints gbc_loadBalance = new GridBagConstraints();
			gbc_loadBalance.insets = new Insets(5, 10, 11, 0);
			gbc_loadBalance.fill = GridBagConstraints.BOTH;
			gbc_loadBalance.gridx = 0;
			gbc_loadBalance.gridy = 1;
			loadBalanceButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if(UPMTClient.interfaceBalance) {
						UPMTClient.interfaceBalance = false;
						loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Disabled</html>");
					} else {
						UPMTClient.interfaceBalance = true;
						loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Active</html>");
					}

				}
			});
			buttonPanel.add(loadBalanceButton,gbc_loadBalance);
			

			DDSQoSReceiverButton = new JButton("<html>DDS QoS <br>Active</html>");
			DDSQoSReceiverButton.setPreferredSize(new Dimension(110, 70));

			if(UPMTClient.DDSQoSReceiver){
				DDSQoSReceiverButton.setText("<html>DDS QoS <br>Active</html>");
			} else {
				DDSQoSReceiverButton.setText("<html>DDS QoS <br>Disabled</html>");
			}
			

			DDSQoSReceiverButton.setFont(new Font("Helvetica",Font.BOLD, 11));
			GridBagConstraints gbc_DDSQoSReceiverButton = new GridBagConstraints();
			gbc_DDSQoSReceiverButton.insets = new Insets(5, 10, 11, 0);
			gbc_DDSQoSReceiverButton.fill = GridBagConstraints.BOTH;
			gbc_DDSQoSReceiverButton.gridx = 0;
			gbc_DDSQoSReceiverButton.gridy = 2;
			DDSQoSReceiverButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if(UPMTClient.DDSQoSReceiver) {
						UPMTClient.DDSQoSReceiver = false;
						DDSQoSReceiverButton.setText("<html>DDS QoS <br>Disabled</html>");
					} else {
						UPMTClient.DDSQoSReceiver = true;
						DDSQoSReceiverButton.setText("<html>DDS QoS <br>Active</html>");
					}

				}
			});
			buttonPanel.add(DDSQoSReceiverButton,gbc_DDSQoSReceiverButton);

		}
		else {
			
			settingsButton = new JButton("<html>Settings</html>");
			settingsButton.setPreferredSize(new Dimension(110, 70));
			settingsButton.setFont(new Font("Helvetica",Font.BOLD, 12));
			GridBagConstraints gbc_settingsButton = new GridBagConstraints();
			gbc_settingsButton.insets = new Insets(0, 10, 11, 0);
			gbc_settingsButton.fill = GridBagConstraints.BOTH;
			gbc_settingsButton.gridx = 0;
			gbc_settingsButton.gridy = 2;
			settingsButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e){
					GUIApplicationManager thiz = GUIApplicationManager.this;
					new Setting(thiz.listener, thiz).setVisible(true);
					thiz.setEnabled(false);
					thiz.setFocusableWindowState(false);
				}
			});
			buttonPanel.add(settingsButton,gbc_settingsButton);
			
		}
	
			
		networkButton = new JButton("<html>Network Graph</html>");
		networkButton.setMargin(new Insets(2, 4, 2, 4));
		networkButton.setPreferredSize(new Dimension(110, 70));
		networkButton.setFont(new Font("Helvetica",Font.BOLD, 11));
		GridBagConstraints gbc_networkButton = new GridBagConstraints();
		gbc_networkButton.insets = new Insets(5, 10, 11, 0);
		gbc_networkButton.fill = GridBagConstraints.BOTH;
		gbc_networkButton.gridx = 0;
		gbc_networkButton.gridy = 3; 
		networkButton.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e)
			{	
				if(!UPMTClient.getRME()){
					if(grapher != null)
						if(!grapher.isVisible())		
							grapher.setVisible(true);
				}else{
					if(UPMTClient.grapher3D){
						if(grapher3d != null)
							if(!grapher3d.isVisible())		
								grapher3d.setVisible(true);
					}else{
						if(grapher != null)
							if(!grapher.isVisible())		
								grapher.setVisible(true);
					}
				}
			}
		});

		buttonPanel.add(networkButton,gbc_networkButton);

		ewmaButton = new JButton("<html>EWMA Delay Graph</html>");
		ewmaButton.setMargin(new Insets(2, 4, 2, 4));
		ewmaButton.setFont(new Font("Helvetica",Font.BOLD, 10));
		ewmaButton.setPreferredSize(new Dimension(110, 70));
		GridBagConstraints gbc_ewmaButton = new GridBagConstraints();
		gbc_ewmaButton.insets = new Insets(5, 10, 11, 0);
		gbc_ewmaButton.fill = GridBagConstraints.BOTH;
		gbc_ewmaButton.gridx = 0;
		gbc_ewmaButton.gridy = 4;
		ewmaButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				if(!UPMTClient.getRME()){
					if(grapherEWMA != null)
						if(!grapherEWMA.isVisible())
							grapherEWMA.setVisible(true);
					if(linechart3d != null)
						if(!linechart3d.isVisible())
							linechart3d.setVisible(true);
				}else{
					if(listFrame!=null)
						if(!listFrame.isVisible())
							listFrame.setVisible(true);
				}
			}
		});
		buttonPanel.add(ewmaButton,gbc_ewmaButton);
		
		if(UPMTClient.getRME()){
			listFrame = new JFrame("Select VEPA");
			listFrame.setSize(200, 250);
			listFrame.setLocationRelativeTo(null);  
		    listFrame.setDefaultCloseOperation(HIDE_ON_CLOSE);
		    listVepaForFrame = new DefaultListModel(); 
		    final JList list = new JList(listVepaForFrame); 
		    JScrollPane panelScrollList =new JScrollPane(list);
		    listFrame.add(panelScrollList);
		    listFrame.setVisible(false);
		    list.setVisibleRowCount(0);
		    list.addListSelectionListener(new ListSelectionListener() {
				
				@Override
				public void valueChanged(ListSelectionEvent e) {
					
					
					if(list.getSelectedValue()!=null){
						if(listFrame!=null)
							if(listFrame.isVisible())
								listFrame.setVisible(false);
						String vepa = list.getSelectedValue().toString();
						if(UPMTClient.grapher3D){
							if(linechart3d!=null){
								linechart3d.chartVisibleForVepa(vepa);
								if(!linechart3d.isVisible())
									linechart3d.setVisible(true);
							}
						}else{
							if(grapherEWMA!=null){
								grapherEWMA.chartVisibleForVepa(vepa);
								if(!grapherEWMA.isVisible())
									grapherEWMA.setVisible(true);
							}

						}
						list.clearSelection();
					}
					
				}
			});
		}
		
		exitButton = new JButton("<html>Exit</html>");
		exitButton.setPreferredSize(new Dimension(110, 70));
		exitButton.setFont(new Font("Helvetica",Font.BOLD, 13));
		GridBagConstraints gbc_exitButton = new GridBagConstraints();
		gbc_exitButton.insets = new Insets(5, 10, 11, 0);
		gbc_exitButton.fill = GridBagConstraints.BOTH;
		gbc_exitButton.gridx = 0;
		gbc_exitButton.gridy = 5;
		exitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				dispose();
				listener.stop();
				System.exit(0);
				
			}
		});
		buttonPanel.add(exitButton,gbc_exitButton);
		
		//Create menu and his layout
		menuPanel = new JPanel();
	    menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		menu = new JMenu("File");
		getContentPane().add(menuPanel,BorderLayout.NORTH);
		menuBar.add(menu);
		
		/*bandWidthMenuItem = new JMenuItem(BANDWIDTH);
		bandWidthMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(bandWidthGrapher != null)
					if(!bandWidthGrapher.isVisible())
						bandWidthGrapher.setVisible(true);
			}
		});
		*/
		if(!UPMTClient.getRME()) {
			settingsMenuItem = new JMenuItem(SETTINGS);
			settingsMenuItem.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					GUIApplicationManager thiz = GUIApplicationManager.this;
					new Setting(thiz.listener, thiz).setVisible(true);
					thiz.setEnabled(false);
					thiz.setFocusableWindowState(false);
				}		
			});
			networkMenuItem = new JMenuItem(NETWORK_GRAPH);		
			networkMenuItem.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e)
				{
					if(grapher != null)
						if(!grapher.isVisible())
							grapher.setVisible(true);
				}
			});
			ewmaMenuItem = new JMenuItem(NETWORK_GRAPH_EWMA);
			ewmaMenuItem.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e)
				{
					
					if(grapherEWMA != null)
						if(!grapherEWMA.isVisible())
							grapherEWMA.setVisible(true);
					if(linechart3d != null)
						if(!linechart3d.isVisible())
							linechart3d.setVisible(true);
				}

			});
		}
		exitMenuItem = new JMenuItem(EXIT);
		exitMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				listener.stop();
				dispose();
			}		
		});
		menu.add(exitMenuItem);
		if(!UPMTClient.getRME()) {
			menu.add(settingsMenuItem);
			//menu.add(bandWidthMenuItem);
			menu.add(networkMenuItem);
			menu.add(ewmaMenuItem);
			

			toolMenu = new JMenu("Tool");
			menuBar.add(toolMenu);
			captureTrafficGrapher = new JMenuItem("Capture traffic grapher");
			captureTrafficGrapher.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e){
					if(grapher != null)
						if(grapher.isVisible()){
							try{
								BufferedImage screenCaptured = grapher.getChart2D().snapShot();
								Image chartImage = screenCaptured.getScaledInstance(-1, -1, 0);
								JLabel chartLabel = new JLabel(new ImageIcon(chartImage));
								saveImage = new SaveImage(chartLabel, screenCaptured, countImage, 1);
								countImage++;
								saveImage.setVisible(true);
							}catch(Exception err){}
						}
				}	
			});
			captureEWMAGrapher = new JMenuItem("Capture EWMA traffic grapher");
			captureEWMAGrapher.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e){
					if(grapherEWMA != null)
						if(grapherEWMA.isVisible()){
							try{
								BufferedImage screenCaptured = grapherEWMA.getChart2D().snapShot();
								Image chartImage = screenCaptured.getScaledInstance(-1, -1, 0);
								JLabel chartLabel = new JLabel(new ImageIcon(chartImage));
								saveImage = new SaveImage(chartLabel, screenCaptured, countImage, 2);
								countImage++;
								saveImage.setVisible(true);
							}catch(Exception err){}
						}
				}
			});
			toolMenu.add(captureTrafficGrapher);
			toolMenu.add(captureEWMAGrapher);	
			GridBagConstraints gbc_menu = new GridBagConstraints();
			gbc_menu.gridx = 0;
			gbc_menu.gridy = 0;
			getContentPane().add(menuPanel);
		}
		centerPanel();	
	}
	
	//Creazione pannello Centrale relativa alle schede status/applications e tabelle
	private void centerPanel(){
				centerPanel = new JPanel();
				getContentPane().add(centerPanel,BorderLayout.CENTER);

				GridBagLayout gbl_panel = new GridBagLayout();
				gbl_panel.columnWidths = new int[]{0, 0};
				gbl_panel.rowHeights = new int[]{0, 0};
				gbl_panel.columnWeights = new double[]{1.0, Double.MIN_VALUE};
				gbl_panel.rowWeights = new double[]{1.0, Double.MIN_VALUE};
				centerPanel.setLayout(gbl_panel);
				
				tabbedPane = new JTabbedPane(JTabbedPane.TOP);
				GridBagConstraints gbc_tabbedPane = new GridBagConstraints();
				gbc_tabbedPane.insets = new Insets(0,30,6,6);
				gbc_tabbedPane.fill = GridBagConstraints.BOTH;
				gbc_tabbedPane.gridx = 0;
				gbc_tabbedPane.gridy = 0;
				centerPanel.add(tabbedPane, gbc_tabbedPane);
				
				//Scheda Status
				statusPanel = new JPanel();
				tabbedPane.addTab("Status", null, statusPanel, null);
				GridBagLayout gbl_statusPanel = new GridBagLayout();
//				gbl_statusPanel.columnWidths = new int[]{153, 56, 0};
				//gbl_statusPanel.rowHeights = new int[]{15, 0, 0};
				gbl_statusPanel.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
				gbl_statusPanel.rowWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
				statusPanel.setLayout(gbl_statusPanel);
				
				lblFirstRow = new JLabel();
				lblFirstRow.setHorizontalAlignment(SwingConstants.LEFT);
				GridBagConstraints gbc_lblVipaDefault = new GridBagConstraints();
				gbc_lblVipaDefault.insets = new Insets(8, 0, 8, 0);
				gbc_lblVipaDefault.anchor = GridBagConstraints.CENTER;
				gbc_lblVipaDefault.gridwidth = 4;
				gbc_lblVipaDefault.gridx = 0;
				gbc_lblVipaDefault.gridy = 0;
				gbc_lblVipaDefault.weightx = 1;
				statusPanel.add(lblFirstRow, gbc_lblVipaDefault);
				
				lblStatus = new JLabel();
				lblStatus.setHorizontalAlignment(SwingConstants.LEFT);
				GridBagConstraints gbc_lblStatus = new GridBagConstraints();
				gbc_lblStatus.anchor = GridBagConstraints.WEST;
				gbc_lblStatus.gridwidth = 4;
				gbc_lblStatus.gridx = 0;
				gbc_lblStatus.gridy = 4;
				gbc_lblStatus.weightx = 1;
				statusPanel.add(lblStatus, gbc_lblStatus);
				
				
				//Scheda Applications
				applicationsPanel = new JPanel();	
				tabbedPane.addTab("Applications", null, applicationsPanel, null);
				GridBagLayout gbl_applicationsPanel = new GridBagLayout();
				gbl_applicationsPanel.columnWidths = new int[]{0};
				gbl_applicationsPanel.rowHeights = new int[]{0};
				gbl_applicationsPanel.columnWeights = new double[]{Double.MIN_VALUE};
				gbl_applicationsPanel.rowWeights = new double[]{Double.MIN_VALUE};
				applicationsPanel.setLayout(gbl_applicationsPanel);
				
				scrollPane = new JScrollPane();
				GridBagConstraints gbc_scrollPane = new GridBagConstraints();
				gbc_scrollPane.weighty = 1.0;
				if(!UPMTClient.getRME()) {
					gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
				} else{
					gbc_scrollPane.insets = new Insets(0, 0, 0, 0);
				}
				gbc_scrollPane.fill = GridBagConstraints.BOTH;
				gbc_scrollPane.gridx = 0;
				gbc_scrollPane.gridy = 1;
				gbc_scrollPane.gridwidth = 4;
				gbc_scrollPane.weightx = 1;
				applicationsPanel.add(scrollPane, gbc_scrollPane);
				
				if(!UPMTClient.getRME()) {
					lblInterface = new JLabel(CURRENT_INTERFACE);
					GridBagConstraints gbc_lblInterface = new GridBagConstraints();
					gbc_lblInterface.gridwidth = 4;
					gbc_lblInterface.insets = new Insets(0, 0, 5, 5);
					gbc_lblInterface.anchor = GridBagConstraints.WEST;
					gbc_lblInterface.gridx = 0;
					gbc_lblInterface.gridy = 2;
					gbc_lblInterface.weightx = 0;
					applicationsPanel.add(lblInterface, gbc_lblInterface);

					lblCurrPolicy = new JLabel(CURRENT_POLICY);
					GridBagConstraints gbc_lblCurrPolicy = new GridBagConstraints();
					gbc_lblCurrPolicy.anchor = GridBagConstraints.WEST;
					gbc_lblCurrPolicy.insets = new Insets(0, 0, 5, 5);
					gbc_lblCurrPolicy.gridx = 0;
					gbc_lblCurrPolicy.gridy = 3;
					applicationsPanel.add(lblCurrPolicy, gbc_lblCurrPolicy);

					CurrentPolicy = new JComboBox();
					CurrentPolicy.setEditable(true);
					GridBagConstraints gbc_CurrentPolicy = new GridBagConstraints();
					gbc_CurrentPolicy.fill = GridBagConstraints.HORIZONTAL;
					gbc_CurrentPolicy.insets = new Insets(0, 0, 5, 5);
					gbc_CurrentPolicy.gridx = 1;
					gbc_CurrentPolicy.gridy = 3;
					gbc_CurrentPolicy.weightx = 1;

					CurrentPolicy.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							onComboSelectedPolicy();
						}
					});
					applicationsPanel.add(CurrentPolicy, gbc_CurrentPolicy);

					btnApply = new JButton("Apply");
					btnApply.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							onApplyPolicy();
						}
					});
					GridBagConstraints gbc_btnApply = new GridBagConstraints();
					gbc_btnApply.insets = new Insets(0, 0, 5, 5);
					gbc_btnApply.gridx = 2;
					gbc_btnApply.gridy = 3;
					applicationsPanel.add(btnApply, gbc_btnApply);

					btnSave = new JButton("Save");
					btnSave.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							onSavePolicy();
						}
					});
					GridBagConstraints gbc_btnSetPolicy = new GridBagConstraints();
					gbc_btnSetPolicy.insets = new Insets(0, 0, 5, 0);
					gbc_btnSetPolicy.gridx = 3;
					gbc_btnSetPolicy.gridy = 3;
					applicationsPanel.add(btnSave, gbc_btnSetPolicy);

					lblStoredPolicy = new JLabel(STORED_POLICY);
					GridBagConstraints gbc_lblStoredProcedure = new GridBagConstraints();
					gbc_lblStoredProcedure.gridwidth = 4;
					gbc_lblStoredProcedure.anchor = GridBagConstraints.WEST;
					gbc_lblStoredProcedure.insets = new Insets(0, 0, 5, 5);
					gbc_lblStoredProcedure.gridx = 0;
					gbc_lblStoredProcedure.gridy = 4;
					applicationsPanel.add(lblStoredPolicy, gbc_lblStoredProcedure);
				}
				rootNode = new DefaultMutableTreeNode();
				treeModel = new DefaultTreeModel(rootNode);
				tree = new JTree(treeModel);
				tree.setRootVisible(false);
				tree.setSelectionModel(null);
				scrollPane.setViewportView(tree);
				
				tree.addTreeSelectionListener(new TreeSelectionListener() {
					public void valueChanged(TreeSelectionEvent e){
						onTreeSelectedItem();
					}
				});
		}
	
	public void startListen(ApplicationManagerListener listener)
	{
		this.listener = listener;


		//		myData = new MyTableModel();
		//		setDataInTable();
		//
		//		addTableToStatusTab();
		//		refreshStatusBarAndTable();

		if(!UPMTClient.textMode) {
			setVisible(true);
			refreshStatusBarAndFirstRow();
		}
	}
	
	public String getActiveTabName(){
		return tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
	}

	/**
	 * called by UPMT client to start the cube rolling :-)
	 */
	public void startWorking()
	{
		vipaFix = listener.getVipaFix();
		numOfConnectedAN = listener.getNumOfConnectedAN();
		defaultAN = listener.getDefaultAN();
		ifList = listener.getInterfacesList();	

		myData = new MyTableModel();
		
		/*	setDataInTable();

			addTableToStatusTab();
			refreshStatusBarAndFirstRow();
		 */
		InvokeInizializeTable(myData);
	}


	public MyTableModel getData(){
		return myData;
	}

	public void onSettingOk()
	{
		setEnabled(true);
		setFocusableWindowState(true);
	}



	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************

	private void onTreeSelectedItem()
	{
		Object treeNode = tree.getLastSelectedPathComponent();
		if (treeNode == null) return;
		DefaultMutableTreeNode selectedNode = ((DefaultMutableTreeNode) treeNode);
		if (selectedNode.isRoot()) return;
		selectedItem = selectedNode.getUserObject();
		refreshAppData();
	}

	private void onComboSelectedPolicy()
	{
		//		System.out.println("[onComboSelectedPolicy] skip: "+skipAction);
		if (skipAction) return;

		skipAction = true;
		String selected = (String) CurrentPolicy.getSelectedItem();
		//		System.out.println(selected);
		if (selected.startsWith(CURRENT_FLAG)) {/*System.out.print("1: ");*/ selected = selected.substring(CURRENT_FLAG.length());}
		if (selected.startsWith(STORED_FLAG)) {/*System.out.print("2: ");*/  selected = selected.substring(STORED_FLAG.length());}
		//		System.out.println(selected);

		CurrentPolicy.getEditor().setItem(selected);
		skipAction = false;
	}


	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************
	
	public void refreshStatusBarAndFirstRow() {
		if(UPMTClient.getRME()) {
			lblFirstRow.setText(VIPA + listener.getVipaFix());
		}
		else {
			lblFirstRow.setText(VIPA + listener.getVipaFix() + "; " + CONNECTED_ANCHOR_NODES + listener.getNumOfConnectedAN() + "; " + DEFAULT_ANCHOR_NODE + listener.getDefaultAN());
		}
		lblStatus.setText(UPMTClient.getStatusMsg());

		//		String[] columnNamesArray = {
		//				"Tunnel Server",
		//                "eth0",
		//                "eth1",
		//                "eth2",
		//                "wifi0"};
		//
		//		String[] dataRowArray =  {"160.80.103.26", "OK", "-", "OK", "-"};





	}

	//Chiamata ogni volta che si seleziona un elemento o che avviene un handover o che si chiude un setting
	//Questo metodo riguarda la scheda "Applications"
	private void refreshAppData() {
		if (selectedItem == null) return;
		if(!(selectedItem instanceof String)) {
			if (((GuiSocket)selectedItem).getSocket()==null) return;
		}
		else {
			if(((String)selectedItem)==null) return; 
		}
		skipAction = true; // see onComboSelectedIface()
		String interf = (selectedItem instanceof String)? listener.getCurrentInterf((String)selectedItem):listener.getCurrentInterf(((GuiSocket)selectedItem).getSocket());
		if(lblInterface!=null && interf!=null) {
			lblInterface.setText(CURRENT_INTERFACE + interf);

			if (selectedItem instanceof String) { //APPLICATION
				String currentPol = listener.getCurrentPolicy((String)selectedItem);
				String storedPol = listener.getStoredPolicy((String)selectedItem);
				lblStoredPolicy.setText(STORED_POLICY + (storedPol==null?NO_STORED_POLICY:storedPol));

				//System.out.println("[refreshAppData] uno: "+skipAction);
				CurrentPolicy.removeAllItems();

				CurrentPolicy.addItem("Any");
				CurrentPolicy.addItem("Block");
				for (String iface : ifList) CurrentPolicy.addItem("Static " + iface);
				CurrentPolicy.addItem(CURRENT_FLAG + currentPol);
				if (storedPol != null) CurrentPolicy.addItem(STORED_FLAG + storedPol);

				//System.out.println("[refreshAppData] due: "+skipAction);
				CurrentPolicy.getEditor().setItem(currentPol);//

				{//TODO: Rimuovere blocco quando si potranno gestire le politiche dei socket
					CurrentPolicy.setEnabled(true);

					btnApply.setEnabled(true);
					btnSave.setEnabled(true);
				}
			}
			else //SOCKET
			{
				lblStoredPolicy.setText(STORED_POLICY + NO_STORED_POLICY);

				{//TODO: Rimuovere blocco quando si potranno gestire le politiche dei socket
					CurrentPolicy.getEditor().setItem("");
					CurrentPolicy.setEnabled(false);
					btnApply.setEnabled(false);
					btnSave.setEnabled(false);
				}
			}
			skipAction = false;
		}
		
	}


	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************

	public void addInterface(String ifName) {
		ifList.add(ifName);
		if(grapher != null)
			grapher.addInterface(ifName);
		addEwmaInterface(ifName);
		add3dInterface(ifName);
		refreshAppData();
	}

	public void removeInterface(String ifName) {
		ifList.remove(ifName);
		if(grapher != null)
			grapher.removeInterface(ifName);
		if(grapher3d != null)
			grapher3d.removeInterface(ifName);
		removeEwmaInterface(ifName);	
		remove3dInterface(ifName);	
		refreshAppData();
		
	}
	
	// aggiunge una nuova interfaccia al grafico ewma *per ogni AN attivo*
	private void addEwmaInterface(String ifName){
		System.out.println("Aggiuno interfacciaEWMA "+ifName);
		if(grapherEWMA != null)
			for(String AN : UPMTClient.getAssociatedANs().keySet())
					grapherEWMA.addANinterf(AN, ifName);
		
	}
	
	private void addRMEEwmaInterface(String endPointAddress, String ifName){
		if(grapherEWMA != null && grapherEWMA.getTraces().get(endPointAddress+":"+ifName)==null){
			grapherEWMA.addANinterf(endPointAddress, ifName);
		}
	}
	
	private void rmvRMEEwmaInterface(String endPointAddress, String ifName){
		if(grapherEWMA != null && grapherEWMA.getTraces().get(endPointAddress+":"+ifName)!=null){
			grapherEWMA.removeInterface(endPointAddress, ifName);
		}
	}
	
	private void addRME3dInterface(String endPointAddress, String ifName){
		if(linechart3d!=null && !linechart3d.getListANInterf().contains(endPointAddress+":"+ifName)){
			linechart3d.addGraph(endPointAddress, ifName);
		}
	}
	
	private void rmvRME3dInterface(String endPointAddress, String ifName){
		if(linechart3d!=null && linechart3d.getListANInterf().contains(endPointAddress+":"+ifName)){
			linechart3d.remove3dInterface(endPointAddress, ifName);
		}
	}
	
	private void add3dInterface(String ifName){
		if(linechart3d != null)
			for(String AN : UPMTClient.getAssociatedANs().keySet())
				linechart3d.add3dInterface(AN, ifName);
	}
	
	//rimuove l'interfaccia ifName nel grafico ewma
	private void removeEwmaInterface(String ifName){
				Vector<String> ANinterf = new Vector<String>(SipSignalManager.getRemoteTidStatusTable().keySet());
				for(int i = 0; i <  ANinterf.size(); i++){
					if(ifName.equals(ANinterf.get(i).substring(ANinterf.get(i).indexOf(":")+1, ANinterf.get(i).length())))
					{
						String an = ANinterf.get(i).substring(0,ANinterf.get(i).indexOf(":"));
						if(grapherEWMA != null)
							grapherEWMA.removeInterface(an, ifName);
					}		
					// System.out.println("addEwmaInterface(): "+ ANinterf.get(i).substring(ANinterf.get(i).indexOf(":"), ANinterf.get(i).length()-1));
					// System.out.println("addEwmaInterface(): ifNAme: "+ ifName + " ANinterf.get(i): "+ ANinterf.get(i).substring(ANinterf.get(i).indexOf(":")+1, ANinterf.get(i).length()));
				}
			 
	}
	//rimuove l'interfaccia ifName nel grafico lineChart3d
	private void remove3dInterface(String ifName){
		Vector<String> ANinterf = new Vector<String>(SipSignalManager.getRemoteTidStatusTable().keySet());
		for(int i = 0; i <  ANinterf.size(); i++){
			if(ifName.equals(ANinterf.get(i).substring(ANinterf.get(i).indexOf(":")+1, ANinterf.get(i).length())))
			{
				String an = ANinterf.get(i).substring(0,ANinterf.get(i).indexOf(":"));
				if(linechart3d != null)
					linechart3d.remove3dInterface(an, ifName);
			}
		}
		
	}

	public void setConnectedAN(int n) {
		numOfConnectedAN = n; refreshStatusBarAndFirstRow();
	}

	public void changeDefaultAN(String ANAddress) {
		defaultAN = ANAddress; 
		refreshStatusBarAndFirstRow();
	}

	/**
	 * refreshes data after a policy check:<BR>
	 * refreshes the application data and the GUI status table
	 */
	
	public void onPolicyCheck() {
		refreshAppData();
		//setDataInTable();
		myData.fireTableStructureChanged();
		setTableColumnWidth();
	}

	public void InvokeInizializeTable(final MyTableModel model){
		SwingUtilities.invokeLater(new Runnable() {
			public void run(){
				setDataInTable();
				addTableToStatusTab();
				refreshStatusBarAndFirstRow();
			}
		});
	}

	//modifica marco bonuglia
//	public void invokeRefreshTable(final MyTableModel model){
//		SwingUtilities.invokeLater(new Runnable() {
//			public void run(){
//				refreshAppData();
//				setDataInTable();
//				model.fireTableStructureChanged();
//				setTableColumnWidth();
//				
//			}
//		});
//	}
	
	public void invokeRefreshTable(){
		EventQueue.invokeLater(new Runnable() {
			public void run(){
				refreshAppData();
				setDataInTable();
				if(myData!=null) {
					myData.fireTableStructureChanged();
				}
				setTableColumnWidth();
				
			}
		});
	}
	
	
	
	public void refreshGui(){
		/*refreshAppData();
		refreshStatusBarAndFirstRow();
		setDataInTable();
		myData.fireTableStructureChanged();
		setTableColumnWidth();
		*/
//		invokeRefreshTable(myData); //modifica marco bonuglia
		invokeRefreshTable();
	}


	//**************************************************************************************************************
	/**
	 * displays an application on the list of active applications
	 * (i.e. applications with open sockets)
	 */
	
	public void addApp(String appName)
	{
		DefaultMutableTreeNode app = new DefaultMutableTreeNode(appName);	
		synchronized (treeModel) {
			treeModel.insertNodeInto(app, rootNode, rootNode.getChildCount());
		}
		printLog("ADDING APP " + appName, Log.LEVEL_HIGH);
		appList.put(appName, app);
		tree.scrollPathToVisible(new TreePath(app.getPath()));
	}
	/**
	 * undisplays an application from the list of active applications
	 * (i.e. applications with open sockets)
	 */

	public void removeApp(String appName)
	{
		DefaultMutableTreeNode appNode = appList.get(appName);
		printLog("removing " + appNode, Log.LEVEL_HIGH);
		synchronized (treeModel) {
			treeModel.removeNodeFromParent(appNode);
		}
	}

	public void addSocket(String appName, Socket socket)
	{
		DefaultMutableTreeNode socketNode = new DefaultMutableTreeNode(new GuiSocket(socket));
		DefaultMutableTreeNode parentApp = appList.get(appName);
		synchronized (treeModel) {
			treeModel.insertNodeInto(socketNode, parentApp, parentApp.getChildCount());
		}
		//tree.scrollPathToVisible(new TreePath(socketNode.getPath())); //espande tutti i socket, per debug
		
		socketList.put(socket.id(), socketNode);
		appForSocket.put(socket.id(), appName);
	}

	public void rmvSocket(final Socket socket) {
		EventQueue.invokeLater(new Runnable(){
			public void run(){
				DefaultMutableTreeNode socketNode = socketList.get(socket.id());
				synchronized (treeModel) {
					treeModel.removeNodeFromParent(socketNode);
				}
			}
		});
	}

	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************
	/**
	 * (TO BE CHECKED!!) this can only be called on applications that have open sockets 
	 */
	
	private void onApplyPolicy() {
		listener.setCurrentPolicy((String)selectedItem, (String)CurrentPolicy.getEditor().getItem());

	}

	private void onSavePolicy() {
		String oldPolicy = listener.getStoredPolicy((String)selectedItem);
		String newPolicy = (String)CurrentPolicy.getEditor().getItem();
		String appName = (String)selectedItem;

		if (oldPolicy == null) 
			listener.cfgPolicyAdding(appName, newPolicy);
		else if (!newPolicy.equals(oldPolicy)){
			listener.cfgPolicyEdit(appName, newPolicy);
		}
	}


	// ****************************** Logs *****************************
	
	private void printLog(String text, int loglevel) {
		UPMTClient.printGenericLog(this, text, loglevel);
	}

	@SuppressWarnings("serial")
	public class MyTableModel extends AbstractTableModel {
		private Vector<String> columnNames ;
		private Vector<Vector<String>> data ;
		
		
		public int getColumnCount() {
			return columnNames.size();
		}

		public void setAllData(Vector<String> vector, Vector<Vector<String>> vector2) {
			columnNames = vector;
			data =  vector2;
		}

		public int getRowCount() {
			return data.size();
		}

		public String getColumnName(int col) {
			return columnNames.elementAt(col);
		}

		public Object getValueAt(int row, int col) {
			if(row>=data.size()) {
//				System.err.println("errore riga --> "+row+" ma dimensione massima --> "+data.size());
				row = row-1; 
//				return null;
			}
			else if(col>=data.get(row).size()) {
//				System.err.println("errore colonna --> "+col+" ma dimensione massima --> "+data.get(row).size());
				col=col-1;
//				return null;
			}
			else {
				return data.elementAt(row).elementAt(col) == null ? null : data.elementAt(row).elementAt(col);
			}
			return data.elementAt(row).elementAt(col) == null ? null : data.elementAt(row).elementAt(col);
		}

		public Class<?> getColumnClass(int c) {
			return getValueAt(0, c).getClass();
		}

		//	    /*
		//	     * Don't need to implement this method unless your table's
		//	     * editable.
		//	     */
		//	    public boolean isCellEditable(int row, int col) {
		//	        //Note that the data/cell address is constant,
		//	        //no matter where the cell appears onscreen.
		//	        if (col < 2) {
		//	            return false;
		//	        } else {
		//	            return true;
		//	        }
		//	    }

		/*
		 * Don't need to implement this method unless your table's
		 * data can change.
		 */
	
		public void setValueAt(Object value, int row, int col) {
			data.elementAt(row).set(col, (String) value); 
			fireTableCellUpdated(row, col);
		}
	}

	/**
	 * builds the data of the table
	 */

	private void setDataInTable() {
		//modifica marco bonuglia
		//		Vector<String> colNameVector = new Vector<String>();
		//		colNameVector.add("Anchor Node");
		//		colNameVector.add("VIPA");
		//		for (String interf : UPMTClient.getAvailableIfs().keySet()) {
		//		colNameVector.add(interf);
		//	}
		
		Vector<Vector<String>> tableData = new Vector<Vector<String>>();
		for(String interf: UPMTClient.getAvailableIfs().keySet()) {
			if(!colNameVector.contains(interf)) {
				colNameVector.add(interf);
			}
		}

		if(UPMTClient.getRME()) { // RME mode
			refreshVepaList();
			for(String endPointAddress: UPMTClient.getRMETunnelsToGUI()) {

				Vector<String> anInfoRow = new Vector<String>();

				if(UPMTClient.getCfgANList().contains(endPointAddress)) { //Tunnel Client
					String vipa = UPMTClient.getVipaUI(endPointAddress);
					int bestTid = UPMTClient.TID_DROP;
					if(tunnelInUseForVipa.containsKey(vipa)) {
						bestTid = tunnelInUseForVipa.get(vipa);
						if(bestTid!=UPMTClient.TID_DROP) {

						}
					}


					anInfoRow.add(vipa==null ? "DISCONNECTED" : vipa);
					anInfoRow.add(endPointAddress + ":" + (UPMTClient.getAssociatedANs().get(endPointAddress) != null ? UPMTClient.getAssociatedANs().get(endPointAddress) : "?"));
					//String vipa = SipSignalManager.getVipaForAN(endPointAddress);
					for (String interf : UPMTClient.getAvailableIfs().keySet()) {
						synchronized(SipSignalManager.getRemoteTidStatusTable()){
							if(SipSignalManager.getRemoteTidStatusTable().containsKey(endPointAddress+":"+interf)){
								TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(endPointAddress+":"+interf);
								if(ti!=null){
//									if(interf!= null && endPointAddress!=null && TunnelManager.getTidTable()!=null) {
									if(TunnelManager.getTidTable().containsKey(interf+":"+endPointAddress)) {
										int localTID = TunnelManager.getTidTable().get(interf+":"+endPointAddress);
										if(ti.getStatus()==TunnelInfo.TUNNEL_SETUP){
											String details = "";
											if(localTID==bestTid) {
												details+="*";
											}
//											details += ti.getLastDelay()!=0?
//											"Connected ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retries) [avg "+ ti.getEWMA_Delay()+" ms, avg "+ ti.getEWMA_Loss()+"% loss]"    
//													: "Connected";
											details += "Connected ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retries) [avg "+ ti.getEWMA_Delay()+" ms, avg "+ ti.getEWMA_Loss()+"% loss]";

											this.addRMEEwmaInterface(endPointAddress, interf);
											this.addRME3dInterface(endPointAddress, interf);
											anInfoRow.add(details);

										} 
										else if(ti.getStatus()==TunnelInfo.NO_TUNNEL){
											anInfoRow.add("Not Connected");
											this.rmvRMEEwmaInterface(endPointAddress, interf);
											this.rmvRME3dInterface(endPointAddress, interf);
										}
										else {
											anInfoRow.add("?");
											anInfoRow.set(1, vipa + " (?)");
											this.rmvRMEEwmaInterface(endPointAddress, interf);
											this.rmvRME3dInterface(endPointAddress, interf);
										}
									}
								}
								else{
									anInfoRow.add("Not Connected");
									this.rmvRMEEwmaInterface(endPointAddress, interf);
									this.rmvRME3dInterface(endPointAddress, interf);
								}
							}
							else{
								anInfoRow.add("Not Connected");
								this.rmvRMEEwmaInterface(endPointAddress, interf);
								this.rmvRME3dInterface(endPointAddress, interf);
							}
						}
						if(!UPMTClient.getAvailableIfs().keySet().contains(interf)) {
							anInfoRow.setElementAt("Disconnected", colNameVector.indexOf(interf));
						}
					}	

					boolean disconnected = true;

					for(int i = 2; i < anInfoRow.size(); i++){
						if(!anInfoRow.get(i).startsWith("?"))
							disconnected = false;
					}

					if(disconnected)
						anInfoRow.set(1, "DISCONNECTED");

					tableData.add(anInfoRow);
				}
				else { // tunnel from server
					String vipa = UPMTClient.getVipaUI(endPointAddress);

					int bestTid = UPMTClient.TID_DROP;
					if(tunnelInUseForVipa.containsKey(vipa)) {
						bestTid = tunnelInUseForVipa.get(vipa);
						if(bestTid!=UPMTClient.TID_DROP) {

						}
					}

					anInfoRow.add(vipa==null ? "DISCONNECTED" : vipa);
					anInfoRow.add(endPointAddress + ":50000");

					for (String interf : UPMTClient.getAvailableIfs().keySet()) {
						synchronized(UPMTClient.getRMERemoteTidStatusTable()){
							if(UPMTClient.getRMERemoteTidStatusTable().containsKey(endPointAddress+":"+interf)){
								RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(endPointAddress+":"+interf);

								if(ti!=null) {
									
									int localTID = ti.getServerTunnelID();
									synchronized (RMEServer.tunnelManager) {
//										if(RMEServer.tunnelManager.getTunnelEndAddress(localTID)==null) {
//											ti.setStatus(RMETunnelInfo.NO_TUNNEL);
//										}
//										else {
//											ti.setStatus(RMETunnelInfo.TUNNEL_SETUP);
//										}
									}
									if(ti.getStatus()==RMETunnelInfo.TUNNEL_SETUP){
										String details = "";
										if(localTID==bestTid) {
											details+="*";
										}
//										details += ti.getLastDelay()!=0?
//												"Connected ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retries) [avg "+ ti.getEWMA_delay()+" ms, avg "+ ti.getEWMA_loss()+"% loss]"    
//												: "Connected";
										details += "Connected ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retries) [avg "+ ti.getEWMA_delay()+" ms, avg "+ ti.getEWMA_loss()+"% loss]";
										
										this.addRMEEwmaInterface(endPointAddress, interf);
										this.addRME3dInterface(endPointAddress, interf);
										anInfoRow.add(details);
									}
									else if(ti.getStatus()==TunnelInfo.NO_TUNNEL){
										anInfoRow.add("Not Connected");
										this.rmvRMEEwmaInterface(endPointAddress, interf);
										this.rmvRME3dInterface(endPointAddress, interf);
									}
									else {
										anInfoRow.add("?");
										anInfoRow.set(1, vipa + " (?)");
										this.rmvRMEEwmaInterface(endPointAddress, interf);
										this.rmvRME3dInterface(endPointAddress, interf);
									}
								}
								else{
									anInfoRow.add("Not Connected");
									this.rmvRMEEwmaInterface(endPointAddress, interf);
									this.rmvRME3dInterface(endPointAddress, interf);
								}
							}
							else{
								anInfoRow.add("Not Connected");
								this.rmvRMEEwmaInterface(endPointAddress, interf);
								this.rmvRME3dInterface(endPointAddress, interf);
							}
						}
						if(!UPMTClient.getAvailableIfs().keySet().contains(interf)) {
							anInfoRow.setElementAt("Disconnected", colNameVector.indexOf(interf));
						}
					}	

					boolean disconnected = true;

					for(int i = 2; i < anInfoRow.size(); i++){
						if(!anInfoRow.get(i).startsWith("?"))
							disconnected = false;
					}

					if(disconnected)
						anInfoRow.set(1, "DISCONNECTED");

					tableData.add(anInfoRow);


				}

			}

		}
		else {
			for (String anchorNode : UPMTClient.getCfgANList()) {

				Vector<String> anInfoRow = new Vector<String>();
				String[] token = anchorNode.split(":");

				anInfoRow.add(token[0].trim() + ":" + (UPMTClient.getAssociatedANs().get(token[0].trim()) != null ? UPMTClient.getAssociatedANs().get(token[0].trim()) : "?"));
				String vipa = SipSignalManager.getVipaForAN(token[0].trim());

				anInfoRow.add(vipa==null ? "DISCONNECTED" : vipa);
				for (String interf : UPMTClient.getAvailableIfs().keySet()) {
					synchronized(SipSignalManager.getRemoteTidStatusTable()){
						TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(anchorNode.split(":")[0].trim()+":"+interf);
						if(ti!=null){
							if(ti.getStatus()==TunnelInfo.TUNNEL_SETUP){

								String details = ti.getLastDelay()!=0?
										"Connected ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retries) [avg "+ ti.getEWMA_Delay()+" ms, avg "+ ti.getEWMA_Loss()+"% loss]"    
										: "Connected";
								anInfoRow.add(details);

							} 

							else if(ti.getStatus()==TunnelInfo.NO_TUNNEL){
								anInfoRow.add("Not Connected");
							}
							else {
								anInfoRow.add("?");
								anInfoRow.set(1, vipa + " (?)");
							}
						}
						else anInfoRow.add("Not Connected");
					}
					if(!UPMTClient.getAvailableIfs().keySet().contains(interf)) {
						anInfoRow.setElementAt("Disconnected", colNameVector.indexOf(interf));
					}
				}	

				boolean disconnected = true;

				for(int i = 2; i < anInfoRow.size(); i++){
					if(!anInfoRow.get(i).startsWith("?"))
						disconnected = false;
				}

				if(disconnected)
					anInfoRow.set(1, "DISCONNECTED");

				tableData.add(anInfoRow);
			}
		}
		if(myData!=null && colNameVector!=null && tableData != null) {
			tableData= sortTableData(tableData);
			myData.setAllData(colNameVector,tableData);		
		}
	}

	private Vector<Vector<String>> sortTableData(Vector<Vector<String>> tableData) {
		Collections.sort(tableData, new Comparator<Vector<String>>(){
			public int compare(Vector<String> v1, Vector<String> v2) {
				return v1.get(0).compareTo(v2.get(0)); 
			}});
		return tableData;
	}

	public void invokeSetAllData(final Vector<String> column, final Vector<Vector<String>> myTableData){
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				myData.setAllData(column,myTableData);
			}	
		});	
	}

	/**
	 * inserts the table in the status tab (and then sets column size)
	 */

	private void addTableToStatusTab() { 
		table = new JTable(myData) {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(MouseEvent e){
				Object tip = null;
				java.awt.Point p = e.getPoint();
				if(rowAtPoint(p) != -1 && columnAtPoint(p) != -1){
					int RowIndex = rowAtPoint(p);
					int colIndex = columnAtPoint(p);
					int realColumnIndex = convertColumnIndexToModel(colIndex);
					int realRowIndex = convertRowIndexToModel(RowIndex);

					TableColumnModel column = table.getColumnModel();
					Object columnlbl = column.getColumn(realColumnIndex).getHeaderValue();
					synchronized (myData) {
						tip ="<html>" + "<DIV align="+"center"+">" + columnlbl +  ": " + "</DIV>" + "<br>" + myData.getValueAt(realRowIndex,realColumnIndex) + "</html";
					}
				}
				return tip == null ? null : tip.toString();
			}

		};
		
		table.getTableHeader().setReorderingAllowed(false);

		GridBagConstraints gbc_table = new GridBagConstraints();
		gbc_table.anchor = GridBagConstraints.WEST;
		gbc_table.gridwidth = 5;
		gbc_table.gridx = 0;
		gbc_table.gridy = 2;
		gbc_table.weightx = 10;
		gbc_table.fill = GridBagConstraints.BOTH;

		GridBagConstraints gbc_tableHeader = new GridBagConstraints();
		gbc_tableHeader.anchor = GridBagConstraints.WEST;
		gbc_tableHeader.gridwidth = 5;
		gbc_tableHeader.gridx = 0;
		gbc_tableHeader.gridy = 1;
		gbc_tableHeader.weightx = 10;
		gbc_tableHeader.fill = GridBagConstraints.HORIZONTAL;
		table.setRowHeight(20);

		statusPanel.add(table.getTableHeader(), gbc_tableHeader);
		statusPanel.add(table, gbc_table);
		setTableColumnWidth();

	}

	public void startGraphers(){
		if(!UPMTClient.getRME()){
			grapher = new TrafficGrapher();
			grapher.startListen(ifList);

			/*
			 * bandWidthGrapher = new BandWidthGrapher();
			 * bandWidthGrapher.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
			 */
			linechart3d = new LineChart3d();
			linechart3d.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));

			grapherEWMA = new TrafficGrapherEWMA();
			grapherEWMA.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
		}else{
			if(UPMTClient.grapher3D){
				linechart3d = new LineChart3d();
				linechart3d.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
				grapher3d = new TrafficGrapher3d();
				grapher3d.startListen(ifList);
			}else{
				grapher = new TrafficGrapher();
				grapher.startListen(ifList);
				grapherEWMA = new TrafficGrapherEWMA();
				grapherEWMA.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
			}
		}
	}

	/**
	 * sets column width and cell alignment (centered) (in the table !!)
	 */
	
	private void setTableColumnWidth() {
		TableColumn column = null;
		DefaultTableCellRenderer dtcr = new DefaultTableCellRenderer();    
		dtcr.setHorizontalAlignment(SwingConstants.CENTER);		
		if(table!=null){
			for (int i = 0; i < myData.columnNames.size(); i++) {
				if( table.getColumnModel().getColumnCount() == myData.columnNames.size()){
					column = table.getColumnModel().getColumn(i);
					column.setCellRenderer(dtcr);
				}
				if (i == 0) {
					if(UPMTClient.getRME()) column.setPreferredWidth(70);
					else column.setPreferredWidth(200); //first column is bigger
				} else if (i==1) {
					if(UPMTClient.getRME()) column.setPreferredWidth(200);
					else column.setPreferredWidth(70); //second column is the VIPA			
				} else {
					column.setPreferredWidth(200);
				}
			}
		}
	}
	
	public HashMap<String, Integer> tunnelInUseForVipa = new HashMap<String, Integer>();
	
	public void setTunnelInUseForVipa(String Vipa, int tunnelID) {
		tunnelInUseForVipa.put(Vipa, tunnelID);
	}
	
	public int getTunnelInUseForVipa(String Vipa) {
		if(tunnelInUseForVipa.containsKey(Vipa)) {
			return tunnelInUseForVipa.get(Vipa).intValue();
		}
		else {
			return UPMTClient.TID_DROP;
		}
	}
	/**
	 * Update the Jlist of Vepa
	 * @author Pierluigi Greto
	 */
	private void refreshVepaList(){

		listVepaForFrame.removeAllElements();
		boolean connected =false;
		for(String endPointAddress: UPMTClient.getRMETunnelsToGUI()) {
			connected=false;
			for (String interf : UPMTClient.getAvailableIfs().keySet()) {
				if(UPMTClient.getCfgANList().contains(endPointAddress)) { //Tunnel Client
					synchronized(SipSignalManager.getRemoteTidStatusTable()){

						TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(endPointAddress+":"+interf);
						if(ti!=null && ti.getStatus()==TunnelInfo.TUNNEL_SETUP){
							connected=true;
						}
					}
				} else {
					synchronized(UPMTClient.getRMERemoteTidStatusTable()){
						RMETunnelInfo ti = UPMTClient.getRMERemoteTidStatusTable().get(endPointAddress+":"+interf);
						if(ti!=null && ti.getStatus()==RMETunnelInfo.TUNNEL_SETUP){
							connected=true;
						}
					}
				}
			}
			String vepa = UPMTClient.getVipaUI(endPointAddress);
			if(connected){
				if(!listVepaForFrame.contains(vepa)){
					boolean inserito=false;
					for(int i=0; i<listVepaForFrame.size() && !inserito;i++){
						if(vepa!=null && listVepaForFrame.get(i)!=null) {
							if(listVepaForFrame.get(i).toString().compareTo(vepa)>0){
								listVepaForFrame.add(i, vepa);
								inserito=true;
							}
						}
					}
					if(!inserito){
						listVepaForFrame.addElement(vepa);
					}
				}
			}

		}
	}
	
	public void setLoadBalance(){
		if(UPMTClient.interfaceBalance){
			loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Active</html>");
		} else {
			loadBalanceButton.setText("<html>Load Balance&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp<br>Disabled</html>");
		}
	}
	
}