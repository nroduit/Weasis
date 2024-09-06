/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.launcher.Launcher;

public class LauncherPrefView extends AbstractItemDialogPage {

  public static final String PAGE_NAME = Messages.getString("launcher");

  public LauncherPrefView() {
    super(PAGE_NAME, 120);
    initGUI();
  }

  private void initGUI() {
    buildPanel(Launcher.Type.DICOM);
    buildPanel(Launcher.Type.OTHER);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "launcher-external"); // NON-NLS
  }

  private void buildPanel(Launcher.Type type) {
    final JComboBox<Launcher> nodeComboBox = new JComboBox<>();
    Launcher.loadLaunchers(nodeComboBox, type);
    Launcher.addTooltipToComboList(nodeComboBox);
    GuiUtils.setPreferredWidth(nodeComboBox, 270, 150);
    JButton editButton = new JButton(Messages.getString("edit"));
    JButton deleteButton = new JButton(Messages.getString("delete"));
    JButton addNodeButton = new JButton(Messages.getString("add.new"));
    deleteButton.addActionListener(_ -> Launcher.deleteNodeActionPerformed(nodeComboBox, type));
    editButton.addActionListener(_ -> Launcher.editNodeActionPerformed(nodeComboBox, type));
    addNodeButton.addActionListener(_ -> Launcher.addNodeActionPerformed(nodeComboBox, type));

    add(buildItem(type.toString(), nodeComboBox, editButton, deleteButton, addNodeButton));

    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
  }

  public static JPanel buildItem(
      String title,
      JComboBox<?> nodeComboBox,
      JButton editButton,
      JButton deleteButton,
      JButton addNodeButton) {
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getTitledBorder(title));
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            ITEM_SEPARATOR,
            ITEM_SEPARATOR,
            nodeComboBox,
            GuiUtils.boxHorizontalStrut(BLOCK_SEPARATOR),
            editButton,
            GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_LARGE),
            deleteButton));
    panel.add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR, ITEM_SEPARATOR, addNodeButton));
    return panel;
  }

  @Override
  public void closeAdditionalWindow() {
    // Do nothing
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }
}
