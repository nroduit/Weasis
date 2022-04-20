/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import bibliothek.extension.gui.dock.theme.EclipseTheme;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabDockActionLocation;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabStateInfo;
import bibliothek.gui.dock.ScreenDockStation;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CAction;
import bibliothek.gui.dock.common.action.predefined.CCloseAction;
import bibliothek.gui.dock.common.event.CFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.common.theme.ThemeMap;
import bibliothek.gui.dock.common.theme.eclipse.CommonEclipseThemeConnector;
import bibliothek.gui.dock.station.screen.BoundaryRestriction;
import bibliothek.gui.dock.util.ConfiguredBackgroundPanel;
import bibliothek.gui.dock.util.DirectWindowProvider;
import bibliothek.gui.dock.util.DockUtilities;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Taskbar;
import java.awt.Taskbar.Feature;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.DropLocation;
import javax.swing.WindowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.LogoIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.UriListFlavor;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;

public class WeasisWin {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWin.class);

  public static final List<String> functions = List.of("info", "ui"); // NON-NLS

  private final JMenu menuFile = new JMenu(Messages.getString("WeasisWin.file"));
  private final JMenu menuView = new JMenu(Messages.getString("WeasisWin.display"));
  private final DynamicMenu menuSelectedPlugin =
      new DynamicMenu("") {

        @Override
        public void popupMenuWillBecomeVisible() {
          buildSelectedPluginMenu(this);
        }
      };
  private ViewerPlugin<?> selectedPlugin = null;

  private final ToolBarContainer toolbarContainer;

  private final List<Runnable> runOnClose = new ArrayList<>();

  private final Frame frame;
  private final RootPaneContainer rootPaneContainer;

  private final CFocusListener selectionListener =
      new CFocusListener() {

        @Override
        public void focusGained(CDockable dockable) {
          if (dockable != null
              && dockable.getFocusComponent() instanceof ViewerPlugin viewerPlugin) {
            setSelectedPlugin(viewerPlugin);
          }
        }

        @Override
        public void focusLost(CDockable dockable) {
          // Do nothing
        }
      };

  public WeasisWin() {

    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    RootPaneContainer container = null;
    try {
      ObjectName objectName = ObjectName.getInstance("weasis:name=MainWindow"); // NON-NLS
      Object containerObj = server.getAttribute(objectName, "RootPaneContainer");
      if (containerObj instanceof RootPaneContainer rootPane) {
        container = rootPane;
        container.getRootPane().updateUI();
        if (container.getContentPane() instanceof JPanel panel) {
          panel.updateUI();
        }
        container.getContentPane().removeAll();
      }
    } catch (InstanceNotFoundException ignored) {
    } catch (JMException e) {
      LOGGER.debug("Error while receiving main window", e);
    }

    JFrame jFrame = container == null ? new JFrame() : (JFrame) container;
    frame = jFrame;
    jFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            closeWindow();
          }
        });
    rootPaneContainer = jFrame;

    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.menu.menubar", true)) {
      rootPaneContainer.getRootPane().setJMenuBar(createMenuBar());
    }
    toolbarContainer = new ToolBarContainer();
    setSelectedPlugin(null);
    rootPaneContainer.getContentPane().add(toolbarContainer, BorderLayout.NORTH);

    rootPaneContainer.setGlassPane(AppProperties.glassPane);

    frame.setTitle(AppProperties.WEASIS_NAME + " v" + AppProperties.WEASIS_VERSION); // NON-NLS

    LogoIcon logoIcon =
        AppProperties.WEASIS_NAME.endsWith("Dicomizer") ? LogoIcon.SMALL_DICOMIZER : LogoIcon.SMALL;
    // Get larger icon (displayed in system toolbar)
    FlatSVGIcon imageIcon = ResourceUtil.getIcon(logoIcon, 512, 512);
    boolean taskBarIcon = false;
    if (Taskbar.isTaskbarSupported()) {
      final Taskbar taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Feature.ICON_IMAGE)) {
        try {
          taskbar.setIconImage(imageIcon.getImage());
          taskBarIcon = true;
        } catch (Exception e) {
          LOGGER.error("cannot set icon to taskbar", e);
        }
      }
    }

    if (!taskBarIcon) frame.setIconImage(imageIcon.getImage());

    DesktopAdapter.buildDesktopMenu(this);
  }

  public Frame getFrame() {
    return frame;
  }

  public RootPaneContainer getRootPaneContainer() {
    return rootPaneContainer;
  }

  public ToolBarContainer getToolbarContainer() {
    return toolbarContainer;
  }

  public boolean closeWindow() {
    boolean busy = false;
    if (!busy) {
      if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(BundleTools.CONFIRM_CLOSE, false)) {
        int option = JOptionPane.showConfirmDialog(frame, Messages.getString("WeasisWin.exit_mes"));
        if (option == JOptionPane.YES_OPTION) {
          closeAllRunnable();
          return true;
        }
      } else {
        closeAllRunnable();
        return true;
      }
    }
    return false;
  }

  private void closeAllRunnable() {
    BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("last.window.state", frame.getExtendedState());
    Rectangle rect = frame.getBounds();
    BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("last.window.x", rect.x);
    BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("last.window.y", rect.y);
    BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("last.window.width", rect.width);
    BundleTools.LOCAL_UI_PERSISTENCE.putIntProperty("last.window.height", rect.height);
    for (Runnable onClose : runOnClose) {
      onClose.run();
    }
    System.exit(0);
  }

  public void runOnClose(Runnable run) {
    runOnClose.add(run);
  }

  public void destroyOnClose(final CControl control) {
    runOnClose(control::destroy);
  }

  public void createMainPanel() {

    // Do not disable check when debugging
    if (System.getProperty("maven.localRepository") == null) {
      DockUtilities.disableCheckLayoutLocked();
    }
    CControl control = UIManager.DOCKING_CONTROL;
    control.setRootWindow(new DirectWindowProvider(frame));
    destroyOnClose(control);
    ThemeMap themes = control.getThemes();
    themes.select(ThemeMap.KEY_ECLIPSE_THEME);
    control.getController().getProperties().set(EclipseTheme.PAINT_ICONS_WHEN_DESELECTED, true);
    control.putProperty(ScreenDockStation.BOUNDARY_RESTRICTION, BoundaryRestriction.HARD);
    control.putProperty(EclipseTheme.THEME_CONNECTOR, new HidingEclipseThemeConnector(control));

    // control.setGroupBehavior(CGroupBehavior.TOPMOST);
    // control.setDefaultLocation(centerArea.getStationLocation());

    control.addFocusListener(selectionListener);

    // control.setDefaultLocation(UIManager.BASE_AREA.
    // this.add(UIManager.EAST_AREA, BorderLayout.EAST);
    rootPaneContainer.getContentPane().add(UIManager.BASE_AREA, BorderLayout.CENTER);
    // Allow dropping series into the empty main area
    UIManager.MAIN_AREA.getComponent().setTransferHandler(new SequenceHandler());
    UIManager.MAIN_AREA.setLocation(CLocation.base().normalRectangle(0, 0, 1, 1));
    UIManager.MAIN_AREA.setVisible(true);
  }

  HashMap<MediaSeriesGroup, List<MediaSeries<?>>> getSeriesByEntry(
      TreeModel treeModel, List<? extends MediaSeries<?>> series, TreeModelNode entry) {
    HashMap<MediaSeriesGroup, List<MediaSeries<?>>> map = new HashMap<>();
    if (series != null && treeModel != null && entry != null) {
      for (MediaSeries<?> s : series) {
        MediaSeriesGroup entry1 = treeModel.getParent(s, entry);
        List<MediaSeries<?>> seriesList =
            Optional.ofNullable(map.get(entry1)).orElseGet(ArrayList::new);
        seriesList.add(s);
        map.put(entry1, seriesList);
      }
    }
    return map;
  }

  void openSeriesInViewerPlugin(ViewerPluginBuilder builder, MediaSeriesGroup group) {
    if (builder == null) {
      return;
    }
    SeriesViewerFactory factory = builder.getFactory();
    DataExplorerModel model = builder.getModel();
    List<MediaSeries<MediaElement>> seriesList = builder.getSeries();
    Map<String, Object> props = builder.getProperties();

    Rectangle screenBound = (Rectangle) props.get(ViewerPluginBuilder.SCREEN_BOUND);
    boolean setInSelection =
        LangUtil.getNULLtoFalse((Boolean) props.get(ViewerPluginBuilder.OPEN_IN_SELECTION));

    if (screenBound == null && group != null) {
      boolean bestDefaultLayout =
          LangUtil.getNULLtoTrue((Boolean) props.get(ViewerPluginBuilder.BEST_DEF_LAYOUT));
      synchronized (UIManager.VIEWER_PLUGINS) {
        for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
          final ViewerPlugin<?> p = UIManager.VIEWER_PLUGINS.get(i);
          // Remove the views not attached to any window (Fix bugs with external window)
          if (WinUtil.getParentWindow(p) == null) {
            UIManager.VIEWER_PLUGINS.remove(i);
            continue;
          }
          if (p instanceof ImageViewerPlugin viewer
              && p.getName().equals(factory.getUIName())
              && group.equals(p.getGroupID())) {
            if (setInSelection && seriesList.size() == 1) {
              viewer.addSeries(seriesList.get(0));
            } else {
              viewer.addSeriesList(seriesList, bestDefaultLayout);
            }
            viewer.setSelectedAndGetFocus();
            return;
          }
        }
      }
    }
    // Pass the DataExplorerModel to the viewer
    props.put(DataExplorerModel.class.getName(), model);
    if (seriesList.size() > 1) {
      props.put(ViewCanvas.class.getName(), seriesList.size());
    }
    SeriesViewer<?> seriesViewer = factory.createSeriesViewer(props);
    if (seriesViewer instanceof MimeSystemAppViewer) {
      for (MediaSeries m : seriesList) {
        seriesViewer.addSeries(m);
      }
    } else if (seriesViewer instanceof ViewerPlugin<?> viewer) {
      String title;
      if (factory.canExternalizeSeries()) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();
        if (gd.length > 1) {
          viewer.getDockable().setExternalizable(true);
          setExternalPosition(viewer.getDockable());
          // viewer.getDockable().addCDockableLocationListener(new CDockableLocationListener() {
          //
          // @Override
          // public void changed(CDockableLocationEvent event) {
          // // TODO not a good condition
          // if (event.getNewLocation() instanceof CExternalizedLocation
          // && !(event.getOldLocation() instanceof CExternalizedLocation)) {
          // CDockable dockable = event.getDockable();
          // if (dockable instanceof DefaultSingleCDockable) {
          // setExternalPosition((DefaultSingleCDockable) dockable);
          // }
          // }
          // }
          // });
        }
      }
      if (group == null
          && model instanceof TreeModel treeModel
          && !seriesList.isEmpty()
          && model.getTreeModelNodeForNewPlugin() != null) {
        MediaSeries s = seriesList.get(0);
        group = treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
      }
      if (group != null) {
        title = group.toString();
        viewer.setGroupID(group);
        viewer.getDockable().setTitleToolTip(title);
        viewer.setPluginName(StringUtil.getTruncatedString(title, 25, Suffix.THREE_PTS));
      }

      // Override default plugin icon
      Object val = props.get(ViewerPluginBuilder.ICON);
      if (val instanceof Icon icon) {
        viewer.getDockable().setTitleIcon(icon);
      }

      boolean registered;
      if (screenBound != null) {
        registered = registerDetachWindow(viewer, screenBound);
      } else {
        registered = registerPlugin(viewer);
      }
      if (registered) {
        viewer.setSelectedAndGetFocus();
        if (seriesViewer instanceof ImageViewerPlugin) {
          if (!setInSelection) {
            ((ImageViewerPlugin) viewer).selectLayoutPositionForAddingSeries(seriesList);
          }
        }
        for (MediaSeries m : seriesList) {
          viewer.addSeries(m);
        }
        viewer.setSelected(true);
      } else {
        viewer.close();
        viewer.handleFocusAfterClosing();
      }
    }
  }

  private void setExternalPosition(final DefaultSingleCDockable dockable) {
    // TODO should be set dynamically. Maximize button of external window does not support
    // multi-screens.
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();
    if (gd.length > 1) {
      // dockable.setExternalizable(true);
      Rectangle bound = WinUtil.getClosedScreenBound(rootPaneContainer.getRootPane().getBounds());
      // LocationHint hint =
      // new LocationHint(LocationHint.DOCKABLE,
      // bibliothek.gui.dock.action.LocationHint.LEFT_OF_ALL);
      // DefaultDockActionSource source = new DefaultDockActionSource(hint);
      // source.add(setupDropDownMenu(viewer.getDockable()));
      // source.addSeparator();

      for (GraphicsDevice graphicsDevice : gd) {
        GraphicsConfiguration config = graphicsDevice.getDefaultConfiguration();
        final Rectangle b = config.getBounds();
        if (!b.contains(bound)) {
          Insets inset = toolkit.getScreenInsets(config);
          b.x += inset.left;
          b.y += inset.top;
          b.width -= (inset.left + inset.right);
          b.height -= (inset.top + inset.bottom);
          dockable.setDefaultLocation(
              ExtendedMode.EXTERNALIZED,
              CLocation.external(b.x, b.y, b.width - 150, b.height - 150));

          // GuiExecutor.instance().execute(new Runnable() {
          //
          // @Override
          // public void run() {
          // if (dockable.isVisible()) {
          // dockable.setLocation(CLocation.external(b.x, b.y, b.width - 150, b.height - 150));
          // UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
          // dockable.setExtendedMode(ExtendedMode.MAXIMIZED);
          // UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
          // }
          // }
          // });
          // source.add(new CloseAction(UIManager.DOCKING_CONTROLLER));
          break;
        }
      }
    }
  }

  private static boolean registerDetachWindow(final ViewerPlugin plugin, Rectangle screenBound) {
    if (plugin != null && screenBound != null) {
      ViewerPlugin oldWin = null;

      synchronized (UIManager.VIEWER_PLUGINS) {
        for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
          ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
          if (p.getDockable().isExternalizable()) {
            Dialog dialog = WinUtil.getParentDialog(p);
            if (dialog != null
                && screenBound.equals(WinUtil.getClosedScreenBound(dialog.getBounds()))) {
              oldWin = p;
              break;
            }
          }
        }
      }

      final DefaultSingleCDockable dock = plugin.getDockable();
      dock.setExternalizable(true);

      if (oldWin == null) {
        dock.setLocation(
            CLocation.external(
                screenBound.x, screenBound.y, screenBound.width - 150, screenBound.height - 150));
        plugin.showDockable();
        GuiExecutor.instance()
            .execute(
                () -> {
                  if (dock.isVisible()) {
                    UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                    dock.setExtendedMode(ExtendedMode.MAXIMIZED);
                    UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                  }
                });
      } else {
        ConfiguredBackgroundPanel parent =
            WinUtil.getParentOfClass(oldWin, ConfiguredBackgroundPanel.class);
        if (parent == null) {
          return false;
        } else {
          Rectangle b2 = parent.getBounds();
          b2.setLocation(parent.getLocationOnScreen());
          dock.setLocation(CLocation.external(b2.x, b2.y, b2.width, b2.height).stack());
          plugin.showDockable();
        }
      }
      return true;
    }
    return false;
  }

  public boolean registerPlugin(final ViewerPlugin plugin) {
    if (plugin == null || UIManager.VIEWER_PLUGINS.contains(plugin)) {
      return false;
    }
    plugin.showDockable();
    return true;
  }

  public synchronized ViewerPlugin getSelectedPlugin() {
    return selectedPlugin;
  }

  public synchronized void setSelectedPlugin(ViewerPlugin plugin) {
    if (plugin == null) {
      toolbarContainer.registerToolBar(UIManager.EXPLORER_PLUGIN_TOOLBARS);
      List<DockableTool> oldTool = selectedPlugin == null ? null : selectedPlugin.getToolPanel();
      if (oldTool != null) {
        for (DockableTool p : oldTool) {
          p.closeDockable();
        }
      }
      selectedPlugin = null;
      return;
    }
    if (selectedPlugin == plugin) {
      plugin.requestFocusInWindow();
      return;
    }
    ViewerPlugin oldPlugin = selectedPlugin;
    if (selectedPlugin != null) {
      selectedPlugin.setSelected(false);
    }
    selectedPlugin = plugin;
    selectedPlugin.setSelected(true);
    menuSelectedPlugin.setText(selectedPlugin.getName());

    List<DockableTool> tool = selectedPlugin.getToolPanel();
    List<DockableTool> oldTool = oldPlugin == null ? null : oldPlugin.getToolPanel();

    if (tool != oldTool) {
      if (oldTool != null) {
        for (DockableTool p : oldTool) {
          p.closeDockable();
        }
      }
      if (tool != null) {
        for (DockableTool p : tool) {
          if (p.isComponentEnabled()) {
            p.showDockable();
          }
        }
      }
    }

    updateToolbars(
        oldPlugin == null ? null : oldPlugin.getToolBar(), selectedPlugin.getToolBar(), false);
  }

  void updateToolbars(List<Toolbar> oldToolBars, List<Toolbar> toolBars, boolean force) {
    if (force || toolBars != oldToolBars) {
      toolbarContainer.registerToolBar(toolBars);
    }
  }

  public void showWindow() {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    Toolkit kit = Toolkit.getDefaultToolkit();

    Monitor defMonitor = Monitor.getDefaultMonitor();
    GraphicsConfiguration config;

    if (defMonitor == null) {
      config = ge.getDefaultScreenDevice().getDefaultConfiguration();
    } else {
      config = defMonitor.getGraphicsConfiguration();
    }

    int lastState =
        BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("last.window.state", Frame.MAXIMIZED_BOTH);
    if (lastState != Frame.NORMAL) {
      lastState = Frame.MAXIMIZED_BOTH;
    }
    Rectangle b;
    if (config != null) {
      b = config.getBounds();
      Insets inset = kit.getScreenInsets(config);
      b.x += inset.left;
      b.y += inset.top;
      b.width -= (inset.left + inset.right);
      b.height -= (inset.top + inset.bottom);
      if (lastState == Frame.NORMAL) {
        int x = BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("last.window.x", 0);
        int y = BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("last.window.y", 0);
        int w =
            BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty("last.window.width", Integer.MAX_VALUE);
        int h =
            BundleTools.LOCAL_UI_PERSISTENCE.getIntProperty(
                "last.window.height", Integer.MAX_VALUE);
        if (x < b.x) {
          x = b.x;
        }
        if (y < b.y) {
          y = b.y;
        }
        if (w > b.width) {
          w = b.width;
        }
        if (h > b.height) {
          h = b.height;
        }
        b = new Rectangle(x, y, w, h);
      }
    } else {
      b = new Rectangle(new Point(0, 0), kit.getScreenSize());
    }
    LOGGER.debug("Max main screen bound: {}", b);

    // Do not apply to JApplet
    if (frame == rootPaneContainer) {
      if (lastState == Frame.MAXIMIZED_BOTH) {
        // set a valid size, insets of screen is often non consistent
        frame.setBounds(b.x, b.y, b.width - 150, b.height - 150);
        frame.setVisible(true);
        frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
      } else {
        frame.setBounds(b.x, b.y, b.width, b.height);
        frame.setExtendedState(lastState);
        frame.setVisible(true);
      }
    }
    LOGGER.info("End of loading the GUI...");
  }

  private JMenuBar createMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    buildMenuFile();
    menuBar.add(menuFile);
    buildMenuView();
    menuBar.add(menuView);
    menuBar.add(menuSelectedPlugin);
    menuSelectedPlugin.addPopupMenuListener();

    final JMenu helpMenuItem = new JMenu(Messages.getString("WeasisWin.help"));
    final String helpURL = System.getProperty("weasis.help.url");
    if (helpURL != null) {
      final JMenuItem helpContentMenuItem = new JMenuItem(Messages.getString("WeasisWin.guide"));
      helpContentMenuItem.addActionListener(e -> openBrowser(helpContentMenuItem, helpURL));
      helpMenuItem.add(helpContentMenuItem);
    }

    final JMenuItem webMenuItem = new JMenuItem(Messages.getString("WeasisWin.shortcuts"));
    webMenuItem.addActionListener(
        e ->
            openBrowser(
                webMenuItem, BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.shortcuts")));
    helpMenuItem.add(webMenuItem);

    final JMenuItem websiteMenuItem =
        new JMenuItem(
            Messages.getString("WeasisWin.online"), ResourceUtil.getIcon(ActionIcon.HELP));
    GuiUtils.applySelectedIconEffect(websiteMenuItem);
    websiteMenuItem.addActionListener(
        e ->
            openBrowser(
                websiteMenuItem, BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")));
    helpMenuItem.add(websiteMenuItem);
    final JMenuItem aboutMenuItem =
        new JMenuItem(
            String.format(Messages.getString("WeasisAboutBox.about"), AppProperties.WEASIS_NAME));
    aboutMenuItem.addActionListener(
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootPaneContainer);
          WeasisAboutBox about = new WeasisAboutBox(getFrame());
          ColorLayerUI.showCenterScreen(about, layer);
        });
    helpMenuItem.add(aboutMenuItem);
    menuBar.add(helpMenuItem);
    return menuBar;
  }

  private void openBrowser(Component c, String ref) {
    try {
      URL url = new URL(ref);
      GuiUtils.openInDefaultBrowser(c, url);
    } catch (MalformedURLException e) {
      LOGGER.error("Open URL in default browser", e);
    }
  }

  private void buildToolBarSubMenu(final JMenu toolBarMenu) {
    List<Toolbar> bars = toolbarContainer.getRegisteredToolBars();
    for (final Toolbar bar : bars) {
      if (!Insertable.Type.EMPTY.equals(bar.getType())) {
        JCheckBoxMenuItem item =
            new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
        item.addActionListener(
            e -> {
              if (e.getSource() instanceof JCheckBoxMenuItem menuItem) {
                toolbarContainer.displayToolbar(bar.getComponent(), menuItem.isSelected());
              }
            });
        toolBarMenu.add(item);
      }
    }
  }

  private void buildToolSubMenu(final JMenu toolMenu) {
    List<DockableTool> tools = selectedPlugin == null ? null : selectedPlugin.getToolPanel();
    if (tools != null) {
      for (final DockableTool t : tools) {
        buildSubMenu(toolMenu, t);
      }
    }
  }

  private static void buildExplorerSubMenu(final JMenu explorerMenu) {
    synchronized (UIManager.EXPLORER_PLUGINS) {
      List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
      for (final DataExplorerView dataExplorerView : explorers) {
        if (dataExplorerView instanceof final DockableTool t) {
          buildSubMenu(explorerMenu, t);
        }
      }
    }
  }

  private static void buildSubMenu(JMenu explorerMenu, DockableTool t) {
    if (!Insertable.Type.EMPTY.equals(t.getType())) {
      JCheckBoxMenuItem item = new JCheckBoxMenuItem(t.getComponentName(), t.isComponentEnabled());
      item.addActionListener(
          e -> {
            if (e.getSource() instanceof JCheckBoxMenuItem menuItem) {
              t.setComponentEnabled(menuItem.isSelected());
              if (t.isComponentEnabled()) {
                t.showDockable();
              } else {
                t.closeDockable();
              }
            }
          });
      explorerMenu.add(item);
    }
  }

  private void buildPrintSubMenu(final JMenu printMenu) {
    if (selectedPlugin != null) {
      fillMenu(printMenu, selectedPlugin.getPrintActions());
    }
  }

  private static void buildOpenSubMenu(final JMenu importMenu) {
    UIManager.SERIES_VIEWER_FACTORIES.forEach(d -> fillMenu(importMenu, d.getOpenActions()));
  }

  private static void buildImportSubMenu(final JMenu importMenu) {
    UIManager.EXPLORER_PLUGINS.forEach(d -> fillMenu(importMenu, d.getOpenImportDialogAction()));
  }

  private void buildExportSubMenu(final JMenu exportMenu) {
    if (selectedPlugin != null) {
      fillMenu(exportMenu, selectedPlugin.getExportActions());
    }
    UIManager.EXPLORER_PLUGINS.forEach(d -> fillMenu(exportMenu, d.getOpenExportDialogAction()));
  }

  private static void fillMenu(final JMenu menu, List<Action> actions) {
    Optional.ofNullable(actions)
        .ifPresent(
            l ->
                l.forEach(
                    a -> {
                      JMenuItem item = new JMenuItem(a);
                      GuiUtils.applySelectedIconEffect(item);
                      menu.add(item);
                    }));
  }

  private void buildSelectedPluginMenu(final JMenu selectedPluginMenu) {
    if (selectedPlugin != null) {
      selectedPlugin.fillSelectedPluginMenu(selectedPluginMenu);
    }
  }

  private void buildMenuView() {
    menuView.removeAll();

    DynamicMenu toolBarMenu =
        new DynamicMenu(Messages.getString("WeasisWin.toolbar")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildToolBarSubMenu(this);
          }
        };
    toolBarMenu.addPopupMenuListener();
    menuView.add(toolBarMenu);

    DynamicMenu toolMenu =
        new DynamicMenu(Messages.getString("WeasisWin.tools")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildToolSubMenu(this);
          }
        };
    toolMenu.addPopupMenuListener();
    menuView.add(toolMenu);

    DynamicMenu explorerMenu = new DynamicMenu("Explorer") { // NON-NLS

          @Override
          public void popupMenuWillBecomeVisible() {
            buildExplorerSubMenu(this);
          }
        };
    explorerMenu.addPopupMenuListener();
    menuView.add(explorerMenu);
  }

  private void buildMenuFile() {
    menuFile.removeAll();
    DynamicMenu openMenu =
        new DynamicMenu(Messages.getString("WeasisWin.open")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildOpenSubMenu(this);
          }
        };
    openMenu.addPopupMenuListener();
    menuFile.add(openMenu);

    DynamicMenu importMenu =
        new DynamicMenu(Messages.getString("WeasisWin.import")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildImportSubMenu(this);
          }
        };
    importMenu.addPopupMenuListener();
    menuFile.add(importMenu);

    DynamicMenu exportMenu =
        new DynamicMenu(Messages.getString("WeasisWin.export")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildExportSubMenu(this);
          }
        };
    exportMenu.addPopupMenuListener();

    menuFile.add(exportMenu);
    menuFile.add(new JSeparator());
    DynamicMenu printMenu =
        new DynamicMenu(Messages.getString("WeasisWin.print")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildPrintSubMenu(this);
          }
        };
    printMenu.addPopupMenuListener();
    menuFile.add(printMenu);

    menuFile.add(new JSeparator());
    Consumer<ActionEvent> prefAction =
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(getRootPaneContainer());
          PreferenceDialog dialog = new PreferenceDialog(getFrame());
          ColorLayerUI.showCenterScreen(dialog, layer);
        };
    DefaultAction preferencesAction =
        new DefaultAction(
            org.weasis.core.ui.Messages.getString("OpenPreferencesAction.title"), prefAction);
    preferencesAction.putValue(
        Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK));
    menuFile.add(new JMenuItem(preferencesAction));

    menuFile.add(new JSeparator());
    DefaultAction exitAction =
        new DefaultAction(Messages.getString("ExitAction.title"), e -> closeWindow());
    menuFile.add(new JMenuItem(exitAction));
  }

  private class SequenceHandler extends TransferHandler {

    public SequenceHandler() {
      super("series"); // NON-NLS
    }

    @Override
    public Transferable createTransferable(JComponent comp) {
      return null;
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDrop()) {
        return false;
      }
      return support.isDataFlavorSupported(Series.sequenceDataFlavor)
          || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
          || support.isDataFlavorSupported(UriListFlavor.flavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }

      Transferable transferable = support.getTransferable();

      List<File> files = null;
      // Not supported by some OS
      if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        try {
          files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
        } catch (Exception e) {
          LOGGER.error("Get draggable files", e);
        }
        return dropFiles(files, support.getDropLocation());
      }
      // When dragging a file or group of files
      // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
      else if (support.isDataFlavorSupported(UriListFlavor.flavor)) {
        try {
          // Files with spaces in the filename trigger an error
          // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
          String val = (String) transferable.getTransferData(UriListFlavor.flavor);
          files = UriListFlavor.textURIListToFileList(val);
        } catch (Exception e) {
          LOGGER.error("Get draggable URIs", e);
        }
        return dropFiles(files, support.getDropLocation());
      }

      Series seq;
      try {
        seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
        if (seq == null) {
          return false;
        }

        synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
          for (final SeriesViewerFactory factory : UIManager.SERIES_VIEWER_FACTORIES) {
            if (factory.canReadMimeType(seq.getMimeType())) {
              DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
              if (model instanceof TreeModel treeModel) {
                ArrayList<MediaSeries<MediaElement>> list = new ArrayList<>(1);
                list.add(seq);
                ViewerPluginBuilder builder = new ViewerPluginBuilder(factory, list, model, null);
                openSeriesInViewerPlugin(
                    builder, treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin()));
              } else {
                ViewerPluginBuilder.openSequenceInDefaultPlugin(
                    seq, model == null ? ViewerPluginBuilder.DefaultDataModel : model, true, true);
              }
              break;
            }
          }
        }

      } catch (Exception e) {
        LOGGER.error("Open series", e);
        return false;
      }
      return true;
    }
  }

  protected boolean dropFiles(final List<File> files, DropLocation dropLocation) {
    if (files != null) {
      List<DataExplorerView> explorers = new ArrayList<>(UIManager.EXPLORER_PLUGINS);
      for (int i = explorers.size() - 1; i >= 0; i--) {
        if (!explorers.get(i).canImportFiles()) {
          explorers.remove(i);
        }
      }

      final List<File> dirs = new ArrayList<>();
      Map<Codec, List<File>> codecs = new HashMap<>();
      for (File file : files) {
        if (file.isDirectory()) {
          dirs.add(file);
          continue;
        }
        MediaReader reader = ViewerPluginBuilder.getMedia(file, false);
        if (reader != null) {
          Codec c = reader.getCodec();
          if (c != null) {
            List<File> cFiles = codecs.computeIfAbsent(c, k -> new ArrayList<>());
            cFiles.add(file);
          }
        }
      }

      if (!dirs.isEmpty() && !explorers.isEmpty()) {
        importInExplorer(explorers, dirs, dropLocation);
      }

      for (Entry<Codec, List<File>> entry : codecs.entrySet()) {
        final List<File> vals = entry.getValue();

        List<DataExplorerView> exps = new ArrayList<>();
        for (final DataExplorerView dataExplorerView : explorers) {
          DataExplorerModel model = dataExplorerView.getDataExplorerModel();
          if (model != null) {
            List<Codec> cList = model.getCodecPlugins();
            if (cList != null && cList.contains(entry.getKey())) {
              exps.add(dataExplorerView);
            }
          }
        }

        if (exps.isEmpty()) {
          for (File file : vals) {
            ViewerPluginBuilder.openSequenceInDefaultPlugin(file, true, true);
          }
        } else {
          importInExplorer(exps, vals, dropLocation);
        }
      }
      return true;
    }
    return false;
  }

  private void importInExplorer(
      List<DataExplorerView> exps, final List<File> vals, DropLocation dropLocation) {
    if (exps.size() == 1) {
      exps.get(0).importFiles(vals.toArray(new File[0]), true);
    } else {
      Point p;
      if (dropLocation == null) {
        Rectangle b = WeasisWin.this.getFrame().getBounds();
        p = new Point((int) b.getCenterX(), (int) b.getCenterY());
      } else {
        p = dropLocation.getDropPoint();
      }

      JPopupMenu popup = new JPopupMenu();

      for (final DataExplorerView dataExplorerView : exps) {
        JMenuItem item = new JMenuItem(dataExplorerView.getUIName(), dataExplorerView.getIcon());
        GuiUtils.applySelectedIconEffect(item);
        item.addActionListener(e -> dataExplorerView.importFiles(vals.toArray(new File[0]), true));
        popup.add(item);
      }

      popup.show(WeasisWin.this.getFrame(), p.x, p.y);
    }
  }

  public static class HidingEclipseThemeConnector extends CommonEclipseThemeConnector {
    public HidingEclipseThemeConnector(CControl control) {
      super(control);
    }

    @Override
    protected EclipseTabDockActionLocation getLocation(CAction action, EclipseTabStateInfo tab) {
      if (action instanceof CCloseAction) {
        /*
         * By redefining the behavior of the close-action, we can hide it if the tab is not selected
         */
        if (tab.isSelected()) {
          return EclipseTabDockActionLocation.TAB;
        } else {
          return EclipseTabDockActionLocation.HIDDEN;
        }
      }
      return super.getLocation(action, tab);
    }
  }

  public void info(String[] argv) {
    final String[] usage = {
      "Show information about Weasis", // NON-NLS
      "Usage: weasis:info (-v | -a)", // NON-NLS
      "  -v --version    show version", // NON-NLS
      "  -a --all        show weasis specifications", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };

    Option opt = Options.compile(usage).parse(argv);

    if (opt.isSet("version")) {
      System.out.println(AppProperties.WEASIS_VERSION);
    } else if (opt.isSet("all")) { // NON-NLS
      PrintStream out = System.out;
      out.println("  " + AppProperties.WEASIS_NAME + " " + AppProperties.WEASIS_VERSION);
      out.println("  Installation path: " + AppProperties.WEASIS_PATH); // NON-NLS
      out.println("  Path for temporary files: " + AppProperties.APP_TEMP_DIR); // NON-NLS
      out.println("  Profile: " + AppProperties.WEASIS_PROFILE); // NON-NLS
      out.println("  User: " + AppProperties.WEASIS_USER); // NON-NLS
      out.println("  OSGI native specs: " + System.getProperty("native.library.spec")); // NON-NLS
      out.format(
          "  Operating system: %s %s %s", // NON-NLS
          System.getProperty("os.name"),
          System.getProperty("os.version"),
          System.getProperty("os.arch"));
      out.println();
      out.println("  Java vendor: " + System.getProperty("java.vendor")); // NON-NLS
      out.println("  Java version: " + System.getProperty("java.version")); // NON-NLS
      out.println("  Java Path: " + System.getProperty("java.home")); // NON-NLS
    } else {
      opt.usage();
    }
  }

  public void ui(String[] argv) {
    final String[] usage = {
      "Manage user interface", // NON-NLS
      "Usage: weasis:ui (-q | -v)", // NON-NLS
      "  -q --quit        shutdown Weasis", // NON-NLS
      "  -v --visible     set window on top", // NON-NLS
      "  -? --help        show help" // NON-NLS
    };

    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("quit")) { // NON-NLS
      System.exit(0);
    } else if (opt.isSet("visible")) { // NON-NLS
      GuiExecutor.instance()
          .execute(
              () -> {
                Frame app = getFrame();
                app.setVisible(true);
                int state = app.getExtendedState();
                state &= ~Frame.ICONIFIED;
                app.setExtendedState(state);
                app.setVisible(true);
                /*
                 * Sets the window to be "always on top" instead using toFront() method that does not always bring the
                 * window to the front. It depends on the platform, Windows XP or Ubuntu has the facility to prevent
                 * windows from stealing focus; instead it flashes the taskbar icon.
                 */
                if (app.isAlwaysOnTopSupported()) {
                  app.setAlwaysOnTop(true);

                  try {
                    Thread.sleep(500L);
                    Robot robot = new Robot();
                    Point old = MouseInfo.getPointerInfo().getLocation();
                    Point p = app.getLocationOnScreen();
                    int x = p.x + app.getWidth() / 2;
                    int y = p.y + app.getHeight() / 2;
                    robot.mouseMove(x, y);
                    // Simulate a mouse click
                    robot.mousePress(InputEvent.BUTTON1_MASK);
                    robot.mouseRelease(InputEvent.BUTTON1_MASK);
                    robot.mouseMove(old.x, old.y);
                  } catch (AWTException e1) {
                    // DO nothing
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    app.setAlwaysOnTop(false);
                  }

                } else {
                  app.toFront();
                }
              });

    } else {
      opt.usage();
    }
  }
}
