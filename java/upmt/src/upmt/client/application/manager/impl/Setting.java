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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import upmt.client.application.manager.ApplicationManagerListener;


//SETTING policy
//TODO: Nella tab delle policy andrebbe scritto:
//L'aggiunta o la modifica alla policy di un applicazione aperta in questa tab, avr� effetto al prossimo avvio dell'applicazione.
//Per modificare dinamicamente la policy di un applicazione aperta, utilizzare la finestra principale.



public class Setting extends JFrame
{
	private JPanel contentPane;
	private JTextField textAN;
	private JTextField textSIP;

	private ApplicationManagerListener listener;
	private GUIApplicationManager am;

	private final JTree policyTree;
	private DefaultTreeModel policyTreeModel;
	private DefaultMutableTreeNode policyRootNode;
	private Object policySelectedItem;
	private DefaultMutableTreeNode policySelectedNode;
	private Hashtable<String, DefaultMutableTreeNode> policyNodeList;
	private JButton btnRemovePolicy;
	private DefaultMutableTreeNode defaultPolicyNode;
	
	private final JTree appTree;
	private DefaultTreeModel appTreeModel;
	private DefaultMutableTreeNode appRootNode;
	private Object appSelectedItem;
	private DefaultMutableTreeNode appSelectedNode;
	private Hashtable<String, DefaultMutableTreeNode> appNodeList;
	private JButton btnRemoveApp;
	
	private final JTree interfTree;
	private DefaultTreeModel interfTreeModel;
	private DefaultMutableTreeNode interfRootNode;
	private Object interfSelectedItem;
	private DefaultMutableTreeNode interfSelectedNode;
	private Hashtable<String, DefaultMutableTreeNode> interfNodeList;
	private JButton btnRemoveInterf;

	private String ANAddress;
	private String sipID;

	private Hashtable<String, String> policy;
	private Hashtable<String, String> currPolicy;
	private String defaultPolicy;
	private String currDefaultPolicy;

	private Vector<String> noApp;
	private Vector<String> currNoApp;
	private Vector<String> noInterf;
	private Vector<String> currNoInterf;


