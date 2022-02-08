/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.awt.FlowLayout;
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
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.print.DicomPrintOptionPane;

public class DicomNodeDialog extends JDialog {

  private JTextField aeTitleTf;
  private DicomPrintOptionPane printOptionsPane;
  private JTextField descriptionTf;
  private JTextField hostnameTf;
  private JFormattedTextField portTf;

  private DefaultDicomNode dicomNode;
  private final JComboBox<DefaultDicomNode> nodesComboBox;
  private final DefaultDicomNode.Type typeNode;
  private JComboBox<AbstractDicomNode.UsageType> comboBox;

  public DicomNodeDialog(
      Window parent,
      String title,
      DefaultDicomNode dicomNode,
      JComboBox<DefaultDicomNode> nodeComboBox,
      DefaultDicomNode.Type typeNode) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.typeNode =
        dicomNode == null
            ? typeNode == null ? AbstractDicomNode.Type.DICOM : typeNode
            : dicomNode.getType();
    initComponents();
    this.dicomNode = dicomNode;
    this.nodesComboBox = nodeComboBox;
    if (dicomNode != null) {
      descriptionTf.setText(dicomNode.getDescription());
      aeTitleTf.setText(dicomNode.getAeTitle());
      hostnameTf.setText(dicomNode.getHostname());
      portTf.setValue(dicomNode.getPort());
      if (dicomNode instanceof DicomPrintNode printNode) {
        printOptionsPane.applyOptions(printNode.getPrintOptions());
      } else {
        comboBox.setSelectedItem(dicomNode.getUsageType());
      }
    }
    pack();
  }

  private void initComponents() {
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JRootPane rootPane = getRootPane();
    rootPane.setLayout(new MigLayout("insets 10lp 15lp 10lp 15lp", "[right]rel[grow,fill]"));

    JLabel descriptionLabel =
        new JLabel(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
    descriptionTf = new JTextField();
    descriptionTf.setColumns(20);
    rootPane.add(descriptionLabel, "newline");
    rootPane.add(descriptionTf);

    JLabel aeTitleLabel = new JLabel(Messages.getString("PrinterDialog.aet") + StringUtil.COLON);
    aeTitleTf = new JTextField();
    aeTitleTf.setColumns(15);
    rootPane.add(aeTitleLabel, "newline");
    rootPane.add(aeTitleTf);

    JLabel hostnameLabel = new JLabel(Messages.getString("PrinterDialog.host") + StringUtil.COLON);
    hostnameTf = new JTextField();
    hostnameTf.setColumns(15);
    rootPane.add(hostnameLabel, "newline");
    rootPane.add(hostnameTf);

    JLabel portLabel = new JLabel(Messages.getString("PrinterDialog.port") + StringUtil.COLON);
    NumberFormat myFormat = LocalUtil.getNumberInstance();
    myFormat.setMinimumIntegerDigits(0);
    myFormat.setMaximumIntegerDigits(65535);
    myFormat.setMaximumFractionDigits(0);
    portTf = new JFormattedTextField(new NumberFormatter(myFormat));
    portTf.setColumns(5);
    GuiUtils.setPreferredWidth(portTf, 60);
    GuiUtils.addCheckAction(portTf);
    rootPane.add(portLabel, "newline");
    rootPane.add(portTf, "grow 0");

    if (typeNode == AbstractDicomNode.Type.PRINTER) {
      printOptionsPane = new DicomPrintOptionPane();
      rootPane.add(printOptionsPane, "newline, gaptop 10, spanx");
    } else {
      comboBox = new JComboBox<>(new DefaultComboBoxModel<>(AbstractDicomNode.UsageType.values()));
      comboBox.setSelectedItem(AbstractDicomNode.UsageType.RETRIEVE);
      if (typeNode == AbstractDicomNode.Type.DICOM_CALLING) {
        portTf.setValue(11113);
        hostnameTf.setText("localhost"); // NON-NLS
      }

      rootPane.add(new JLabel(Messages.getString("usage.type") + StringUtil.COLON), "newline");
      rootPane.add(comboBox, "grow 0");
    }

    JButton okButton = new JButton(Messages.getString("PrinterDialog.ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton(Messages.getString("PrinterDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());

    rootPane.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, 0, 5, okButton, GuiUtils.boxHorizontalStrut(20), cancelButton),
        "newline, spanx, gaptop 10lp");
  }

  private void okButtonActionPerformed() {
    String desc = descriptionTf.getText();
    String aeTitle = aeTitleTf.getText();
    String hostname = hostnameTf.getText();
    Number port = GuiUtils.getFormattedValue(portTf);

    if (!StringUtil.hasText(desc)
        || !StringUtil.hasText(aeTitle)
        || !StringUtil.hasText(hostname)
        || port == null) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("PrinterDialog.fill_message"),
          Messages.getString("PrinterDialog.error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (aeTitle.length() > 16) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("DicomNodeDialog.long_aet_msg"),
          Messages.getString("PrinterDialog.error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    UsageType usageType =
        Optional.ofNullable(comboBox).map(c -> (UsageType) c.getSelectedItem()).orElse(null);
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
