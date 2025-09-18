package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import java.awt.SystemColor;
import java.net.URL;

import javax.swing.JLabel;
import javax.swing.ImageIcon;

import com.bsmart.scale.CalibrationStatus;
import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;
import javax.swing.JPanel;

public class Page6 extends PropertyPage {

	private final String stCalibSuccess = "<html>Градуировка успешно завершена.<br>Уберите груз с платформы.";
	private final String stCalibFailed = "<html>Градуировка завершена с ошибкой.<br>Реперные точки не изменены.";
	private final String stErrorReasons = "<html>Возможные причины ошибки:<br><br>1. Установленный груз не соответствовал требуемому.<br>2. Неисправен АЦП.";
	
	private final JLabel lblInfo1;
	private final JLabel lblInfo2;
	private JLabel lblOKImage = null;
	private JLabel lblFailImage = null;
	
	private final SmScale driver = SmScale.instance;
	
	/**
	 * Create the panel.
	 */
	public Page6() {
		setLayout(null);
		
		lblInfo1 = new JLabel();
		lblInfo1.setBounds(92, 62, 398, 72);
		lblInfo1.setText("<html>Градуировка успешно завершена.<br>\r\nУберите груз с платформы.");
		lblInfo1.setForeground(new Color(0, 0, 128));
		lblInfo1.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblInfo1.setBackground(SystemColor.activeCaptionBorder);
		add(lblInfo1);
		
		lblInfo2 = new JLabel();
		lblInfo2.setText("<html>Возможные причины ошибки:<br><br>\r\n1. Установленный груз не соответствовал требуемому.<br>\r\n2. Неисправен АЦП.");
		lblInfo2.setForeground(new Color(0, 0, 0));
		lblInfo2.setFont(new Font("Tahoma", Font.PLAIN, 13));
		lblInfo2.setBackground(SystemColor.activeCaptionBorder);
		lblInfo2.setBounds(92, 130, 398, 86);
		add(lblInfo2);
		
		URL imageURL = null;
		ImageIcon image = null;
		imageURL = getClass().getResource("/res/ok.png");
		if (imageURL != null) 
		{
			image = new ImageIcon(imageURL);
		}
		lblOKImage = new JLabel(image);
		lblOKImage.setText("lblOKImage");
		lblOKImage.setBounds(20, 75, 50, 50);
		lblOKImage.setVisible(false);
		lblOKImage.setText("");
		add(lblOKImage);
		
		image = null;
		imageURL = getClass().getResource("/res/fail.png");
		if (imageURL != null) 
		{
			image = new ImageIcon(imageURL);
		}
		lblFailImage = new JLabel(image);
		lblFailImage.setText("lblFailImage");
		lblFailImage.setBounds(20, 75, 50, 50);
		lblFailImage.setVisible(false);
		lblFailImage.setText("");
		add(lblFailImage);
		
	}
	
	public void updatePage()
	{
		CalibrationStatus status = driver.getCalibrationStatus();
		if (status.getStatus() == CalibrationStatus.POINT_STATUS_SUCCEDED)
		{
			lblOKImage.setVisible(true);
			lblFailImage.setVisible(false);
			lblInfo1.setText(stCalibSuccess);
			lblInfo2.setVisible(false);
		} else{
			lblOKImage.setVisible(false);
			lblFailImage.setVisible(true);
			lblInfo1.setText(stCalibFailed);
			lblInfo2.setVisible(true);
		}
	}
}
