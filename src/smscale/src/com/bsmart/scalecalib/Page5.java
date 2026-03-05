package com.bsmart.scalecalib;

import java.net.URL;
import java.awt.Font;
import java.awt.Color;
import java.awt.SystemColor;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

import com.bsmart.scale.CalibrationStatus;
import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;

import javax.swing.JProgressBar;

public class Page5 extends PropertyPage {

	private final JTextPane edtStatus;
	private final JLabel lblPointNumber;
	private final JProgressBar progressBar;


	private final SmScale driver = SmScale.instance;

	/**
	 * Create the panel.
	 */
	public Page5() {
		setLayout(null);

		JLabel lblInfo1 = new JLabel();
		lblInfo1.setBounds(10, 11, 349, 47);
		lblInfo1.setText("<html>Процесс градуировки начался.<br>\r\nСледуйте указаниям для продолжения.\r\n");
		lblInfo1.setForeground(new Color(0, 0, 128));
		lblInfo1.setFont(new Font("Tahoma", Font.BOLD, 16));
		lblInfo1.setBackground(SystemColor.activeCaptionBorder);
		add(lblInfo1);

		lblPointNumber = new JLabel();
		lblPointNumber.setText("Градуируется реперная точка (вес 0 кг).");
		lblPointNumber.setForeground(new Color(0, 0, 0));
		lblPointNumber.setFont(new Font("Tahoma", Font.BOLD, 13));
		lblPointNumber.setBackground(SystemColor.activeCaptionBorder);
		lblPointNumber.setBounds(10, 127, 449, 29);
		add(lblPointNumber);

		JLabel lblInfo3 = new JLabel();
		lblInfo3.setText("<html>Не трогайте весовой модуль и груз на нем,<br>\r\nпостарайтесь оградить устройство от колебаний и <br>\r\nвибраций (например, не стоит опираться на стол, <br>\r\nна котором стоит весовой модуль)");
		lblInfo3.setForeground(new Color(0, 0, 0));
		lblInfo3.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblInfo3.setBackground(SystemColor.activeCaptionBorder);
		lblInfo3.setBounds(91, 223, 341, 72);
		add(lblInfo3);

		JLabel lblInfo2 = new JLabel();
		lblInfo2
				.setText("<html>Процесс градуировки может занять несколько минут.<br>\r\nДождитесь окончания процесса.");
		lblInfo2.setForeground(new Color(0, 0, 0));
		lblInfo2.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblInfo2.setBackground(SystemColor.activeCaptionBorder);
		lblInfo2.setBounds(10, 69, 419, 47);
		add(lblInfo2);

		edtStatus = new JTextPane();
		edtStatus.setFont(new Font("Tahoma", Font.BOLD, 13));
		edtStatus.setToolTipText("");
		edtStatus.setEditable(false);
		edtStatus.setBounds(10, 156, 449, 29);
		edtStatus.setBackground(SystemColor.activeCaptionBorder);
		add(edtStatus);
		
		JLabel label = new JLabel("<html>Для продолжения нажмите кнопку \"Далее\",<br>для возврата к предыдущему шагу - \"Назад\".");
		label.setForeground(Color.BLACK);
		label.setFont(new Font("Tahoma", Font.PLAIN, 11));
		label.setBounds(10, 313, 245, 31);
		add(label);
		
		JLabel label_1 = new JLabel();
		label_1.setText("Внимание:");
		label_1.setForeground(Color.BLACK);
		label_1.setFont(new Font("Tahoma", Font.BOLD, 13));
		label_1.setBackground(SystemColor.activeCaptionBorder);
		label_1.setBounds(10, 226, 71, 20);
		add(label_1);
		
		progressBar = new JProgressBar();
		progressBar.setValue(0);
		progressBar.setBounds(10, 196, 303, 16);
		add(progressBar);
		
		ImageIcon image = null;
		URL imageURL = getClass().getResource("/res/progress.gif");
		if (imageURL != null) 
		{
			image = new ImageIcon(imageURL);
		}
	}

	public synchronized void updatePage() 
	{
		CalibrationStatus status = driver.getCalibrationStatus();
		
		String text = "Градуируется реперная точка №%d (вес %.3f кг).";
		text = String.format(text, driver.getPointNumber(), status.getWeight());
		lblPointNumber.setText(text);
		edtStatus.setText(status.getStatusText());
		
		progressBar.setValue(driver.getCalibrationProgress());
	}
}
