/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.weasis.core.ui.editor.ViewerPluginBuilder.BEST_DEF_LAYOUT;
import static org.weasis.core.ui.editor.ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER;
import static org.weasis.core.ui.editor.ViewerPluginBuilder.SCREEN_BOUND;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class MprFactory implements SeriesViewerFactory {

  public static final String NAME = Messages.getString("oblique.mpr");
  public static final String P_DEFAULT_LAYOUT = "mpr.default.layout";

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(OtherIcon.VIEW_3D);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  public static GridBagLayoutModel getDefaultGridBagLayoutModel() {
    String defLayout =
        GuiUtils.getUICore().getSystemPreferences().getProperty(MprFactory.P_DEFAULT_LAYOUT);
    if (StringUtil.hasText(defLayout)) {
      return MprContainer.LAYOUT_LIST.stream()
          .filter(g -> defLayout.equals(g.getId()))
          .findFirst()
          .orElse(MprContainer.view1);
    }
    return MprContainer.view1;
  }

  @Override
  public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
    LayoutModel layout =
        ImageViewerPlugin.getLayoutModel(properties, getDefaultGridBagLayoutModel(), null);
    MprContainer instance = new MprContainer(layout.model(), layout.uid());
    ImageViewerPlugin.registerInDataExplorerModel(properties, instance);
    int index = 0;
    for (Component val : layout.model().getConstraints().values()) {
      if (val instanceof MprView mprView) {
        SliceOrientation sliceOrientation =
            switch (index) {
              case 1 -> SliceOrientation.CORONAL;
              case 2 -> SliceOrientation.SAGITTAL;
              default -> SliceOrientation.AXIAL;
            };
        mprView.setType(sliceOrientation);
        index++;
      }
    }
    return instance;
  }

  public static void closeSeriesViewer(MprContainer mprContainer) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(mprContainer);
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof MprContainer;
  }

  @Override
  public int getLevel() {
    return 15;
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
    return series != null && series.isSuitableFor3d();
  }

  public static ActionListener getMprAction(MediaSeries<DicomImageElement> series) {
    return e -> {
      SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(MprFactory.class);
      MediaSeries<DicomImageElement> s = series;
      if (s == null) {
        s = EventManager.getInstance().getSelectedSeries();
      }
      if (factory != null && factory.canReadSeries(s)) {
        Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
        props.put(CMP_ENTRY_BUILD_NEW_VIEWER, false);
        props.put(BEST_DEF_LAYOUT, false);
        props.put(SCREEN_BOUND, null);
        ArrayList<MediaSeries<? extends MediaElement>> list = new ArrayList<>(1);
        list.add(s);
        ViewerPluginBuilder builder =
            new ViewerPluginBuilder(
                factory, list, (DataExplorerModel) s.getTagValue(TagW.ExplorerModel), props);
        ViewerPluginBuilder.openSequenceInPlugin(builder);
      }
    };
  }
}
