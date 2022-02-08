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
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;

public class DicomWebNodeDialog extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomWebNodeDialog.class);

  private JTextField urlTf;
  private JTextField descriptionTf;
  private final DicomWebNode dicomNode;
  private final JComboBox<DicomWebNode> nodesComboBox;
  private JComboBox<DicomWebNode.WebType> comboBox;
  private final JComboBox<AuthMethod> comboBoxAuth = new JComboBox<>();

  public DicomWebNodeDialog(
      Window parent, String title, DicomWebNode dicomNode, JComboBox<DicomWebNode> nodeComboBox) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    initComponents();
    this.nodesComboBox = nodeComboBox;
    if (dicomNode == null) {
      this.dicomNode =
          new DicomWebNode("", (DicomWebNode.WebType) comboBox.getSelectedItem(), null, null);
      nodesComboBox.addItem(this.dicomNode);
      nodesComboBox.setSelectedItem(this.dicomNode);
    } else {
      this.dicomNode = dicomNode;
      descriptionTf.setText(dicomNode.getDescription());
      urlTf.setText(dicomNode.getUrl().toString());
      comboBox.setSelectedItem(dicomNode.getWebType());
      comboBoxAuth.setSelectedItem(dicomNode.getAuthMethod());
    }
    pack();
  }

  private void initComponents() {
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JRootPane rootPane = getRootPane();
    rootPane.setLayout(
        new MigLayout("insets 10lp 15lp 10lp 15lp", "[right]rel[left][grow,fill]", "[]10[]"));

    JLabel descriptionLabel =
        new JLabel(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
    descriptionTf = new JTextField();
    descriptionTf.setColumns(20);
    rootPane.add(descriptionLabel, "newline");
    rootPane.add(descriptionTf);

    JLabel lblType =
        new JLabel(Messages.getString("DicomNodeDialog.lblType.text") + StringUtil.COLON);
    comboBox = new JComboBox<>(new DefaultComboBoxModel<>(DicomWebNode.WebType.values()));
    rootPane.add(lblType, "newline");
    rootPane.add(comboBox);

    JLabel urlLabel = new JLabel("URL" + StringUtil.COLON);
    urlTf = new JTextField(50);
    rootPane.add(urlLabel, "newline");
    rootPane.add(urlTf, "growx, spanx 3, alignx leading");

    JLabel lblAuth = new JLabel(Messages.getString("authentication") + StringUtil.COLON);
    AuthenticationPersistence.loadMethods(comboBoxAuth);
    comboBoxAuth.setSelectedIndex(0);
    JButton btnAuth = new JButton(Messages.getString("manager"));
    btnAuth.addActionListener(e -> manageAuth());
    rootPane.add(lblAuth, "newline");
    rootPane.add(comboBoxAuth);
    rootPane.add(btnAuth, "growx 0");

    JLabel headersLabel = new JLabel(Messages.getString("http.optional") + StringUtil.COLON);
    JButton btnHttpHeaders = new JButton(Messages.getString("DicomWebNodeDialog.httpHeaders"));
    btnHttpHeaders.addActionListener(e -> manageHeader());
    rootPane.add(headersLabel, "newline");
    rootPane.add(btnHttpHeaders);

    JButton okButton = new JButton(Messages.getString("PrinterDialog.ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton(Messages.getString("PrinterDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());

    rootPane.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, 0, 0, okButton, GuiUtils.boxHorizontalStrut(15), cancelButton),
        "newline, skip 3, gap 15lp 0lp 10lp 10lp, alignx trailing");
  }

  private void manageAuth() {
    AuthenticationEditor dialog = new AuthenticationEditor(this, comboBoxAuth);
    GuiUtils.showCenterScreen(dialog);
  }

  private void manageHeader() {
    HttpHeadersEditor dialog = new HttpHeadersEditor(this, dicomNode);
    GuiUtils.showCenterScreen(dialog);
  }

  private void okButtonActionPerformed() {
    String desc = descriptionTf.getText();
    String url = urlTf.getText();
    DicomWebNode.WebType webType = (DicomWebNode.WebType) comboBox.getSelectedItem();

    if (!StringUtil.hasText(desc) || !StringUtil.hasText(url)) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("PrinterDialog.fill_message"),
          Messages.getString("PrinterDialog.error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    URL validUrl;
    try {
      validUrl = new URL(url);
    } catch (MalformedURLException e) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("this.url.is.not.valid"),
          Messages.getString("PrinterDialog.error"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    UsageType usageType = DicomWebNode.getUsageType(webType);

    dicomNode.setDescription(desc);
    dicomNode.setWebType(webType);
    dicomNode.setUrl(validUrl);
    dicomNode.setUsageType(usageType);
    dicomNode.setAuthMethodUid(((AuthMethod) comboBoxAuth.getSelectedItem()).getUid());
    nodesComboBox.repaint();

    AbstractDicomNode.saveDicomNodes(nodesComboBox, AbstractDicomNode.Type.WEB);
    dispose();
  }
}
