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

import javax.swing.JButton;
import javax.swing.JComboBox;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.pref.LauncherPrefView;
import org.weasis.dicom.explorer.Messages;

public class DicomNodeListView extends AbstractItemDialogPage {

  public DicomNodeListView() {
    super(Messages.getString("DicomNodeListView.node_list"), 605);
    initGUI();
  }

  private void initGUI() {
    buildPanel(AbstractDicomNode.Type.DICOM_CALLING);
    buildPanel(AbstractDicomNode.Type.DICOM);
    buildPanel(AbstractDicomNode.Type.WEB);
    // buildPanel(AbstractDicomNode.Type.WEB_QIDO);

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  private void buildPanel(AbstractDicomNode.Type nodeType) {
    final JComboBox<AbstractDicomNode> nodeComboBox = new JComboBox<>();
    AbstractDicomNode.loadDicomNodes(nodeComboBox, nodeType);
    AbstractDicomNode.addTooltipToComboList(nodeComboBox);
    GuiUtils.setPreferredWidth(nodeComboBox, 270, 150);
    JButton editButton = new JButton(Messages.getString("DicomNodeListView.edit"));
    JButton deleteButton = new JButton(Messages.getString("DicomNodeListView.delete"));
    JButton addNodeButton = new JButton(Messages.getString("DicomNodeListView.add_new"));
    deleteButton.addActionListener(_ -> AbstractDicomNode.deleteNodeActionPerformed(nodeComboBox));
    editButton.addActionListener(_ -> AbstractDicomNode.editNodeActionPerformed(nodeComboBox));
    addNodeButton.addActionListener(
        e -> AbstractDicomNode.addNodeActionPerformed(nodeComboBox, nodeType));

    add(
        LauncherPrefView.buildItem(
            nodeType.toString(), nodeComboBox, editButton, deleteButton, addNodeButton));

    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
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
