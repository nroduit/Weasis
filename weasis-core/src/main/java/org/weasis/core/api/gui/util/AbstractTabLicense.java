/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.Locale;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.PlainDocument;

import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;

import net.miginfocom.swing.MigLayout;

public class AbstractTabLicense extends JPanel implements Insertable {

  private static final int TOTAL_LICENSE_REGISTER_TASKS = 9;

  protected final JTextArea codeTextField;
  protected final JTextField serverTextField;
  protected final JTextField bootJarTextField;
  protected final JScrollPane textScroll;
  protected final LicenseController licenseController;

  protected final JButton saveButton;
  protected final JButton cancelButton;
  protected final JButton testButton;
  protected final GUIEntry guiEntry;
  private JLabel versionLabel;
  private JLabel versionContents;
  private JPanel jPanelMiddle;
  private JLabel progressMessageLabel;
  private JProgressBar progressBar;

  private int totalTasks = TOTAL_LICENSE_REGISTER_TASKS;
  private int currentTask = 0;

  public AbstractTabLicense(GUIEntry entry) {
    this(entry, null);
  }

  public AbstractTabLicense(GUIEntry entry, LicenseController licenseController) {
    this.guiEntry = entry;
    this.codeTextField = new JTextArea();
    this.textScroll = new JScrollPane(codeTextField);
    this.serverTextField = new JTextField();
    this.bootJarTextField = new JTextField();

    codeTextField.setLineWrap(false);
    codeTextField.setDocument(new PlainDocument());

    saveButton = new JButton(Messages.getString("license.btnSave"));
    cancelButton = new JButton(Messages.getString("license.btnCancel"));
    testButton = new JButton(Messages.getString("license.btnTest"));
    versionLabel = new JLabel(Messages.getString("license.version"));
    versionContents = new JLabel();

    this.licenseController =
        Objects.requireNonNullElseGet(
            licenseController,
            () ->
                new LicenseDialogController(
                    codeTextField.getDocument(),
                    bootJarTextField.getDocument(),
                    this));

    initGUI();
  }

