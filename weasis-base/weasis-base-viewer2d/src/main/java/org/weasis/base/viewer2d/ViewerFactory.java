/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.DefaultAction;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class ViewerFactory implements SeriesViewerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ViewerFactory.class);

  public static final String NAME = Messages.getString("ViewerFactory.img_viewer");

  private static final DefaultAction preferencesAction =
      new DefaultAction(
          Messages.getString("OpenImageAction.img"),
          ResourceUtil.getIcon(ActionIcon.IMPORT_IMAGE),
          ViewerFactory::getOpenImageAction);

  public ViewerFactory() {
    super();
  }

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(OtherIcon.RASTER_IMAGE);
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
  public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
    GridBagLayoutModel model = ImageViewerPlugin.VIEWS_1x1;
    String uid = null;
    if (properties != null) {
      Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
      if (obj instanceof GridBagLayoutModel gridBagLayoutModel) {
        model = gridBagLayoutModel;
      } else {
        obj = properties.get(ViewCanvas.class.getName());
        if (obj instanceof Integer intVal) {
          Optional<ComboItemListener<GridBagLayoutModel>> layout =
              EventManager.getInstance().getAction(ActionW.LAYOUT);
          if (layout.isPresent()) {
            model = ImageViewerPlugin.getBestDefaultViewLayout(layout.get(), intVal);
          }
        }
      }
      // Set UID
      Object val = properties.get(ViewerPluginBuilder.UID);
      if (val instanceof String s) {
        uid = s;
      }
    }
    View2dContainer instance = new View2dContainer(model, uid);
    if (properties != null) {
      Object obj = properties.get(DataExplorerModel.class.getName());
      if (obj instanceof DataExplorerModel m) {
        // Register the PropertyChangeListener
        m.addPropertyChangeListener(instance);
      }
    }

    return instance;
  }

  public static int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    int val = 0;
    if (layout != null && defaultClass != null) {
      Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
      while (enumVal.hasNext()) {
        try {
          Class<?> clazz = Class.forName(enumVal.next().getType());
          if (defaultClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          LOGGER.error("Checking view type", e);
        }
      }
    }
    return val;
  }

  public static void closeSeriesViewer(View2dContainer view2dContainer) {
    // Unregister the PropertyChangeListener
    ViewerPluginBuilder.DefaultDataModel.removePropertyChangeListener(view2dContainer);
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return mimeType != null && mimeType.startsWith("image/"); // NON-NLS
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof View2dContainer;
  }

  @Override
  public int getLevel() {
    return 5;
  }

  @Override
  public List<Action> getOpenActions() {
    if (!BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.images", true)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(preferencesAction);
  }

  @Override
  public boolean canAddSeries() {
    return true;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  static void getOpenImageAction(ActionEvent e) {
    String directory =
        BundleTools.LOCAL_UI_PERSISTENCE.getProperty("last.open.image.dir", ""); // NON-NLS
    JFileChooser fileChooser = new JFileChooser(directory);

    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(true);

    FileFormatFilter.setImageDecodeFilters(fileChooser);
    File[] selectedFiles;
    if (fileChooser.showOpenDialog(UIManager.getApplicationWindow()) != JFileChooser.APPROVE_OPTION
        || (selectedFiles = fileChooser.getSelectedFiles()) == null) {
      return;
    } else {
      MediaSeries<MediaElement> series = null;
      for (File file : selectedFiles) {
        String mimeType = MimeInspector.getMimeType(file);
        if (mimeType != null && mimeType.startsWith("image")) {
          Codec codec = BundleTools.getCodec(mimeType, null);
          if (codec != null) {
            MediaReader reader = codec.getMediaIO(file.toURI(), mimeType, null);
            if (reader != null) {
              if (series == null) {
                // TODO improve group model for image, uid for group ?
                series = reader.getMediaSeries();
                MediaElement[] elements = reader.getMediaElement();
                if (elements != null) {
                  for (MediaElement media : elements) {
                    ViewerPluginBuilder.openAssociatedGraphics(media);
                  }
                }
              } else {
                MediaElement[] elements = reader.getMediaElement();
                if (elements != null) {
                  for (MediaElement media : elements) {
                    series.addMedia(media);
                    ViewerPluginBuilder.openAssociatedGraphics(media);
                  }
                }
              }
            }
          }
        }
      }

      if (series != null && series.size(null) > 0) {
        ViewerPluginBuilder.openSequenceInDefaultPlugin(
            series, ViewerPluginBuilder.DefaultDataModel, true, false);
      } else {
        JOptionPane.showMessageDialog(
            e.getSource() instanceof Component c ? c : null,
            Messages.getString("OpenImageAction.error_open_msg"),
            Messages.getString("OpenImageAction.open_img"),
            JOptionPane.WARNING_MESSAGE);
      }
      BundleTools.LOCAL_UI_PERSISTENCE.setProperty(
          "last.open.image.dir", selectedFiles[0].getParent());
    }
  }
}
