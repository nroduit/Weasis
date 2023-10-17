/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import javax.swing.Icon;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;

public abstract class DicomViewerPlugin extends ImageViewerPlugin<DicomImageElement> {

  protected DicomViewerPlugin(
      ImageViewerEventManager<DicomImageElement> eventManager, String pluginName) {
    super(eventManager, pluginName);
  }

  protected DicomViewerPlugin(
      ImageViewerEventManager<DicomImageElement> eventManager,
      GridBagLayoutModel layoutModel,
      String uid,
      String pluginName,
      Icon icon,
      String tooltips) {
    super(eventManager, layoutModel, uid, pluginName, icon, tooltips);
  }

  @Override
  public void setSelected(boolean selected) {
    if (selected) {
      if (eventManager.getSelectedView2dContainer() != this) {
        eventManager.setSelectedView2dContainer(this);
      }
      // Send event to select the related patient in Dicom Explorer.
      DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
      if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel model) {
        model.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.SELECT, this, null, getGroupID()));
      }
    } else {
      eventManager.setSelectedView2dContainer(null);
    }
  }
}
