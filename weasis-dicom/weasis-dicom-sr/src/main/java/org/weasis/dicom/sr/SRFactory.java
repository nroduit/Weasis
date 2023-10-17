/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.util.Map;
import javax.swing.Icon;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.dicom.explorer.DicomExplorer;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class SRFactory implements SeriesViewerFactory {

  public static final String NAME = Messages.getString("SRFactory.viewer");

  public SRFactory() {
    super();
  }

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(FileIcon.TEXT);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return Messages.getString("SRFactory.sr");
  }

  @Override
  public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
    LayoutModel layout = ImageViewerPlugin.getLayoutModel(properties, SRContainer.VIEWS_SR, null);
    SRContainer instance = new SRContainer(layout.model(), layout.uid());
    ImageViewerPlugin.registerInDataExplorerModel(properties, instance);
    return instance;
  }

  public static void closeSeriesViewer(SRContainer mprContainer) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(mprContainer);
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return SRElementFactory.SERIES_SR_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof SRContainer;
  }

  @Override
  public int getLevel() {
    return 25;
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
    GuiUtils.getUICore().closeSeriesViewerType(SRContainer.class);
  }
}
