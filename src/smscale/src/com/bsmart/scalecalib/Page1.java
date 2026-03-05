package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import org.eclipse.wb.swing.FocusTraversalOnArray;

import com.bsmart.port.GnuSerialPort;
import com.bsmart.scale.FindDlg;
import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;

import javax.swing.JOptionPane;

public class Page1 extends PropertyPage {

    private JComboBox cbBaudRate;
    private JComboBox cbPortName;
    private JTextField edtPassword;
    private JTextArea txtResult;
    private JButton btnTestConnection;
    private JSpinner spTimeout;
    private final SmScale driver = SmScale.instance;

    /**
     * Create the panel.
     */
    public Page1() {
        setLayout(null);

        cbPortName = new JComboBox();
        cbPortName.setEditable(true);
        cbPortName.setBounds(87, 152, 150, 26);
        cbPortName.setSelectedItem("COM1");
        add(cbPortName);
        updatePortList();

        cbBaudRate = new JComboBox();
        cbBaudRate.setModel(new DefaultComboBoxModel(new String[]{"2400",
            "4800", "9600", "19200", "38400", "57600", "115200"}));
        cbBaudRate.setBounds(87, 185, 150, 26);
        cbBaudRate.setSelectedItem("9600");
        add(cbBaudRate);

        spTimeout = new JSpinner();
        spTimeout.setModel(new SpinnerNumberModel(100, 0, 3000, 1));
        spTimeout.setBounds(87, 218, 150, 26);
        add(spTimeout);

        edtPassword = new JTextField();
        edtPassword.setText("30");
        edtPassword.setBounds(87, 251, 150, 26);
        add(edtPassword);
        edtPassword.setColumns(10);

        JLabel lblInfo1 = new JLabel();
        lblInfo1.setFont(new Font("Tahoma", Font.BOLD, 14));
        lblInfo1.setText("<html>Для начала процесса градуировки необходимо<br>установить связь с весовой ячейкой.");
        lblInfo1.setBounds(10, 11, 480, 47);
        lblInfo1.setForeground(new Color(0, 0, 128));
        add(lblInfo1);

        JLabel lblInfo2 = new JLabel();
        lblInfo2.setText("<html>Выберите номер СОМ-порта, скорость и пароль.<br>\r\nЕсли параметры связи неизвестны нажмите кнопку \"Настройка связи\"<br>\r\nдля поиска устройства и установления соединения.");
        lblInfo2.setForeground(Color.BLACK);
        lblInfo2.setFont(new Font("Tahoma", Font.PLAIN, 14));
        lblInfo2.setBounds(10, 64, 480, 60);
        add(lblInfo2);

        JLabel lblPortNumber = new JLabel("СОМ порт:");
        lblPortNumber.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lblPortNumber.setBounds(20, 152, 74, 14);
        add(lblPortNumber);

        JLabel lblBaudRate = new JLabel("Скорость:");
        lblBaudRate.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lblBaudRate.setBounds(20, 189, 74, 14);
        add(lblBaudRate);

        JLabel lblTimeout = new JLabel("Таймаут:");
        lblTimeout.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lblTimeout.setBounds(20, 219, 74, 14);
        add(lblTimeout);

        JLabel lblPassword = new JLabel("Пароль:");
        lblPassword.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lblPassword.setBounds(20, 254, 74, 14);
        add(lblPassword);

        JLabel lblPressNext = new JLabel(
                "Для продолжения нажмите кнопку \"Далее\".");
        lblPressNext.setForeground(Color.BLACK);
        lblPressNext.setFont(new Font("Tahoma", Font.PLAIN, 12));
        lblPressNext.setBounds(10, 329, 343, 14);
        add(lblPressNext);

        JButton btnUpdatePorts = new JButton("Обновить порты");
        btnUpdatePorts.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                updatePortList();
            }
        });
        btnUpdatePorts.setBounds(245, 152, 240, 26);
        add(btnUpdatePorts);

        txtResult = new JTextArea();
        txtResult.setFont(new Font("Courier New", Font.PLAIN, 14));
        txtResult.setEditable(false);
        txtResult.setFocusable(false);
        txtResult.setBounds(245, 185, 240, 86);
        add(txtResult);
        
        
        Border border = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
        txtResult.setBorder(BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        
        JButton btnFind = new JButton("Поиск...");
        btnFind.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateObject();
                FindDlg dlg = new FindDlg();
                dlg.setLocationRelativeTo(getTopLevelAncestor());
                dlg.setVisible(true);

            }
        });
        btnFind.setBounds(245, 282, 100, 26);
        btnFind.setVisible(true);
        add(btnFind);
        
        btnTestConnection = new JButton("Подключиться");
        btnTestConnection.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                testConnection();
            }
        });
        btnTestConnection.setBounds(353, 282, 132, 26);
        add(btnTestConnection);

        
    }

    public void updatePortList() {
        String portName = (String) cbPortName.getSelectedItem();
        ComboBoxModel model = new DefaultComboBoxModel(GnuSerialPort.getPortList()
                .toArray());
        cbPortName.setModel(model);
        cbPortName.setSelectedItem(portName);
    }

    public void updatePage() {
        cbPortName.setSelectedItem(driver.getPortName());
        cbBaudRate.setSelectedItem(String.valueOf(driver.getBaudRate()));
        spTimeout.setValue(driver.getTimeout());
        edtPassword.setText(driver.getPassword());
    }

    public void updateObject() {
        driver.setPortName((String) cbPortName.getSelectedItem());
        driver.setBaudRate(Integer.parseInt((String) cbBaudRate
                .getSelectedItem()));
        driver.setTimeout((Integer) spTimeout.getValue());
        driver.setPassword(edtPassword.getText());
        driver.saveParams();
    }

    public void testConnection() {
        btnTestConnection.setEnabled(false);
        try {
            txtResult.setText("");
            updateObject();
            driver.connect(); 
            txtResult.setText(driver.getDeviceText());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
        btnTestConnection.setEnabled(true);
    }
}
