/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marcelo Porto - initial API and implementation, Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *     
 ******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.text.NumberFormat;

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
import org.weasis.dicom.explorer.Messages;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 */
public class PrinterDialog extends JDialog {
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
    private DicomPrinter dicomPrinter;
    private JComboBox printersComboBox;
    private JPanel footPanel;

    /**
     * Creates new form NewPrinterDialog
     */
    public PrinterDialog(Window parent, String title, DicomPrinter dicomPrinter, JComboBox printersComboBox) {
        super(parent, ModalityType.APPLICATION_MODAL);
        initComponents();
        this.dicomPrinter = dicomPrinter;
        this.printersComboBox = printersComboBox;
        if (dicomPrinter != null) {
            descriptionTf.setText(dicomPrinter.getDescription());
            aeTitleTf.setText(dicomPrinter.getAeTitle());
            hostnameTf.setText(dicomPrinter.getHostname());
            portTf.setText(dicomPrinter.getPort());
            colorPrintSupportCheckBox.setSelected(dicomPrinter.isColorPrintSupported());
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
        gridBagLayout.columnWidths = new int[] { 86, 82, 10, 13, 0 };
        gridBagLayout.rowHeights = new int[] { 19, 19, 19, 23, 0 };
        gridBagLayout.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        content.setLayout(gridBagLayout);
        descriptionLabel = new JLabel();

        descriptionLabel.setText(Messages.getString("PrinterDialog.desc")); //$NON-NLS-1$
        GridBagConstraints gbc_descriptionLabel = new GridBagConstraints();
        gbc_descriptionLabel.anchor = GridBagConstraints.EAST;
        gbc_descriptionLabel.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionLabel.gridx = 0;
        gbc_descriptionLabel.gridy = 0;
        content.add(descriptionLabel, gbc_descriptionLabel);
        descriptionTf = new JTextField();
        descriptionTf.setColumns(15);
        GridBagConstraints gbc_descriptionTf = new GridBagConstraints();
        gbc_descriptionTf.anchor = GridBagConstraints.WEST;
        gbc_descriptionTf.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionTf.gridx = 1;
        gbc_descriptionTf.gridy = 0;
        content.add(descriptionTf, gbc_descriptionTf);
        aeTitleLabel = new JLabel();

        aeTitleLabel.setText(Messages.getString("PrinterDialog.aet")); //$NON-NLS-1$
        GridBagConstraints gbc_aeTitleLabel = new GridBagConstraints();
        gbc_aeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbc_aeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbc_aeTitleLabel.gridx = 0;
        gbc_aeTitleLabel.gridy = 1;
        content.add(aeTitleLabel, gbc_aeTitleLabel);
        aeTitleTf = new JTextField();
        aeTitleTf.setColumns(15);
        GridBagConstraints gbc_aeTitleTf = new GridBagConstraints();
        gbc_aeTitleTf.anchor = GridBagConstraints.WEST;
        gbc_aeTitleTf.insets = new Insets(0, 0, 5, 5);
        gbc_aeTitleTf.gridx = 1;
        gbc_aeTitleTf.gridy = 1;
        content.add(aeTitleTf, gbc_aeTitleTf);
        hostnameLabel = new JLabel();

        hostnameLabel.setText(Messages.getString("PrinterDialog.host")); //$NON-NLS-1$
        GridBagConstraints gbc_hostnameLabel = new GridBagConstraints();
        gbc_hostnameLabel.anchor = GridBagConstraints.EAST;
        gbc_hostnameLabel.insets = new Insets(0, 0, 5, 5);
        gbc_hostnameLabel.gridx = 0;
        gbc_hostnameLabel.gridy = 2;
        content.add(hostnameLabel, gbc_hostnameLabel);
        hostnameTf = new JTextField();
        hostnameTf.setColumns(15);
        GridBagConstraints gbc_hostnameTf = new GridBagConstraints();
        gbc_hostnameTf.anchor = GridBagConstraints.WEST;
        gbc_hostnameTf.insets = new Insets(0, 0, 5, 5);
        gbc_hostnameTf.gridx = 1;
        gbc_hostnameTf.gridy = 2;
        content.add(hostnameTf, gbc_hostnameTf);
        portLabel = new JLabel();

        portLabel.setText(Messages.getString("PrinterDialog.port")); //$NON-NLS-1$
        GridBagConstraints gbc_portLabel = new GridBagConstraints();
        gbc_portLabel.anchor = GridBagConstraints.WEST;
        gbc_portLabel.insets = new Insets(0, 0, 5, 5);
        gbc_portLabel.gridx = 2;
        gbc_portLabel.gridy = 2;
        content.add(portLabel, gbc_portLabel);
        NumberFormat myFormat = NumberFormat.getInstance();
        portTf = new JFormattedTextField(new NumberFormatter(myFormat));
        portTf.setColumns(5);
        JMVUtils.setPreferredWidth(portTf, 60);
        JMVUtils.addCheckAction(portTf);
        GridBagConstraints gbc_portTf = new GridBagConstraints();
        gbc_portTf.anchor = GridBagConstraints.WEST;
        gbc_portTf.insets = new Insets(0, 0, 5, 0);
        gbc_portTf.gridx = 3;
        gbc_portTf.gridy = 2;
        content.add(portTf, gbc_portTf);
        colorPrintSupportCheckBox = new JCheckBox();

        colorPrintSupportCheckBox.setText(Messages.getString("PrinterDialog.color")); //$NON-NLS-1$
        GridBagConstraints gbc_colorPrintSupportCheckBox = new GridBagConstraints();
        gbc_colorPrintSupportCheckBox.anchor = GridBagConstraints.WEST;
        gbc_colorPrintSupportCheckBox.insets = new Insets(0, 0, 0, 5);
        gbc_colorPrintSupportCheckBox.gridwidth = 2;
        gbc_colorPrintSupportCheckBox.gridx = 0;
        gbc_colorPrintSupportCheckBox.gridy = 3;
        content.add(colorPrintSupportCheckBox, gbc_colorPrintSupportCheckBox);

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
        String port = portTf.getText();

        if (desc == null || "".equals(desc) || aeTitle == null || "".equals(aeTitle) || hostname == null //$NON-NLS-1$ //$NON-NLS-2$
            || "".equals(hostname) || port == null || "".equals(port)) { //$NON-NLS-1$ //$NON-NLS-2$
            JOptionPane.showMessageDialog(this, Messages.getString("PrinterDialog.fill_message"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }
        if (dicomPrinter == null) {
            dicomPrinter = new DicomPrinter();
            printersComboBox.addItem(dicomPrinter);
        }
        dicomPrinter.setDescription(desc);
        dicomPrinter.setAeTitle(aeTitle);
        dicomPrinter.setHostname(hostname);
        dicomPrinter.setPort(port);
        dicomPrinter.setColorPrintSupported(colorPrintSupportCheckBox.isSelected());

        DicomPrinter.savePrintersSettings(printersComboBox);
        dispose();
    }

}
