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
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.LocalUtil;
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
  private JComboBox<AuthMethod> comboBoxAuth = new JComboBox<>();

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
    final JPanel rootPane = new JPanel();
    rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
    this.setContentPane(rootPane);

    final JPanel content = new JPanel();

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    rootPane.setLayout(new BorderLayout(0, 0));
    GridBagLayout gridBagLayout = new GridBagLayout();
    content.setLayout(gridBagLayout);
    JLabel descriptionLabel = new JLabel();
    GridBagConstraints gbcDescriptionLabel = new GridBagConstraints();
    gbcDescriptionLabel.anchor = GridBagConstraints.EAST;
    gbcDescriptionLabel.insets = new Insets(0, 0, 5, 5);
    gbcDescriptionLabel.gridx = 0;
    gbcDescriptionLabel.gridy = 0;
    content.add(descriptionLabel, gbcDescriptionLabel);

    descriptionLabel.setText(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
    descriptionTf = new JTextField();
    GridBagConstraints gbcDescriptionTf = new GridBagConstraints();
    gbcDescriptionTf.anchor = GridBagConstraints.WEST;
    gbcDescriptionTf.insets = new Insets(0, 0, 5, 0);
    gbcDescriptionTf.gridx = 1;
    gbcDescriptionTf.gridy = 0;
    gbcDescriptionTf.gridwidth = 2;
    content.add(descriptionTf, gbcDescriptionTf);
    descriptionTf.setColumns(20);

    JLabel lblType =
        new JLabel(Messages.getString("DicomNodeDialog.lblType.text") + StringUtil.COLON);
    GridBagConstraints gbcLblType = new GridBagConstraints();
    gbcLblType.anchor = GridBagConstraints.EAST;
    gbcLblType.insets = new Insets(0, 0, 5, 5);
    gbcLblType.gridx = 0;
    gbcLblType.gridy = 1;
    content.add(lblType, gbcLblType);

    comboBox = new JComboBox<>(new DefaultComboBoxModel<>(DicomWebNode.WebType.values()));
    GridBagConstraints gbcComboBox = new GridBagConstraints();
    gbcComboBox.anchor = GridBagConstraints.WEST;
    gbcComboBox.insets = new Insets(0, 0, 5, 0);
    gbcComboBox.gridx = 1;
    gbcComboBox.gridy = 1;
    gbcComboBox.gridwidth = 2;
    content.add(comboBox, gbcComboBox);

    JLabel urlLabel = new JLabel();
    urlLabel.setText("URL" + StringUtil.COLON);
    GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
    gbcAeTitleLabel.anchor = GridBagConstraints.EAST;
    gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
    gbcAeTitleLabel.gridx = 0;
    gbcAeTitleLabel.gridy = 2;
    content.add(urlLabel, gbcAeTitleLabel);
    urlTf = new JTextField(50);
    GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
    gbcAeTitleTf.anchor = GridBagConstraints.WEST;
    gbcAeTitleTf.insets = new Insets(0, 0, 5, 0);
    gbcAeTitleTf.gridx = 1;
    gbcAeTitleTf.gridwidth = 2;
    gbcAeTitleTf.gridy = 2;
    content.add(urlTf, gbcAeTitleTf);
    NumberFormat myFormat = LocalUtil.getNumberInstance();
    myFormat.setMinimumIntegerDigits(0);
    myFormat.setMaximumIntegerDigits(65535);
    myFormat.setMaximumFractionDigits(0);

    JLabel lblAuth = new JLabel(Messages.getString("authentication") + StringUtil.COLON);
    GridBagConstraints gbcLblAuth = new GridBagConstraints();
    gbcLblAuth.anchor = GridBagConstraints.EAST;
    gbcLblAuth.insets = new Insets(0, 0, 5, 5);
    gbcLblAuth.gridx = 0;
    gbcLblAuth.gridy = 3;
    content.add(lblAuth, gbcLblAuth);

    AuthenticationPersistence.loadMethods(comboBoxAuth);
    comboBoxAuth.setSelectedIndex(0);
    GridBagConstraints gbcComboBoxAuth = new GridBagConstraints();
    gbcComboBoxAuth.anchor = GridBagConstraints.LINE_START;
    gbcComboBoxAuth.insets = new Insets(0, 0, 5, 5);
    gbcComboBoxAuth.gridx = 1;
    gbcComboBoxAuth.gridy = 3;
    content.add(comboBoxAuth, gbcComboBoxAuth);
    JButton btnAuth = new JButton(Messages.getString("manager"));
    GridBagConstraints gbcButtonAuth = new GridBagConstraints();
    gbcButtonAuth.anchor = GridBagConstraints.LINE_START;
    gbcButtonAuth.insets = new Insets(0, 0, 5, 5);
    gbcButtonAuth.gridx = 2;
    gbcButtonAuth.gridy = 3;
    content.add(btnAuth, gbcButtonAuth);
    btnAuth.addActionListener(e -> manageAuth());

    JLabel headersLabel = new JLabel();
    headersLabel.setText(Messages.getString("http.optional") + StringUtil.COLON);
    GridBagConstraints gbchttpitleLabel = new GridBagConstraints();
    gbchttpitleLabel.anchor = GridBagConstraints.EAST;
    gbchttpitleLabel.insets = new Insets(0, 0, 5, 5);
    gbchttpitleLabel.gridx = 0;
    gbchttpitleLabel.gridy = 4;
    content.add(headersLabel, gbchttpitleLabel);
    JButton btnHttpHeaders = new JButton(Messages.getString("DicomWebNodeDialog.httpHeaders"));
    GridBagConstraints gbcBtnHttpHeaders = new GridBagConstraints();
    gbcBtnHttpHeaders.anchor = GridBagConstraints.WEST;
    gbcBtnHttpHeaders.insets = new Insets(2, 0, 7, 0);
    gbcBtnHttpHeaders.gridx = 1;
    gbcBtnHttpHeaders.gridy = 4;
    content.add(btnHttpHeaders, gbcBtnHttpHeaders);
    btnHttpHeaders.addActionListener(e -> manageHeader());

    this.getContentPane().add(content, BorderLayout.CENTER);

    JPanel footPanel = new JPanel();
    FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
    flowLayout.setVgap(15);
    flowLayout.setAlignment(FlowLayout.RIGHT);
    flowLayout.setHgap(20);
    getContentPane().add(footPanel, BorderLayout.SOUTH);

    JButton okButton = new JButton();
    footPanel.add(okButton);

    okButton.setText(Messages.getString("PrinterDialog.ok"));
    okButton.addActionListener(e -> okButtonActionPerformed());
    JButton cancelButton = new JButton();
    footPanel.add(cancelButton);

    cancelButton.setText(Messages.getString("PrinterDialog.cancel"));
    cancelButton.addActionListener(e -> dispose());
  }

  private void manageAuth() {
    AuthenticationEditor dialog = new AuthenticationEditor(this, comboBoxAuth);
    JMVUtils.showCenterScreen(dialog);
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
