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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
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
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.image.CrosshairListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
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

public class MPRContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(MPRContainer.class);

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
            "MPR synch", // NON-NLS
            "mpr", // NON-NLS
            SynchData.Mode.STACK,
            ActionIcon.TILE,
            actions);
  }

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE, defaultMpr);

  public static final GridBagLayoutModel view1 =
      new GridBagLayoutModel(new LinkedHashMap<>(3), "mpr", "MPR (col 1,2)"); // NON-NLS
  protected static final GridBagLayoutModel view2 = VIEWS_2x2_f2.copy();
  protected static final GridBagLayoutModel view3 = VIEWS_2_f1x2.copy();
  public static final GridBagLayoutModel view4 =
      new GridBagLayoutModel(new LinkedHashMap<>(3), "mpr", "MPR (row 2,1)"); // NON-NLS
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

  // Static tools shared by all the View2dContainer instances, tools are registered when a container
  // is selected
  // Do not initialize tools in a static block (order initialization issue with eventManager), use
  // instead a lazy
  // initialization with a method.
  public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<>());
  public static final List<DockableTool> TOOLS = View2dContainer.TOOLS;
  private static volatile boolean initComponents = false;

  private Thread process;
  private String lastCommand;

  public MPRContainer() {
    this(VIEWS_1x1, null);
  }

  public MPRContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        EventManager.getInstance(),
        layoutModel,
        uid,
        MPRFactory.NAME,
        ResourceUtil.getIcon(OtherIcon.VIEW_3D),
        null);
    setSynchView(SynchView.NONE);
    if (!initComponents) {
      initComponents = true;
      // Add standard toolbars
      // WProperties props = (WProperties) BundleTools.SYSTEM_PREFERENCES.clone();
      // props.putBooleanProperty("weasis.toolbar.synch.button", false);

      EventManager evtMg = EventManager.getInstance();
      Optional<Toolbar> importBar =
          View2dContainer.TOOLBARS.stream().filter(ImportToolBar.class::isInstance).findFirst();
      importBar.ifPresent(TOOLBARS::add);
      Optional<Toolbar> exportBar =
          View2dContainer.TOOLBARS.stream().filter(ExportToolBar.class::isInstance).findFirst();
      exportBar.ifPresent(TOOLBARS::add);
      Optional<Toolbar> viewBar =
          View2dContainer.TOOLBARS.stream().filter(ViewerToolBar.class::isInstance).findFirst();
      viewBar.ifPresent(TOOLBARS::add);
      TOOLBARS.add(new MeasureToolBar(evtMg, 11));
      TOOLBARS.add(new ZoomToolBar(evtMg, 20, true));
      TOOLBARS.add(new RotationToolBar(evtMg, 30));
      TOOLBARS.add(new DcmHeaderToolBar(evtMg, 35));
      TOOLBARS.add(new LutToolBar(evtMg, 40));

      final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
      Preferences prefs = BundlePreferences.getDefaultPreferences(context);
      if (prefs != null) {
        String className = this.getClass().getSimpleName().toLowerCase();
        InsertableUtil.applyPreferences(
            TOOLBARS, prefs, context.getBundle().getSymbolicName(), className, Type.TOOLBAR);
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
  public List<DockableTool> getToolPanel() {
    return TOOLS;
  }

  @Override
  public void setSelected(boolean selected) {
    final ViewerToolBar toolBar = getViewerToolBar();
    if (selected) {
      if (toolBar != null) {
        String command = ActionW.CROSSHAIR.cmd();
        MouseActions mouseActions = eventManager.getMouseActions();
        String lastAction = mouseActions.getAction(MouseActions.T_LEFT);
        if (!command.equals(lastAction)) {
          lastCommand = lastAction;
          mouseActions.setAction(MouseActions.T_LEFT, command);
          setMouseActions(mouseActions);
          toolBar.changeButtonState(MouseActions.T_LEFT, command);
        }
      }
    } else {
      if (lastCommand != null && toolBar != null) {
        MouseActions mouseActions = eventManager.getMouseActions();
        if (ActionW.CROSSHAIR.cmd().equals(mouseActions.getAction(MouseActions.T_LEFT))) {
          mouseActions.setAction(MouseActions.T_LEFT, lastCommand);
          setMouseActions(mouseActions);
          toolBar.changeButtonState(MouseActions.T_LEFT, lastCommand);
          lastCommand = null;
        }
      }
    }
    super.setSelected(true);
  }

  @Override
  protected synchronized void setLayoutModel(GridBagLayoutModel layoutModel) {
    super.setLayoutModel(layoutModel);
    if (eventManager instanceof EventManager manager) {
      // Force to refresh view with ZoomType.CURRENT
      manager.reset(ResetTools.ZOOM);
    }
  }

  private boolean closeIfNoContent() {
    if (getOpenSeries().isEmpty()) {
      close();
      handleFocusAfterClosing();
      return true;
    }
    return false;
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
    MPRFactory.closeSeriesViewer(this);
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

  @Override
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return new MprView(eventManager);
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
  public synchronized List<Toolbar> getToolBar() {
    return TOOLBARS;
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
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MPRContainer.this);
              PrintDialog<DicomImageElement> dialog =
                  new PrintDialog<>(
                      SwingUtilities.getWindowAncestor(MPRContainer.this), title, eventManager);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
    actions.add(printStd);

    final String title2 = Messages.getString("View2dContainer.dcm_print");
    DefaultAction printStd2 =
        new DefaultAction(
            title2,
            null,
            event -> {
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MPRContainer.this);
              DicomPrintDialog<?> dialog =
                  new DicomPrintDialog<>(
                      SwingUtilities.getWindowAncestor(MPRContainer.this), title2, eventManager);
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
                SeriesBuilder.createMissingSeries(this, MPRContainer.this, view);

                // Following actions need to be executed in EDT thread
                GuiExecutor.instance()
                    .execute(
                        () -> {
                          ActionState synch = eventManager.getAction(ActionW.SYNCH);
                          if (synch instanceof ComboItemListener<?> itemListener) {
                            itemListener.setSelectedItem(MPRContainer.defaultMpr);
                          }
                          // Set the middle image ( the best choice to propagate the default preset
                          // of non CT modalities)
                          ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                          if (seqAction instanceof SliderCineListener sliceAction) {
                            sliceAction.setSliderValue(sliceAction.getSliderMax() / 2);
                          }
                          ActionState cross = eventManager.getAction(ActionW.CROSSHAIR);
                          if (cross instanceof CrosshairListener itemListener) {
                            itemListener.setPoint(
                                view.getImageCoordinatesFromMouse(
                                    view.getWidth() / 2, view.getHeight() / 2));
                          }
                          // Force to propagate the default preset
                          ActionState presetAction = eventManager.getAction(ActionW.PRESET);
                          if (presetAction instanceof ComboItemListener<?> p) {
                            p.setSelectedItemWithoutTriggerAction(null);
                            p.setSelectedItem(p.getFirstItem());
                          }
                        });

              } catch (final Exception e) {
                LOGGER.error("Build MPR", e);
                // Following actions need to be executed in EDT thread
                GuiExecutor.instance()
                    .execute(() -> showErrorMessage(view2ds, view, e.getMessage()));
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
