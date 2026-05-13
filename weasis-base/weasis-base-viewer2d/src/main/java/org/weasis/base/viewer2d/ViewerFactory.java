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

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.MediaFactory;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
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
  public SeriesViewer<?> createSeriesViewer(ViewerOpenOptions options, DataExplorerModel model) {
    ComboItemListener<MigLayoutModel> layoutAction =
        EventManager.getInstance().getAction(ActionW.LAYOUT).orElse(null);
    LayoutModel layout =
        ImageViewerPlugin.getLayoutModel(options, ImageViewerPlugin.VIEWS_1x1, layoutAction);
    View2dContainer instance = new View2dContainer(layout.model(), layout.uid());
    ImageViewerPlugin.registerInDataExplorerModel(model, instance);

    return instance;
  }

  public static int getViewTypeNumber(MigLayoutModel layout, Class<?> defaultClass) {
    int val = 0;
    if (layout != null && defaultClass != null) {
      for (MigCell cell : layout.getCells()) {
        try {
          Class<?> clazz = Class.forName(cell.type());
          if (defaultClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          LOGGER.error("Checking view", e);
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
    if (!GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.import.images", true)) {
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
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    String directory = localPersistence.getProperty("last.open.image.dir", ""); // NON-NLS
    SystemFileChooser fileChooser = new SystemFileChooser(directory);

    fileChooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(true);

    setImageDecodeFilters(fileChooser);
    File[] selectedFiles;
    if (fileChooser.showOpenDialog(GuiUtils.getUICore().getApplicationWindow())
            != SystemFileChooser.APPROVE_OPTION
        || (selectedFiles = fileChooser.getSelectedFiles()) == null) {
      return;
    } else {
      MediaSeries series = null;
      for (File file : selectedFiles) {
        String mimeType = MimeInspector.getMimeType(file.toPath());
        if (mimeType != null && mimeType.startsWith("image")) {
          Codec<?> codec = BundleTools.getCodec(mimeType, null);
          if (codec != null) {
            MediaReader<?> reader = codec.getMediaIO(file.toURI(), mimeType, null);
            if (reader != null) {
              if (series == null) {
                // TODO improve group model for image, uid for group ?
                series = reader.getMediaSeries();
                MediaElement[] elements = reader.getMediaElement();
                if (elements != null) {
                  for (MediaElement media : elements) {
                    MediaFactory.openAssociatedGraphics(media);
                  }
                }
              } else {
                MediaElement[] elements = reader.getMediaElement();
                if (elements != null) {
                  for (MediaElement media : elements) {
                    series.addMedia(media);
                    MediaFactory.openAssociatedGraphics(media);
                  }
                }
              }
            }
          }
        }
      }

      if (series != null && series.size(null) > 0) {
        ViewerPluginBuilder.openInDefaultViewer(
            series,
            ViewerPluginBuilder.DefaultDataModel,
            ViewerOpenOptions.builder()
                .placement(ViewerPlacement.reuseViewer(false, false))
                .build());
      } else {
        JOptionPane.showMessageDialog(
            GuiUtils.getUICore().getApplicationWindow(),
            Messages.getString("OpenImageAction.error_open_msg"),
            Messages.getString("OpenImageAction.open_img"),
            JOptionPane.WARNING_MESSAGE);
      }
      localPersistence.setProperty("last.open.image.dir", selectedFiles[0].getParent());
    }
  }

  @Override
  public boolean canReadSeries(MediaSeries<?> series) {
    return series != null && series.size(null) > 0;
  }

  private static void setImageDecodeFilters(SystemFileChooser chooser) {
    // Get the current available codecs.
    List<String> namesList =
        GuiUtils.getUICore().getCodecPlugins().stream()
            .filter(c -> c.getCodecName().contains("OpenCV"))
            .flatMap(c -> Arrays.stream(c.getReaderExtensions()))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    Iterator<String> it = namesList.iterator();
    String desc = org.weasis.core.Messages.getString("FileFormatFilter.all_supported");
    ArrayList<String> names = new ArrayList<>();
    while (it.hasNext()) {
      String name = it.next();
      names.add(name);
    }

    FileNameExtensionFilter imageFilter =
        new FileNameExtensionFilter(desc, names.toArray(new String[0]));
    chooser.addChoosableFileFilter(imageFilter);
    it = namesList.iterator();
    while (it.hasNext()) {
      String name = it.next();
      desc = name.toUpperCase();
      FileNameExtensionFilter filter = new FileNameExtensionFilter(desc, name);
      chooser.addChoosableFileFilter(filter);
    }
    chooser.setAcceptAllFileFilterUsed(true);
    chooser.setFileFilter(imageFilter);
  }
}
