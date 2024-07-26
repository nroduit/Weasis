/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventObject;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.ref.AnatomicItem;
import org.weasis.dicom.ref.AnatomicRegion;

public class AnatomicRegionCellEditor extends AbstractCellEditor
    implements TableCellEditor, ActionListener {
  private final JButton buttonOpen;
  private final AcquireImageInfo imageInfo;
  private AnatomicRegion currentValue;

  public AnatomicRegionCellEditor(AcquireImageInfo imageInfo) {
    this.imageInfo = Objects.requireNonNull(imageInfo);
    this.currentValue = (AnatomicRegion) imageInfo.getImage().getTagValue(TagW.AnatomicRegion);
    this.buttonOpen = new JButton();
    buttonOpen.setOpaque(true);
    buttonOpen.addActionListener(this);
  }

  @Override
  public Object getCellEditorValue() {
    return currentValue;
  }

  @Override
  public Component getTableCellEditorComponent(
      JTable table, Object value, boolean isSelected, int row, int column) {
    currentValue = (AnatomicRegion) value;
    return buttonOpen;
  }

  @Override
  public boolean isCellEditable(EventObject anEvent) {
    return true;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AnatomicRegionView regionView =
        new AnatomicRegionView(
            currentValue, imageInfo.getSeries().getTagValue(TagW.AnatomicRegion) != null);
    // ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(buttonOpen);
    int res =
        JOptionPane.showConfirmDialog(
            buttonOpen, regionView, buttonOpen.getText(), JOptionPane.OK_CANCEL_OPTION);
    //    if (layer != null) {
    //      layer.hideUI();
    //    }
    if (res == JOptionPane.OK_OPTION) {
      AnatomicItem item = regionView.getSelectedAnatomicItem();
      this.currentValue =
          item == null
              ? null
              : new AnatomicRegion(
                  regionView.getSelectedCategory(), item, regionView.getModifiers());
      if (regionView.isApplyingToSeries()) {
        SeriesGroup seriesGroup = imageInfo.getSeries();
        if (seriesGroup != null) {
          for (AcquireImageInfo info : AcquireManager.findBySeries(seriesGroup)) {
            info.getImage().setTag(TagW.AnatomicRegion, currentValue);
          }
        }
      } else {
        imageInfo.getImage().setTag(TagW.AnatomicRegion, currentValue);
      }
    }
    if (stopCellEditing()) {
      fireEditingStopped();
    } else {
      fireEditingCanceled();
    }
    JTable table = WinUtil.getParentOfClass(buttonOpen, JTable.class);
    if (table != null) {
      table.removeEditor();
    }
  }
}
