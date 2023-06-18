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

import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.dockable.DisplayTool;
import org.weasis.base.viewer2d.dockable.ImageTool;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.ScreenshotToolBar;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;

public class View2dContainer extends ImageViewerPlugin<ImageElement>
    implements PropertyChangeListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(View2dContainer.class);

  // Unmodifiable list of the default synchronization elements
  public static final List<SynchView> DEFAULT_SYNCH_LIST =
      List.of(SynchView.NONE, SynchView.DEFAULT_STACK, SynchView.DEFAULT_TILE);

  public static final GridBagLayoutModel VIEWS_2x1_r1xc2_histo =
      new GridBagLayoutModel(
          View2dContainer.class.getResourceAsStream("/config/layoutModelHisto.xml"), // NON-NLS
          "layout_histo", // NON-NLS
          Messages.getString("View2dContainer.histogram"));
  // Unmodifiable list of the default layout elements
  public static final List<GridBagLayoutModel> DEFAULT_LAYOUT_LIST =
      List.of(
          VIEWS_1x1,
          VIEWS_1x2,
          VIEWS_1x3,
          VIEWS_1x4,
          VIEWS_2x1,
          VIEWS_2x1_r1xc2_histo,
          VIEWS_2x2_f2,
          VIEWS_2_f1x2,
          VIEWS_2x2,
          VIEWS_2x3,
          VIEWS_2x4);

  // Static tools shared by all the View2dContainer instances, tools are registered when a container
  // is selected
  // Do not initialize tools in a static block (order initialization issue with eventManager), use
  // instead a lazy
  // initialization with a method.
  public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<>());
  public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean initComponents = false;

  public View2dContainer() {
    this(VIEWS_1x1, null);
  }

  public View2dContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        EventManager.getInstance(),
        layoutModel,
        uid,
        ViewerFactory.NAME,
        ResourceUtil.getIcon(OtherIcon.RASTER_IMAGE),
        null);
    setSynchView(SynchView.DEFAULT_STACK);
    addComponentListener(
        new ComponentAdapter() {

          @Override
          public void componentResized(ComponentEvent e) {
            ImageViewerPlugin<ImageElement> container =
                EventManager.getInstance().getSelectedView2dContainer();
            if (container == View2dContainer.this) {
              Optional<ComboItemListener<GridBagLayoutModel>> layoutAction =
                  EventManager.getInstance().getAction(ActionW.LAYOUT);
              layoutAction.ifPresent(
                  a ->
                      a.setDataListWithoutTriggerAction(
                          getLayoutList().toArray(new GridBagLayoutModel[0])));
            }
          }
        });

    if (!initComponents) {
      initComponents = true;

      // Add standard toolbars
      final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
      EventManager evtMg = EventManager.getInstance();

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
          InsertableUtil.getCName(ScreenshotToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new ScreenshotToolBar<>(evtMg, 9));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ViewerToolBar.class),
          key,
          true)) {
        TOOLBARS.add(
            new ViewerToolBar<>(
                evtMg,
                evtMg.getMouseActions().getActiveButtons(),
                BundleTools.SYSTEM_PREFERENCES,
                10));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(MeasureToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new MeasureToolBar(evtMg, 11));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ZoomToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new ZoomToolBar(evtMg, 20, true));
      }
      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(RotationToolBar.class),
          key,
          true)) {
        TOOLBARS.add(new RotationToolBar(evtMg, 30));
      }

      PluginTool tool = null;

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
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
                eventManager.getAction(ActionW.SCROLL_SERIES).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ZOOM).ifPresent(listeners::add);
                eventManager.getAction(ActionW.ROTATION).ifPresent(listeners::add);
                return listeners.toArray(new SliderChangeListener[0]);
              }
            };

        TOOLS.add(tool);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(ImageTool.class),
          key,
          true)) {
        tool = new ImageTool(ImageTool.BUTTON_NAME);
        TOOLS.add(tool);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(DisplayTool.class),
          key,
          true)) {
        tool = new DisplayTool(DisplayTool.BUTTON_NAME);
        TOOLS.add(tool);
        eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
      }

      if (InsertableUtil.getBooleanProperty(
          BundleTools.SYSTEM_PREFERENCES,
          bundleName,
          componentName,
          InsertableUtil.getCName(MeasureTool.class),
          key,
          true)) {
        tool = new MeasureTool(eventManager);
        TOOLS.add(tool);
      }

      InsertableUtil.sortInsertable(TOOLS);

      Preferences prefs = BundlePreferences.getDefaultPreferences(context);
      if (prefs != null) {
        InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, componentName, Type.TOOLBAR);
        InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, componentName, Type.TOOL);
      }
    }
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();

      if (eventManager instanceof EventManager manager) {
        GuiUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getFilterMenu(null));
        menuRoot.add(new JSeparator());
        GuiUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
        GuiUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
        // GuiUtils.addItemToMenu(menuRoot, manager.getSortStackMenu(null));
        menuRoot.add(new JSeparator());
        menuRoot.add(manager.getResetMenu(null));
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
    eventManager.setSelectedView2dContainer(selected ? this : null);
  }

  @Override
  public void close() {
    ViewerFactory.closeSeriesViewer(this);
    super.close();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();
      if (newVal instanceof SeriesEvent seriesEvent) {
        if (ObservableEvent.BasicAction.ADD.equals(action)) {
          SeriesEvent.Action action2 = seriesEvent.getActionCommand();
          Object source = seriesEvent.getSource();
          Object param = seriesEvent.getParam();

          if (SeriesEvent.Action.ADD_IMAGE.equals(action2)) {
            if (source instanceof Series series) {
              ViewCanvas view2DPane = eventManager.getSelectedViewPane();
              ImageElement img = view2DPane.getImage();
              if (img != null && view2DPane.getSeries() == series) {
                Optional<SliderCineListener> seqAction =
                    eventManager.getAction(ActionW.SCROLL_SERIES);
                if (seqAction.isPresent()) {
                  if (param instanceof ImageElement) {
                    Filter<ImageElement> filter =
                        (Filter<ImageElement>)
                            view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd());
                    int imgIndex =
                        series.getImageIndex(img, filter, view2DPane.getCurrentSortComparator());
                    if (imgIndex < 0) {
                      imgIndex = 0;
                      // add again the series for registering listeners
                      // (require at least one image)
                      view2DPane.setSeries(series, null);
                    }
                    seqAction.get().setSliderMinMaxValue(1, series.size(filter), imgIndex + 1);
                  }
                }
              }
            }
          } else if (SeriesEvent.Action.PRELOADING.equals(action2)) {
            if (source instanceof Series s) {
              for (ViewCanvas<ImageElement> v : view2ds) {
                if (s == v.getSeries()) {
                  v.getJComponent().repaint(v.getInfoLayer().getPreloadingProgressBound());
                }
              }
            }
          }
        }
      } else if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof MediaSeriesGroup group) {
          // Patient Group
          if (TagW.Group.equals(group.getTagID())) {
            if (group.equals(getGroupID())) {
              // Close the content of the plug-in
              close();
              handleFocusAfterClosing();
            }
          }
          // Series Group
          else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            for (ViewCanvas<ImageElement> v : view2ds) {
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
          for (ViewCanvas<ImageElement> v : view2ds) {
            MediaSeries<ImageElement> s = v.getSeries();
            if (series.equals(s)) {
              // Set to null to be sure that all parameters from the view are applied again to the
              // Series
              // (in case for instance it is the same series with more images)
              v.setSeries(null);
              v.setSeries(series, null);
            }
          }
        }
      }
    }
  }

  @Override
  public DefaultView2d<ImageElement> createDefaultView(String classType) {
    return new View2d(eventManager);
  }

  @Override
  public JComponent createComponent(String clazz) {
    if (DefaultView2d.class.getName().equals(clazz) || View2d.class.getName().equals(clazz)) {
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
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    return ViewerFactory.getViewTypeNumber(layout, defaultClass);
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
  public List<Action> getPrintActions() {
    ArrayList<Action> actions = new ArrayList<>(1);
    final String title = Messages.getString("View2dContainer.print_layout");
    Consumer<ActionEvent> event =
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
          PrintDialog<?> dialog =
              new PrintDialog<>(
                  SwingUtilities.getWindowAncestor(View2dContainer.this), title, eventManager);
          ColorLayerUI.showCenterScreen(dialog, layer);
        };
    DefaultAction printStd =
        new DefaultAction(title, ResourceUtil.getIcon(ActionIcon.PRINT), event);
    printStd.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
    actions.add(printStd);
    return actions;
  }

  @Override
  public List<SynchView> getSynchList() {
    return DEFAULT_SYNCH_LIST;
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    int rx = 1;
    int ry = 1;
    double ratio = getWidth() / (double) getHeight();
    if (ratio >= 1.0) {
      rx = (int) Math.round(ratio * 1.5);
    } else {
      ry = (int) Math.round((1.0 / ratio) * 1.5);
    }

    ArrayList<GridBagLayoutModel> list = new ArrayList<>(DEFAULT_LAYOUT_LIST);
    // Exclude 1x1
    if (rx != ry && rx != 0 && ry != 0) {
      int factorLimit =
          (int) (rx == 1 ? Math.round(getWidth() / 512.0) : Math.round(getHeight() / 512.0));
      if (factorLimit < 1) {
        factorLimit = 1;
      }
      if (rx > ry) {
        int step = 1 + (rx / 20);
        for (int i = rx / 2; i < rx; i = i + step) {
          addLayout(list, factorLimit, i, ry);
        }
      } else {
        int step = 1 + (ry / 20);
        for (int i = ry / 2; i < ry; i = i + step) {
          addLayout(list, factorLimit, rx, i);
        }
      }

      addLayout(list, factorLimit, rx, ry);
    }
    list.sort(Comparator.comparingInt(o -> o.getConstraints().size()));
    return list;
  }

  private void addLayout(List<GridBagLayoutModel> list, int factorLimit, int rx, int ry) {
    for (int i = 1; i <= factorLimit; i++) {
      if (i > 2 || i * ry > 2 || i * rx > 2) {
        if (i * ry < 50 && i * rx < 50) {
          list.add(
              ImageViewerPlugin.buildGridBagLayoutModel(i * ry, i * rx, view2dClass.getName()));
        }
      }
    }
  }
}
