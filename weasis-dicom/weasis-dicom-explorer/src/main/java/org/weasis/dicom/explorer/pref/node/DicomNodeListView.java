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

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
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

    add(GuiUtils.getBoxYLastElement(LAST_FILLER_HEIGHT));
  }

  private void buildPanel(AbstractDicomNode.Type nodeType) {
    JLabel label1 = new JLabel(Messages.getString("DicomNodeListView.node") + StringUtil.COLON);
    final JComboBox<AbstractDicomNode> nodeComboBox = new JComboBox<>();
    AbstractDicomNode.loadDicomNodes(nodeComboBox, nodeType);
    AbstractDicomNode.addTooltipToComboList(nodeComboBox);
    GuiUtils.setPreferredWidth(nodeComboBox, 270, 150);
    JButton editButton = new JButton(Messages.getString("DicomNodeListView.edit"));
    JButton deleteButton = new JButton(Messages.getString("DicomNodeListView.delete"));
    JButton addNodeButton = new JButton(Messages.getString("DicomNodeListView.add_new"));
    deleteButton.addActionListener(e -> AbstractDicomNode.deleteNodeActionPerformed(nodeComboBox));
    editButton.addActionListener(e -> AbstractDicomNode.editNodeActionPerformed(nodeComboBox));
    addNodeButton.addActionListener(
        e -> AbstractDicomNode.addNodeActionPerformed(nodeComboBox, nodeType));

    JPanel panel = new JPanel();
    panel.setBorder(GuiUtils.getTitledBorder(nodeType.toString()));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(
        GuiUtils.getComponentsInJPanel(
            ITEM_SEPARATOR_SMALL,
            ITEM_SEPARATOR,
            label1,
            nodeComboBox,
            GuiUtils.createHorizontalStrut(BLOCK_SEPARATOR),
            editButton,
            GuiUtils.createHorizontalStrut(ITEM_SEPARATOR_LARGE),
            deleteButton));
    panel.add(GuiUtils.getComponentsInJPanel(ITEM_SEPARATOR, ITEM_SEPARATOR, addNodeButton));
    add(panel);

    add(GuiUtils.createVerticalStrut(BLOCK_SEPARATOR));
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
