/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import com.jogamp.opengl.GL4;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer3d.dockable.DisplayTool;
import org.weasis.dicom.viewer3d.dockable.SegmentationTool;
import org.weasis.dicom.viewer3d.dockable.VolumeTool;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.DicomVolTextureFactory;
import org.weasis.dicom.viewer3d.vr.OpenglUtils;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.View3d.ViewType;
import org.weasis.dicom.viewer3d.vr.VolumeBuilder;

public class View3DContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(View3DContainer.class);

  static SynchView defaultMpr;

  static {
    HashMap<String, Boolean> actions = new HashMap<>();
    actions.put(ActionW.RESET.cmd(), true);
    actions.put(ActionW.ZOOM.cmd(), true);
    actions.put(ActionW.WINDOW.cmd(), true);
    actions.put(ActionW.LEVEL.cmd(), true);
    actions.put(ActionW.PRESET.cmd(), true);
    actions.put(ActionW.LUT_SHAPE.cmd(), true);
    actions.put(ActionVol.VOL_PRESET.cmd(), true);
    actions.put(ActionW.INVERT_LUT.cmd(), true);
    actions.put(ActionW.FILTER.cmd(), true);
    actions.put(ActionVol.MIP_TYPE.cmd(), true);
    actions.put(ActionVol.MIP_DEPTH.cmd(), true);
    defaultMpr =
        new SynchView(
            org.weasis.dicom.viewer2d.Messages.getString("mpr.synchronisation"),
            "mpr", // NON-NLS
            SynchData.Mode.STACK,
            ActionIcon.TILE,
            actions);
  }

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE, defaultMpr);

  public static final MigLayoutModel VIEWS_vr =
      new MigLayoutModel(
          "vr", Messages.getString("volume.rendering"), 1, 1, View3d.class.getName()); // NON-NLS

  public static final MigLayoutModel VIEWS_vr_1x2 =
      new MigLayoutModel(
          "vr_1x2", VIEWS_vr.getUIName() + " (1x2)", 1, 2, View3d.class.getName()); // NON-NLS
  private static final List<MigCell> cells = new ArrayList<>(MprContainer.VIEWS_2x2_mpr.getCells());

  static {
    MigCell lastCell = cells.get(3);
    cells.set(
        3,
        new MigCell(
            lastCell.position(),
            View3d.class.getName(),
            lastCell.constraints(),
            lastCell.x(),
            lastCell.y(),
            lastCell.spanX(),
            lastCell.spanY()));
  }

  public static final MigLayoutModel VIEWS_2x2_mpr =
      new MigLayoutModel(
          "mpr2x2_vr", // NON-NLS
          Messages.getString("mpr.volume.rendering"), // NON-NLS
          MprContainer.VIEWS_2x2_mpr.getLayoutConstraints(),
          MprContainer.VIEWS_2x2_mpr.getColumnConstraints(),
          MprContainer.VIEWS_2x2_mpr.getRowConstraints(),
          cells);

  public static final List<MigLayoutModel> LAYOUT_LIST =
      Stream.of(VIEWS_vr, VIEWS_vr_1x2, VIEWS_2x2_mpr).toList();

  public static final SeriesViewerUI UI = new SeriesViewerUI(View3DContainer.class);

  // protected ControlAxes controlAxes;
  protected final DicomVolTextureFactory factory;
  protected VolumeBuilder volumeBuilder;
  protected SegmentationTool.Type segType;

  private final Object volumeBuilderLock = new Object();

  protected final Map<String, List<SegRegion<?>>> regionMap = new HashMap<>();

  public View3DContainer() {
    this(VIEWS_vr, null, View3DFactory.NAME, ResourceUtil.getIcon(ActionIcon.VOLUME), null);
  }

  public View3DContainer(
      MigLayoutModel layoutModel, String uid, String pluginName, Icon icon, String tooltips) {
    super(EventManager.getInstance(), layoutModel, uid, pluginName, icon, tooltips);
    setSynchView(SynchView.NONE);
    this.factory = new DicomVolTextureFactory();
    this.segType = SegmentationTool.Type.NONE;
    initTools();

    //    final ViewerToolBar toolBar = getViewerToolBar();
    //    if (toolBar != null) {
    //      String command = ActionW.CROSSHAIR.cmd();
    //      MouseActions mouseActions = eventManager.getMouseActions();
    //      String lastAction = mouseActions.getAction(MouseActions.T_LEFT);
    //      if (!command.equals(lastAction)) {
    //        mouseActions.setAction(MouseActions.T_LEFT, command);
    //        toolBar.changeButtonState(MouseActions.T_LEFT, command);
    //      }
    //    }
    factory.addPropertyChangeListener(this);
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  private void initTools() {
    setSynchView(defaultMpr);

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
          InsertableUtil.getCName(ViewerToolBar.class),
          key,
          true)) {
        toolBars.add(
            new ViewerToolBar<>(
                eventManager, eventManager.getMouseActions().getActiveButtons(), preferences, 10));
      }
      //      if (InsertableUtil.getBooleanProperty(
      //          GuiUtils.getUICore().getSystemPreferences(),
      //          bundleName,
      //          componentName,
      //          InsertableUtil.getCName(MeasureToolBar.class),
      //          key,
      //          true)) {
      //        TOOLBARS.add(new MeasureToolBar(eventManager, 11));
      //      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ZoomToolBar.class),
          key,
          true)) {
        toolBars.add(new ZoomToolBar(eventManager, 20, false));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(RotationToolBar.class),
          key,
          true)) {
        toolBars.add(new RotationToolBar(eventManager, 30));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(LutToolBar.class),
          key,
          true)) {
        toolBars.add(new VolLutToolBar(eventManager, 40));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(View3DToolbar.class),
          key,
          true)) {
        toolBars.add(new View3DToolbar(50));
      }

      List<DockableTool> tools = UI.tools;
      PluginTool tool;

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(MiniTool.class),
          key,
          true)) {
        tool =
            new MiniTool(MiniTool.BUTTON_NAME) {

              @Override
              public SliderChangeListener[] getActions() {
                ArrayList<SliderChangeListener> listeners = new ArrayList<>(3);
                eventManager.getAction(ActionVol.SCROLLING).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ZOOM).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ROTATION).ifPresent(listeners::add);
                return listeners.toArray(new SliderChangeListener[0]);
              }
            };
        tools.add(tool);
      }

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(VolumeTool.class),
          key,
          true)) {
        tools.add(new VolumeTool(VolumeTool.BUTTON_NAME));
      }

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(DisplayTool.class),
          key,
          true)) {
        tool = new DisplayTool(DisplayTool.BUTTON_NAME);
        tools.add(tool);
        eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
      }

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(MeasureTool.class),
          key,
          true)) {
        tool = new MeasureTool(eventManager);
        tools.add(tool);
      }

      InsertableUtil.sortInsertable(tools);

      // Send event to synchronize the series selection.
      DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
      if (dicomView != null) {
        eventManager.addSeriesViewerListener((SeriesViewerListener) dicomView);
      }

      Preferences prefs = BundlePreferences.getDefaultPreferences(context);
      if (prefs != null) {
        InsertableUtil.applyPreferences(toolBars, prefs, bundleName, componentName, Type.TOOLBAR);
        InsertableUtil.applyPreferences(tools, prefs, bundleName, componentName, Type.TOOL);
      }
    }
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> series) {
    if (series == null) {
      return;
    }
    try {
      if (View3DFactory.isOpenglEnable()) {
        MediaSeries<DicomImageElement> oldSequence = null;
        if (volumeBuilder != null) {
          oldSequence = volumeBuilder.getVolTexture().getSeries();
          GL4 gl4 = OpenglUtils.getGL4();
          if (gl4 != null && !series.equals(oldSequence)) {
            volumeBuilder.getVolTexture().destroy(gl4);
          }
        }

        if (!series.equals(oldSequence)) {
          GuiUtils.getUICore().closeSeries(oldSequence);
          synchronized (volumeBuilderLock) {
            for (ViewCanvas<DicomImageElement> view : cellManager) {
              if (view instanceof View3d v) {
                if (volumeBuilder == null) {
                  this.volumeBuilder = new VolumeBuilder(factory.createImageSeries(series, v));
                }
                v.setVolTexture(volumeBuilder.getVolTexture());
              }
            }
            volumeBuilder.getVolTexture().getSeries().setOpen(true);
            startVolumeBuilder();
          }
        }
      } else {
        View3DFactory.showOpenglErrorMessage(this);
      }
    } catch (Exception ex) {
      close();
      handleFocusAfterClosing();
      if (ex instanceof IllegalArgumentException) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), null, JOptionPane.ERROR_MESSAGE);
      } else {
        View3DFactory.showOpenglErrorMessage(this);
      }
      return;
    }

    setPluginName(TagD.getTagValue(series, Tag.PatientName, String.class));
    setSelected(true);
  }

  public void reload() {
    if (volumeBuilder != null) {
      MediaSeries<DicomImageElement> oldSequence = volumeBuilder.getVolTexture().getSeries();
      volumeBuilder.getVolTexture().destroy(OpenglUtils.getGL4());
      // Force to rebuild
      this.volumeBuilder = null;
      addSeries(oldSequence);
    }
  }

  protected void startVolumeBuilder() {
    VolumeBuilder builder = volumeBuilder;
    if (!builder.isRunning() && !builder.isDone()) {
      builder.start();
    }
  }

  private DicomVolTexture getVolTexture() {
    VolumeBuilder builder = volumeBuilder;
    if (builder != null) {
      return builder.getVolTexture();
    }
    return null;
  }

  @Override
  protected synchronized void setLayoutModel(MigLayoutModel layoutModel) {
    super.setLayoutModel(layoutModel);

    DicomVolTexture curVolTexture = getVolTexture();
    final List<MigCell> cells = getLayoutModel().getCells();
    int position = 0;
    for (MigCell cell : cells) {
      // Get the component directly from cellManager using the cell's position
      Component component = cellManager.getComponent(cell.position());

      if (component instanceof View3d vt) {
        ViewType viewType;
        if (layoutModel.getCellCount() < 3) {
          viewType = ViewType.VOLUME3D;
        } else {
          viewType =
              switch (position) {
                case 0 -> ViewType.AXIAL;
                case 1 -> ViewType.CORONAL;
                case 2 -> ViewType.SAGITTAL;
                default -> ViewType.VOLUME3D;
              };
        }
        vt.setViewType(viewType);
        position++;
        vt.setVolTexture(curVolTexture);
      } else if (component instanceof MprView mpr) {
        Plane plane =
            switch (position) {
              case 1 -> Plane.CORONAL;
              case 2 -> Plane.SAGITTAL;
              default -> Plane.AXIAL;
            };
        mpr.setType(plane);
        position++;
      }
    }
  }

  protected void removeContent(final ObservableEvent event) {
    Object newVal = event.getNewValue();
    if (newVal instanceof MediaSeriesGroup group) {
      View2dContainer.removeViews(this, group, event);
    }
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();
      if (eventManager instanceof EventManager manager) {
        GuiUtils.addItemToMenu(menuRoot, manager.getPresetMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
        menuRoot.add(new JSeparator());
        GuiUtils.addItemToMenu(menuRoot, manager.getViewTypeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getMipTypeMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getShadingMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getSProjectionMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getSlicingMenu(null));
        menuRoot.add(new JSeparator());
        GuiUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
        menuRoot.add(new JSeparator());
        menuRoot.add(manager.getResetMenu(null));
      }
    }
    return menuRoot;
  }

  @Override
  public void close() {
    View3DFactory.closeSeriesViewer(this);
    super.close();

    GuiExecutor.execute(
        () -> {
          for (var v : cellManager) {
            resetMaximizedSelectedImagePane(v);
            v.disposeView();
          }
        });
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    String command = event.getPropertyName();
    boolean fullyLoaded = DicomVolTextureFactory.FULLY_LOADED.equals(command);
    if (fullyLoaded || DicomVolTextureFactory.PARTIALLY_LOADED.equals(command)) {
      if (event.getNewValue() instanceof DicomVolTexture texture) {
        for (var view : cellManager) {
          if (view instanceof View3d v) {
            if (fullyLoaded) {
              v.getCamera().resetAll();
              v.setVolTexture(texture);
            } else {
              v.display();
            }
          }
        }
      }
    }

    if (event instanceof ObservableEvent e) {
      ObservableEvent.BasicAction action = e.getActionCommand();
      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        removeContent(e);
      }
    }
  }

  /**
   * Overridden because this Container can be closed before the first call to setSelected.
   *
   * <p>If that happens, all toolbars get visible and viewer not. Need a way out.
   *
   * @param selected true if selected
   */
  @Override
  public void setSelected(boolean selected) {
    if (!isShowing()) {
      return;
    }
    if (selected) {
      eventManager.setSelectedView2dContainer(this);
    }
    super.setSelected(selected);
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    if (seriesList != null && !seriesList.isEmpty()) {
      addSeries(seriesList.getFirst());
    }
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Do it in addSeries()
  }

  @Override
  public boolean isViewType(Class defaultClass, String type) {
    if (defaultClass != null) {
      try {
        Class clazz = Class.forName(type);
        return defaultClass.isAssignableFrom(clazz);
      } catch (Exception e) {
        LOGGER.error("Checking view type", e);
      }
    }
    return false;
  }

  @Override
  public int getViewTypeNumber(MigLayoutModel layout, Class defaultClass) {
    return View3DFactory.getViewTypeNumber(layout, defaultClass);
  }

  @Override
  public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
    try {
      return new View3d(eventManager, null);
    } catch (Exception e) {
      LOGGER.error("Cannot create a 3D view: {}", e.getMessage());
    }
    return null;
  }

  @Override
  public Component createComponent(String clazz) {
    if (isViewType(View3d.class, clazz) || isViewType(MprView.class, clazz)) {
      return createDefaultView(clazz).getJComponent();
    }

    try {
      // FIXME use classloader.loadClass or injection
      Class cl = Class.forName(clazz);
      JComponent component = (JComponent) cl.newInstance();
      if (component instanceof SeriesViewerListener) {
        eventManager.addSeriesViewerListener((SeriesViewerListener) component);
      }
      return component;

    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return View3d.class;
  }

  @Override
  public MigLayoutModel getDefaultLayoutModel() {
    return VIEWS_vr;
  }

  @Override
  public WtoolBar getStatusBar() {
    return null;
  }

  @Override
  public List<Action> getExportActions() {
    return null;
  }

  @Override
  public List<Action> getPrintActions() {
    return null;
  }

  @Override
  public List<SynchView> getSynchList() {
    return SYNCH_LIST;
  }

  @Override
  public List<MigLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }

  public void setSegmentationType(SegmentationTool.Type type) {
    this.segType = type;
  }

  public SegmentationTool.Type getSegmentationType() {
    return segType;
  }

  public Map<String, List<SegRegion<?>>> getRegionMap() {
    return regionMap;
  }
}