	/**Create the frame.*/
	
//	new Setting(thiz.listener, thiz, thiz.listener.getCfgDefaultAN(), thiz.listener.getCfgSipID(), thiz.listener.getAllStoredPolicy(),
//			thiz.listener.getDefaultPolicy(), thiz.listener.getNoUpmtApp(), thiz.listener.getNoUpmtInterf());
	
	
	public Setting(ApplicationManagerListener amListener, GUIApplicationManager appMan)
	{
		this.listener = amListener;
		this.am = appMan;
		this.ANAddress = amListener.getCfgDefaultAN();
		this.sipID = amListener.getCfgSipID();
		this.policy = amListener.getAllStoredPolicy();
		this.defaultPolicy = amListener.getDefaultPolicy();
		this.noApp = amListener.getNoUpmtApp();
		this.noInterf = amListener.getNoUpmtInterf();

		DefaultMutableTreeNode node;
		
		setTitle("UPMT settings");

		policySelectedItem = null;
		appSelectedItem = null;
		interfSelectedItem = null;

		policyNodeList = new Hashtable<String, DefaultMutableTreeNode>();
		appNodeList = new Hashtable<String, DefaultMutableTreeNode>();
		interfNodeList = new Hashtable<String, DefaultMutableTreeNode>();

		currPolicy = new Hashtable<String, String>();
		currNoApp = new Vector<String>();
		currNoInterf = new Vector<String>();

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				am.onSettingOk();
				dispose();
			}
		});
		setAlwaysOnTop(true);
		setBounds(150, 150, 550, 400);

		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWidths = new int[]{440, 440, 0};
		gbl_contentPane.rowHeights = new int[]{0, 0, 0};
		gbl_contentPane.columnWeights = new double[]{1.0, 1.0, Double.MIN_VALUE};
		gbl_contentPane.rowWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
		contentPane.setLayout(gbl_contentPane);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		GridBagConstraints gbc_tabbedPane = new GridBagConstraints();
		gbc_tabbedPane.gridwidth = 2;
		gbc_tabbedPane.insets = new Insets(0, 0, 5, 0);
		gbc_tabbedPane.fill = GridBagConstraints.BOTH;
		gbc_tabbedPane.gridx = 0;
		gbc_tabbedPane.gridy = 0;
		contentPane.add(tabbedPane, gbc_tabbedPane);



		//*************************************
		//General TAB *************************
		//*************************************
		JPanel generalPanel = new JPanel();
		generalPanel.setBorder(BorderFactory.createEmptyBorder());
		generalPanel.setOpaque(false);
		tabbedPane.addTab("General", null, generalPanel, null);
		GridBagLayout gbl_generalPanel = new GridBagLayout();
		gbl_generalPanel.columnWidths = new int[]{0, 0, 0};
		gbl_generalPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
		gbl_generalPanel.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gbl_generalPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		generalPanel.setLayout(gbl_generalPanel);
		
		JLabel labelInstruction = new JLabel("General setting:");
		GridBagConstraints gbc_labelInstruction = new GridBagConstraints();
		gbc_labelInstruction.gridwidth = 2;
		gbc_labelInstruction.insets = new Insets(0, 0, 5, 0);
		gbc_labelInstruction.gridx = 0;
		gbc_labelInstruction.gridy = 0;
		generalPanel.add(labelInstruction, gbc_labelInstruction);
		
		JLabel labelAN = new JLabel("Anchor Node IP address");
		GridBagConstraints gbc_labelAN = new GridBagConstraints();
		gbc_labelAN.insets = new Insets(0, 0, 5, 5);
		gbc_labelAN.anchor = GridBagConstraints.EAST;
		gbc_labelAN.gridx = 0;
		gbc_labelAN.gridy = 1;
		generalPanel.add(labelAN, gbc_labelAN);
		
		textAN = new JTextField();
		GridBagConstraints gbc_textAN = new GridBagConstraints();
		gbc_textAN.insets = new Insets(0, 0, 5, 0);
		gbc_textAN.fill = GridBagConstraints.HORIZONTAL;
		gbc_textAN.gridx = 1;
		gbc_textAN.gridy = 1;
		generalPanel.add(textAN, gbc_textAN);
		textAN.setColumns(10);
		textAN.setText(ANAddress);
		
		JLabel labelSip = new JLabel("Your Sip Address");
		GridBagConstraints gbc_labelSip = new GridBagConstraints();
		gbc_labelSip.anchor = GridBagConstraints.EAST;
		gbc_labelSip.insets = new Insets(0, 0, 5, 5);
		gbc_labelSip.gridx = 0;
		gbc_labelSip.gridy = 2;
		generalPanel.add(labelSip, gbc_labelSip);
		
		textSIP = new JTextField();
		GridBagConstraints gbc_textSIP = new GridBagConstraints();
		gbc_textSIP.insets = new Insets(0, 0, 5, 0);
		gbc_textSIP.fill = GridBagConstraints.HORIZONTAL;
		gbc_textSIP.gridx = 1;
		gbc_textSIP.gridy = 2;
		generalPanel.add(textSIP, gbc_textSIP);
		textSIP.setColumns(10);
		textSIP.setText(sipID);



		//*************************************
		//Application TAB *********************
		//*************************************
		JPanel policyPanel = new JPanel();
		policyPanel.setBorder(BorderFactory.createEmptyBorder());
		policyPanel.setOpaque(false);
		tabbedPane.addTab("Application Policy", null, policyPanel, null);
		GridBagLayout gbl_policyPanel = new GridBagLayout();
		gbl_policyPanel.columnWidths = new int[]{0, 0, 0, 0};
		gbl_policyPanel.rowHeights = new int[]{0, 0, 0, 0};
		gbl_policyPanel.columnWeights = new double[]{1.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_policyPanel.rowWeights = new double[]{0.0, 1.0, 0.0, Double.MIN_VALUE};
		policyPanel.setLayout(gbl_policyPanel);

		JLabel lblPolicy = new JLabel("Policy");
		GridBagConstraints gbc_lblPolicy = new GridBagConstraints();
		gbc_lblPolicy.gridwidth = 3;
		gbc_lblPolicy.insets = new Insets(0, 0, 5, 0);
		gbc_lblPolicy.gridx = 0;
		gbc_lblPolicy.gridy = 0;
		policyPanel.add(lblPolicy, gbc_lblPolicy);

		JScrollPane policyPane = new JScrollPane();
		GridBagConstraints gbc_policyPane = new GridBagConstraints();
		gbc_policyPane.gridwidth = 3;
		gbc_policyPane.insets = new Insets(0, 0, 5, 0);
		gbc_policyPane.fill = GridBagConstraints.BOTH;
		gbc_policyPane.gridx = 0;
		gbc_policyPane.gridy = 1;
		policyPanel.add(policyPane, gbc_policyPane);

		
		policyRootNode = new DefaultMutableTreeNode();
		policyTreeModel = new DefaultTreeModel(policyRootNode);
		policyTree = new JTree(policyTreeModel);
		policyPane.setViewportView(policyTree);
		policyTree.setRootVisible(false);
		policyTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		policyTree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				Object treeNode = policyTree.getLastSelectedPathComponent();
				if (treeNode == null) return;
				policySelectedNode = ((DefaultMutableTreeNode) treeNode);
				if (policySelectedNode.isRoot()) return;
				policySelectedItem = policySelectedNode.getUserObject();
				btnRemovePolicy.setEnabled(!policySelectedNode.equals(defaultPolicyNode));
			}
		});

		String pol = defaultPolicy;
		node = new DefaultMutableTreeNode("**DEFAULT**" + "   ->   " + pol);		
		defaultPolicyNode = node;
		policyTreeModel.insertNodeInto(node, policyRootNode, policyRootNode.getChildCount());
		policyTree.scrollPathToVisible(new TreePath(node.getPath()));
		currDefaultPolicy = pol;

		for (String appName : policy.keySet())
		{
			pol = policy.get(appName);
			node = new DefaultMutableTreeNode(appName + "   ->   " + pol);		
			policyTreeModel.insertNodeInto(node, policyRootNode, policyRootNode.getChildCount());
			policyTree.scrollPathToVisible(new TreePath(node.getPath()));
			policyNodeList.put(appName, node);
			currPolicy.put(appName, pol);	
		}

		JButton btnAddPolicy = new JButton("Add");
		GridBagConstraints gbc_btnAddPolicy = new GridBagConstraints();
		gbc_btnAddPolicy.anchor = GridBagConstraints.EAST;
		gbc_btnAddPolicy.insets = new Insets(0, 0, 0, 5);
		gbc_btnAddPolicy.gridx = 0;
		gbc_btnAddPolicy.gridy = 2;
		policyPanel.add(btnAddPolicy, gbc_btnAddPolicy);
		btnAddPolicy.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e)
		{
			setAlwaysOnTop(false);
			setEnabled(false);
			setFocusableWindowState(false);

			new PopUpWindows(new String[]{"Application Name", "Policy"}, "Insert a new Policy:", new PopUpListener() {public void done(Vector<String> ret)
			{
				setAlwaysOnTop(true);
				setEnabled(true);
				setFocusableWindowState(true);
				if (ret==null) return; //TODO: controlli su ret (non campi nulli, ecc...)
				
				DefaultMutableTreeNode app = new DefaultMutableTreeNode(ret.get(0) + "   ->   " + ret.get(1));		
				policyTreeModel.insertNodeInto(app, policyRootNode, policyRootNode.getChildCount());
				policyTree.scrollPathToVisible(new TreePath(app.getPath()));
				policyNodeList.put(ret.get(0), app);
				currPolicy.put(ret.get(0), ret.get(1));	
			}}).setVisible(true);
		}});
		
		JButton btnEditPolicy = new JButton("Edit");
		GridBagConstraints gbc_btnEditPolicy = new GridBagConstraints();
		gbc_btnEditPolicy.insets = new Insets(0, 0, 0, 5);
		gbc_btnEditPolicy.gridx = 1;
		gbc_btnEditPolicy.gridy = 2;
		policyPanel.add(btnEditPolicy, gbc_btnEditPolicy);
		btnEditPolicy.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e)
		{
			if (policySelectedItem==null) return;
			String appName = ((String)policySelectedItem).split(" ",2)[0];
			setAlwaysOnTop(false);
			setEnabled(false);
			setFocusableWindowState(false);

			new PopUpWindows(new String[]{"Policy"}, new String[]{currPolicy.get(appName)}, "New Policy for \""+appName+"\":", new PopUpListener() {public void done(Vector<String> ret)
			{
				setAlwaysOnTop(true);
				setEnabled(true);
				setFocusableWindowState(true);
				if (ret==null) return; //TODO: controlli su ret (non campi nulli, ecc...)

				if(policySelectedNode.equals(defaultPolicyNode)){defaultPolicyNode.setUserObject("**DEFAULT**" + "   ->   " + ret.get(0)); currDefaultPolicy = ret.get(0); return;}

				String appName = ((String)policySelectedItem).split(" ",2)[0];
				policyNodeList.get(appName).setUserObject(appName + "   ->   " + ret.get(0));
				policyTree.repaint(); policyTree.scrollPathToVisible(new TreePath(policyNodeList.get(appName).getPath())); //TODO: Fare bene refresh
				currPolicy.put(appName, ret.get(0));	
			}}).setVisible(true);
		}});
		
		btnRemovePolicy = new JButton("Remove");
		GridBagConstraints gbc_btnRemovePolicy = new GridBagConstraints();
		gbc_btnRemovePolicy.gridx = 2;
		gbc_btnRemovePolicy.gridy = 2;
		policyPanel.add(btnRemovePolicy, gbc_btnRemovePolicy);
		btnRemovePolicy.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (policySelectedItem==null) return;
				String appName = ((String)policySelectedItem).split(" ",2)[0];
				policyTreeModel.removeNodeFromParent(policyNodeList.remove(appName));
				currPolicy.remove(appName);
			}
		});

		policyTree.setSelectionPath(new TreePath(defaultPolicyNode.getPath()));


		//*************************************
		//Exceptions TAB **********************
		//*************************************		
		JSplitPane exceptionsPane = new JSplitPane();
		exceptionsPane.setContinuousLayout(true);
		exceptionsPane.setEnabled(false);
		exceptionsPane.setResizeWeight(0.5);
		exceptionsPane.setBorder(BorderFactory.createEmptyBorder());
		exceptionsPane.setOpaque(false);
		tabbedPane.addTab("Exceptions", null, exceptionsPane, null);
		
		JPanel exceptAppPanel = new JPanel();
		exceptAppPanel.setBorder(BorderFactory.createEmptyBorder());
		exceptAppPanel.setOpaque(false);
		exceptionsPane.setLeftComponent(exceptAppPanel);
		GridBagLayout gbl_exceptAppPanel = new GridBagLayout();
		gbl_exceptAppPanel.columnWidths = new int[]{0, 0, 0};
		gbl_exceptAppPanel.rowHeights = new int[]{0, 0, 0, 0};
		gbl_exceptAppPanel.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
		gbl_exceptAppPanel.rowWeights = new double[]{0.0, 1.0, 0.0, Double.MIN_VALUE};
		exceptAppPanel.setLayout(gbl_exceptAppPanel);
		
		JLabel lblApplications = new JLabel("Applications");
		GridBagConstraints gbc_lblApplications = new GridBagConstraints();
		gbc_lblApplications.gridwidth = 2;
		gbc_lblApplications.insets = new Insets(0, 0, 5, 5);
		gbc_lblApplications.gridx = 0;
		gbc_lblApplications.gridy = 0;
		exceptAppPanel.add(lblApplications, gbc_lblApplications);

		JScrollPane appPane = new JScrollPane();
		GridBagConstraints gbc_appPane = new GridBagConstraints();
		gbc_appPane.gridwidth = 2;
		gbc_appPane.insets = new Insets(0, 0, 5, 0);
		gbc_appPane.fill = GridBagConstraints.BOTH;
		gbc_appPane.gridx = 0;
		gbc_appPane.gridy = 1;
		exceptAppPanel.add(appPane, gbc_appPane);

		
		
		
		
		
		appRootNode = new DefaultMutableTreeNode();
		appTreeModel = new DefaultTreeModel(appRootNode);
		appTree = new JTree(appTreeModel);
		appPane.setViewportView(appTree);
		appTree.setRootVisible(false);
		appTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		appTree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				Object treeNode = appTree.getLastSelectedPathComponent();
				if (treeNode == null) return;
				appSelectedNode = ((DefaultMutableTreeNode) treeNode);
				if (appSelectedNode.isRoot()) return;
				appSelectedItem = appSelectedNode.getUserObject();
			}
		});

		for (String appName : noApp)
		{
			node = new DefaultMutableTreeNode(appName);		
			appTreeModel.insertNodeInto(node, appRootNode, appRootNode.getChildCount());
			appTree.scrollPathToVisible(new TreePath(node.getPath()));
			appNodeList.put(appName, node);
			currNoApp.add(appName);	
		}
		
		appPane.setViewportView(appTree);
		
		JButton btnAddApp = new JButton("Add");
		GridBagConstraints gbc_btnAddApp = new GridBagConstraints();
		gbc_btnAddApp.anchor = GridBagConstraints.EAST;
		gbc_btnAddApp.insets = new Insets(0, 0, 0, 5);
		gbc_btnAddApp.gridx = 0;
		gbc_btnAddApp.gridy = 2;
		exceptAppPanel.add(btnAddApp, gbc_btnAddApp);
		btnAddApp.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e)
		{
			setAlwaysOnTop(false);
			setEnabled(false);
			setFocusableWindowState(false);

			new PopUpWindows(new String[]{"Application Name"}, "Insert a new Application:", new PopUpListener() {public void done(Vector<String> ret)
			{
				setAlwaysOnTop(true);
				setEnabled(true);
				setFocusableWindowState(true);
				if (ret==null) return; //TODO: controlli su ret (non campi nulli, ecc...)
				
				DefaultMutableTreeNode app = new DefaultMutableTreeNode(ret.get(0));		
				appTreeModel.insertNodeInto(app, appRootNode, appRootNode.getChildCount());
				appTree.scrollPathToVisible(new TreePath(app.getPath()));
				appNodeList.put(ret.get(0), app);
				currNoApp.add(ret.get(0));	
			}}).setVisible(true);
		}});
		
		
		btnRemoveApp = new JButton("Remove");
		GridBagConstraints gbc_btnRemoveApp = new GridBagConstraints();
		gbc_btnRemoveApp.anchor = GridBagConstraints.WEST;
		gbc_btnRemoveApp.gridx = 1;
		gbc_btnRemoveApp.gridy = 2;
		exceptAppPanel.add(btnRemoveApp, gbc_btnRemoveApp);
		btnRemoveApp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (appSelectedItem==null) return;
				String appName = (String)appSelectedItem;
				appTreeModel.removeNodeFromParent(appNodeList.remove(appName));
				currNoApp.remove(appName);
			}
		});





		JPanel exceptInterfPanel = new JPanel();
		exceptInterfPanel.setBorder(BorderFactory.createEmptyBorder());
		exceptInterfPanel.setOpaque(false);
		exceptionsPane.setRightComponent(exceptInterfPanel);
		GridBagLayout gbl_exceptInterfPanel = new GridBagLayout();
		gbl_exceptInterfPanel.columnWidths = new int[]{0, 0, 0};
		gbl_exceptInterfPanel.rowHeights = new int[]{0, 0, 0, 0};
		gbl_exceptInterfPanel.columnWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
		gbl_exceptInterfPanel.rowWeights = new double[]{0.0, 1.0, 0.0, Double.MIN_VALUE};
		exceptInterfPanel.setLayout(gbl_exceptInterfPanel);
		
		JLabel lblInterfaces = new JLabel("Interfaces");
		GridBagConstraints gbc_lblInterfaces = new GridBagConstraints();
		gbc_lblInterfaces.gridwidth = 2;
		gbc_lblInterfaces.insets = new Insets(0, 0, 5, 5);
		gbc_lblInterfaces.gridx = 0;
		gbc_lblInterfaces.gridy = 0;
		exceptInterfPanel.add(lblInterfaces, gbc_lblInterfaces);
		
		JScrollPane interfPane = new JScrollPane();
		GridBagConstraints gbc_interfPane = new GridBagConstraints();
		gbc_interfPane.gridwidth = 2;
		gbc_interfPane.insets = new Insets(0, 0, 5, 0);
		gbc_interfPane.fill = GridBagConstraints.BOTH;
		gbc_interfPane.gridx = 0;
		gbc_interfPane.gridy = 1;
		exceptInterfPanel.add(interfPane, gbc_interfPane);

		interfRootNode = new DefaultMutableTreeNode();
		interfTreeModel = new DefaultTreeModel(interfRootNode);
		interfTree = new JTree(interfTreeModel);
		interfPane.setViewportView(interfTree);
		interfTree.setRootVisible(false);
		interfTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		interfTree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				Object treeNode = interfTree.getLastSelectedPathComponent();
				if (treeNode == null) return;
				interfSelectedNode = ((DefaultMutableTreeNode) treeNode);
				if (interfSelectedNode.isRoot()) return;
				interfSelectedItem = interfSelectedNode.getUserObject();
			}
		});

		for (String ifName : noInterf)
		{
			node = new DefaultMutableTreeNode(ifName);		
			interfTreeModel.insertNodeInto(node, interfRootNode, interfRootNode.getChildCount());
			interfTree.scrollPathToVisible(new TreePath(node.getPath()));
			interfNodeList.put(ifName, node);
			currNoInterf.add(ifName);	
		}

		interfPane.setViewportView(interfTree);
		
		JButton btnAddInterf = new JButton("Add");
		GridBagConstraints gbc_btnAddInterf = new GridBagConstraints();
		gbc_btnAddInterf.anchor = GridBagConstraints.EAST;
		gbc_btnAddInterf.insets = new Insets(0, 0, 0, 5);
		gbc_btnAddInterf.gridx = 0;
		gbc_btnAddInterf.gridy = 2;
		exceptInterfPanel.add(btnAddInterf, gbc_btnAddInterf);
		btnAddInterf.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e)
		{
			setAlwaysOnTop(false);
			setEnabled(false);
			setFocusableWindowState(false);

			new PopUpWindows(new String[]{"Interface Name"}, "Insert a new Interface:", new PopUpListener() {public void done(Vector<String> ret)
			{
				setAlwaysOnTop(true);
				setEnabled(true);
				setFocusableWindowState(true);
				if (ret==null) return; //TODO: controlli su ret (non campi nulli, ecc...)
				
				DefaultMutableTreeNode interf = new DefaultMutableTreeNode(ret.get(0));		
				interfTreeModel.insertNodeInto(interf, interfRootNode, interfRootNode.getChildCount());
				interfTree.scrollPathToVisible(new TreePath(interf.getPath()));
				interfNodeList.put(ret.get(0), interf);
				currNoInterf.add(ret.get(0));	
			}}).setVisible(true);
		}});

		btnRemoveInterf = new JButton("Remove");
		GridBagConstraints gbc_btnRemoveInterf = new GridBagConstraints();
		gbc_btnRemoveInterf.anchor = GridBagConstraints.WEST;
		gbc_btnRemoveInterf.gridx = 1;
		gbc_btnRemoveInterf.gridy = 2;
		exceptInterfPanel.add(btnRemoveInterf, gbc_btnRemoveInterf);
		btnRemoveInterf.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (interfSelectedItem==null) return;
				String ifName = (String)interfSelectedItem;
				interfTreeModel.removeNodeFromParent(interfNodeList.remove(ifName));
				currNoInterf.remove(ifName);
			}
		});
		
		


		JButton buttonOk = new JButton("OK");
		GridBagConstraints gbc_buttonOk = new GridBagConstraints();
		gbc_buttonOk.anchor = GridBagConstraints.EAST;
		gbc_buttonOk.insets = new Insets(0, 0, 0, 5);
		gbc_buttonOk.gridx = 0;
		gbc_buttonOk.gridy = 1;
		contentPane.add(buttonOk, gbc_buttonOk);
		buttonOk.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e)
		{
			Setting thiz = Setting.this;
			
			if (!textAN.getText().equals(thiz.ANAddress)) listener.setDefaultAN(textAN.getText());
			if (!textSIP.getText().equals(thiz.sipID)) listener.setCfgSipID(textSIP.getText());

			Vector<String> temp = new Vector<String>();
			for (String appName : policy.keySet())
				if (!currPolicy.containsKey(appName)) temp.add(appName);
			for (String string : temp) listener.cfgPolicyRemoval(string);
					
			for (String appName : currPolicy.keySet())
				if (!policy.containsKey(appName))
					listener.cfgPolicyAdding(appName, currPolicy.get(appName));
				else if (!currPolicy.get(appName).equals(policy.get(appName)))
					listener.cfgPolicyEdit(appName, currPolicy.get(appName));

			if (!defaultPolicy.equals(currDefaultPolicy)) listener.defPolicyEdit(currDefaultPolicy);

			temp = new Vector<String>();
			for (String appName : noApp)
				if (!currNoApp.contains(appName)) temp.add(appName);
			for (String string : temp) listener.cfgNoAppRemoval(string);
			for (String appName : currNoApp)
				if (!noApp.contains(appName))
					listener.cfgNoAppAdding(appName);

			temp = new Vector<String>();
			for (String ifName : noInterf)
				if (!currNoInterf.contains(ifName)) temp.add(ifName);
			for (String string : temp) listener.cfgNoInterfRemoval(string);
			for (String ifName : currNoInterf)
				if (!noInterf.contains(ifName))
					listener.cfgNoInterfAdding(ifName);
			
			am.onSettingOk();
			dispose();
		}});
		
		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				am.onSettingOk();
				dispose();
			}
		});
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.anchor = GridBagConstraints.WEST;
		gbc_btnCancel.gridx = 1;
		gbc_btnCancel.gridy = 1;
		contentPane.add(btnCancel, gbc_btnCancel);
		
		
	}
}
