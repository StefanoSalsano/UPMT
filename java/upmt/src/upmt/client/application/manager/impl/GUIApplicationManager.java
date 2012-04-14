package upmt.client.application.manager.impl;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
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

//come si aggiungono i socket (modificare l'interfaccia aggiungendo addSocket)
//cosa succede qnd si rimuove un'interfaccia
//come accorgersi ke e' stata rimossa un'applicazione (o socket)
public class GUIApplicationManager extends JFrame implements ApplicationManager
{

	private static final long serialVersionUID = -247976287606177664L;
	private static final String VIPA = "Local VIPA: ";
	private static final String DEFAULT_ANCHOR_NODE = "Default AN: ";
	private static final String CONNECTED_ANCHOR_NODES = "Connected AN(s): ";
	private static final String CURRENT_POLICY = "Current policy: ";
	private static final String CURRENT_INTERFACE = "Current interface: ";
	private static final String STORED_POLICY = "Stored Policy: ";
	private static final String CURRENT_AN="Current Anchor Node: ";
	private static final String SETTINGS = "Settings";
	private static final String NETWORK_GRAPH = "Show network graph";
	private static final String NETWORK_GRAPH_EWMA = "Show EWMA delay graph";
	private static final String EXIT = "Exit";
	private static final String STORED_FLAG = "Stored -> ";
	private static final String CURRENT_FLAG = "Current -> ";
	private static final String NO_STORED_POLICY = "-";

	private JPanel contentPane;
	private final JTree tree;
	private DefaultTreeModel treeModel;
	private DefaultMutableTreeNode rootNode;
	private Object selectedItem;
	private JLabel lblCurrPolicy;
	private JButton btnApply;
	private JButton btnSave;
	private JLabel lblStoredPolicy;
	private JMenuBar menuBar;
	private JMenu menuFile;

	private ApplicationManagerListener listener;
	private JComboBox CurrentPolicy;
	private JLabel lblFirstRow;
	private JLabel lblStatus;
	private JLabel lblInterface;
	private JLabel lblAnchorNode;
	/**
	 * number of connected Anchor Nodes
	 */
	private int numOfConnectedAN;
	private String defaultAN;
	private String vipaFix;
	private Vector<String> ifList;
	private boolean skipAction;

	private MyTableModel myData ;

	private JTable table;
	
	private JTabbedPane tabbedPane; 

	/**appName -> treeNode*/
	private Hashtable<String, DefaultMutableTreeNode> appList = new Hashtable<String, DefaultMutableTreeNode>();

	/**socketID -> treeNode*/
	private Hashtable<String, DefaultMutableTreeNode> socketList = new Hashtable<String, DefaultMutableTreeNode>();

	/**socketID -> appName*/
	private Hashtable<String, String> appForSocket = new Hashtable<String, String>();

	private TrafficGrapher grapher;
	private static TrafficGrapherEWMA grapherEWMA;

	private JPanel statusPane;

