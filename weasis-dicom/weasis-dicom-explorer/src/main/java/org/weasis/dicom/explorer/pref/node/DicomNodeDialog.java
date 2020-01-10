/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.Optional;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
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
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.print.DicomPrintOptionPane;

public class DicomNodeDialog extends JDialog {
    private JLabel aeTitleLabel;
    private JTextField aeTitleTf;
    private JButton cancelButton;
    private DicomPrintOptionPane printOptionsPane;
    private JLabel descriptionLabel;
    private JTextField descriptionTf;
    private JLabel hostnameLabel;
    private JTextField hostnameTf;
    private JButton okButton;
    private JLabel portLabel;
    private JFormattedTextField portTf;
    private JPanel footPanel;

    private DefaultDicomNode dicomNode;
    private final JComboBox<DefaultDicomNode> nodesComboBox;
    private final DefaultDicomNode.Type typeNode;
    private JComboBox<AbstractDicomNode.UsageType> comboBox;

    public DicomNodeDialog(Window parent, String title, DefaultDicomNode dicomNode,
        JComboBox<DefaultDicomNode> nodeComboBox, DefaultDicomNode.Type typeNode) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.typeNode =
            dicomNode == null ? typeNode == null ? AbstractDicomNode.Type.DICOM : typeNode : dicomNode.getType();
        initComponents();
        this.dicomNode = dicomNode;
        this.nodesComboBox = nodeComboBox;
        if (dicomNode != null) {
            descriptionTf.setText(dicomNode.getDescription());
            aeTitleTf.setText(dicomNode.getAeTitle());
            hostnameTf.setText(dicomNode.getHostname());
            portTf.setValue(dicomNode.getPort());
            if (dicomNode instanceof DicomPrintNode) {
                printOptionsPane.applyOptions(((DicomPrintNode) dicomNode).getPrintOptions());
            } else {
                comboBox.setSelectedItem(dicomNode.getUsageType());
            }
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
        GridBagConstraints gbcDescriptionLabel = new GridBagConstraints();
        gbcDescriptionLabel.insets = new Insets(0, 0, 5, 5);
        gbcDescriptionLabel.gridx = 0;
        gbcDescriptionLabel.gridy = 0;
        content.add(descriptionLabel, gbcDescriptionLabel);

        descriptionLabel.setText(Messages.getString("PrinterDialog.desc") + StringUtil.COLON); //$NON-NLS-1$
        descriptionTf = new JTextField();
        GridBagConstraints gbcDescriptionTf = new GridBagConstraints();
        gbcDescriptionTf.insets = new Insets(0, 0, 5, 5);
        gbcDescriptionTf.gridx = 1;
        gbcDescriptionTf.gridy = 0;
        content.add(descriptionTf, gbcDescriptionTf);
        descriptionTf.setColumns(15);

        aeTitleLabel = new JLabel();
        aeTitleLabel.setText(Messages.getString("PrinterDialog.aet") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
        gbcAeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleLabel.gridx = 0;
        gbcAeTitleLabel.gridy = 1;
        content.add(aeTitleLabel, gbcAeTitleLabel);
        aeTitleTf = new JTextField();
        aeTitleTf.setColumns(15);
        GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
        gbcAeTitleTf.anchor = GridBagConstraints.WEST;
        gbcAeTitleTf.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleTf.gridx = 1;
        gbcAeTitleTf.gridy = 1;
        content.add(aeTitleTf, gbcAeTitleTf);
        hostnameLabel = new JLabel();

        hostnameLabel.setText(Messages.getString("PrinterDialog.host") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcHostnameLabel = new GridBagConstraints();
        gbcHostnameLabel.anchor = GridBagConstraints.EAST;
        gbcHostnameLabel.insets = new Insets(0, 0, 5, 5);
        gbcHostnameLabel.gridx = 0;
        gbcHostnameLabel.gridy = 2;
        content.add(hostnameLabel, gbcHostnameLabel);
        hostnameTf = new JTextField();
        hostnameTf.setColumns(15);

        GridBagConstraints gbcHostnameTf = new GridBagConstraints();
        gbcHostnameTf.anchor = GridBagConstraints.WEST;
        gbcHostnameTf.insets = new Insets(0, 0, 5, 5);
        gbcHostnameTf.gridx = 1;
        gbcHostnameTf.gridy = 2;
        content.add(hostnameTf, gbcHostnameTf);
        portLabel = new JLabel();

        portLabel.setText(Messages.getString("PrinterDialog.port") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPortLabel = new GridBagConstraints();
        gbcPortLabel.anchor = GridBagConstraints.WEST;
        gbcPortLabel.insets = new Insets(0, 0, 5, 5);
        gbcPortLabel.gridx = 2;
        gbcPortLabel.gridy = 2;
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
        gbcPortTf.gridy = 2;
        content.add(portTf, gbcPortTf);

        if (typeNode == AbstractDicomNode.Type.PRINTER) {
            printOptionsPane = new DicomPrintOptionPane();
            this.getContentPane().add(content, BorderLayout.NORTH);
            this.getContentPane().add(printOptionsPane, BorderLayout.CENTER);
        } else {
            JLabel lblType = new JLabel("Usage type" + StringUtil.COLON); //$NON-NLS-1$
            GridBagConstraints gbcLblType = new GridBagConstraints();
            gbcLblType.anchor = GridBagConstraints.EAST;
            gbcLblType.insets = new Insets(0, 0, 5, 5);
            gbcLblType.gridx = 0;
            gbcLblType.gridy = 3;
            content.add(lblType, gbcLblType);

            comboBox = new JComboBox<>(new DefaultComboBoxModel<>(AbstractDicomNode.UsageType.values()));
            comboBox.setSelectedItem(AbstractDicomNode.UsageType.RETRIEVE);
            GridBagConstraints gbcComboBox = new GridBagConstraints();
            gbcComboBox.anchor = GridBagConstraints.LINE_START;
            gbcComboBox.insets = new Insets(0, 0, 5, 5);
            gbcComboBox.gridx = 1;
            gbcComboBox.gridy = 3;
            content.add(comboBox, gbcComboBox);
            this.getContentPane().add(content, BorderLayout.CENTER);

            if (typeNode == AbstractDicomNode.Type.DICOM_CALLING) {
                portTf.setValue(11113);
                hostnameTf.setText("localhost"); //$NON-NLS-1$
            }
        }

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);

        okButton = new JButton();
        footPanel.add(okButton);

        okButton.setText(Messages.getString("PrinterDialog.ok")); //$NON-NLS-1$
        okButton.addActionListener(e -> okButtonActionPerformed());
        cancelButton = new JButton();
        footPanel.add(cancelButton);

        cancelButton.setText(Messages.getString("PrinterDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(e -> dispose());
    }

    private void okButtonActionPerformed() {
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

        if (aeTitle.length() > 16) {
            JOptionPane.showMessageDialog(this, Messages.getString("DicomNodeDialog.long_aet_msg"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        UsageType usageType = Optional.ofNullable(comboBox).map(c -> (UsageType) c.getSelectedItem()).orElse(null);
        boolean addNode = dicomNode == null;
        if (addNode) {
            if (AbstractDicomNode.Type.PRINTER == typeNode) {
                dicomNode = new DicomPrintNode(desc, aeTitle, hostname, port.intValue());
            } else {
                dicomNode = new DefaultDicomNode(desc, aeTitle, hostname, port.intValue(), usageType);
            }
            nodesComboBox.addItem(dicomNode);
        } else {
            dicomNode.setDescription(desc);
            dicomNode.setAeTitle(aeTitle);
            dicomNode.setHostname(hostname);
            dicomNode.setPort(port.intValue());
            dicomNode.setUsageType(usageType);
        }
        dicomNode.setType(typeNode);

        if (dicomNode instanceof DicomPrintNode) {
            printOptionsPane.saveOptions(((DicomPrintNode) dicomNode).getPrintOptions());
        }
        if (addNode) {
            nodesComboBox.setSelectedItem(dicomNode);
        } else {
            nodesComboBox.repaint();
        }

        AbstractDicomNode.saveDicomNodes(nodesComboBox, typeNode);
        dispose();
    }
}
