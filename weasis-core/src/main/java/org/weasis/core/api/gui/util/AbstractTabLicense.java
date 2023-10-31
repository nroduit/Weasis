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
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.PlainDocument;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.LicenseDialogController.STATUS;

public class AbstractTabLicense extends JPanel implements Insertable {
  protected final JTextArea codeTextField;
  protected final JTextField serverTextField;
  protected final JScrollPane textScroll;
  protected final LicenseController licenseController;

  protected final JButton saveButton;
  protected final JButton cancelButton;
  protected final JButton testButton;
  protected final GUIEntry guiEntry;
  private JLabel versionLabel;
  private JLabel versionContents;

  public AbstractTabLicense(GUIEntry entry) {
    this(entry, null);
  }

  public AbstractTabLicense(GUIEntry entry, LicenseController licenseController) {
    this.guiEntry = entry;
    this.codeTextField = new JTextArea();
    this.textScroll = new JScrollPane(codeTextField);
    this.serverTextField = new JTextField();

    codeTextField.setLineWrap(false);
    codeTextField.setDocument(new PlainDocument());

    this.licenseController =
        Objects.requireNonNullElseGet(
            licenseController,
            () ->
                new LicenseDialogController(
                    codeTextField.getDocument(),
                    serverTextField.getDocument(),
                    this,
                    s -> {
                      if (s == STATUS.START_PROCESSING) {
                        codeTextField.setEnabled(false);
                      } else if (s == STATUS.END_PROCESSING) {
                        codeTextField.setEnabled(true);
                      }
                    }));

    saveButton = new JButton(Messages.getString("license.btnSave"));
    cancelButton = new JButton(Messages.getString("license.btnCancel"));
    testButton = new JButton(Messages.getString("license.btnTest"));
    versionLabel = new JLabel(Messages.getString("license.version"));
    versionContents = new JLabel();
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
            licenseController.save();
          }
        });
    saveButton.setModel(newModel);

    newModel = new DefaultButtonModel();
    newModel.setActionCommand(LicenseDialogController.CANCEL_COMMAND);
    cancelButton.setModel(newModel);

    JPanel jPanelServer = new JPanel();
    jPanelServer.setLayout(
        new MigLayout("insets 25lp 10lp 25lp 10lp", "[right]rel[grow,fill]")); // NON-NLS
    jPanelServer.add(new JLabel("Server address"));
    jPanelServer.add(serverTextField);
    final ButtonGroup ratioGroup = new ButtonGroup();
    JRadioButton codeButton = new JRadioButton("Activation Code");
    JRadioButton serverButton = new JRadioButton("License Server");
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
            add(textScroll, BorderLayout.CENTER);
            revalidate();
            repaint();
          }
        });
    serverButton.addActionListener(
        e -> {
          if (e.getSource() instanceof JRadioButton btn && btn.isSelected()) {
            remove(textScroll);
            add(jPanelServer, BorderLayout.CENTER);
            revalidate();
            repaint();
          }
        });

    add(jPanelTop, BorderLayout.NORTH);
    textScroll.setPreferredSize(GuiUtils.getDimension(530, 300));
    add(textScroll, BorderLayout.CENTER);
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
}
