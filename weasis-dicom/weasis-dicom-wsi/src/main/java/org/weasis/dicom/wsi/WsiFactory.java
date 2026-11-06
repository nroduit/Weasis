/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import javax.swing.Icon;
import org.pf4j.Extension;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.plugin.SingletonExtension;

/**
 * Opens a whole slide series in the slide viewer. It reads the image series type at a lower
 * priority than the 2D viewer and accepts only VL Whole Slide Microscopy series, so it is offered
 * for them and opened with {@code dicom:open --viewer wsi}.
 */
@Extension
@SingletonExtension
public class WsiFactory implements SeriesViewerFactory {

  public static final String NAME = Messages.getString("WsiFactory.name");

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.ZOOM);
  }

  @Override
  public String getViewerTypeId() {
    return "wsi"; // NON-NLS
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  @Override
  public SeriesViewer<? extends MediaElement> createSeriesViewer(
      ViewerOpenOptions options, DataExplorerModel model) {
    LayoutModel layout = ImageViewerPlugin.getLayoutModel(options, WsiContainer.DEFAULT_VIEW, null);
    WsiContainer instance = new WsiContainer(layout.model(), layout.uid());
    ImageViewerPlugin.registerInDataExplorerModel(model, instance);
    return instance;
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_WSI_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof WsiContainer;
  }

  @Override
  public int getLevel() {
    return 40;
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
}
