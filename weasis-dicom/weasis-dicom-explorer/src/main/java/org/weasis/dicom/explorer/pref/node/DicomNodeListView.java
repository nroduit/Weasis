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

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.pref.LauncherPrefView;
import org.weasis.dicom.explorer.Messages;

public class DicomNodeListView extends AbstractItemDialogPage {

  public DicomNodeListView() {
    super(Messages.getString("DicomNodeListView.node_list"), 605);
    getProperties()
        .setProperty(AbstractItemDialogPage.KEY_SCOPE, AbstractItemDialogPage.SCOPE_USER);
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
    nodeComboBox.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setText(value instanceof AbstractDicomNode node ? displayName(node) : "");
            return this;
          }
        });
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

    // Only the user's own nodes can be changed: the site ones are read-only
    Runnable enableActions =
        () -> {
          boolean local =
              nodeComboBox.getSelectedItem() instanceof AbstractDicomNode node && node.isLocal();
          editButton.setEnabled(local);
          deleteButton.setEnabled(local);
        };
    nodeComboBox.addActionListener(e -> enableActions.run());
    enableActions.run();

    add(
        LauncherPrefView.buildItem(
            nodeType.toString(), nodeComboBox, editButton, deleteButton, addNodeButton));
    if (nodeType == AbstractDicomNode.Type.DICOM_CALLING) {
      // A calling node names this workstation (AE title, listener port): it never roams
      JLabel scope = new JLabel(Messages.getString("DicomNodeListView.calling_scope"));
      scope.setEnabled(false);
      add(GuiUtils.getFlowLayoutPanel(scope));
    }

    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
  }

  /** The description with where the node comes from, and whether the site locked it. */
  static String displayName(AbstractDicomNode node) {
    String description = String.valueOf(node.getDescription());
    if (node.isLocal()) {
      return description;
    }
    String suffix = Origin.SITE.displayName();
    if (node.isLocked()) {
      suffix += ", " + Origin.lockedName();
    }
    return description + " (" + suffix + ")";
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
