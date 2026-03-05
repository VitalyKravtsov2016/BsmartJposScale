package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.border.BevelBorder;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import org.eclipse.wb.swing.FocusTraversalOnArray;
import java.awt.Component;
import javax.swing.JTextField;
import javax.swing.JSeparator;


public class PasswordDialog extends JDialog {

	private boolean okPressed = false;
	private String password = "";
	private JTextField edtPassword;
	
	/**
	 * Create the dialog.
	 */
	public PasswordDialog() 
	{
		setTitle("Вход в режим градуировки");
		setResizable(false);
		setModal(true);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		
		setBounds(100, 100, 450, 228);
		getContentPane().setLayout(null);
		
		JLabel lblInfo = new JLabel();
		lblInfo.setBounds(10, 11, 429, 81);
		lblInfo.setText("<html>\r\nДля входа в режим градуировки  необходимо  перевести <br>\r\nградуировочный переключатель весов в положение \"ГРАДУИРОВКА\".<br>\r\nНажмите \"ОК\"  для продолжения или \"Отмена\" <br>\r\nдля прекращения градуировки.");
		lblInfo.setForeground(Color.BLACK);
		lblInfo.setFont(new Font("Tahoma", Font.PLAIN, 13));
		lblInfo.setBackground(SystemColor.activeCaptionBorder);
		getContentPane().add(lblInfo);
		
		JButton btnOK = new JButton("OK");
		btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				password = edtPassword.getText();
				okPressed = true;
				setVisible(false);
			}
		});
		btnOK.setBounds(261, 167, 75, 23);
		btnOK.setActionCommand("OK");
		getContentPane().add(btnOK);
		
		JButton btnCancel = new JButton("Отмена");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				okPressed = false;
				setVisible(false);
			}
		});
		btnCancel.setBounds(346, 167, 86, 23);
		btnCancel.setActionCommand("Cancel");
		getContentPane().add(btnCancel);
		
		JLabel label = new JLabel("Пароль:");
		label.setBounds(10, 112, 120, 14);
		getContentPane().add(label);
		Border border = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
		
		getRootPane().setDefaultButton(btnOK);
		
		edtPassword = new JTextField();
		edtPassword.setBounds(140, 109, 178, 20);
		getContentPane().add(edtPassword);
		edtPassword.setColumns(10);
		
		JSeparator separator = new JSeparator();
		separator.setBounds(10, 152, 424, 14);
		getContentPane().add(separator);
		
		getContentPane().setFocusTraversalPolicy(new FocusTraversalOnArray(new Component[]{edtPassword, btnOK, btnCancel}));
		getContentPane().setFocusCycleRoot(true); 
	}
	
	public boolean getOKPressed(){
		return okPressed;
	}
	
	public String getPassword(){
		return password;
	}
	
}
