package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.SystemColor;

import com.bsmart.scale.CalibrationStatus;
import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;

public class Page4 extends PropertyPage {

	private final JLabel lblPointNumber;
	private final JLabel lblCalibGuidance;
	private final SmScale driver = SmScale.instance;
	
	/**
	 * Create the panel.
	 */
	public Page4() {
		setLayout(null);
		
		JLabel lblInfo1 = new JLabel();
		lblInfo1.setBounds(10, 11, 480, 47);
		lblInfo1.setText("<html>Процесс градуировки начался.<br>\r\nСледуйте указаниям для продолжения.");
		lblInfo1.setForeground(new Color(0, 0, 128));
		lblInfo1.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblInfo1.setBackground(SystemColor.activeCaptionBorder);
		add(lblInfo1);
		
		lblPointNumber = new JLabel();
		lblPointNumber.setText("Градуируется реперная точка (вес 0 кг).");
		lblPointNumber.setForeground(SystemColor.desktop);
		lblPointNumber.setFont(new Font("Tahoma", Font.BOLD, 13));
		lblPointNumber.setBackground(SystemColor.activeCaptionBorder);
		lblPointNumber.setBounds(10, 127, 449, 29);
		add(lblPointNumber);
		
		JLabel lblInfo2 = new JLabel();
		lblInfo2.setText("<html>Убедитесь, что весовой модуль находится на <br> \r\nгоризонтальной и устойчивой поверхности.");
		lblInfo2.setForeground(new Color(0, 0, 0));
		lblInfo2.setFont(new Font("Tahoma", Font.PLAIN, 13));
		lblInfo2.setBackground(SystemColor.activeCaptionBorder);
		lblInfo2.setBounds(10, 69, 419, 47);
		add(lblInfo2);
		
		lblCalibGuidance = new JLabel();
		lblCalibGuidance.setText("Положите на платформу вес 0 кг.");
		lblCalibGuidance.setForeground(Color.BLACK);
		lblCalibGuidance.setFont(new Font("Tahoma", Font.BOLD, 13));
		lblCalibGuidance.setBackground(SystemColor.activeCaptionBorder);
		lblCalibGuidance.setBounds(10, 155, 449, 29);
		add(lblCalibGuidance);
		
		JLabel label = new JLabel("<html>Для продолжения нажмите кнопку \"Далее\",<br>для возврата к предыдущему шагу - \"Назад\".");
		label.setForeground(Color.BLACK);
		label.setFont(new Font("Tahoma", Font.PLAIN, 11));
		label.setBounds(10, 313, 245, 31);
		add(label);
	}
	
	public void updatePage()
	{
		CalibrationStatus status = driver.getCalibrationStatus();
		String text = String.format("Градуируется реперная точка №%d (вес %.3f кг).", 
				driver.getPointNumber(), status.getWeight());
		lblPointNumber.setText(text);
		if (status.getWeight() == 0){
			text = "Убедитесь, что платформа пуста.";
			lblCalibGuidance.setText(text);
		} else
		{
			text = String.format("Положите на платформу вес %.3f кг.", 
					status.getWeight());
			lblCalibGuidance.setText(text);
		}
	}
}
