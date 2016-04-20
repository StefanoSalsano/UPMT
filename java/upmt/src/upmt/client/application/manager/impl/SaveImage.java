package upmt.client.application.manager.impl;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;

public class SaveImage extends JFrame{
	/**
	 * 
	 */
	private static final long serialVersionUID = -2681100857566868368L;
	private JPanel BottomPanel;
	private JScrollPane scrollPane;
	private BufferedImage screenCaptured;

	private int countImage;
	private int typeImage;
	private JButton saveButton;
	private JButton undoButton;
	
	public SaveImage(JLabel label, BufferedImage bufferedImage, int count,int typeGraphicImage){
		screenCaptured = bufferedImage;
		countImage = count;
		typeImage = typeGraphicImage;
		
		this.setBounds(50, 50, 800, 600);
		scrollPane = new JScrollPane(label);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		scrollPane.setVisible(true);
		
		BottomPanel = new JPanel();
		getContentPane().add(BottomPanel,BorderLayout.SOUTH);
		
		saveButton = new JButton("Save");
		saveButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent avt){
				try {
				String nameFile="";
					if(typeImage == 1){
						nameFile = "image_TrafficGrapher-" + countImage + ".jpg";
					
					}
					else if(typeImage == 2)
						nameFile = "image_EWMATrafficGrapher-" + countImage + ".jpg";
					File image = new File(nameFile);
				
					ImageIO.write(screenCaptured, "jpg", image);
					dispose();
					} catch (IOException e) {
					// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		});
		BottomPanel.add(saveButton);
		
		undoButton = new JButton("Undo");
		undoButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e){
				dispose();
			}
		});
		BottomPanel.add(undoButton);
		
		
		
	}
}
