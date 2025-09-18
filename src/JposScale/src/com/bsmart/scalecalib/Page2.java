package com.bsmart.scalecalib;

import java.awt.Font;
import java.awt.Color;
import java.awt.SystemColor;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.ListModel;
import javax.swing.border.BevelBorder;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.bsmart.scale.PropertyPage;
import com.bsmart.scale.SmScale;

public class Page2 extends PropertyPage {

    private JList lbChannels;
    private JLabel lblDeviceName;
    private JTextArea txtChannel;
    private DefaultListModel lbChannelsData;

    private final SmScale driver = SmScale.instance;

    /**
     * Create the panel.
     */
    public Page2() {
        setLayout(null);

        JLabel lblInfo1 = new JLabel();
        lblInfo1.setText("<html>Связь установлена. Выберите из списка весовой канал,<br>\r\nкоторый необходимо градуировать.");
        lblInfo1.setForeground(new Color(0, 0, 128));
        lblInfo1.setFont(new Font("Tahoma", Font.BOLD, 16));
        lblInfo1.setBackground(SystemColor.activeCaptionBorder);
        lblInfo1.setBounds(10, 11, 480, 42);
        add(lblInfo1);

        lbChannels = new JList();
        lbChannels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lbChannels.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent arg0) {
                updateChannelData(lbChannels.getSelectedIndex());
            }
        });
        lbChannels.setBounds(10, 81, 128, 263);
        Border border = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
        lbChannels.setBorder(BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        lbChannelsData = new DefaultListModel();
        lbChannels.setModel(lbChannelsData);
        lbChannels.setSelectedIndex(0);
        add(lbChannels);

        txtChannel = new JTextArea();
        txtChannel.setFont(new Font("Courier New", Font.PLAIN, 14));
        txtChannel.setBounds(143, 80, 347, 264);
        border = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
        txtChannel.setBorder(BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        add(txtChannel);

        JLabel lblDeviceName_ = new JLabel("Устройство:");
        lblDeviceName_.setFont(new Font("Tahoma", Font.PLAIN, 11));
        lblDeviceName_.setBounds(10, 58, 69, 14);
        add(lblDeviceName_);

        lblDeviceName = new JLabel("");
        lblDeviceName.setForeground(Color.BLUE);
        lblDeviceName.setFont(new Font("Tahoma", Font.BOLD, 12));
        lblDeviceName.setBounds(89, 58, 401, 14);
        add(lblDeviceName);

    }

    public void updatePage() {
        lblDeviceName.setText(driver.getDeviceName());
        lbChannelsData.removeAllElements();
        for (int i = 0; i < driver.getChannelCount(); i++) {
            lbChannelsData.addElement("Канал №" + String.valueOf(i + 1));
        }
        updateChannelData(0);
        lbChannels.setSelectedIndex(0);
    }

    public void updateObject() {

    }

    public void updateChannelData(int index) {
        try {
            driver.readChannelParams(index);
            txtChannel.setText(driver.getChannelParams().toText());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
