package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.NumberFormat;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.NumberFormatter;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class DicomNodeDialog extends JDialog {
    private JLabel aeTitleLabel;
    private JTextField aeTitleTf;
    private JButton cancelButton;
    private JCheckBox colorPrintSupportCheckBox;
    private JLabel descriptionLabel;
    private JTextField descriptionTf;
    private JLabel hostnameLabel;
    private JTextField hostnameTf;
    private JButton okButton;
    private JLabel portLabel;
    private JFormattedTextField portTf;
    private DicomNodeEx dicomNode;
    private JComboBox<DicomNodeEx> nodesComboBox;
    private JPanel footPanel;
    private JLabel lblType;
    private JComboBox<DicomNodeEx.Type> comboBox;

    public DicomNodeDialog(Window parent, String title, DicomNodeEx dicomNode, JComboBox<DicomNodeEx> nodeComboBox) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        initComponents();
        this.dicomNode = dicomNode;
        this.nodesComboBox = nodeComboBox;
        if (dicomNode != null) {
            descriptionTf.setText(dicomNode.getDescription());
            aeTitleTf.setText(dicomNode.getAeTitle());
            hostnameTf.setText(dicomNode.getHostname());
            portTf.setValue(dicomNode.getPort());
            colorPrintSupportCheckBox.setSelected(dicomNode.isColorPrintSupported());
            comboBox.setSelectedItem(dicomNode.getType());
        }
        pack();
    }

    private void initComponents() {
        final JPanel rootPane = new JPanel();
        rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        this.setContentPane(rootPane);

        final JPanel content = new JPanel();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        rootPane.setLayout(new BorderLayout(0, 0));
        GridBagLayout gridBagLayout = new GridBagLayout();
        content.setLayout(gridBagLayout);
        descriptionLabel = new JLabel();
        GridBagConstraints gbc_descriptionLabel = new GridBagConstraints();
        gbc_descriptionLabel.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionLabel.gridx = 0;
        gbc_descriptionLabel.gridy = 0;
        content.add(descriptionLabel, gbc_descriptionLabel);

        descriptionLabel.setText(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
        descriptionTf = new JTextField();
        GridBagConstraints gbc_descriptionTf = new GridBagConstraints();
        gbc_descriptionTf.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionTf.gridx = 1;
        gbc_descriptionTf.gridy = 0;
        content.add(descriptionTf, gbc_descriptionTf);
        descriptionTf.setColumns(15);

        lblType = new JLabel(Messages.getString("DicomNodeDialog.lblType.text")); //$NON-NLS-1$
        GridBagConstraints gbcLblType = new GridBagConstraints();
        gbcLblType.anchor = GridBagConstraints.EAST;
        gbcLblType.insets = new Insets(0, 0, 5, 5);
        gbcLblType.gridx = 0;
        gbcLblType.gridy = 1;
        content.add(lblType, gbcLblType);

        comboBox = new JComboBox<>(new DefaultComboBoxModel<>(DicomNodeEx.Type.values()));
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.insets = new Insets(0, 0, 5, 5);
        gbcComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 1;
        content.add(comboBox, gbcComboBox);

        colorPrintSupportCheckBox = new JCheckBox();

        colorPrintSupportCheckBox.setText(Messages.getString("PrinterDialog.color")); //$NON-NLS-1$
        GridBagConstraints gbcColorPrintSupportCheckBox = new GridBagConstraints();
        gbcColorPrintSupportCheckBox.anchor = GridBagConstraints.WEST;
        gbcColorPrintSupportCheckBox.insets = new Insets(0, 0, 0, 5);
        gbcColorPrintSupportCheckBox.gridwidth = 2;
        gbcColorPrintSupportCheckBox.gridx = 0;
        gbcColorPrintSupportCheckBox.gridy = 4;
        content.add(colorPrintSupportCheckBox, gbcColorPrintSupportCheckBox);
        colorPrintSupportCheckBox.setEnabled(false);
        final ItemListener changeViewListener = new ItemListener() {

            @Override
            public void itemStateChanged(final ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    colorPrintSupportCheckBox.setEnabled(DicomNodeEx.Type.PRINTER.equals(comboBox.getSelectedItem()));
                }
            }
        };
        comboBox.addItemListener(changeViewListener);

        aeTitleLabel = new JLabel();
        aeTitleLabel.setText(Messages.getString("PrinterDialog.aet") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
        gbcAeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleLabel.gridx = 0;
        gbcAeTitleLabel.gridy = 2;
        content.add(aeTitleLabel, gbcAeTitleLabel);
        aeTitleTf = new JTextField();
        aeTitleTf.setColumns(15);
        GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
        gbcAeTitleTf.anchor = GridBagConstraints.WEST;
        gbcAeTitleTf.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleTf.gridx = 1;
        gbcAeTitleTf.gridy = 2;
        content.add(aeTitleTf, gbcAeTitleTf);
        hostnameLabel = new JLabel();

        hostnameLabel.setText(Messages.getString("PrinterDialog.host") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcHostnameLabel = new GridBagConstraints();
        gbcHostnameLabel.anchor = GridBagConstraints.EAST;
        gbcHostnameLabel.insets = new Insets(0, 0, 5, 5);
        gbcHostnameLabel.gridx = 0;
        gbcHostnameLabel.gridy = 3;
        content.add(hostnameLabel, gbcHostnameLabel);
        hostnameTf = new JTextField();
        hostnameTf.setColumns(15);
        GridBagConstraints gbcHostnameTf = new GridBagConstraints();
        gbcHostnameTf.anchor = GridBagConstraints.WEST;
        gbcHostnameTf.insets = new Insets(0, 0, 5, 5);
        gbcHostnameTf.gridx = 1;
        gbcHostnameTf.gridy = 3;
        content.add(hostnameTf, gbcHostnameTf);
        portLabel = new JLabel();

        portLabel.setText(Messages.getString("PrinterDialog.port") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPortLabel = new GridBagConstraints();
        gbcPortLabel.anchor = GridBagConstraints.WEST;
        gbcPortLabel.insets = new Insets(0, 0, 5, 5);
        gbcPortLabel.gridx = 2;
        gbcPortLabel.gridy = 3;
        content.add(portLabel, gbcPortLabel);
        NumberFormat myFormat = LocalUtil.getNumberInstance();
        myFormat.setMinimumIntegerDigits(0);
        myFormat.setMaximumIntegerDigits(65535);
        myFormat.setMaximumFractionDigits(0);
        portTf = new JFormattedTextField(new NumberFormatter(myFormat));
        portTf.setColumns(5);
        JMVUtils.setPreferredWidth(portTf, 60);
        JMVUtils.addCheckAction(portTf);
        GridBagConstraints gbcPortTf = new GridBagConstraints();
        gbcPortTf.anchor = GridBagConstraints.WEST;
        gbcPortTf.insets = new Insets(0, 0, 5, 0);
        gbcPortTf.gridx = 3;
        gbcPortTf.gridy = 3;
        content.add(portTf, gbcPortTf);

        this.getContentPane().add(content, BorderLayout.CENTER);

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);

        okButton = new JButton();
        footPanel.add(okButton);

        okButton.setText(Messages.getString("PrinterDialog.ok")); //$NON-NLS-1$
        okButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        cancelButton = new JButton();
        footPanel.add(cancelButton);

        cancelButton.setText(Messages.getString("PrinterDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
    }

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {
        dispose();
    }

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String desc = descriptionTf.getText();
        String aeTitle = aeTitleTf.getText();
        String hostname = hostnameTf.getText();
        Number port = JMVUtils.getFormattedValue(portTf);

        if (!StringUtil.hasText(desc) || !StringUtil.hasText(aeTitle) || !StringUtil.hasText(hostname)
            || port == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("PrinterDialog.fill_message"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        boolean addNode = dicomNode == null;
        if (addNode) {
            dicomNode = new DicomNodeEx(desc, aeTitle, hostname, port.intValue());
            nodesComboBox.addItem(dicomNode);
        } else {
            dicomNode.setDescription(desc);
            dicomNode.setAeTitle(aeTitle);
            dicomNode.setHostname(hostname);
            dicomNode.setPort(port.intValue());
        }

        dicomNode.setType((DicomNodeEx.Type) comboBox.getSelectedItem());
        dicomNode.setColorPrintSupported(colorPrintSupportCheckBox.isSelected());
        if (addNode) {
            nodesComboBox.setSelectedItem(dicomNode);
        } else {
            nodesComboBox.repaint();
        }

        DicomNodeEx.saveDicomNodes(nodesComboBox);
        dispose();
    }
}
