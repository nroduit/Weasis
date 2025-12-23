/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.List;
import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.explorer.exp.ExportToolBar;
import org.weasis.dicom.explorer.imp.ImportToolBar;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class DicomExplorerFactory implements DataExplorerViewFactory {

  private DicomExplorer explorer = null;
  private DicomModel model;

  @Override
  public DataExplorerView createDataExplorerView(Map<String, Object> properties) {
    if (explorer == null) {
      // Get DicomModel instance - implementation depends on how DicomModel is registered
      if (model == null) {
        model = getDicomModel();
      }

      explorer = new DicomExplorer(model);
      model.addPropertyChangeListener(explorer);
      List<Toolbar> toolbar = GuiUtils.getUICore().getExplorerPluginToolbars();
      toolbar.add(new ImportToolBar(5, explorer));
      toolbar.add(new ExportToolBar(7, explorer));
      ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.NULL_SELECTION, explorer, null, null));
    }
    return explorer;
  }

  private DicomModel getDicomModel() {
    List<?> extensions = GuiUtils.getUICore().getPluginManager().getExtensions(DicomModel.class);
    if (!extensions.isEmpty()) {
      return (DicomModel) extensions.getFirst();
    }
    return new DicomModel();
  }

  public void stop() {
    if (explorer != null) {
      DataExplorerModel dataModel = explorer.getDataExplorerModel();
      dataModel.removePropertyChangeListener(explorer);
      GuiUtils.getUICore()
          .getExplorerPluginToolbars()
          .removeIf(b -> b.getComponent().getAttachedInsertable() == explorer);
    }
  }
}
