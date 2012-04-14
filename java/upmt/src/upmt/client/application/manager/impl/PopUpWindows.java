package upmt.client.application.manager.impl;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class PopUpWindows extends JFrame
{
	private JPanel contentPane;
	private Vector<JTextField> textFields;
	private PopUpListener listener;

	/**Create the frame.*/
	public PopUpWindows(String[] fields, String descr, PopUpListener listener)
	{this(fields, null, descr, listener);}

	public PopUpWindows(String[] fields, String[] initValues, String descr, PopUpListener listener)
	{
		this.listener = listener;
		this.textFields = new Vector<JTextField>();
		if (initValues!=null && initValues.length != fields.length) {initValues = null;}
		
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				dispose();
				PopUpWindows.this.listener.done(null);
			}
		});
		setSize(600, 200);
		
		
		setAlwaysOnTop(true);
		
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWidths = new int[]{0, 0, 0, 0};
		gbl_contentPane.rowHeights = new int[]{0, 0, 0, 0, 0};
		gbl_contentPane.columnWeights = new double[]{0.0, 1.0, 1.0, Double.MIN_VALUE};
		gbl_contentPane.rowWeights = new double[]{0.0, 1.0, 0.0, 0.0, Double.MIN_VALUE};
		contentPane.setLayout(gbl_contentPane);

		JLabel description = new JLabel(descr);
		GridBagConstraints gbc_label = new GridBagConstraints();
		gbc_label.gridwidth = 3;
		gbc_label.insets = new Insets(0, 0, 5, 5);
		gbc_label.gridx = 0;
		gbc_label.gridy = 0;
		contentPane.add(description, gbc_label);

		int rowIndex = 2;
		for (String field : fields)
		{
			JLabel fieldLabel = new JLabel(field);
			GridBagConstraints gbc_fieldLabel = new GridBagConstraints();
			gbc_fieldLabel.insets = new Insets(0, 0, 5, 5);
			gbc_fieldLabel.anchor = GridBagConstraints.EAST;
			gbc_fieldLabel.gridx = 0;
			gbc_fieldLabel.gridy = rowIndex;
			contentPane.add(fieldLabel, gbc_fieldLabel);
			
			JTextField textField = new JTextField();
			textFields.add(textField);
			GridBagConstraints gbc_textField = new GridBagConstraints();
			gbc_textField.insets = new Insets(0, 0, 5, 0);
			gbc_textField.fill = GridBagConstraints.HORIZONTAL;
			gbc_textField.gridx = 1;
			gbc_textField.gridy = rowIndex;
			gbc_textField.gridwidth = 2;
			contentPane.add(textField, gbc_textField);
			textField.setColumns(20);
			if (initValues != null) textField.setText(initValues[rowIndex-2]);
			rowIndex++;
		}

		JButton btnOk = new JButton("OK");
		GridBagConstraints gbc_btnOk = new GridBagConstraints();
		gbc_btnOk.insets = new Insets(0, 0, 0, 5);
		gbc_btnOk.gridwidth = 2;
		gbc_btnOk.gridx = 0;
		gbc_btnOk.gridy = rowIndex;
		btnOk.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Vector<String> ret = new Vector<String>();
				for (JTextField textField : textFields) ret.add(textField.getText());
				PopUpWindows.this.listener.done(ret);
				//ret.add("fsa:3");
				//ret.add("fgfdds");
				dispose();
			}
		});
		contentPane.add(btnOk, gbc_btnOk);
		
		JButton btnCancel = new JButton("Cancel");
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.gridx = 2;
		gbc_btnCancel.gridy = rowIndex;
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
				PopUpWindows.this.listener.done(null);
			}
		});
		contentPane.add(btnCancel, gbc_btnCancel);
	}
}
