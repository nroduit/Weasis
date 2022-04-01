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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenu;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;

public class AuContainer extends ImageViewerPlugin<DicomImageElement>
    implements PropertyChangeListener {
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

  // Static tools shared by all the View2dContainer instances, tools are registered when a container
  // is selected
  // Do not initialize tools in a static block (order initialization issue with eventManager), use
  // instead a lazy
  // initialization with a method.
  private static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<>(1));
  private static volatile boolean initComponents = false;

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
    if (!initComponents) {
      initComponents = true;
      // Add standard toolbars
      final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
      String bundleName = context.getBundle().getSymbolicName();
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ImportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            UIManager.EXPLORER_PLUGIN_TOOLBARS.stream()
                .filter(ImportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(TOOLBARS::add);
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ExportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            UIManager.EXPLORER_PLUGIN_TOOLBARS.stream()
                .filter(ExportToolBar.class::isInstance)
                .findFirst();
        b.ifPresent(TOOLBARS::add);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(AuToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new AuToolBar(10));
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
  public List<DockableTool> getToolPanel() {
    return null;
  }

  @Override
  public void setSelected(boolean selected) {
    if (selected) {
      eventManager.setSelectedView2dContainer(this);

      // Send event to select the related patient in Dicom Explorer.
      DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
      if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
        dicomView
            .getDataExplorerModel()
            .firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, this, null, getGroupID()));
      }

    } else {
      eventManager.setSelectedView2dContainer(null);
    }
  }

  @Override
  public void close() {
    AuFactory.closeSeriesViewer(this);
    super.close();

    GuiExecutor.instance()
        .execute(
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
  public synchronized List<Toolbar> getToolBar() {
    return TOOLBARS;
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