  protected void initGUI() {
    setBorder(GuiUtils.getEmptyBorder(10));
    setLayout(new BorderLayout());

    DefaultButtonModel newModel = new DefaultButtonModel();
    newModel.setActionCommand(LicenseDialogController.TEST_COMMAND);
    newModel.addActionListener(
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            progressBar.setMaximum(totalTasks);
            licenseController.test();
          }
        });
    testButton.setModel(newModel);

    newModel = new DefaultButtonModel();
    newModel.setActionCommand(LicenseDialogController.OK_COMMAND);
    newModel.addActionListener(
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (currentTask != 0) { // if the test was already executed
              totalTasks = TOTAL_LICENSE_REGISTER_TASKS + 1;
            }
            progressBar.setMaximum(totalTasks);
            licenseController.save();
          }
        });
    saveButton.setModel(newModel);

    newModel = new DefaultButtonModel();
    newModel.setActionCommand(LicenseDialogController.CANCEL_COMMAND);
    newModel.setEnabled(false);
    newModel.addActionListener(
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            licenseController.cancel();
          }
        });
    cancelButton.setModel(newModel);

    JPanel jPanelServer = new JPanel();
    jPanelServer.setLayout(
        new MigLayout("insets 25lp 10lp 25lp 10lp", "[right]rel[grow,fill]")); // NON-NLS
    jPanelServer.add(new JLabel(Messages.getString("license.server.address")));
    jPanelServer.add(serverTextField);
    final ButtonGroup ratioGroup = new ButtonGroup();
    JRadioButton codeButton = new JRadioButton(Messages.getString("license.activation.code.button"));
    JRadioButton serverButton = new JRadioButton(Messages.getString("license.server.button"));
    final JPanel jPanelTop =
        GuiUtils.getFlowLayoutPanel(
            Insertable.BLOCK_SEPARATOR, Insertable.ITEM_SEPARATOR, codeButton, serverButton);
    ratioGroup.add(codeButton);
    ratioGroup.add(serverButton);
    codeButton.setSelected(true);
    codeButton.addActionListener(
        e -> {
          if (e.getSource() instanceof JRadioButton btn && btn.isSelected()) {
            remove(jPanelServer);
            add(jPanelMiddle, BorderLayout.CENTER);
            revalidate();
            repaint();
          }
        });
    serverButton.addActionListener(
        e -> {
          if (e.getSource() instanceof JRadioButton btn && btn.isSelected()) {
            remove(jPanelMiddle);
            add(jPanelServer, BorderLayout.CENTER);
            
            revalidate();
            repaint();
          }
        });
    add(jPanelTop, BorderLayout.NORTH);

    jPanelMiddle = new JPanel(new BorderLayout());
    textScroll.setPreferredSize(GuiUtils.getDimension(530, 300));
    JPanel jPanelBootJarAddress = new JPanel();
    jPanelBootJarAddress.setLayout(
        new MigLayout("insets 25lp 10lp 25lp 10lp", "[right]rel[grow,fill]")); // NON-NLS
    jPanelBootJarAddress.add(new JLabel(Messages.getString("license.boot.jar.address")));
    jPanelBootJarAddress.add(bootJarTextField);
    jPanelMiddle.add(jPanelBootJarAddress, BorderLayout.NORTH);
    jPanelMiddle.add(textScroll, BorderLayout.CENTER);
    JPanel jPanelProgressMessage = new JPanel();
    jPanelProgressMessage.setLayout(new BorderLayout()); // NON-NLS
    progressMessageLabel = new JLabel(" ");
    progressBar = new JProgressBar();
    jPanelProgressMessage.add(progressMessageLabel, BorderLayout.NORTH);
    jPanelProgressMessage.add(progressBar, BorderLayout.CENTER);
    jPanelMiddle.add(jPanelProgressMessage, BorderLayout.SOUTH);
    add(jPanelMiddle, BorderLayout.CENTER);

    JPanel jPanelBottom =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            15,
            5,
            testButton,
            saveButton,
            cancelButton,
            versionLabel,
            versionContents);
    add(jPanelBottom, BorderLayout.SOUTH);
  }

  public void setVersionContents(String versionContents) {
    this.versionContents.setText(versionContents);
  }

  public String getVersionContents() {
    return this.versionContents.getText();
  }

  public JButton getCancelButton() {
    return cancelButton;
  }

  public JButton getSaveButton() {
    return saveButton;
  }

  public JButton getTestButton() {
      return testButton;
  }

  public GUIEntry getGuiEntry() {
    return guiEntry;
  }

  public String getPluginName() {
    return guiEntry.getUIName().toLowerCase(Locale.US);
  }

  public String getLicenseName() {
    return guiEntry.getUIName().toLowerCase(Locale.US);
  }

  @Override
  public String getComponentName() {
    return guiEntry.getUIName();
  }

  @Override
  public Type getType() {
    return Type.LICENSE;
  }

  @Override
  public boolean isComponentEnabled() {
    return true;
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    // Do nothing
  }
  

  @Override
  public int getComponentPosition() {
    return 0;
  }

  @Override
  public void setComponentPosition(int position) {
    // Do nothing
  }

  public void endProcessing() {
    saveButton.getModel().setEnabled(true);
    testButton.getModel().setEnabled(true);
    cancelButton.getModel().setEnabled(false);
    codeTextField.setEnabled(true);
    bootJarTextField.setEnabled(true);
  }

  public void startProcessing() {
    saveButton.getModel().setEnabled(false);
    testButton.getModel().setEnabled(false);
    cancelButton.getModel().setEnabled(true);
    codeTextField.setEnabled(false);
    bootJarTextField.setEnabled(false);
  }

  public void logProgress(String message) {
    currentTask++;
    progressBar.setValue(currentTask);
    progressMessageLabel.setText(currentTask + " of " + totalTasks + ": " + message);
  }

  public void close() {
    // in case we just tested, we can close close stuff we opened when executing the 
    // plugin register process
    if (currentTask < TOTAL_LICENSE_REGISTER_TASKS) { 
      licenseController.close();
    }
  }
}
