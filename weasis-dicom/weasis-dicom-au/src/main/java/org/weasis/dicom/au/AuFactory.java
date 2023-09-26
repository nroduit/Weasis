/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.au;

import java.util.Map;
import javax.swing.Icon;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.dicom.explorer.DicomExplorer;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class AuFactory implements SeriesViewerFactory {

  public static final String NAME = "DICOM AU"; // NON-NLS

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(OtherIcon.AUDIO);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return Messages.getString("AuFactory.dcm_audio");
  }

  @Override
  public SeriesViewer createSeriesViewer(Map<String, Object> properties) {
    LayoutModel layout =
        ImageViewerPlugin.getLayoutModel(properties, AuContainer.DEFAULT_VIEW, null);
    AuContainer instance = new AuContainer(layout.model(), layout.uid());
    ImageViewerPlugin.registerInDataExplorerModel(properties, instance);

    // Close all the other audio views
    GuiUtils.getUICore().closeSeriesViewerType(AuContainer.class);

    return instance;
  }

  public static void closeSeriesViewer(AuContainer container) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(container);
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return AuElementFactory.SERIES_AU_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
    return viewer instanceof AuContainer;
  }

  @Override
  public int getLevel() {
    return 35;
  }

  @Override
  public boolean canAddSeries() {
    return false;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  @Override
  public boolean canReadSeries(MediaSeries<?> series) {
    return series != null;
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Deactivate
  protected void deactivate(ComponentContext context) {
    GuiUtils.getUICore().closeSeriesViewerType(AuContainer.class);
  }
}
