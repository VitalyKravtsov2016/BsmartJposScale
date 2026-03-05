package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import javax.swing.border.Border;

import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;

import javax.swing.SwingConstants;

public class Page3 extends PropertyPage 
{
	private JLabel lblADC;
	private final SmScale driver = SmScale.instance;
	
	/**
	 * Create the panel.
	 */
	public Page3() {
		setLayout(null);
		
		JLabel lblADCValue = new JLabel("Значение АЦП:\r\n");
		lblADCValue.setForeground(Color.BLACK);
		lblADCValue.setFont(new Font("Tahoma", Font.PLAIN, 13));
		lblADCValue.setBounds(32, 112, 110, 31);
		add(lblADCValue);
		
		JLabel lblInfo2 = new JLabel("Выставьте значение АЦП согласно документации на весовой модуль");
		lblInfo2.setForeground(Color.BLACK);
		lblInfo2.setFont(new Font("Tahoma", Font.PLAIN, 13));
		lblInfo2.setBounds(20, 36, 470, 31);
		add(lblInfo2);
		
		JLabel label = new JLabel("Установка начального значения АЦП");
		label.setForeground(new Color(0, 0, 128));
		label.setFont(new Font("Tahoma", Font.BOLD, 16));
		label.setBounds(10, 11, 470, 31);
		add(label);
		
		lblADC = new JLabel("12345");
		lblADC.setHorizontalAlignment(SwingConstants.CENTER);
		lblADC.setForeground(new Color(0, 0, 128));
		lblADC.setFont(new Font("Tahoma", Font.PLAIN, 50));
		lblADC.setBounds(152, 92, 272, 76);
		add(lblADC);
		Border border = BorderFactory.createLineBorder(Color.BLACK, 1);
		lblADC.setBorder(BorderFactory.createCompoundBorder(border, 
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		
		JLabel label_1 = new JLabel("<html>Для продолжения нажмите кнопку \"Далее\",<br>для возврата к предыдущему шагу - \"Назад\".");
		label_1.setForeground(Color.BLACK);
		label_1.setFont(new Font("Tahoma", Font.PLAIN, 12));
		label_1.setBounds(10, 313, 245, 31);
		add(label_1);
	}

	public void updatePage()
	{
		lblADC.setText(String.valueOf(driver.getADCValue()));
	}
}
