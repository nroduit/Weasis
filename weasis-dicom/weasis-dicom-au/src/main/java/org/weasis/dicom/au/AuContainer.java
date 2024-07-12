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

import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenu;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.pref.LauncherToolBar;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomExportAction;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;

public class AuContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuContainer.class);

  static final GridBagLayoutModel DEFAULT_VIEW =
      new GridBagLayoutModel(
          "1x1", // NON-NLS
          "1x1", // NON-NLS
          1,
          1,
          AuView.class.getName());
  private static final List<GridBagLayoutModel> LAYOUT_LIST = List.of(DEFAULT_VIEW);

  private static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE);

  public static final SeriesViewerUI UI = new SeriesViewerUI(AuContainer.class);

  static final ImageViewerEventManager<DicomImageElement> AU_EVENT_MANAGER =
      new ImageViewerEventManager<>() {

        @Override
        public boolean updateComponentsListener(ViewCanvas<DicomImageElement> defaultView2d) {
          // Do nothing
          return true;
        }

        @Override
        public void resetDisplay() {
          // Do nothing
        }

        @Override
        public void setSelectedView2dContainer(
            ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
          this.selectedView2dContainer = selectedView2dContainer;
        }

        @Override
        public void keyTyped(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyPressed(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyReleased(KeyEvent e) {
          // Do nothing
        }

        @Override
        public String resolvePlaceholders(String template) {
          return DicomExportAction.resolvePlaceholders(template, this);
        }
      };
  protected AuView auview;

  public AuContainer() {
    this(DEFAULT_VIEW, null);
  }

  public AuContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        AU_EVENT_MANAGER,
        layoutModel,
        uid,
        AuFactory.NAME,
        ResourceUtil.getIcon(OtherIcon.AUDIO),
        null);
    setSynchView(SynchView.NONE);

    if (!UI.init.getAndSet(true)) {
      List<Toolbar> toolBars = UI.toolBars;
      // Add standard toolbars
      final BundleContext context = AppProperties.getBundleContext(this.getClass());
      if (context == null) {
        LOGGER.error("Cannot get BundleContext");
        return;
      }
      String bundleName = context.getBundle().getSymbolicName();
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS
      WProperties preferences = GuiUtils.getUICore().getSystemPreferences();

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ImportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(ImportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(toolBars::add);
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ExportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(ExportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(toolBars::add);
      }

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(AuToolBar.class),
          key,
          true)) {
        toolBars.add(new AuToolBar(10));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(LauncherToolBar.class),
          key,
          true)) {
        toolBars.add(new LauncherToolBar(getEventManager(), 130));
      }
    }
  }

  @Override
  public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
    setSelectedImagePane(defaultView2d);
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    return menuRoot;
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  @Override
  public void close() {
    AuFactory.closeSeriesViewer(this);
    super.close();

    GuiExecutor.execute(
        () -> {
          if (auview != null) {
            auview.dispose();
          }
        });
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();

      if (ObservableEvent.BasicAction.REMOVE.equals(action)
          && newVal instanceof MediaSeriesGroup group) {
        // Patient Group
        if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
          if (group.equals(getGroupID())) {
            // Close the content of the plug-in
            close();
            handleFocusAfterClosing();
          }
        }
        // Study Group
        else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
          if (event.getSource() instanceof DicomModel model
              && auview != null
              && group.equals(model.getParent(auview.getSeries(), DicomModel.study))) {
            close();
            handleFocusAfterClosing();
          }
        }
        // Series Group
        else if (TagD.getUID(Level.SERIES).equals(group.getTagID())
            && auview != null
            && auview.getSeries() == newVal) {
          close();
          handleFocusAfterClosing();
        }
      }
    }
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    return 0;
  }

  @Override
  public boolean isViewType(Class<?> defaultClass, String type) {
    if (defaultClass != null) {
      try {
        Class<?> clazz = Class.forName(type);
        return defaultClass.isAssignableFrom(clazz);
      } catch (Exception e) {
        LOGGER.error("Checking view type", e);
      }
    }
    return false;
  }

  @Override
  public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
    return null;
  }

  @Override
  public JComponent createComponent(String clazz) {
    try {
      // FIXME use classloader.loadClass or injection
      JComponent component = buildInstance(Class.forName(clazz));
      if (component instanceof AuView view) {
        auview = view;
      }
      return component;
    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return AuView.class;
  }

  @Override
  public GridBagLayoutModel getDefaultLayoutModel() {
    return DEFAULT_VIEW;
  }

  public Series<?> getSeries() {
    if (auview != null) {
      return auview.getSeries();
    }
    return null;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    if (auview != null && sequence instanceof Series series && auview.getSeries() != sequence) {
      auview.setSeries(series);
    }
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    if (seriesList != null && !seriesList.isEmpty()) {
      addSeries(seriesList.get(0));
    }
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Do it in addSeries()
  }

  @Override
  public List<SynchView> getSynchList() {
    return SYNCH_LIST;
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }
}
