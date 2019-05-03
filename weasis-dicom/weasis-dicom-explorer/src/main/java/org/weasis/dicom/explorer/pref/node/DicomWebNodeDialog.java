/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;

public class DicomWebNodeDialog extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomWebNodeDialog.class);

    private JLabel urlLabel;
    private JTextField urlTf;
    private JButton cancelButton;
    private JLabel descriptionLabel;
    private JTextField descriptionTf;
    private JButton okButton;
    private DicomWebNode dicomNode;
    private JComboBox<DicomWebNode> nodesComboBox;
    private JPanel footPanel;
    private JLabel lblType;
    private JComboBox<DicomWebNode.WebType> comboBox;
    private JButton btnHttpHeaders;

    public DicomWebNodeDialog(Window parent, String title, DicomWebNode dicomNode,
        JComboBox<DicomWebNode> nodeComboBox) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        initComponents();
        this.dicomNode = dicomNode;
        this.nodesComboBox = nodeComboBox;
        if (dicomNode != null) {
            descriptionTf.setText(dicomNode.getDescription());
            urlTf.setText(dicomNode.getUrl().toString());
            comboBox.setSelectedItem(dicomNode.getWebType());
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
        gbcDescriptionTf.anchor = GridBagConstraints.LINE_START;
        gbcDescriptionTf.insets = new Insets(0, 0, 5, 0);
        gbcDescriptionTf.gridx = 1;
        gbcDescriptionTf.gridy = 0;
        content.add(descriptionTf, gbcDescriptionTf);
        descriptionTf.setColumns(20);

        lblType = new JLabel(Messages.getString("DicomNodeDialog.lblType.text") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLblType = new GridBagConstraints();
        gbcLblType.anchor = GridBagConstraints.EAST;
        gbcLblType.insets = new Insets(0, 0, 5, 5);
        gbcLblType.gridx = 0;
        gbcLblType.gridy = 1;
        content.add(lblType, gbcLblType);

        comboBox = new JComboBox<>(new DefaultComboBoxModel<>(DicomWebNode.WebType.values()));
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.LINE_START;
        gbcComboBox.insets = new Insets(0, 0, 5, 0);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 1;
        content.add(comboBox, gbcComboBox);

        urlLabel = new JLabel();
        urlLabel.setText("URL" + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
        gbcAeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleLabel.gridx = 0;
        gbcAeTitleLabel.gridy = 2;
        content.add(urlLabel, gbcAeTitleLabel);
        urlTf = new JTextField();
        urlTf.setColumns(30);
        GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
        gbcAeTitleTf.anchor = GridBagConstraints.WEST;
        gbcAeTitleTf.insets = new Insets(0, 0, 5, 0);
        gbcAeTitleTf.gridx = 1;
        gbcAeTitleTf.gridy = 2;
        content.add(urlTf, gbcAeTitleTf);
        NumberFormat myFormat = LocalUtil.getNumberInstance();
        myFormat.setMinimumIntegerDigits(0);
        myFormat.setMaximumIntegerDigits(65535);
        myFormat.setMaximumFractionDigits(0);

        this.getContentPane().add(content, BorderLayout.CENTER);
        
        btnHttpHeaders = new JButton(Messages.getString("DicomWebNodeDialog.httpHeaders")); //$NON-NLS-1$
        GridBagConstraints gbcBtnHttpHeaders = new GridBagConstraints();
        gbcBtnHttpHeaders.anchor = GridBagConstraints.WEST;
        gbcBtnHttpHeaders.insets = new Insets(2, 0, 7, 0);
        gbcBtnHttpHeaders.gridx = 1;
        gbcBtnHttpHeaders.gridy = 3;
        content.add(btnHttpHeaders, gbcBtnHttpHeaders);
        btnHttpHeaders.addActionListener(e -> manageHeader());

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

    private void manageHeader() {
        HttpHeadersEditor dialog = new HttpHeadersEditor(this, dicomNode);
        JMVUtils.showCenterScreen(dialog);
    }

    private void okButtonActionPerformed() {
        String desc = descriptionTf.getText();
        String url = urlTf.getText();
        DicomWebNode.WebType webType = (DicomWebNode.WebType) comboBox.getSelectedItem();

        if (!StringUtil.hasText(desc) || !StringUtil.hasText(url)) {
            JOptionPane.showMessageDialog(this, Messages.getString("PrinterDialog.fill_message"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        URL validUrl;
        try {
            validUrl = new URL(url);
        } catch (MalformedURLException e) {
            LOGGER.warn("Non valid url", e); //$NON-NLS-1$
            JOptionPane.showMessageDialog(this, "This URL is not valid", Messages.getString("PrinterDialog.error"), //$NON-NLS-1$//$NON-NLS-2$
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean addNode = dicomNode == null;
        UsageType usageType = DicomWebNode.getUsageType(webType);

        if (addNode) {
            dicomNode = new DicomWebNode(desc, webType, validUrl, usageType);
            nodesComboBox.addItem(dicomNode);
            nodesComboBox.setSelectedItem(dicomNode);
        } else {
            dicomNode.setDescription(desc);
            dicomNode.setWebType(webType);
            dicomNode.setUrl(validUrl);
            dicomNode.setUsageType(usageType);
            nodesComboBox.repaint();
        }

        AbstractDicomNode.saveDicomNodes(nodesComboBox, AbstractDicomNode.Type.WEB);
        dispose();
    }
}
