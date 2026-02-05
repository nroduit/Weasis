/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;
import org.weasis.dicom.viewer2d.DcmHeaderToolBar;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;

/**
 * Viewer plugin container for curved MPR panoramic views.
 */
public class CurvedMprContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprContainer.class);

  public static final String NAME = "Curved MPR";

  public static final SeriesViewerUI UI =
      new SeriesViewerUI(CurvedMprContainer.class, null, View2dContainer.UI.tools, null);

  private CurvedMprAxis curvedMprAxis;

  public CurvedMprContainer() {
    this(VIEWS_1x1, null);
  }

  public CurvedMprContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        EventManager.getInstance(),
        layoutModel,
        uid,
        NAME,
        ResourceUtil.getIcon(OtherIcon.VIEW_3D),
        null);
    setSynchView(SynchView.NONE);
    if (!UI.init.getAndSet(true)) {
      initToolBars();
    }
  }

  private void initToolBars() {
    List<Toolbar> toolBars = UI.toolBars;
    EventManager evtMg = EventManager.getInstance();

    Optional<Toolbar> importBar =
        View2dContainer.UI.toolBars.stream().filter(ImportToolBar.class::isInstance).findFirst();
    importBar.ifPresent(toolBars::add);
    Optional<Toolbar> exportBar =
        View2dContainer.UI.toolBars.stream().filter(ExportToolBar.class::isInstance).findFirst();
    exportBar.ifPresent(toolBars::add);
    Optional<Toolbar> viewBar =
        View2dContainer.UI.toolBars.stream().filter(ViewerToolBar.class::isInstance).findFirst();
    viewBar.ifPresent(toolBars::add);
    toolBars.add(new MeasureToolBar(evtMg, 11));
    toolBars.add(new ZoomToolBar(evtMg, 20, true));
    toolBars.add(new RotationToolBar(evtMg, 30));
    toolBars.add(new LutToolBar(evtMg, 40));
    toolBars.add(new DcmHeaderToolBar(evtMg, 50));
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();
      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof MediaSeries) {
          close();
        }
      }
    }
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  @Override
  public void close() {
    CurvedMprFactory.closeSeriesViewer(this);
    super.close();
    if (curvedMprAxis != null) {
      curvedMprAxis.dispose();
    }
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    return 1;
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
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return new CurvedMprView(eventManager);
  }

  @Override
  public JComponent createComponent(String clazz) {
    if (isViewType(DefaultView2d.class, clazz)) {
      return createDefaultView(clazz);
    }
    try {
      return buildInstance(Class.forName(clazz));
    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return CurvedMprView.class;
  }

  @Override
  public GridBagLayoutModel getDefaultLayoutModel() {
    return VIEWS_1x1;
  }

  @Override
  public List<Action> getExportActions() {
    return selectedImagePane == null
        ? super.getExportActions()
        : selectedImagePane.getExportActions();
  }

  @Override
  public List<Action> getPrintActions() {
    ArrayList<Action> actions = new ArrayList<>(1);
    final String title = Messages.getString("View2dContainer.print_layout");
    DefaultAction printStd =
        new DefaultAction(
            title,
            ResourceUtil.getIcon(ActionIcon.PRINT),
            event -> {
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(CurvedMprContainer.this);
              PrintDialog<DicomImageElement> dialog =
                  new PrintDialog<>(
                      SwingUtilities.getWindowAncestor(CurvedMprContainer.this), title, eventManager);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
    actions.add(printStd);
    return actions;
  }

  @Override
  public List<SynchView> getSynchList() {
    return List.of(SynchView.NONE);
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    return List.of(VIEWS_1x1);
  }

  public CurvedMprAxis getCurvedMprAxis() {
    return curvedMprAxis;
  }

  public void setCurvedMprAxis(CurvedMprAxis axis) {
    LOGGER.info("setCurvedMprAxis called, axis={}", axis != null ? "not null" : "null");
    this.curvedMprAxis = axis;
    CurvedMprView view = getSelectedCurvedMprView();
    LOGGER.info("getSelectedCurvedMprView returned: {}", view != null ? view.getClass().getName() : "null");
    if (view != null && axis != null) {
      view.setCurvedMprAxis(axis);
      DicomImageElement img = axis.getImageElement();
      LOGGER.info("ImageElement from axis: {}", img != null ? "not null" : "null");
      if (img != null) {
        // Create a series containing the image - required by InfoLayer
        String uid = "curved-mpr-" + System.currentTimeMillis();
        DicomSeries series = new DicomSeries(uid);
        series.addMedia(img);
        LOGGER.info("Created DicomSeries with uid: {}", uid);
        
        // Set the series first, then the image
        view.setSeries(series, img);
        LOGGER.info("Called view.setSeries()");
      }
    } else {
      LOGGER.warn("Cannot set image: view={}, axis={}", view, axis);
    }
  }

  public CurvedMprView getSelectedCurvedMprView() {
    ViewCanvas<?> selected = getSelectedImagePane();
    if (selected instanceof CurvedMprView curvedMprView) {
      return curvedMprView;
    }
    for (ViewCanvas<?> v : view2ds) {
      if (v instanceof CurvedMprView curvedMprView) {
        return curvedMprView;
      }
    }
    return null;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    // Curved MPR is created programmatically, not from a series
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    // Not used for curved MPR
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Not used for curved MPR
  }

  public JMenu createCurvedMprMenu() {
    JMenu menu = new JMenu("Curved MPR");
    
    JMenuItem widthItem = new JMenuItem("Adjust Width...");
    widthItem.addActionListener(e -> {
      CurvedMprView view = getSelectedCurvedMprView();
      if (view != null) {
        view.showWidthDialog();
      }
    });
    menu.add(widthItem);
    
    JMenuItem stepItem = new JMenuItem("Adjust Sampling Step...");
    stepItem.addActionListener(e -> {
      CurvedMprView view = getSelectedCurvedMprView();
      if (view != null) {
        view.showStepDialog();
      }
    });
    menu.add(stepItem);
    
    return menu;
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();

      if (eventManager instanceof EventManager manager) {
        int count = menuRoot.getItemCount();

        GuiUtils.addItemToMenu(menuRoot, manager.getPresetMenu("weasis.pluginMenu.presets"));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu("weasis.pluginMenu.lutShape"));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutMenu("weasis.pluginMenu.lut"));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu("weasis.pluginMenu.invertLut"));
        GuiUtils.addItemToMenu(menuRoot, manager.getFilterMenu("weasis.pluginMenu.filter"));

        if (count < menuRoot.getItemCount()) {
          menuRoot.add(new JSeparator());
          count = menuRoot.getItemCount();
        }

        GuiUtils.addItemToMenu(menuRoot, manager.getZoomMenu("weasis.pluginMenu.zoom"));
        GuiUtils.addItemToMenu(
            menuRoot, manager.getOrientationMenu("weasis.pluginMenu.orientation"));

        if (count < menuRoot.getItemCount()) {
          menuRoot.add(new JSeparator());
          count = menuRoot.getItemCount();
        }

        menuRoot.add(createCurvedMprMenu());

        if (count < menuRoot.getItemCount()) {
          menuRoot.add(new JSeparator());
        }

        menuRoot.add(manager.getResetMenu("weasis.pluginMenu.reset"));
      }
    }
    return menuRoot;
  }
}
