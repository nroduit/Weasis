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
import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.print.DicomPrintOptionPane;

public class DicomNodeDialog extends JDialog {

  private JTextField aeTitleTf;
  private DicomPrintOptionPane printOptionsPane;
  private JTextField descriptionTf;
  private JTextField hostnameTf;
  private final JFormattedTextField portTf;
  private final JCheckBox tlsCheckBox = new JCheckBox(Messages.getString("DicomNodeDialog.tls"));
  private final JComboBox<NodeTls.Trust> trustCombo = new JComboBox<>(NodeTls.Trust.values());
  private final JTextField trustStoreTf = new JTextField();
  private final JPasswordField trustStorePasswordTf = new JPasswordField();
  private final JCheckBox legacyCheckBox =
      new JCheckBox(Messages.getString("DicomNodeDialog.legacy"));

  private DefaultDicomNode dicomNode;
  private final JComboBox<DefaultDicomNode> nodesComboBox;
  private final AbstractDicomNode.Type typeNode;
  private JComboBox<AbstractDicomNode.UsageType> comboBox;

  public DicomNodeDialog(
      Window parent,
      String title,
      DefaultDicomNode dicomNode,
      JComboBox<DefaultDicomNode> nodeComboBox,
      AbstractDicomNode.Type typeNode) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.typeNode =
        dicomNode == null
            ? typeNode == null ? AbstractDicomNode.Type.DICOM : typeNode
            : dicomNode.getType();
    this.portTf = new JFormattedTextField();
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
      NodeTls tls = dicomNode.getTls();
      tlsCheckBox.setSelected(tls.enabled());
      trustCombo.setSelectedItem(tls.trust());
      trustStoreTf.setText(tls.trustStore() == null ? "" : tls.trustStore());
      legacyCheckBox.setSelected(tls.legacy());
      if (dicomNode.getId() != null) {
        SecretStore.getInstance()
            .get(dicomNode.getId(), NodeTls.SECRET_TRUST_STORE_PASSWORD)
            .ifPresent(trustStorePasswordTf::setText);
      }
    }
    updateTlsControls();
    pack();
  }

  /** The TLS the fields describe. */
  private NodeTls tlsFromFields() {
    return new NodeTls(
        tlsCheckBox.isSelected(),
        (NodeTls.Trust) trustCombo.getSelectedItem(),
        trustStoreTf.getText(),
        legacyCheckBox.isSelected());
  }

  private void updateTlsControls() {
    boolean on = tlsCheckBox.isSelected();
    boolean file = on && trustCombo.getSelectedItem() == NodeTls.Trust.FILE;
    trustCombo.setEnabled(on);
    trustStoreTf.setEnabled(file);
    trustStorePasswordTf.setEnabled(file);
    legacyCheckBox.setEnabled(on);
  }

  /** One C-ECHO with the fields as they are, the TLS included; the node is not saved. */
  private void testConnection() {
    String aeTitle = aeTitleTf.getText();
    String hostname = hostnameTf.getText();
    Number port = GuiUtils.getFormattedValue(portTf);
    if (!StringUtil.hasText(aeTitle) || !StringUtil.hasText(hostname) || port == null) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("PrinterDialog.fill_message"),
          Messages.common("error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    DefaultDicomNode probe =
        new DefaultDicomNode(
            "probe", aeTitle, hostname, port.intValue(), UsageType.BOTH); // NON-NLS
    NodeTls tls = tlsFromFields();
    String password = new String(trustStorePasswordTf.getPassword());
    String calling = DicomEcho.defaultCallingAet();
    JDialog self = this;
    new javax.swing.SwingWorker<DicomEcho.Result, Void>() {
      @Override
      protected DicomEcho.Result doInBackground() throws Exception {
        return DicomEcho.echo(probe, calling, tls.tlsOptions(null, password), 10);
      }

      @Override
      protected void done() {
        try {
          DicomEcho.Result result = get();
          if (result.success()) {
            JOptionPane.showMessageDialog(
                WinUtil.getValidComponent(self),
                Messages.format(
                    "DicomNodeDialog.test_ok",
                    result.millis(),
                    calling,
                    aeTitle,
                    hostname,
                    port.intValue(),
                    tls.describe()),
                Messages.getString("DicomNodeDialog.test"),
                JOptionPane.INFORMATION_MESSAGE);
          } else {
            JOptionPane.showMessageDialog(
                WinUtil.getValidComponent(self),
                result.message(),
                Messages.getString("DicomNodeDialog.test_failed"),
                JOptionPane.ERROR_MESSAGE);
          }
        } catch (Exception e) {
          Throwable cause = e.getCause() == null ? e : e.getCause();
          JOptionPane.showMessageDialog(
              WinUtil.getValidComponent(self),
              cause.getMessage(),
              Messages.getString("DicomNodeDialog.test_failed"),
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private void initComponents() {
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel panel = new JPanel();
    panel.setLayout(
        new MigLayout("insets 10lp 15lp 10lp 15lp", "[trailing]rel[grow,fill]")); // NON-NLS

    JLabel descriptionLabel = new JLabel(Messages.common("description") + StringUtil.COLON);
    descriptionTf = new JTextField();
    descriptionTf.setColumns(20);
    panel.add(descriptionLabel, GuiUtils.NEWLINE);
    panel.add(descriptionTf);

    JLabel aeTitleLabel = new JLabel(Messages.getString("PrinterDialog.aet") + StringUtil.COLON);
    aeTitleTf = new JTextField();
    aeTitleTf.setColumns(15);
    panel.add(aeTitleLabel, GuiUtils.NEWLINE);
    panel.add(aeTitleTf);

    JLabel hostnameLabel = new JLabel(Messages.getString("PrinterDialog.host") + StringUtil.COLON);
    hostnameTf = new JTextField();
    hostnameTf.setColumns(15);
    panel.add(hostnameLabel, GuiUtils.NEWLINE);
    panel.add(hostnameTf);

    JLabel portLabel = new JLabel(Messages.common("port") + StringUtil.COLON);
    ;
    GuiUtils.formatPortTextField(portTf);
    GuiUtils.setPreferredWidth(portTf, 60);

    panel.add(portLabel, GuiUtils.NEWLINE);
    panel.add(portTf, "grow 0"); // NON-NLS

    // TLS: one check box, the trust source and the legacy flag behind it
    panel.add(tlsCheckBox, "newline, spanx, gaptop 5lp"); // NON-NLS
    trustCombo.setRenderer(
        new javax.swing.DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setText(
                value == NodeTls.Trust.FILE
                    ? Messages.getString("DicomNodeDialog.trust_file")
                    : Messages.getString("DicomNodeDialog.trust_platform"));
            return this;
          }
        });
    panel.add(
        new JLabel(Messages.getString("DicomNodeDialog.trust") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    panel.add(trustCombo, "grow 0"); // NON-NLS
    panel.add(
        new JLabel(Messages.getString("DicomNodeDialog.trust_store") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    trustStoreTf.setColumns(20);
    panel.add(trustStoreTf);
    panel.add(
        new JLabel(Messages.getString("DicomNodeDialog.trust_store_password") + StringUtil.COLON),
        GuiUtils.NEWLINE);
    trustStorePasswordTf.setColumns(12);
    panel.add(trustStorePasswordTf, "grow 0"); // NON-NLS
    panel.add(legacyCheckBox, "newline, spanx"); // NON-NLS
    tlsCheckBox.addActionListener(e -> updateTlsControls());
    trustCombo.addActionListener(e -> updateTlsControls());

    if (typeNode == AbstractDicomNode.Type.PRINTER) {
      printOptionsPane = new DicomPrintOptionPane();
      panel.add(printOptionsPane, "newline, gaptop 10, spanx"); // NON-NLS
    } else {
      comboBox = new JComboBox<>(new DefaultComboBoxModel<>(AbstractDicomNode.UsageType.values()));
      comboBox.setSelectedItem(AbstractDicomNode.UsageType.RETRIEVE);
      if (typeNode == AbstractDicomNode.Type.DICOM_CALLING) {
        portTf.setValue(11113);
        hostnameTf.setText("localhost"); // NON-NLS
      }

      panel.add(new JLabel(Messages.getString("usage.type") + StringUtil.COLON), GuiUtils.NEWLINE);
      panel.add(comboBox, "grow 0"); // NON-NLS
    }

    JButton testButton = new JButton(Messages.getString("DicomNodeDialog.test"));
    testButton.addActionListener(e -> testConnection());
    JButton okButton = new JButton(Messages.common("ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton(Messages.common("cancel"));
    cancelButton.addActionListener(e -> dispose());
    JButton helpButton =
        GuiUtils.createHelpButton(
            typeNode == AbstractDicomNode.Type.PRINTER
                ? "print/#dicom-printer" // NON-NLS
                : "dicom-import/#dicom-queryretrieve"); // NON-NLS

    panel.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            0,
            5,
            helpButton,
            GuiUtils.boxHorizontalStrut(20),
            testButton,
            GuiUtils.boxHorizontalStrut(20),
            okButton,
            GuiUtils.boxHorizontalStrut(20),
            cancelButton),
        "newline, spanx, gaptop 10lp"); // NON-NLS
    setContentPane(panel);
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
          WinUtil.getValidComponent(this),
          Messages.getString("PrinterDialog.fill_message"),
          Messages.common("error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (aeTitle.length() > 16) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("DicomNodeDialog.long_aet_msg"),
          Messages.common("error"),
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
      dicomNode.setId(dicomNode.deriveId(EntryIds.USER_PREFIX));
      nodesComboBox.addItem(dicomNode);
    } else {
      dicomNode.setDescription(desc);
      dicomNode.setAeTitle(aeTitle);
      dicomNode.setHostname(hostname);
      dicomNode.setPort(port.intValue());
      dicomNode.setUsageType(usageType);
    }
    dicomNode.setType(typeNode);
    NodeTls tls = tlsFromFields();
    dicomNode.setTls(tls);
    if (dicomNode.getId() != null) {
      String password = new String(trustStorePasswordTf.getPassword());
      if (tls.enabled() && tls.trust() == NodeTls.Trust.FILE && !password.isEmpty()) {
        SecretStore.getInstance()
            .put(dicomNode.getId(), NodeTls.SECRET_TRUST_STORE_PASSWORD, password);
      }
    }

    if (dicomNode instanceof DicomPrintNode printNode) {
      printOptionsPane.saveOptions(printNode.getPrintOptions());
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
