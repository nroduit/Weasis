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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.DcmHeaderToolBar;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MprContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(MprContainer.class);

  static SynchView defaultMpr;

  static {
    HashMap<String, Boolean> actions = new HashMap<>();
    actions.put(ActionW.SCROLL_SERIES.cmd(), true);
    actions.put(ActionW.RESET.cmd(), true);
    actions.put(ActionW.ZOOM.cmd(), true);
    actions.put(ActionW.WINDOW.cmd(), true);
    actions.put(ActionW.LEVEL.cmd(), true);
    actions.put(ActionW.PRESET.cmd(), true);
    actions.put(ActionW.LUT_SHAPE.cmd(), true);
    actions.put(ActionW.LUT.cmd(), true);
    actions.put(ActionW.INVERT_LUT.cmd(), true);
    actions.put(ActionW.FILTER.cmd(), true);
    defaultMpr =
        new SynchView(
            Messages.getString("mpr.synchronisation"),
            "mpr", // NON-NLS
            SynchData.Mode.STACK,
            ActionIcon.TILE,
            actions);
  }

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE, defaultMpr);

  public static final GridBagLayoutModel view1 =
      new GridBagLayoutModel(LinkedHashMap.newLinkedHashMap(3), "mpr", "MPR (col 1,2)"); // NON-NLS
  protected static final GridBagLayoutModel view2 = VIEWS_2x2_f2.copy();
  protected static final GridBagLayoutModel view3 = VIEWS_2_f1x2.copy();
  public static final GridBagLayoutModel view4 =
      new GridBagLayoutModel(
          LinkedHashMap.newLinkedHashMap(3), "layout_r2x1", "MPR (row 2,1)"); // NON-NLS
  protected static final GridBagLayoutModel view5 = VIEWS_1x3.copy();

  static {
    view2.setTitle("MPR (col 2,1)"); // NON-NLS
    view3.setTitle("MPR (row 1,2)"); // NON-NLS
    view5.setTitle("MPR (col 1,1,1)"); // NON-NLS

    Map<LayoutConstraints, Component> constraints = view1.getConstraints();
    constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            0,
            0,
            0,
            1,
            2,
            0.5,
            1.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            1,
            1,
            0,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            2,
            1,
            1,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);

    Map<LayoutConstraints, Component> view4Constraints = view4.getConstraints();
    view4Constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            0,
            0,
            0,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    view4Constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            1,
            1,
            0,
            1,
            1,
            0.5,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
    view4Constraints.put(
        new LayoutConstraints(
            MprView.class.getName(),
            2,
            0,
            1,
            2,
            1,
            1.0,
            0.5,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH),
        null);
  }

  public static final List<GridBagLayoutModel> LAYOUT_LIST =
      List.of(view1, view2, view3, view4, view5);

  public static final SeriesViewerUI UI =
      new SeriesViewerUI(MprContainer.class, null, View2dContainer.UI.tools, null);
  private MprController mprController;

  private Thread process;

  public MprContainer() {
    this(VIEWS_1x1, null);
  }

  public MprContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        EventManager.getInstance(),
        layoutModel,
        uid,
        MprFactory.NAME,
        ResourceUtil.getIcon(OtherIcon.VIEW_3D),
        null);
    setSynchView(SynchView.NONE);
    if (!UI.init.getAndSet(true)) {
      List<Toolbar> toolBars = UI.toolBars;

      // Add standard toolbars
      // WProperties props = (WProperties) BundleTools.SYSTEM_PREFERENCES.clone();
      // props.putBooleanProperty("weasis.toolbar.synch.button", false);

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
      toolBars.add(new DcmHeaderToolBar(evtMg, 35));
      toolBars.add(new LutToolBar(evtMg, 40));

      final BundleContext context = AppProperties.getBundleContext(this.getClass());
      Preferences prefs = BundlePreferences.getDefaultPreferences(context);
      if (prefs != null) {
        String className = this.getClass().getSimpleName().toLowerCase();
        InsertableUtil.applyPreferences(
            toolBars, prefs, context.getBundle().getSymbolicName(), className, Type.TOOLBAR);
      }
    }
  }

  @Override
  public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
    setSelectedImagePane(defaultView2d);
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

        menuRoot.add(manager.getResetMenu("weasis.pluginMenu.reset"));
      }
    }
    return menuRoot;
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return View2dContainer.UI;
  }

  @Override
  protected synchronized void setLayoutModel(GridBagLayoutModel layoutModel) {
    super.setLayoutModel(layoutModel);
    if (eventManager instanceof EventManager manager) {
      // Force to refresh view with ZoomType.CURRENT
      manager.reset(ResetTools.ZOOM);
    }
  }

  private synchronized void stopCurrentProcess() {
    if (process != null) {
      final Thread t = process;
      process = null;
      t.interrupt();
    }
  }

  @Override
  public void close() {
    stopCurrentProcess();
    if (mprController != null) {
      mprController.dispose();
    }
    MprFactory.closeSeriesViewer(this);
    super.close();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();
      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof MediaSeriesGroup group) {
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
            if (event.getSource() instanceof DicomModel model) {
              for (ViewCanvas<DicomImageElement> v : view2ds) {
                if (group.equals(model.getParent(v.getSeries(), DicomModel.study))) {
                  v.setSeries(null);
                  if (closeIfNoContent()) {
                    return;
                  }
                }
              }
            }
          }
          // Series Group
          else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
            for (ViewCanvas<DicomImageElement> v : view2ds) {
              if (newVal.equals(v.getSeries())) {
                v.setSeries(null);
                if (closeIfNoContent()) {
                  return;
                }
              }
            }
          }
        }
      } else if (ObservableEvent.BasicAction.REPLACE.equals(action)) {
        if (newVal instanceof Series series) {
          for (ViewCanvas<DicomImageElement> v : view2ds) {
            MediaSeries<DicomImageElement> s = v.getSeries();
            if (series.equals(s)) {
              // It will reset MIP view
              v.setSeries(series, null);
            }
          }
        }
      }
    }
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    return View2dFactory.getViewTypeNumber(layout, defaultClass);
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

  public MprController getMprController() {
    if (mprController == null) {
      mprController = new MprController();
    }
    return mprController;
  }

  @Override
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return new MprView(eventManager, getMprController());
  }

  @Override
  public JComponent createComponent(String clazz) {
    if (isViewType(DefaultView2d.class, clazz)) {
      return createDefaultView(clazz);
    }

    try {
      // FIXME use classloader.loadClass or injection
      return buildInstance(Class.forName(clazz));

    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return view2dClass;
  }

  @Override
  public GridBagLayoutModel getDefaultLayoutModel() {
    return view1;
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
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MprContainer.this);
              PrintDialog<DicomImageElement> dialog =
                  new PrintDialog<>(
                      SwingUtilities.getWindowAncestor(MprContainer.this), title, eventManager);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
    actions.add(printStd);

    final String title2 = Messages.getString("View2dContainer.dcm_print");
    DefaultAction printStd2 =
        new DefaultAction(
            title2,
            null,
            event -> {
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MprContainer.this);
              DicomPrintDialog<?> dialog =
                  new DicomPrintDialog<>(
                      SwingUtilities.getWindowAncestor(MprContainer.this), title2, eventManager);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
    actions.add(printStd2);
    return actions;
  }

  public MprView getMprView(SliceOrientation sliceOrientation) {
    for (ViewCanvas<?> v : view2ds) {
      if (v instanceof MprView mprView
          && sliceOrientation != null
          && sliceOrientation.equals(mprView.getSliceOrientation())) {
        return mprView;
      }
    }
    return null;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    stopCurrentProcess();
    // TODO Should be init elsewhere
    for (int i = 0; i < view2ds.size(); i++) {
      ViewCanvas<DicomImageElement> val = view2ds.get(i);
      if (val instanceof MprView mprView) {
        SliceOrientation sliceOrientation =
            switch (i) {
              case 1 -> SliceOrientation.CORONAL;
              case 2 -> SliceOrientation.SAGITTAL;
              default -> SliceOrientation.AXIAL;
            };
        mprView.setType(sliceOrientation);
      }
    }

    final MprView view = selectLayoutPositionForAddingSeries(sequence);
    if (view != null) {
      view.setSeries(sequence);

      String title = TagD.getTagValue(sequence, Tag.PatientName, String.class);
      if (title != null) {
        this.getDockable().setTitleToolTip(title);
        this.setPluginName(StringUtil.getTruncatedString(title, 25, Suffix.THREE_PTS));
      }
      view.repaint();
      process =
          new Thread(Messages.getString("MPRContainer.build")) {
            @Override
            public void run() {
              try {
                MPRGenerator.createMissingSeries(this, MprContainer.this, view);

                // Following actions need to be executed in EDT thread
                GuiExecutor.execute(
                    () -> {
                      eventManager
                          .getAction(ActionW.SYNCH)
                          .ifPresent(c -> c.setSelectedItem(MprContainer.defaultMpr));

                      if (!mprController.isOblique()) {
                        // Set the middle image ( the best choice to propagate the default preset
                        // of non CT modalities)
                        eventManager
                            .getAction(ActionW.SCROLL_SERIES)
                            .ifPresent(s -> s.setSliderValue(s.getSliderMax() / 2));
                        eventManager
                            .getAction(ActionW.CROSSHAIR)
                            .ifPresent(
                                i -> {
                                  Point2D pt =
                                      view.getImageCoordinatesFromMouse(
                                          view.getWidth() / 2, view.getHeight() / 2);
                                  PanPoint panPoint =
                                      new PanPoint(PanPoint.State.CENTER, pt.getX(), pt.getY());
                                  i.setPoint(panPoint);
                                });
                      }

                      // Force to propagate the default preset
                      eventManager
                          .getAction(ActionW.PRESET)
                          .ifPresent(
                              c -> {
                                c.setSelectedItemWithoutTriggerAction(null);
                                c.setSelectedItem(c.getFirstItem());
                              });
                    });

              } catch (final Exception e) {
                LOGGER.error("Build MPR", e);
                // Following actions need to be executed in EDT thread
                GuiExecutor.execute(() -> showErrorMessage(view2ds, view, e.getMessage()));
              }
            }
          };
      process.start();
    } else {
      showErrorMessage(view2ds, null, Messages.getString("MPRContainer.mesg_missing_3d"));
    }
  }

  public static void showErrorMessage(
      List<ViewCanvas<DicomImageElement>> view2ds,
      DefaultView2d<DicomImageElement> view,
      String message) {
    for (ViewCanvas<DicomImageElement> v : view2ds) {
      if (v != view && v instanceof MprView mprView) {
        JProgressBar bar = mprView.getProgressBar();
        if (bar == null) {
          bar = new JProgressBar();
          Dimension dim =
              new Dimension(v.getJComponent().getWidth() / 2, GuiUtils.getScaleLength(30));
          bar.setSize(dim);
          bar.setPreferredSize(dim);
          bar.setMaximumSize(dim);
          bar.setValue(0);
          bar.setStringPainted(true);
          mprView.setProgressBar(bar);
        }
        bar.setString(message);
        v.getJComponent().repaint();
      }
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

  public MprView selectLayoutPositionForAddingSeries(MediaSeries<DicomImageElement> s) {
    if (s != null) {
      Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
      if (img instanceof DicomImageElement imageElement) {
        Plan orientation = ImageOrientation.getPlan(imageElement);
        if (orientation != null) {
          SliceOrientation sliceOrientation = SliceOrientation.AXIAL;
          if (Plan.CORONAL.equals(orientation)) {
            sliceOrientation = SliceOrientation.CORONAL;
          } else if (Plan.SAGITTAL.equals(orientation)) {
            sliceOrientation = SliceOrientation.SAGITTAL;
          }
          MprView view = getMprView(sliceOrientation);
          if (view != null) {
            setSelectedImagePane(view);
            return view;
          }
        }
      }
    }
    return null;
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
