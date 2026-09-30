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

import static java.time.temporal.ChronoUnit.SECONDS;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemInfo;
import jakarta.json.JsonException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Taskbar;
import java.awt.Taskbar.Feature;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler.DropLocation;
import javax.swing.WindowConstants;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.LicenseTabFactory;
import org.weasis.core.api.gui.util.AbstractTabLicense;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.ShortcutTable;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.LogoIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.ImportExportFooter;
import org.weasis.core.ui.docking.MiniStrip;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.ws.Workspace;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.MediaFactory;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.TabFocusPolicy;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.launcher.Launcher.Type;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.CaptureButton;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.MaskingIndicator;
import org.weasis.core.ui.util.MaskingProfileSelector;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.StringUtil;

public class WeasisWin {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWin.class);

  public static final List<String> functions = List.of("info", "ui"); // NON-NLS

  private final JMenu menuFile = new JMenu(Messages.getString("WeasisWin.file"));
  private final JMenu menuView = new JMenu(Messages.getString("WeasisWin.display"));
  private final JMenu menuTools = new JMenu(Messages.getString("WeasisWin.tools"));
  private JCheckBoxMenuItem anonymizeMenuItem;
  private final DynamicMenu menuSelectedPlugin =
      new DynamicMenu("") {

        @Override
        public void popupMenuWillBecomeVisible() {
          buildSelectedPluginMenu(this);
        }
      };
  private ViewerPlugin<?> selectedPlugin = null;

  private final List<Runnable> runOnClose = new ArrayList<>();
  private final ShortcutTable<Integer> tabShifts =
      new ShortcutTable<Integer>()
          .on(ShortcutManager.ID_DOCKING_NEXT_TAB, 1)
          .on(ShortcutManager.ID_DOCKING_PREV_TAB, -1);
  private final ShortcutTable<Consumer<ViewerPlugin<?>>> dockingActions = buildDockingActions();

  private final Frame frame;
  private final RootPaneContainer rootPaneContainer;

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
            if (canBeClosed()) {
              closeAllRunnable();
            }
          }
        });
    rootPaneContainer = jFrame;
    configureMaskingModel();

    workspace()
        .addListener(
            (view, window) -> {
              if (view instanceof ViewerPlugin<?> viewer) {
                setSelectedPlugin(viewer);
              }
            });
    KeyEventDispatcher dockingShortcutDispatcher = this::dispatchDockingShortcut;
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(dockingShortcutDispatcher);

    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.menu.menubar", true)) {
      rootPaneContainer.getRootPane().setJMenuBar(createMenuBar());
    }
    setSelectedPlugin(null);

    var toolbarContainer = GuiUtils.getUICore().getToolbarContainer();
    var scrollPane =
        new JScrollPane(toolbarContainer) {
          @Override
          public Dimension getPreferredSize() {
            Dimension containerPreferredSize = toolbarContainer.getPreferredSize();
            int scrollBarHeight = getScrollBarHeight(containerPreferredSize);
            return new Dimension(
                super.getPreferredSize().width, containerPreferredSize.height + scrollBarHeight);
          }

          private int getScrollBarHeight(Dimension containerPreferredSize) {
            JScrollBar hScrollBar = getHorizontalScrollBar();
            int scrollBarHeight = 0;

            if (hScrollBar != null) {
              int availableWidth = getViewport().getWidth();
              int contentWidth = containerPreferredSize.width;

              if (availableWidth > 0 && contentWidth > availableWidth) {
                scrollBarHeight = hScrollBar.getPreferredSize().height;
              } else if (hScrollBar.isVisible()) {
                scrollBarHeight = hScrollBar.getPreferredSize().height;
              }
            }
            return scrollBarHeight;
          }

          @Override
          public Dimension getMinimumSize() {
            Dimension containerMinSize = toolbarContainer.getMinimumSize();
            JScrollBar hScrollBar = getHorizontalScrollBar();
            int scrollBarHeight = 0;
            if (hScrollBar != null && hScrollBar.isVisible()) {
              scrollBarHeight = hScrollBar.getPreferredSize().height;
            }
            return new Dimension(
                super.getMinimumSize().width, containerMinSize.height + scrollBarHeight);
          }
        };

    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    rootPaneContainer.getContentPane().add(scrollPane, BorderLayout.NORTH);

    rootPaneContainer.setGlassPane(AppProperties.glassPane);

    updateTitle();
    IdentityMask.addChangeListener(() -> GuiExecutor.execute(this::onMaskingChanged));

    LogoIcon logoIcon =
        AppProperties.WEASIS_NAME.endsWith("Dicomizer") // NON-NLS
            ? LogoIcon.SMALL_DICOMIZER
            : LogoIcon.SMALL;
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
    return GuiUtils.getUICore().getToolbarContainer();
  }

  public boolean canBeClosed() {
    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty(UICore.CONFIRM_CLOSE, false)) {
      int option = JOptionPane.showConfirmDialog(frame, Messages.getString("WeasisWin.exit_mes"));
      return option == JOptionPane.YES_OPTION;
    } else {
      return true;
    }
  }

  void closeAllRunnable() {
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    localPersistence.putIntProperty("last.window.state", frame.getExtendedState());
    Rectangle rect = frame.getBounds();
    localPersistence.putIntProperty("last.window.x", rect.x);
    localPersistence.putIntProperty("last.window.y", rect.y);
    localPersistence.putIntProperty("last.window.width", rect.width);
    localPersistence.putIntProperty("last.window.height", rect.height);

    GuiUtils.getUICore().savePreferences();

    for (Runnable onClose : runOnClose) {
      try {
        onClose.run();
      } catch (Exception e) {
        LOGGER.error("Error while closing", e);
      }
    }
    System.exit(0);
  }

  public void runOnClose(Runnable run) {
    runOnClose.add(run);
  }

  private static WorkspaceManager workspace() {
    return GuiUtils.getUICore().getWorkspaceManager();
  }

  public void createMainPanel() {
    runOnClose(() -> workspace().disposeAll());
    workspace()
        .setDragEnabled(
            GuiUtils.getUICore()
                .getSystemPreferences()
                .getBooleanProperty("weasis.docking.drag", true));

    // Three-zone layout: explorers on the left, the central workspace (viewers only) in the
    // middle, and the plugin tools on the right. The side panels are NOT part of the workspace
    // tree, so viewers can only be tabbed/split within the centre.
    Workspace centerArea = GuiUtils.getUICore().getCenterWorkspace();
    centerArea.setTransferHandler(new SeriesHandler());

    SidePanel left = GuiUtils.getUICore().getLeftSidePanel();
    SidePanel right = GuiUtils.getUICore().getRightSidePanel();
    MiniStrip miniStrip = GuiUtils.getUICore().getMiniStrip();

    // the central area and the fixed MiniTool strip sit together (mini immediately right of centre)
    JPanel centreWithMini = new JPanel(new BorderLayout());
    centreWithMini.add(centerArea, BorderLayout.CENTER);
    centreWithMini.add(miniStrip, BorderLayout.EAST);

    // right tools: the resizable content is the split's right child
    JSplitPane innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centreWithMini, right);
    innerSplit.setResizeWeight(1.0); // the centre absorbs resizing, the right content stays fixed
    innerSplit.setContinuousLayout(true);
    innerSplit.setBorder(null);
    right.attachTo(innerSplit, false);
    JPanel rightGroup = new JPanel(new BorderLayout());
    rightGroup.add(innerSplit, BorderLayout.CENTER);
    rightGroup.add(right.getStripe(), BorderLayout.EAST);

    // left explorers: resizable content is the split's left child
    JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightGroup);
    outerSplit.setResizeWeight(0.0); // the left content stays fixed, the rest absorbs resizing
    outerSplit.setContinuousLayout(true);
    outerSplit.setBorder(null);
    left.attachTo(outerSplit, true);

    // bottom footer (import/export): its panel spans the full window width under the three zones
    ImportExportFooter footer = new ImportExportFooter();
    JSplitPane footerSplit =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, outerSplit, footer.getPanelHost());
    footerSplit.setResizeWeight(1.0); // the viewing area absorbs resizing, the footer stays fixed
    footerSplit.setContinuousLayout(true);
    footerSplit.setBorder(null);
    footer.attachTo(footerSplit);

    JPanel leftEdge = new JPanel(new BorderLayout());
    leftEdge.add(left.getStripe(), BorderLayout.CENTER);
    leftEdge.add(footer, BorderLayout.SOUTH);
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(leftEdge, BorderLayout.WEST);
    mainPanel.add(footerSplit, BorderLayout.CENTER);

    rootPaneContainer.getContentPane().add(mainPanel, BorderLayout.CENTER);

    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    boolean updateRelease = preferences.getBooleanProperty("weasis.update.release", true);
    boolean showDownloadRelease =
        preferences.getBooleanProperty("weasis.show.update.next.release", true);
    if (updateRelease && showDownloadRelease) {
      CompletableFuture.runAsync(() -> checkReleaseUpdate(frame));
    }
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
    ViewerOpenOptions opts = builder.getViewerOpenOptions();
    ViewerPlacement placement = opts.placement();
    TabFocusPolicy focusPolicy = opts.tabFocusPolicy();

    // -- ReuseViewer: try to reuse an existing viewer matching the same group --
    if (placement instanceof ViewerPlacement.ReuseViewer reuse && group != null) {
      List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
      synchronized (viewerPlugins) {
        for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
          final ViewerPlugin<?> p = viewerPlugins.get(i);
          // Remove the views not attached to any window (Fix bugs with external window)
          if (WinUtil.getParentWindow(p) == null) {
            viewerPlugins.remove(i);
            continue;
          }
          if (p instanceof ImageViewerPlugin viewer
              && p.getName().equals(factory.getUIName())
              && group.equals(p.getGroupID())) {
            if (reuse.openInSelection() && seriesList.size() == 1) {
              viewer.addSeries(seriesList.getFirst());
            } else {
              viewer.addSeriesList(seriesList, reuse.bestDefaultLayout());
            }
            if (focusPolicy.shouldBringToFront()) {
              viewer.setSelectedAndGetFocus();
            }
            return;
          }
        }
      }
    }

    // -- No existing viewer found or non-reuse strategy: create a new viewer --
    ViewerOpenOptions factoryOpts =
        seriesList.size() > 1 ? opts.withSeriesCount(seriesList.size()) : opts;
    SeriesViewer<?> seriesViewer = factory.createSeriesViewer(factoryOpts, model);
    if (seriesViewer instanceof MimeSystemAppViewer) {
      for (MediaSeries m : seriesList) {
        seriesViewer.addSeries(m);
      }
    } else if (seriesViewer instanceof ViewerPlugin<?> viewer) {
      if (factory.canExternalizeSeries()) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();
        if (gd.length > 1) {
          viewer.setDetachable(true);
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
        MediaSeriesGroup titleGroup = group;
        viewer.setGroupID(group);
        viewer.setPluginName(titleGroup::toString);
      }

      // Override default plugin icon
      Icon pluginIcon = opts.icon();
      if (pluginIcon != null) {
        viewer.setIcon(pluginIcon);
      }

      // Apply placement-specific configuration
      boolean openInSelection = false;
      boolean registered;
      if (placement instanceof ViewerPlacement.External ext) {
        registered = registerDetachWindow(viewer, ext.externalDisplay());
      } else {
        if (placement instanceof ViewerPlacement.ReuseViewer reuse) {
          openInSelection = reuse.openInSelection();
        }
        registered = registerPlugin(viewer, opts);
      }

      if (registered) {
        boolean bringToFront = focusPolicy.shouldBringToFront();
        if (bringToFront) {
          viewer.setSelectedAndGetFocus();
        }
        if (seriesViewer instanceof ImageViewerPlugin) {
          if (!openInSelection) {
            ((ImageViewerPlugin) viewer).selectLayoutPositionForAddingSeries(seriesList);
          }
        }
        for (MediaSeries m : seriesList) {
          viewer.addSeries(m);
        }
        if (bringToFront) {
          viewer.setSelected(true);
        }
      } else {
        viewer.close();
        viewer.handleFocusAfterClosing();
      }
    }
  }

  /**
   * Registers a viewer in its own floating window on the given external display. Mirrors {@link
   * #registerPlugin(ViewerPlugin, ViewerOpenOptions)} but, instead of docking into the main area,
   * detaches the viewer into a new floating window positioned over the target screen.
   */
  private boolean registerDetachWindow(ViewerPlugin<?> plugin, ExternalDisplay extDisplay) {
    if (plugin == null
        || extDisplay == null
        || GuiUtils.getUICore().getViewerPlugins().contains(plugin)) {
      return false;
    }
    GuiUtils.getUICore().getViewerPlugins().add(plugin);
    workspace().detach(plugin, extDisplay.screenBounds());
    return true;
  }

  public boolean registerPlugin(ViewerPlugin plugin, ViewerOpenOptions opts) {
    if (plugin == null || GuiUtils.getUICore().getViewerPlugins().contains(plugin)) {
      return false;
    }
    plugin.showDockable(opts);
    return true;
  }

  public boolean registerPlugin(ViewerPlugin plugin) {
    return registerPlugin(plugin, ViewerOpenOptions.defaults());
  }

  public synchronized ViewerPlugin getSelectedPlugin() {
    return selectedPlugin;
  }

  public synchronized void setSelectedPlugin(ViewerPlugin plugin) {
    if (plugin == null) {
      GuiUtils.getUICore()
          .getToolbarContainer()
          .registerToolBar(GuiUtils.getUICore().getExplorerPluginToolbars());
      List<DockableTool> oldTool =
          selectedPlugin == null ? null : selectedPlugin.getSeriesViewerUI().tools;
      if (oldTool != null) {
        for (DockableTool p : oldTool) {
          p.closeDockable();
        }
      }
      selectedPlugin = null;
      GuiUtils.getUICore().setSelectedPlugin(null);
      return;
    }
    if (selectedPlugin == plugin) {
      plugin.requestFocusInWindow();
      selectedPlugin.setSelected(true);
      return;
    }
    ViewerPlugin<?> oldPlugin = selectedPlugin;
    if (oldPlugin != null) {
      oldPlugin.setSelected(false);
    }
    selectedPlugin = plugin;
    GuiUtils.getUICore().setSelectedPlugin(plugin);
    menuSelectedPlugin.setText(selectedPlugin.getName());

    SeriesViewerUI.updateTools(oldPlugin, selectedPlugin, false);
    SeriesViewerUI.updateToolbars(oldPlugin, selectedPlugin, false);

    selectedPlugin.setSelected(true);
  }

  /**
   * Global handler for the docking keyboard shortcuts (next/previous tab, maximize, normalize,
   * externalize and close) acting on the currently selected viewer.
   */
  private boolean dispatchDockingShortcut(KeyEvent e) {
    if (e.getID() != KeyEvent.KEY_PRESSED) {
      return false;
    }
    Optional<Integer> tabShift = tabShifts.find(e);
    if (tabShift.isPresent()) {
      return cycleViewer(tabShift.get());
    }
    ViewerPlugin<?> plugin = getSelectedPlugin();
    if (plugin == null) {
      return false;
    }
    Optional<Consumer<ViewerPlugin<?>>> action = dockingActions.find(e);
    action.ifPresent(a -> a.accept(plugin));
    return action.isPresent();
  }

  /** Docking shortcuts acting on the selected viewer. */
  private ShortcutTable<Consumer<ViewerPlugin<?>>> buildDockingActions() {
    return new ShortcutTable<Consumer<ViewerPlugin<?>>>()
        .on(ShortcutManager.ID_DOCKING_MAXIMIZE, this::toggleMaximized)
        .on(ShortcutManager.ID_DOCKING_NORMALIZE, plugin -> workspace().restore(plugin))
        .on(
            ShortcutManager.ID_DOCKING_EXTERNALIZE,
            plugin -> {
              if (plugin.isDetachable()) {
                workspace().detach(plugin, null);
              }
            })
        .on(
            ShortcutManager.ID_DOCKING_CLOSE,
            plugin -> {
              if (plugin.isClosable()) {
                plugin.close();
                plugin.handleFocusAfterClosing();
              }
            });
  }

  private void toggleMaximized(ViewerPlugin<?> plugin) {
    WorkspaceManager control = workspace();
    if (control.isMaximized(plugin)) {
      control.restore(plugin);
    } else {
      control.maximize(plugin);
    }
  }

  private boolean cycleViewer(int delta) {
    WorkspaceManager control = workspace();
    List<ViewerPlugin<?>> docked =
        GuiUtils.getUICore().getViewerPlugins().stream().filter(control::isShowing).toList();
    if (docked.size() < 2) {
      return false;
    }
    int index = docked.indexOf(getSelectedPlugin());
    if (index < 0) {
      index = 0;
    }
    docked.get(Math.floorMod(index + delta, docked.size())).setSelectedAndGetFocus();
    return true;
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

    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    int lastState = localPersistence.getIntProperty("last.window.state", Frame.MAXIMIZED_BOTH);
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
        int x = localPersistence.getIntProperty("last.window.x", 0);
        int y = localPersistence.getIntProperty("last.window.y", 0);
        int w = localPersistence.getIntProperty("last.window.width", Integer.MAX_VALUE);
        int h = localPersistence.getIntProperty("last.window.height", Integer.MAX_VALUE);
        Rectangle lastWin = new Rectangle(x, y, w, h);
        Rectangle intersection = lastWin.intersection(b);
        int localArea = lastWin.width * lastWin.height;
        int intersectionArea = intersection.width * intersection.height;

        // Only If window is more than 25% within the screen
        if (intersectionArea >= 0.25 * localArea) {
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
    buildMenuTools();
    menuBar.add(menuTools);
    menuBar.add(menuSelectedPlugin);
    menuSelectedPlugin.addPopupMenuListener();

    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();

    final JMenu helpMenuItem = new JMenu(Messages.getString("WeasisWin.help"));
    String helpPrefURL = preferences.getProperty("weasis.help.url");

    // Exception in Priority order >> config property overrides Java System parameter
    if (helpPrefURL == null) helpPrefURL = System.getProperty("weasis.help.url");
    final String helpURL = helpPrefURL;

    if (helpURL != null) {
      final JMenuItem helpContentMenuItem = new JMenuItem(Messages.getString("WeasisWin.guide"));
      helpContentMenuItem.addActionListener(e -> openBrowser(helpContentMenuItem, helpURL));
      helpMenuItem.add(helpContentMenuItem);
    }

    final JMenuItem webMenuItem = new JMenuItem(Messages.getString("WeasisWin.shortcuts"));
    webMenuItem.addActionListener(
        _ -> {
          try {
            Path htmlFile =
                ShortcutManager.getInstance().writeHtmlToTempFile(AppProperties.APP_TEMP_DIR);
            GuiUtils.openInDefaultBrowser(webMenuItem, htmlFile.toUri());
          } catch (IOException ex) {
            LOGGER.error("Cannot generate shortcuts page", ex);
          }
        });
    helpMenuItem.add(webMenuItem);

    final JMenuItem websiteMenuItem =
        new JMenuItem(
            Messages.getString("WeasisWin.online"), ResourceUtil.getIcon(ActionIcon.HELP));
    GuiUtils.applySelectedIconEffect(websiteMenuItem);
    websiteMenuItem.addActionListener(
        e -> openBrowser(websiteMenuItem, GuiUtils.getOnlineHelpUrl(null)));
    helpMenuItem.add(websiteMenuItem);
    helpMenuItem.add(new JSeparator());

    final JMenuItem updateMenuItem = new JMenuItem(Messages.getString("check.for.updates"));
    updateMenuItem.addActionListener(
        e -> {
          Release release = getLastRelease();
          if (release != null) {
            Semver vOld = AppProperties.getVersion(AppProperties.WEASIS_VERSION);
            Semver vNew = AppProperties.getVersion(release.version());
            if (vNew.compareTo(vOld) > 0) {
              openBrowser(updateMenuItem, release.url());
            } else {
              GuiExecutor.execute(
                  () ->
                      JOptionPane.showMessageDialog(
                          WinUtil.getValidComponent(updateMenuItem),
                          Messages.getString("current.release.latest"),
                          Messages.getString("update"),
                          JOptionPane.INFORMATION_MESSAGE));
            }
          }
        });
    helpMenuItem.add(updateMenuItem);

    final JMenuItem openLogFolderMenuItem =
        new JMenuItem(Messages.getString("open.logging.folder"));
    openLogFolderMenuItem.addActionListener(
        _ ->
            GuiUtils.openSystemExplorer(
                openLogFolderMenuItem, AppProperties.WEASIS_LOG_PATH.resolve("log")));
    helpMenuItem.add(openLogFolderMenuItem);

    final JMenuItem reportMenuItem = new JMenuItem(Messages.getString("submit.bug.report"));
    reportMenuItem.addActionListener(
        e -> openBrowser(reportMenuItem, "https://github.com/nroduit/Weasis/issues"));
    helpMenuItem.add(reportMenuItem);
    helpMenuItem.add(new JSeparator());
    WProperties p = GuiUtils.getUICore().getSystemPreferences();
    if (p.getBooleanProperty("weasis.plugins.license", false)) {
      final JMenuItem licencesMenuItem = new JMenuItem(Messages.getString("LicencesDialog.title"));
      licencesMenuItem.addActionListener(
          e -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootPaneContainer);
            final List<AbstractTabLicense> list = new ArrayList<>();
            try {
              // Get all LicenseTabFactory extensions from PF4J plugin manager
              List<LicenseTabFactory> factories =
                  GuiUtils.getUICore().getPluginManager().getExtensions(LicenseTabFactory.class);
              Map<String, Object> properties = new HashMap<>();
              for (LicenseTabFactory factory : factories) {
                if (factory != null) {
                  AbstractTabLicense page = factory.createInstance(properties);
                  if (page != null) {
                    list.add(page);
                  }
                }
              }
            } catch (Exception ex) {
              LOGGER.error("Get License from plugin manager", ex);
            }
            if (list.isEmpty()) {
              JOptionPane.showMessageDialog(
                  WinUtil.getValidComponent(licencesMenuItem),
                  Messages.getString("LicencesDialog.no.licence"),
                  Messages.getString("LicencesDialog.title"),
                  JOptionPane.INFORMATION_MESSAGE);
              layer.hideUI();
            } else {
              LicencesDialog dialog = new LicencesDialog(getFrame(), list);
              ColorLayerUI.showCenterScreen(dialog, layer);
            }
          });
      helpMenuItem.add(licencesMenuItem);
      helpMenuItem.add(new JSeparator());
    }

    final JMenuItem resourcesMenuItem = new JMenuItem(Messages.getString("ResourceMonitor.title"));
    resourcesMenuItem.addActionListener(
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootPaneContainer);
          ResourceMonitorDialog dialog = new ResourceMonitorDialog(getFrame());
          ColorLayerUI.showCenterScreen(dialog, layer);
        });
    helpMenuItem.add(resourcesMenuItem);

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
    menuBar.add(Box.createHorizontalGlue());
    menuBar.add(
        new CaptureButton(
            () -> selectedPlugin == null ? List.of() : selectedPlugin.getExportActions()));
    menuBar.add(new MaskingIndicator());
    return menuBar;
  }

  private Release getRelease(String body) {
    try {
      Release release = Release.fromJson(JsonUtil.readObject(body));
      if (StringUtil.hasText(release.version())) {
        return release;
      }
      LOGGER.error("No version in the release response: {}", body);
    } catch (JsonException ex) {
      LOGGER.error("Cannot read the json response", ex);
    }
    return null;
  }

  private Release getLastRelease() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI("https://nroduit.github.io/en/api/release/api.json"))
              .timeout(Duration.of(10, SECONDS))
              .GET()
              .build();

      HttpResponse<String> response =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.ALWAYS)
              .build()
              .send(request, BodyHandlers.ofString());
      return getRelease(response.body());
    } catch (IOException | URISyntaxException ex) {
      LOGGER.error("Cannot check release update", ex);
    } catch (InterruptedException ex2) {
      Thread.currentThread().interrupt();
    }
    return null;
  }

  private void checkReleaseUpdate(Component parent) {
    Release release = getLastRelease();
    if (release != null) {
      Semver vOld = AppProperties.getVersion(AppProperties.WEASIS_VERSION);
      Semver vNew = AppProperties.getVersion(release.version());
      if (vNew.compareTo(vOld) > 0) {
        GuiExecutor.execute(
            () -> {
              JLabel label = new JLabel(Messages.getString("new.release.available"));
              label.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
              JCheckBox dontAskMeAgain = new JCheckBox(Messages.getString("don.t.ask.again"));
              dontAskMeAgain.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
              JPanel panel = GuiUtils.getVerticalBoxLayoutPanel(label, dontAskMeAgain);
              int confirm =
                  JOptionPane.showConfirmDialog(
                      parent, panel, Messages.getString("update"), JOptionPane.YES_NO_OPTION);
              if (confirm == 0) {
                openBrowser(parent, release.url());
              }
              if (dontAskMeAgain.isSelected()) {
                GuiUtils.getUICore()
                    .getSystemPreferences()
                    .putBooleanProperty("weasis.show.update.next.release", false);
              }
            });
      }
    }
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
    List<Toolbar> bars = GuiUtils.getUICore().getToolbarContainer().getRegisteredToolBars();
    for (final Toolbar bar : bars) {
      if (!Insertable.Type.EMPTY.equals(bar.getPanelType())) {
        JCheckBoxMenuItem item =
            new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
        item.addActionListener(
            e -> {
              if (e.getSource() instanceof JCheckBoxMenuItem menuItem) {
                GuiUtils.getUICore()
                    .getToolbarContainer()
                    .displayToolbar(bar.getComponent(), menuItem.isSelected());
              }
            });
        toolBarMenu.add(item);
      }
    }
  }

  private void buildToolSubMenu(final JMenu toolMenu) {
    List<DockableTool> tools =
        selectedPlugin == null ? null : selectedPlugin.getSeriesViewerUI().tools;
    if (tools != null) {
      for (final DockableTool t : tools) {
        buildSubMenu(toolMenu, t);
      }
    }
  }

  private static void buildExplorerSubMenu(final JMenu explorerMenu) {
    synchronized (GuiUtils.getUICore().getExplorerPlugins()) {
      List<DataExplorerView> explorers = GuiUtils.getUICore().getExplorerPlugins();
      for (final DataExplorerView dataExplorerView : explorers) {
        if (dataExplorerView instanceof final DockableTool t) {
          buildSubMenu(explorerMenu, t);
        }
      }
    }
  }

  private static void buildSubMenu(JMenu explorerMenu, DockableTool t) {
    if (!Insertable.Type.EMPTY.equals(t.getPanelType())) {
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
    GuiUtils.getUICore()
        .getSeriesViewerFactories()
        .forEach(d -> fillMenu(importMenu, d.getOpenActions()));
  }

  private static void buildImportSubMenu(final JMenu importMenu) {
    GuiUtils.getUICore()
        .getExplorerPlugins()
        .forEach(d -> fillMenu(importMenu, d.getOpenImportDialogAction()));
  }

  private void buildExportSubMenu(final JMenu exportMenu) {
    if (selectedPlugin != null) {
      fillMenu(exportMenu, selectedPlugin.getExportActions());
    }
    GuiUtils.getUICore()
        .getExplorerPlugins()
        .forEach(d -> fillMenu(exportMenu, d.getOpenExportDialogAction()));
  }

  private void buildLauncherSubMenu(final JMenu exportMenu) {
    if (selectedPlugin instanceof ImageViewerPlugin<?> viewerPlugin) {
      fillMenu(exportMenu, Launcher.getLauncherActions(viewerPlugin.getEventManager(), null));
    } else {
      fillMenu(exportMenu, Launcher.getLauncherActions(null, Type.OTHER));
    }
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

  private void buildMenuTools() {
    menuTools.removeAll();

    anonymizeMenuItem =
        new JCheckBoxMenuItem(
            Messages.getString("WeasisWin.anonymize"), IdentityMask.isSessionMasking());
    anonymizeMenuItem.setToolTipText(Messages.getString("WeasisWin.anonymize.tip"));
    anonymizeMenuItem.addActionListener(
        _ -> {
          if (!anonymizeMenuItem.isSelected()) {
            IdentityMask.setSessionMasking(false);
            return;
          }
          MaskingProfile profile = chooseSessionProfile();
          if (profile == null) {
            anonymizeMenuItem.setSelected(false);
          } else {
            IdentityMask.setSessionMasking(true, profile);
          }
        });
    menuTools.add(anonymizeMenuItem);
  }

  /**
   * Asks which profile masks the screen, together with the reminder that anonymization only
   * substitutes what Weasis reads as a tag: burned-in pixel text and anything a plugin renders on
   * its own are untouched, so the user must verify before sharing.
   *
   * @return the chosen profile, or null when cancelled
   */
  private MaskingProfile chooseSessionProfile() {
    MaskingProfileSelector selector = MaskingProfileSelector.forSession();
    Object[] message = {
      anonymizationWarning(), GuiUtils.getFlowLayoutPanel(selector.createLabel(), selector)
    };
    int response =
        JOptionPane.showConfirmDialog(
            getFrame(),
            message,
            Messages.getString("WeasisWin.anonymize"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return response == JOptionPane.OK_OPTION ? selector.getSelectedProfile() : null;
  }

  /** The warning as HTML with a clickable Karnak link, wrapped to a readable width. */
  private static JTextPane anonymizationWarning() {
    JTextPane pane = GuiUtils.getPanelWithHyperlink(Messages.getString("WeasisWin.anonymize.warn"));
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    pane.setOpaque(false);
    int width = GuiUtils.getScaleLength(460);
    pane.setSize(width, Short.MAX_VALUE);
    pane.setPreferredSize(new Dimension(width, pane.getPreferredSize().height));
    return pane;
  }

  /** Loads the site ({@value MaskingModelRegistry#CONFIG_PROPERTY}) and user masking documents. */
  private static void configureMaskingModel() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    String prefDir = preferences.getProperty("weasis.pref.dir"); // NON-NLS
    MaskingModelRegistry.getInstance()
        .configure(
            preferences.getProperty(MaskingModelRegistry.CONFIG_PROPERTY),
            StringUtil.hasText(prefDir)
                ? Path.of(prefDir).resolve(MaskingModelRegistry.USER_FILE)
                : null);
  }

  /** Keeps the menu item and the window title in line with session masking, whoever changed it. */
  private void onMaskingChanged() {
    if (anonymizeMenuItem != null) {
      anonymizeMenuItem.setSelected(IdentityMask.isSessionMasking());
    }
    updateTitle();
  }

  private void updateTitle() {
    String title = AppProperties.WEASIS_NAME + " v" + AppProperties.WEASIS_VERSION; // NON-NLS
    if (IdentityMask.isSessionMasking()) {
      title += " — " + MaskingIndicator.label(IdentityMask.sessionProfile());
    }
    frame.setTitle(title);
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

    DynamicMenu launcherMenu =
        new DynamicMenu(Messages.getString("launcher")) {

          @Override
          public void popupMenuWillBecomeVisible() {
            buildLauncherSubMenu(this);
          }
        };
    launcherMenu.addPopupMenuListener();

    menuFile.add(launcherMenu);

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
            org.weasis.core.Messages.getString("OpenPreferencesAction.title"), prefAction);
    preferencesAction.putValue(
        Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK));
    menuFile.add(new JMenuItem(preferencesAction));

    menuFile.add(new JSeparator());
    DefaultAction exitAction =
        new DefaultAction(
            Messages.getString("ExitAction.title"),
            e -> {
              if (canBeClosed()) {
                closeAllRunnable();
              }
            });
    menuFile.add(new JMenuItem(exitAction));
  }

  private class SeriesHandler extends SequenceHandler {

    @Override
    protected boolean importDataExt(TransferSupport support) {
      try {
        Transferable transferable = support.getTransferable();
        Series<?> seq = (Series<?>) transferable.getTransferData(Series.sequenceDataFlavor);
        openSeriesInViewer(seq);
        return true;
      } catch (Exception e) {
        LOGGER.error("Failed to open series", e);
        return false;
      }
    }

    private void openSeriesInViewer(Series<?> seq) {
      List<SeriesViewerFactory> viewerFactories = GuiUtils.getUICore().getSeriesViewerFactories();
      synchronized (viewerFactories) {
        for (SeriesViewerFactory factory : viewerFactories) {
          if (factory.canReadMimeType(seq.getMimeType())) {
            openWithFactory(seq, factory);
            break;
          }
        }
      }
    }

    private void openWithFactory(Series<?> seq, SeriesViewerFactory factory) {
      DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
      if (model instanceof TreeModel treeModel) {
        ViewerPluginBuilder builder =
            new ViewerPluginBuilder(factory, List.of(seq), model, ViewerOpenOptions.defaults());
        openSeriesInViewerPlugin(
            builder, treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin()));
      } else {
        ViewerPluginBuilder.openInDefaultViewer(
            seq,
            model == null ? ViewerPluginBuilder.DefaultDataModel : model,
            ViewerOpenOptions.defaults());
      }
    }

    @Override
    protected boolean dropFiles(List<Path> files) {
      if (files == null || files.isEmpty()) {
        return false;
      }
      List<DataExplorerView> explorers = getImportableExplorers();
      List<Path> dirs = new ArrayList<>();
      Map<Codec, List<Path>> codecFiles = new HashMap<>();

      categorizeFiles(files, dirs, codecFiles);
      processDirectories(explorers, dirs);
      processCodecFiles(explorers, codecFiles);
      return true;
    }

    private List<DataExplorerView> getImportableExplorers() {
      return GuiUtils.getUICore().getExplorerPlugins().stream()
          .filter(DataExplorerView::canImportFiles)
          .toList();
    }

    private void categorizeFiles(
        List<Path> files, List<Path> dirs, Map<Codec, List<Path>> codecFiles) {
      for (Path file : files) {
        if (Files.isDirectory(file)) {
          dirs.add(file);
        } else {
          categorizeMediaFile(file, codecFiles);
        }
      }
    }

    private void categorizeMediaFile(Path file, Map<Codec, List<Path>> codecFiles) {
      MediaReader reader = MediaFactory.getMedia(file, false);
      if (reader != null) {
        Codec codec = reader.getCodec();
        if (codec != null) {
          codecFiles.computeIfAbsent(codec, _ -> new ArrayList<>()).add(file);
        }
      }
    }

    private void processDirectories(List<DataExplorerView> explorers, List<Path> dirs) {
      if (!dirs.isEmpty() && !explorers.isEmpty()) {
        importInExplorer(explorers, dirs, null);
      }
    }

    private void processCodecFiles(
        List<DataExplorerView> explorers, Map<Codec, List<Path>> codecFiles) {
      for (Entry<Codec, List<Path>> entry : codecFiles.entrySet()) {
        List<Path> files = entry.getValue();
        List<DataExplorerView> compatibleExplorers =
            findCompatibleExplorers(explorers, entry.getKey());

        if (compatibleExplorers.isEmpty()) {
          files.forEach(
              file -> ViewerPluginBuilder.openInDefaultViewer(file, ViewerOpenOptions.defaults()));
        } else {
          importInExplorer(compatibleExplorers, files, null);
        }
      }
    }

    private List<DataExplorerView> findCompatibleExplorers(
        List<DataExplorerView> explorers, Codec codec) {
      return explorers.stream().filter(explorer -> isCodecSupported(explorer, codec)).toList();
    }

    private boolean isCodecSupported(DataExplorerView explorer, Codec codec) {
      DataExplorerModel model = explorer.getDataExplorerModel();
      if (model == null) {
        return false;
      }
      var codecs = model.getCodecPlugins();
      return codecs != null && codecs.contains(codec);
    }
  }

  private void importInExplorer(
      List<DataExplorerView> explorers, List<Path> paths, DropLocation dropLocation) {
    File[] files = paths.stream().map(Path::toFile).toArray(File[]::new);

    if (explorers.size() == 1) {
      explorers.getFirst().importFiles(files, true);
    } else {
      showExplorerSelectionMenu(explorers, files, dropLocation);
    }
  }

  private void showExplorerSelectionMenu(
      List<DataExplorerView> explorers, File[] files, DropLocation dropLocation) {
    Point location = determineMenuLocation(dropLocation);
    JPopupMenu popup = new JPopupMenu();

    for (DataExplorerView explorer : explorers) {
      JMenuItem item = new JMenuItem(explorer.getUIName(), explorer.getIcon());
      GuiUtils.applySelectedIconEffect(item);
      item.addActionListener(e -> explorer.importFiles(files, true));
      popup.add(item);
    }

    popup.show(getFrame(), location.x, location.y);
  }

  private Point determineMenuLocation(DropLocation dropLocation) {
    if (dropLocation != null) {
      return dropLocation.getDropPoint();
    }
    Rectangle bounds = getFrame().getBounds();
    return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
  }

  /**
   * Brings the window to the foreground with the mechanism each platform allows. Desktops with
   * focus-stealing prevention (Windows, GNOME, any Wayland compositor) may only flag the window as
   * demanding attention: no input is simulated to bypass that.
   */
  static void bringToFront(Frame app) {
    app.setVisible(true);
    if (SystemInfo.isMacOS) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
        desktop.requestForeground(true);
      }
    } else if (SystemInfo.isWindows && (app.getExtendedState() & Frame.ICONIFIED) == 0) {
      // Windows grants the foreground to a window being restored from the taskbar
      app.setExtendedState(app.getExtendedState() | Frame.ICONIFIED);
    }
    app.setExtendedState(app.getExtendedState() & ~Frame.ICONIFIED);

    // Raises the window in the stacking order even when toFront() is refused
    if (app.isAlwaysOnTopSupported()) {
      app.setAlwaysOnTop(true);
      app.setAlwaysOnTop(false);
    }
    app.toFront();
    app.requestFocus();
  }
}