	public GUIApplicationManager() {

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setTitle("UPMT Manager");
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				listener.stop();
				dispose();
			}
		});
		setBounds(100, 100, 900, 500);
		skipAction = true;

		contentPane = new JPanel();	
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));	
		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0};
		contentPane.setLayout(gbl_contentPane);

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		GridBagConstraints gbc_tabbedPane = new GridBagConstraints();
		gbc_tabbedPane.gridwidth = 2;
		gbc_tabbedPane.insets = new Insets(0, 0, 5, 0);
		gbc_tabbedPane.fill = GridBagConstraints.BOTH;
		gbc_tabbedPane.gridx = 0;
		gbc_tabbedPane.gridy = 0;
		contentPane.add(tabbedPane, gbc_tabbedPane);




		//*************************************
		//Status TAB *************************
		//*************************************
		statusPane = new JPanel();
		statusPane.setBorder(BorderFactory.createEmptyBorder());
		statusPane.setOpaque(false);
		tabbedPane.addTab("Status", null, statusPane, null);
		GridBagLayout gbl_statusPanel = new GridBagLayout();
		gbl_statusPanel.columnWidths = new int[]{0, 0, 0};
		gbl_statusPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
		gbl_statusPanel.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gbl_statusPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		statusPane.setLayout(gbl_statusPanel);

		lblFirstRow = new JLabel();
		lblFirstRow.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblVipaDefault = new GridBagConstraints();
		gbc_lblVipaDefault.anchor = GridBagConstraints.WEST;
		gbc_lblVipaDefault.gridwidth = 4;
		gbc_lblVipaDefault.gridx = 0;
		gbc_lblVipaDefault.gridy = 0;
		gbc_lblVipaDefault.weightx = 1;
		statusPane.add(lblFirstRow, gbc_lblVipaDefault);


		lblStatus = new JLabel();
		lblStatus.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblStatus = new GridBagConstraints();
		gbc_lblStatus.anchor = GridBagConstraints.WEST;
		gbc_lblStatus.gridwidth = 4;
		gbc_lblStatus.gridx = 0;
		gbc_lblStatus.gridy = 4;
		gbc_lblStatus.weightx = 1;
		statusPane.add(lblStatus, gbc_lblStatus);


		//******************************************************************************		


		//*************************************
		//Applications TAB *************************
		//*************************************
		JPanel applicationPane = new JPanel();
		applicationPane.setBorder(BorderFactory.createEmptyBorder());
		applicationPane.setOpaque(false);
		tabbedPane.addTab("Applications", null, applicationPane, null);
		GridBagLayout gbl_applicationPanel = new GridBagLayout();
		gbl_applicationPanel.columnWidths = new int[]{0, 0, 0};
		gbl_applicationPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
		gbl_applicationPanel.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gbl_applicationPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		applicationPane.setLayout(gbl_applicationPanel);


		//		JLabel lblApplications = new JLabel("Applications:");
		//		GridBagConstraints gbc_lblApplications = new GridBagConstraints();
		//		gbc_lblApplications.anchor = GridBagConstraints.WEST;
		//		gbc_lblApplications.gridwidth = 4;
		//		gbc_lblApplications.insets = new Insets(0, 0, 5, 0);
		//		gbc_lblApplications.gridx = 0;
		//		gbc_lblApplications.gridy = 0;
		//		gbc_lblApplications.weightx = 1;
		////		contentPane.add(lblApplications, gbc_lblApplications);
		//		applicationPane.add(lblApplications, gbc_lblApplications);


		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.weighty = 1.0;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 0);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 0;
		gbc_scrollPane.gridy = 1;
		gbc_scrollPane.gridwidth = 4;
		gbc_scrollPane.weightx = 1;
		//		contentPane.add(scrollPane, gbc_scrollPane);
		applicationPane.add(scrollPane, gbc_scrollPane);

		rootNode = new DefaultMutableTreeNode();
		treeModel = new DefaultTreeModel(rootNode);
		tree = new JTree(treeModel);
		tree.setRootVisible(false);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		scrollPane.setViewportView(tree);

		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				onTreeSelectedItem();
			}
		});


		lblInterface = new JLabel(CURRENT_INTERFACE);
		GridBagConstraints gbc_lblInterface = new GridBagConstraints();
		gbc_lblInterface.gridwidth = 4;
		gbc_lblInterface.insets = new Insets(0, 0, 5, 5);
		gbc_lblInterface.anchor = GridBagConstraints.WEST;
		gbc_lblInterface.gridx = 0;
		gbc_lblInterface.gridy = 2;
		gbc_lblInterface.weightx = 0;
		//		contentPane.add(lblInterface, gbc_lblInterface);
		applicationPane.add(lblInterface, gbc_lblInterface);

		lblCurrPolicy = new JLabel(CURRENT_POLICY);
		GridBagConstraints gbc_lblCurrPolicy = new GridBagConstraints();
		gbc_lblCurrPolicy.anchor = GridBagConstraints.WEST;
		gbc_lblCurrPolicy.insets = new Insets(0, 0, 5, 5);
		gbc_lblCurrPolicy.gridx = 0;
		gbc_lblCurrPolicy.gridy = 3;
		//		contentPane.add(lblCurrPolicy, gbc_lblCurrPolicy);
		applicationPane.add(lblCurrPolicy, gbc_lblCurrPolicy);

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
		//		contentPane.add(CurrentPolicy, gbc_CurrentPolicy);
		applicationPane.add(CurrentPolicy, gbc_CurrentPolicy);

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
		//		contentPane.add(btnApply, gbc_btnApply);
		applicationPane.add(btnApply, gbc_btnApply);

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
		//		contentPane.add(btnSave, gbc_btnSetPolicy);
		applicationPane.add(btnSave, gbc_btnSetPolicy);

		lblStoredPolicy = new JLabel(STORED_POLICY);
		GridBagConstraints gbc_lblStoredProcedure = new GridBagConstraints();
		gbc_lblStoredProcedure.gridwidth = 4;
		gbc_lblStoredProcedure.anchor = GridBagConstraints.WEST;
		gbc_lblStoredProcedure.insets = new Insets(0, 0, 5, 5);
		gbc_lblStoredProcedure.gridx = 0;
		gbc_lblStoredProcedure.gridy = 4;
		//		contentPane.add(lblStoredPolicy, gbc_lblStoredProcedure);
		applicationPane.add(lblStoredPolicy, gbc_lblStoredProcedure);


		//******************************************************************************		


		//***********MENU
		menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		menuFile = new JMenu("File");
		menuBar.add(menuFile);

		JMenuItem menuItemSetting = new JMenuItem(SETTINGS);
		menuFile.add(menuItemSetting);
		menuItemSetting.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
			GUIApplicationManager thiz = GUIApplicationManager.this;
			new Setting(thiz.listener, thiz).setVisible(true);
			thiz.setEnabled(false);
			thiz.setFocusableWindowState(false);
		}});

		JMenuItem menuItemGraph = new JMenuItem(NETWORK_GRAPH);
		menuFile.add(menuItemGraph);
		menuItemGraph.addActionListener(
				new ActionListener() { 
					public void actionPerformed(ActionEvent e) {
						grapher = new TrafficGrapher();
						grapher.startListen(ifList);
					}
				}
				);

		JMenuItem menuItemGraphEWMA = new JMenuItem(NETWORK_GRAPH_EWMA);
		menuFile.add(menuItemGraphEWMA);
		menuItemGraphEWMA.addActionListener(
				new ActionListener() { 
					public void actionPerformed(ActionEvent e) {
						grapherEWMA = new TrafficGrapherEWMA();
						grapherEWMA.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
					}
				}
				);

		JMenuItem menuItemExit = new JMenuItem(EXIT);
		menuFile.add(menuItemExit);
		menuItemExit.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				listener.stop();
				dispose();
			}});
	}

	public void startListen(ApplicationManagerListener listener)
	{
		this.listener = listener;


		//		myData = new MyTableModel();
		//		setDataInTable();
		//
		//		addTableToStatusTab();
		//		refreshStatusBarAndTable();


		setVisible(true);
		refreshStatusBarAndFirstRow();
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
		setDataInTable();

		addTableToStatusTab();
		refreshStatusBarAndFirstRow();
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
		if (selected.startsWith(STORED_FLAG)) {/*System.out.print("2: ");*/ selected = selected.substring(STORED_FLAG.length());}
		//		System.out.println(selected);

		CurrentPolicy.getEditor().setItem(selected);
		skipAction = false;
	}


	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************
	
	public void refreshStatusBarAndFirstRow() {
		lblFirstRow.setText(VIPA + listener.getVipaFix() + "; " + CONNECTED_ANCHOR_NODES + listener.getNumOfConnectedAN() + "; " + DEFAULT_ANCHOR_NODE + listener.getDefaultAN());
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

	//kiamata ogni volta ke si seleziona un elemento o ke avviene un handover o ke si kiude setting

	private void refreshAppData() {
		if (selectedItem == null) return;
		skipAction = true; // see onComboSelectedIface()
		String interf = (selectedItem instanceof String)? listener.getCurrentInterf((String)selectedItem):listener.getCurrentInterf(((GuiSocket)selectedItem).getSocket());
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

			{//TODO: Rimuovere blocco quando si potranno gestire le politike dei socket
				CurrentPolicy.setEnabled(true);

				btnApply.setEnabled(true);
				btnSave.setEnabled(true);
			}
		}
		else //SOCKET
		{
			lblStoredPolicy.setText(STORED_POLICY + NO_STORED_POLICY);

			{//TODO: Rimuovere blocco quando si potranno gestire le politike dei socket
				CurrentPolicy.getEditor().setItem("");
				CurrentPolicy.setEnabled(false);
				btnApply.setEnabled(false);
				btnSave.setEnabled(false);
			}
		}

		skipAction = false;
	}


	//**************************************************************************************************************
	//**************************************************************************************************************
	//**************************************************************************************************************

	public void addInterface(String ifName) {
		ifList.add(ifName);
		if(grapher != null)
		grapher.addInterface(ifName);
	
		refreshAppData();
	}

	public void removeInterface(String ifName) {
		ifList.remove(ifName);
		if(grapher != null)
		grapher.removeInterface(ifName);
		refreshAppData();
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
		setDataInTable();
		myData.fireTableStructureChanged();
		setTableColumnWidth();
	}

	public void refreshGui(){
		refreshAppData();
		refreshStatusBarAndFirstRow();
		setDataInTable();
		myData.fireTableStructureChanged();
		setTableColumnWidth();
	}


	//**************************************************************************************************************
	/**
	 * displays an application on the list of active applications
	 * (i.e. applications with open sockets)
	 */
	
	public void addApp(String appName)
	{
		DefaultMutableTreeNode app = new DefaultMutableTreeNode(appName);		
		treeModel.insertNodeInto(app, rootNode, rootNode.getChildCount());

		printLog("ADDING APP " + appName, Log.LEVEL_HIGH);
		appList.put(appName, app);

		//System.out.println("added app " + appName +" "+ iface.id);
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
		treeModel.removeNodeFromParent(appNode);
	}

	public void addSocket(String appName, Socket socket)
	{
		DefaultMutableTreeNode socketNode = new DefaultMutableTreeNode(new GuiSocket(socket));
		DefaultMutableTreeNode parentApp = appList.get(appName);

		treeModel.insertNodeInto(socketNode, parentApp, parentApp.getChildCount());
		//tree.scrollPathToVisible(new TreePath(socketNode.getPath())); //espande tutti i socket, per debug

		socketList.put(socket.id(), socketNode);
		appForSocket.put(socket.id(), appName);
	}

	public void rmvSocket(Socket socket) {
		DefaultMutableTreeNode socketNode = socketList.get(socket.id());

		//System.out.println("removing " + socketNode);
		treeModel.removeNodeFromParent(socketNode);
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
		else if (!newPolicy.equals(oldPolicy)) 
			listener.cfgPolicyEdit(appName, newPolicy);
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
			return data.elementAt(row).elementAt(col);
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
		Vector<String> colNameVector = new Vector<String>();
		Vector<Vector<String>> tableData = new Vector<Vector<String>>(); 

		colNameVector.add("Anchor Node");
		colNameVector.add("VIPA");
		for (String interf : UPMTClient.getAvailableIfs().keySet()) {
			colNameVector.add(interf);
		}
		for (String anchorNode : UPMTClient.getCfgANList()) {

			Vector<String> anInfoRow = new Vector<String>();
			String[] token = anchorNode.split(":");

			anInfoRow.add(token[0].trim() + ":" + (UPMTClient.getAssociatedANs().get(token[0].trim()) != null ? UPMTClient.getAssociatedANs().get(token[0].trim()) : "?"));
			String vipa = SipSignalManager.getVipaForAN(token[0].trim());

			anInfoRow.add(vipa==null ? "DISCONNECTED" : vipa);
			for (String interf : UPMTClient.getAvailableIfs().keySet()) {
				//if (SipSignalManager.canUseTunnel(anchorNode.split(":")[0].trim(), interf)) {
				synchronized(SipSignalManager.getRemoteTidStatusTable()){
					TunnelInfo ti = SipSignalManager.getRemoteTidStatusTable().get(anchorNode.split(":")[0].trim()+":"+interf);
					if(ti!=null){
						if(ti.getStatus()==TunnelInfo.TUNNEL_SETUP){
							
								String details = ti.getLastDelay()!=0?
										"OK ("+ Integer.toString(ti.getLastDelay())+" ms, " + ti.getNumberRetry()+" retrays) [avg "+ ti.getEWMA_Delay()+" ms, avg "+ ti.getEWMA_Loss()+"% loss]"    
										: "OK";
								anInfoRow.add(details);
							
						} 

						else if(ti.getStatus()==TunnelInfo.NO_TUNNEL){
							anInfoRow.add("--");
						}
						else {
							anInfoRow.add("?");
							anInfoRow.set(1, vipa + " (?)");
						}
					}
					else anInfoRow.add("--");
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

		myData.setAllData(colNameVector,tableData);
	}

	
	/**
	 * inserts the table in the status tab (and then sets column size)
	 */
	private void addTableToStatusTab() { 

		table = new JTable(myData);
		

		GridBagConstraints gbc_table = new GridBagConstraints();
		gbc_table.anchor = GridBagConstraints.WEST;
		gbc_table.gridwidth = 4;
		gbc_table.gridx = 0;
		gbc_table.gridy = 2;
		gbc_table.weightx = 10;
		gbc_table.fill = GridBagConstraints.HORIZONTAL;
		//	gbc_table.gridheight = GridBagConstraints.REMAINDER;

		GridBagConstraints gbc_tableHeader = new GridBagConstraints();
		gbc_tableHeader.anchor = GridBagConstraints.WEST;
		gbc_tableHeader.gridwidth = 4;
		gbc_tableHeader.gridx = 0;
		gbc_tableHeader.gridy = 1;
		gbc_tableHeader.weightx = 10;
		gbc_tableHeader.fill = GridBagConstraints.HORIZONTAL;

		statusPane.add(table.getTableHeader(), gbc_tableHeader);
		statusPane.add(table, gbc_table);
		setTableColumnWidth();

	}

	public void startGraphers(){
		grapher = new TrafficGrapher();
		grapher.startListen(ifList);
		grapherEWMA = new TrafficGrapherEWMA();
		grapherEWMA.startListen(new Vector<String>(UPMTClient.getAssociatedANs().keySet()),new Vector<String>(UPMTClient.getAvailableIfs().keySet()));
	}



	/**
	 * sets column width and cell alignment (centered) (in the table !!)
	 */
	
	private void setTableColumnWidth() {
		TableColumn column = null;
		DefaultTableCellRenderer dtcr = new DefaultTableCellRenderer();    
		dtcr.setHorizontalAlignment(SwingConstants.CENTER);  

		for (int i = 0; i < myData.columnNames.size(); i++) {
			column = table.getColumnModel().getColumn(i);
			column.setCellRenderer(dtcr);
			if (i == 0) {
				column.setPreferredWidth(200); //first column is bigger
			} else if (i==1) {
				column.setPreferredWidth(70); //second column is the VIPA
			} else {
				column.setPreferredWidth(300);
			}
		}
	}
	


}
