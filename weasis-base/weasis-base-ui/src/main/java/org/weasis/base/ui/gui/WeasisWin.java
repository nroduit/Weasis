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

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import jakarta.json.JsonException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import java.awt.Window;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler.DropLocation;
import javax.swing.WindowConstants;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.command.StateBoundary;
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
import org.weasis.core.api.gui.util.KeyBinding;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.ShortcutTable;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingModel;
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
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.SettingsFileStore;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.LogoIcon;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.MiniStrip;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.docking.ToolContributions;
import org.weasis.core.ui.docking.ToolVisibility;
import org.weasis.core.ui.docking.ws.DetachedFrame;
import org.weasis.core.ui.docking.ws.ToolbarRowHost;
import org.weasis.core.ui.docking.ws.Workspace;
import org.weasis.core.ui.docking.ws.WorkspaceListener;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.ActionCatalog;
import org.weasis.core.ui.editor.MediaFactory;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.ScreenRoles;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.TabFocusPolicy;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.ViewContexts;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.GraphicEditActions;
import org.weasis.core.ui.editor.image.GraphicMouseHandler;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.launcher.Launcher.Type;
import org.weasis.core.ui.model.utils.GraphicUndoHistory;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.CaptureButton;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.CommandPalette;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.MaskingIndicator;
import org.weasis.core.ui.util.MaskingProfileSelector;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.StringUtil;
import org.weasis.launcher.Prerelease;
import org.weasis.launcher.UiOrientation;

public class WeasisWin {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWin.class);

  public static final List<String> functions = List.of("info", "ui"); // NON-NLS

  private final JMenu menuFile = new JMenu(Messages.common("file"));
  private final DynamicMenu menuEdit =
      new DynamicMenu(Messages.common("edit")) {

        @Override
        protected void fill(JMenu menu) {
          buildMenuEdit(menu);
        }
      };
  private final JMenu menuView = new JMenu(Messages.getString("WeasisWin.display"));
  private final JMenu menuTools = new JMenu(Messages.common("tools"));
  private final DynamicMenu menuWindow =
      new DynamicMenu(Messages.getString("WeasisWin.window")) {

        @Override
        protected void fill(JMenu menu) {
          buildMenuWindow(menu);
        }
      };
  private JCheckBoxMenuItem anonymizeMenuItem;
  private final DynamicMenu menuSelectedPlugin =
      new DynamicMenu("") {

        @Override
        protected void fill(JMenu menu) {
          buildSelectedPluginMenu(menu);
        }
      };
  private ViewerPlugin<?> selectedPlugin = null;

  private final List<Runnable> runOnClose = new ArrayList<>();
  private final ShortcutTable<Integer> tabShifts =
      new ShortcutTable<Integer>()
          .on(ShortcutManager.ID_DOCKING_NEXT_TAB, 1)
          .on(ShortcutManager.ID_DOCKING_PREV_TAB, -1);
  private final ShortcutTable<Role> sideFocus =
      new ShortcutTable<Role>()
          .on(ShortcutManager.ID_DOCKING_FOCUS_EXPLORER, Role.EXPLORER)
          .on(ShortcutManager.ID_DOCKING_FOCUS_TOOLS, Role.TOOLS);
  private final ShortcutTable<Consumer<ViewerPlugin<?>>> dockingActions = buildDockingActions();

  private final Frame frame;

  /**
   * The place of the toolbar row in the main window: the row is one component that follows the
   * active window (main or viewer window); the place keeps its height when the row leaves. A press
   * on the empty place selects the view of the main window.
   */
  private final ToolbarRowHost toolbarHost =
      new ToolbarRowHost(() -> workspace().activate(workspace().mainWorkspace()));

  private JComponent toolbarRow;
  private Window toolbarWindow;
  private boolean explorerCollapsedBefore;
  private boolean toolsCollapsedBefore;
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
    SiteDocuments.onReload(MaskingModelRegistry.SITE_FILE, WeasisWin::configureMaskingModel);

    workspace()
        .addListener(
            new WorkspaceListener() {
              @Override
              public void viewSelected(WorkspaceView view, Window window) {
                if (view instanceof ViewerPlugin<?> viewer) {
                  setSelectedPlugin(viewer);
                  moveToolbarRow(window);
                  focusSelected(viewer);
                }
              }

              @Override
              public void workspaceEmptied(Window window) {
                if (window == toolbarWindow) {
                  moveToolbarRow(frame);
                }
              }

              @Override
              public void immersiveChanged(Window window, boolean immersive) {
                setImmersive(window, immersive);
              }
            });
    ToolContributions.follow();
    Relevance.follow();
    KeyEventDispatcher dockingShortcutDispatcher = this::dispatchDockingShortcut;
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(dockingShortcutDispatcher);

    boolean indicatorsInMenuBar = false;
    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.menu.menubar", true)) {
      rootPaneContainer.getRootPane().setJMenuBar(createMenuBar());
      indicatorsInMenuBar = !isScreenMenuBar();
    }
    setSelectedPlugin(null);

    // the row is as long as the window: what does not fit goes to its overflow button
    toolbarRow = GuiUtils.getUICore().getToolbarContainer();
    toolbarHost.setRow(toolbarRow);
    toolbarWindow = frame;
    rootPaneContainer
        .getContentPane()
        .add(indicatorsInMenuBar ? toolbarHost : createNorthPanel(toolbarHost), BorderLayout.NORTH);

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
    if (Settings.get(CoreSettings.CONFIRM_CLOSING)) {
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

  private static JPanel oriented(JPanel panel, ComponentOrientation orientation) {
    panel.setComponentOrientation(orientation);
    return panel;
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
    // every workspace window accepts a series drop and opens it there
    workspace().setDropHandler(SeriesHandler::new);

    // The shell is named by role: the explorer side leads, the tools side trails, and the
    // orientation of the interface (from the locale) decides which one is on the left. The canvas
    // never mirrors.
    ComponentOrientation orientation = UiOrientation.get();
    boolean ltr = orientation.isLeftToRight();
    SidePanel explorer = GuiUtils.getUICore().getSidePanel(SidePanel.Role.EXPLORER);
    SidePanel tools = GuiUtils.getUICore().getSidePanel(SidePanel.Role.TOOLS);
    MiniStrip miniStrip = GuiUtils.getUICore().getMiniStrip();

    // the central area and the fixed strip sit together (strip immediately trailing the centre)
    JPanel centreWithStrip = oriented(new JPanel(new BorderLayout()), orientation);
    centreWithStrip.add(centerArea, BorderLayout.CENTER);
    centreWithStrip.add(miniStrip, BorderLayout.LINE_END);

    // tools side: the resizable content is the trailing child of the split
    JSplitPane toolsSplit =
        ltr
            ? new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centreWithStrip, tools)
            : new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tools, centreWithStrip);
    toolsSplit.setResizeWeight(ltr ? 1.0 : 0.0); // the centre absorbs resizing
    toolsSplit.setContinuousLayout(true);
    toolsSplit.setBorder(null);
    tools.attachTo(toolsSplit);
    JPanel toolsGroup = oriented(new JPanel(new BorderLayout()), orientation);
    toolsGroup.add(toolsSplit, BorderLayout.CENTER);
    toolsGroup.add(tools.getStripe(), BorderLayout.LINE_END);

    // explorer side: the resizable content is the leading child of the split
    JSplitPane explorerSplit =
        ltr
            ? new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorer, toolsGroup)
            : new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, toolsGroup, explorer);
    explorerSplit.setResizeWeight(ltr ? 0.0 : 1.0); // the explorer content stays fixed
    explorerSplit.setContinuousLayout(true);
    explorerSplit.setBorder(null);
    explorer.attachTo(explorerSplit);

    // the explorer stripe on the leading edge; import and export are tools of the explorer side
    JPanel mainPanel = oriented(new JPanel(new BorderLayout()), orientation);
    mainPanel.add(explorer.getStripe(), BorderLayout.LINE_START);
    mainPanel.add(explorerSplit, BorderLayout.CENTER);

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
          // Match by factory: the component name follows the tab title (the patient)
          if (p instanceof ImageViewerPlugin viewer
              && factory.isViewerCreatedByThisFactory(p)
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
      if (placement instanceof ViewerPlacement.ReuseViewer reuse) {
        openInSelection = reuse.openInSelection();
      }
      registered = registerPlugin(viewer, opts);

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
   * The immersive presentation of a window: the workspace alone. In the main window the menu bar,
   * the toolbar row, both shell sides with their stripes and the strip go; a viewer window hides
   * its toolbar row. What the user had collapsed stays collapsed on the way back.
   */
  private void setImmersive(Window window, boolean immersive) {
    if (window instanceof DetachedFrame viewerWindow) {
      viewerWindow.setToolbarVisible(!immersive);
      return;
    }
    if (window != frame) {
      return;
    }
    JMenuBar menuBar = rootPaneContainer.getRootPane().getJMenuBar();
    if (menuBar != null) {
      menuBar.setVisible(!immersive);
    }
    toolbarHost.setVisible(!immersive);
    SidePanel explorer = GuiUtils.getUICore().getSidePanel(SidePanel.Role.EXPLORER);
    SidePanel tools = GuiUtils.getUICore().getSidePanel(SidePanel.Role.TOOLS);
    MiniStrip strip = GuiUtils.getUICore().getMiniStrip();
    if (immersive) {
      explorerCollapsedBefore = explorer.isCollapsed();
      toolsCollapsedBefore = tools.isCollapsed();
      explorer.collapse();
      tools.collapse();
    } else {
      if (!explorerCollapsedBefore) {
        explorer.expand();
      }
      if (!toolsCollapsedBefore) {
        tools.expand();
      }
    }
    explorer.getStripe().setVisible(!immersive);
    tools.getStripe().setVisible(!immersive);
    if (strip != null) {
      strip.setVisible(!immersive);
    }
    rootPaneContainer.getRootPane().revalidate();
    rootPaneContainer.getRootPane().repaint();
  }

  /**
   * Moves the toolbar row to the window of the selected viewer: a viewer window shows it under its
   * title bar, the main window in its north panel. The row is one component (the viewer toolbars
   * are singletons bound to the event manager), so it leaves its previous host first.
   */
  private void moveToolbarRow(Window window) {
    Window target = window instanceof DetachedFrame ? window : frame;
    if (target == toolbarWindow || toolbarRow == null) {
      return;
    }
    // every place keeps the height of the row: no window moves its views
    if (toolbarWindow instanceof DetachedFrame previous) {
      previous.setToolbarRow(null);
    } else {
      toolbarHost.setRow(null);
    }
    if (target instanceof DetachedFrame next) {
      next.setToolbarRow(toolbarRow);
    } else {
      toolbarHost.setRow(toolbarRow);
    }
    toolbarWindow = target;
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
      moveToolbarRow(frame);
      ViewContexts.publish(ViewContext.empty());
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
    menuSelectedPlugin.setText(viewerTypeName(selectedPlugin));

    SeriesViewerUI.updateTools(oldPlugin, selectedPlugin, false);
    SeriesViewerUI.updateToolbars(oldPlugin, selectedPlugin, false);
    // what is displayed and why, for the tools of the plugins and the commands
    ViewContexts.publish(selectedPlugin);

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
      return cycleView(tabShift.get());
    }
    Optional<Role> focusTarget = sideFocus.find(e);
    if (focusTarget.isPresent()) {
      return focusSide(focusTarget.get());
    }
    if (leaveWindowFullscreen(e)) {
      return true;
    }
    ViewerPlugin<?> plugin = getSelectedPlugin();
    if (plugin == null) {
      return false;
    }
    Optional<Consumer<ViewerPlugin<?>>> action = dockingActions.find(e);
    action.ifPresent(a -> a.accept(plugin));
    return action.isPresent();
  }

  /**
   * Escape ends the window fullscreen (Shift+F11) as it ends the canvas fullscreen (F11). The
   * canvas fullscreen keeps its own way out through the viewer; an open menu or combo popup and a
   * drawing in progress keep their Escape.
   */
  private boolean leaveWindowFullscreen(KeyEvent e) {
    if (e.getKeyCode() != KeyEvent.VK_ESCAPE || e.getModifiersEx() != 0) {
      return false;
    }
    KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Window window = focus.getActiveWindow();
    WorkspaceManager control = workspace();
    if (window == null
        || !control.isFullscreen(window)
        || control.isImmersive(window)
        || MenuSelectionManager.defaultManager().getSelectedPath().length > 0
        || (focus.getFocusOwner() instanceof JComboBox<?> combo && combo.isPopupVisible())
        || isDrawingInProgress()) {
      return false;
    }
    control.setFullscreen(window, false);
    return true;
  }

  private boolean isDrawingInProgress() {
    if (getSelectedPlugin() instanceof ImageViewerPlugin<?> viewer) {
      ViewCanvas<?> canvas = viewer.getSelectedViewCanvas();
      GraphicMouseHandler<?> handler = canvas == null ? null : canvas.getGraphicMouseHandler();
      return handler != null && handler.isDrawing();
    }
    return false;
  }

  /** Docking shortcuts acting on the selected viewer. */
  private ShortcutTable<Consumer<ViewerPlugin<?>>> buildDockingActions() {
    return new ShortcutTable<Consumer<ViewerPlugin<?>>>()
        .on(ShortcutManager.ID_DOCKING_MAXIMIZE, this::toggleMaximized)
        .on(ShortcutManager.ID_DOCKING_NORMALIZE, plugin -> workspace().restore(plugin))
        .on(ShortcutManager.ID_DOCKING_FULLSCREEN, this::toggleFullscreen)
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

  /** Toggles the fullscreen state of the window that holds the viewer. */
  private void toggleFullscreen(ViewerPlugin<?> plugin) {
    WorkspaceManager control = workspace();
    control
        .groupOf(plugin)
        .map(group -> group.getWorkspace().getWindow())
        .ifPresent(window -> control.setFullscreen(window, !control.isFullscreen(window)));
  }

  /**
   * Selects the next or previous tab in the order the tabs are shown: the tabs of a group, then the
   * next group, then the next window; viewers and tools shown as tabs alike.
   */
  private boolean cycleView(int delta) {
    WorkspaceManager control = workspace();
    List<WorkspaceView> views =
        control.getWindows().stream()
            .flatMap(window -> control.getTabGroups(window).stream())
            .flatMap(group -> group.getViews().stream())
            .toList();
    if (views.size() < 2) {
      return false;
    }
    int index = control.selectedView().map(views::indexOf).orElse(-1);
    if (index < 0) {
      index = 0;
    }
    WorkspaceView next = views.get(Math.floorMod(index + delta, views.size()));
    if (next instanceof ViewerPlugin<?> viewer) {
      viewer.setSelectedAndGetFocus();
    } else {
      control.select(next);
    }
    return true;
  }

  /**
   * The keyboard focus follows the selection: a viewer selected by its tab, Ctrl+Tab or its window
   * gets the focus on its selected view, unless the focus is already inside it (a field of the
   * viewer, or the view a click just reached).
   */
  private static void focusSelected(ViewerPlugin<?> viewer) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null || !SwingUtilities.isDescendingFrom(owner, viewer)) {
      viewer.getFocusTarget().requestFocusInWindow();
    }
  }

  /**
   * Puts the focus in a side of the shell, opening its tools when it is collapsed; when the focus
   * is already there, gives it back to the selected viewer.
   */
  private boolean focusSide(Role role) {
    Shell shell = Shell.current();
    if (shell == null) {
      return false;
    }
    SidePanel side = shell.side(role);
    if (side.hasFocusInside()) {
      ViewerPlugin<?> plugin = getSelectedPlugin();
      if (plugin != null) {
        plugin.setSelectedAndGetFocus();
      }
      return true;
    }
    return side.focusContent();
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
    showPreviewNoticeOnce();
  }

  /** The persistence key of the version whose preview notice the user acknowledged. */
  static final String P_PREVIEW_ACKNOWLEDGED = "weasis.preview.acknowledged"; // NON-NLS

  /**
   * A preview or a development build says once per version that it is not for clinical use: a
   * dialog at the first start of that version, acknowledged by the user, then the title and the
   * About box keep the mark. {@code weasis:info} reports the same.
   */
  private void showPreviewNoticeOnce() {
    Prerelease build = Prerelease.of(AppProperties.WEASIS_VERSION);
    if (!build.isPreview() || GraphicsEnvironment.isHeadless()) {
      return;
    }
    WProperties persistence = GuiUtils.getUICore().getLocalPersistence();
    if (build.version().equals(persistence.getProperty(P_PREVIEW_ACKNOWLEDGED))) {
      return;
    }
    GuiExecutor.execute(
        () -> {
          String message =
              Messages.format(
                  "WeasisWin.preview_notice",
                  // one argument: the words of a name keep their order in a right-to-left sentence
                  String.join(" ", AppProperties.WEASIS_NAME, build.baseVersion(), build.label()));
          // modeless: a modal dialog at start would hold the event thread and the command
          // server with it, so an agent driving a preview could not reach it before a click
          JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE);
          JDialog dialog =
              pane.createDialog(
                  frame, Messages.format("WeasisWin.preview_notice_title", build.label()));
          dialog.setModal(false);
          dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
          pane.addPropertyChangeListener(
              JOptionPane.VALUE_PROPERTY,
              e -> {
                if (e.getNewValue() != null && e.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                  dialog.dispose();
                }
              });
          dialog.addWindowListener(
              new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                  persistence.setProperty(P_PREVIEW_ACKNOWLEDGED, build.version());
                  // written now: an instance stopped from outside may skip its shutdown
                  SettingsFileStore.machine().store(AppProperties.CORE_PLUGIN_ID, "", persistence);
                  SettingsFileStore.machine().flushAll();
                }
              });
          dialog.setVisible(true);
        });
  }

  private JMenuBar createMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    ActionCatalog.addSource(() -> ActionCatalog.ofMenuBar(menuBar));
    buildMenuFile();
    menuBar.add(menuFile);
    menuBar.add(menuEdit);
    menuEdit.addPopupMenuListener();
    buildMenuView();
    menuBar.add(menuView);
    buildMenuTools();
    menuBar.add(menuTools);
    menuBar.add(menuSelectedPlugin);
    menuSelectedPlugin.addPopupMenuListener();
    menuBar.add(menuWindow);
    menuWindow.addPopupMenuListener();

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
    if (Prerelease.of(AppProperties.WEASIS_VERSION).isPreview()) {
      // The feedback channel of the preview: the issue form with the version and the platform
      // filled in, the same link as weasis:feedback
      final JMenuItem feedbackMenuItem = new JMenuItem(Messages.getString("send.preview.feedback"));
      feedbackMenuItem.addActionListener(e -> openBrowser(feedbackMenuItem, FeedbackLink.url()));
      helpMenuItem.add(feedbackMenuItem);
    }
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
        new JMenuItem(Messages.format("WeasisAboutBox.about", AppProperties.WEASIS_NAME));
    aboutMenuItem.addActionListener(
        e -> {
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootPaneContainer);
          WeasisAboutBox about = new WeasisAboutBox(getFrame());
          ColorLayerUI.showCenterScreen(about, layer);
        });
    helpMenuItem.add(aboutMenuItem);
    menuBar.add(helpMenuItem);
    if (!isScreenMenuBar()) {
      menuBar.add(Box.createHorizontalGlue());
      createIndicators().forEach(menuBar::add);
    }
    return menuBar;
  }

  /**
   * Places the indicators when the menu bar cannot hold them: in the title bar when FlatLaf can
   * draw there (macOS full window content), otherwise at the end of the toolbar row.
   */
  private JComponent createNorthPanel(JComponent toolbarRow) {
    JPanel indicators = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 2));
    createIndicators().forEach(indicators::add);
    JPanel northPanel = new JPanel(new BorderLayout());
    if (SystemInfo.isMacFullWindowContentSupported) {
      // The content extends under the transparent title bar, which keeps the window title and
      // buttons; the placeholder reserves the room of the buttons
      JRootPane rootPane = rootPaneContainer.getRootPane();
      rootPane.putClientProperty("apple.awt.fullWindowContent", true); // NON-NLS
      rootPane.putClientProperty("apple.awt.transparentTitleBar", true); // NON-NLS
      JPanel buttonsPlaceholder = new JPanel();
      buttonsPlaceholder.putClientProperty(
          FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER,
          "mac zeroInFullScreen"); // NON-NLS
      JPanel titleBar = new JPanel(new BorderLayout());
      titleBar.add(buttonsPlaceholder, BorderLayout.WEST); // absolute side: macOS window buttons
      titleBar.add(indicators, BorderLayout.EAST); // absolute side: opposite the macOS buttons
      northPanel.add(titleBar, BorderLayout.NORTH);
      northPanel.add(toolbarRow, BorderLayout.CENTER);
    } else {
      northPanel.add(toolbarRow, BorderLayout.CENTER);
      northPanel.add(indicators, BorderLayout.LINE_END);
    }
    return northPanel;
  }

  /** The macOS screen menu bar shows only menus: other components are dropped. */
  private static boolean isScreenMenuBar() {
    return SystemInfo.isMacOS && Boolean.getBoolean("apple.laf.useScreenMenuBar"); // NON-NLS
  }

  private List<JComponent> createIndicators() {
    return List.of(
        new CaptureButton(
            () -> selectedPlugin == null ? List.of() : selectedPlugin.getExportActions()),
        new MaskingIndicator());
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
    ToolBarContainer container = GuiUtils.getUICore().getToolbarContainer();
    List<Toolbar> bars = container.getRegisteredToolBars();
    List<Toolbar> shown = container.shownBars();
    for (final Toolbar bar : bars) {
      if (!Insertable.Type.EMPTY.equals(bar.getPanelType())) {
        boolean selected = container.isSelected(bar);
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(bar.getComponentName(), selected);
        if (selected && !shown.contains(bar)) {
          // selected, yet out of the row: the selected view does not need it
          item.setToolTipText(Messages.getString("WeasisWin.toolbar_not_relevant"));
        }
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

  /**
   * The families of the explorer side as radio items (the explorer shown is checked) when several
   * explorers are loaded, then the visibility of each explorer.
   */
  private static void buildExplorerSubMenu(final JMenu explorerMenu) {
    SidePanel side = GuiUtils.getUICore().getSidePanel(SidePanel.Role.EXPLORER);
    if (side != null && side.familyIds().size() > 1) {
      String active = side.activeFamily().orElse(null);
      ButtonGroup group = new ButtonGroup();
      for (String id : side.familyIds()) {
        JRadioButtonMenuItem item =
            new JRadioButtonMenuItem(side.toolName(id).orElse(id), id.equals(active));
        item.addActionListener(e -> side.selectFamily(id));
        group.add(item);
        explorerMenu.add(item);
      }
      explorerMenu.addSeparator();
    }
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
      JCheckBoxMenuItem item =
          new JCheckBoxMenuItem(t.getComponentName(), ToolVisibility.isVisible(t));
      item.setEnabled(!Settings.isLocked(ToolVisibility.setting(t.getToolId())));
      item.addActionListener(
          e -> {
            if (e.getSource() instanceof JCheckBoxMenuItem menuItem
                && !ToolVisibility.setVisible(t, menuItem.isSelected())) {
              menuItem.setSelected(ToolVisibility.isVisible(t));
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

  /** The setting that keeps the folder of the last files or folder opened from the File menu. */
  private static final String LAST_OPEN_DIR = "lastOpenDir"; // NON-NLS

  private static final int MENU_SHORTCUT_MASK =
      Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

  /**
   * File › Open files… and Open folder…: what is picked is opened as if it were dropped on the
   * window, DICOM files, folders and archives through the explorer that imports them, an image in
   * the viewer of its type.
   */
  private void openFromChooser(boolean folders) {
    WProperties persistence = GuiUtils.getUICore().getLocalPersistence();
    SystemFileChooser chooser = new SystemFileChooser(persistence.getProperty(LAST_OPEN_DIR, ""));
    chooser.setFileSelectionMode(
        folders ? SystemFileChooser.DIRECTORIES_ONLY : SystemFileChooser.FILES_ONLY);
    chooser.setMultiSelectionEnabled(true);
    if (chooser.showOpenDialog(getFrame()) != SystemFileChooser.APPROVE_OPTION) {
      return;
    }
    File[] selected = chooser.getSelectedFiles();
    if (selected == null || selected.length == 0) {
      return;
    }
    File first = selected[0];
    File directory = first.isDirectory() && !folders ? first : first.getParentFile();
    if (directory != null) {
      persistence.setProperty(LAST_OPEN_DIR, directory.getPath());
    }
    openPaths(Arrays.stream(selected).map(File::toPath).toList());
  }

  /** Opens files and folders as a drop on the window does. */
  void openPaths(List<Path> paths) {
    new SeriesHandler().dropFiles(paths);
  }

  private static void buildImportSubMenu(final JMenu importMenu) {
    GuiUtils.getUICore()
        .getExplorerPlugins()
        .forEach(d -> fillMenu(importMenu, d.getOpenImportDialogAction()));
  }

  private void buildExportSubMenu(final JMenu exportMenu) {
    // The export tools of the explorers first (a folder, a disc image, an archive), then, apart,
    // what captures the selected viewer (a screenshot, a cine loop, a recording)
    GuiUtils.getUICore()
        .getExplorerPlugins()
        .forEach(d -> fillMenu(exportMenu, d.getOpenExportDialogAction()));
    if (selectedPlugin != null) {
      List<Action> captures = selectedPlugin.getExportActions();
      if (captures != null && !captures.isEmpty() && exportMenu.getMenuComponentCount() > 0) {
        exportMenu.add(new JSeparator());
      }
      fillMenu(exportMenu, captures);
    }
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

  /**
   * Menu title of the selected viewer: its type (2D Viewer, Oblique MPR, 3D Viewer…), not its tab
   * title, which names the patient and is the same for every viewer of a study.
   */
  private static String viewerTypeName(ViewerPlugin<?> plugin) {
    SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(plugin);
    String name = factory == null ? null : factory.getUIName();
    return StringUtil.hasText(name) ? name : plugin.getName();
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
          protected void fill(JMenu menu) {
            buildToolBarSubMenu(menu);
          }
        };
    toolBarMenu.addPopupMenuListener();
    menuView.add(toolBarMenu);

    DynamicMenu toolMenu =
        new DynamicMenu(Messages.common("tools")) {

          @Override
          protected void fill(JMenu menu) {
            buildToolSubMenu(menu);
          }
        };
    toolMenu.addPopupMenuListener();
    menuView.add(toolMenu);

    DynamicMenu explorerMenu = new DynamicMenu("Explorer") { // NON-NLS

          @Override
          protected void fill(JMenu menu) {
            buildExplorerSubMenu(menu);
          }
        };
    explorerMenu.addPopupMenuListener();
    menuView.add(explorerMenu);

    menuView.addSeparator();
    JCheckBoxMenuItem showAll =
        new JCheckBoxMenuItem(
            Messages.getString("WeasisWin.show_all_tools"), !Relevance.contextual());
    showAll.setToolTipText(Messages.getString("WeasisWin.show_all_tools.tip"));
    showAll.addActionListener(e -> Settings.set(Relevance.CONTEXTUAL, !showAll.isSelected()));
    Settings.addListener(
        Relevance.CONTEXTUAL, value -> GuiExecutor.execute(() -> showAll.setSelected(!value)));
    menuView.add(showAll);

    JMenuItem palette = new JMenuItem(Messages.getString("WeasisWin.palette"));
    palette.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_P, KeyBinding.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
    palette.addActionListener(e -> CommandPalette.show(activeWindow()));
    menuView.add(palette);
  }

  /** The window of the selected viewer, else the main window. */
  private Window activeWindow() {
    WorkspaceManager control = workspace();
    return control.selectedView().flatMap(control::windowOf).orElse(frame);
  }

  /**
   * The Window menu: every window (the main one and the viewer windows, the active one checked),
   * the screens by role to move the active window to, its fullscreen state, and the selected viewer
   * in a new window.
   */
  private void buildMenuWindow(JMenu menu) {
    menu.removeAll();
    WorkspaceManager control = workspace();
    Window active = activeWindow();
    for (Window window : control.getWindows()) {
      if (window == null) {
        continue;
      }
      String title =
          window instanceof Frame f && !f.getTitle().isBlank()
              ? f.getTitle()
              : Messages.getString("WeasisWin.main_window");
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(title, window == active);
      item.addActionListener(
          e -> {
            Workspace target = control.workspaceOf(window);
            if (target != null) {
              control.activate(target);
            }
            window.toFront();
          });
      menu.add(item);
    }
    menu.addSeparator();
    JMenu screens = new JMenu(Messages.getString("WeasisWin.move_to_screen"));
    ScreenRoles.Mapping mapping = ScreenRoles.mapping(frame);
    for (ScreenRole role : ScreenRole.values()) {
      ScreenRoles.Screen screen = mapping.screen(role);
      if (screen == null) {
        continue;
      }
      JMenuItem item =
          new JMenuItem(
              ScreenRoles.label(role)
                  + (mapping.isFulfilled(role) ? "" : " *")
                  + " ("
                  + screen.id()
                  + ")");
      item.addActionListener(e -> control.moveToScreen(active, role));
      screens.add(item);
    }
    menu.add(screens);
    JCheckBoxMenuItem fullscreen =
        new JCheckBoxMenuItem(
            Messages.getString("WeasisWin.win_fullscreen"), control.isFullscreen(active));
    fullscreen.setAccelerator(
        ShortcutManager.getInstance().getKeyStroke(ShortcutManager.ID_DOCKING_FULLSCREEN));
    fullscreen.addActionListener(e -> control.setFullscreen(active, !control.isFullscreen(active)));
    menu.add(fullscreen);
    ViewerPlugin<?> plugin = getSelectedPlugin();
    JMenuItem newWindow = new JMenuItem(Messages.getString("WeasisWin.new_window"));
    newWindow.setAccelerator(
        ShortcutManager.getInstance().getKeyStroke(ShortcutManager.ID_DOCKING_EXTERNALIZE));
    newWindow.setEnabled(plugin != null && plugin.isDetachable());
    newWindow.addActionListener(e -> control.detach(plugin, null));
    menu.add(newWindow);
    menu.addSeparator();
    menu.add(restoreDisplayItem());
  }

  /**
   * The Edit menu: undo and redo of the drawings of the selected view, enabled when its history has
   * something to undo or redo.
   */
  private void buildMenuEdit(JMenu menu) {
    menu.removeAll();
    ViewCanvas<?> view =
        getSelectedPlugin() instanceof ImageViewerPlugin<?> viewer
            ? viewer.getSelectedViewCanvas()
            : null;
    GraphicUndoHistory history = view == null ? null : view.getGraphicUndo();
    JMenuItem undo = new JMenuItem(Messages.getString("WeasisWin.undo_drawing"));
    undo.setAccelerator(ShortcutManager.getInstance().getKeyStroke(ShortcutManager.ID_DRAW_UNDO));
    undo.setEnabled(history != null && history.isEnabled() && history.canUndo());
    undo.addActionListener(_ -> GraphicEditActions.undo(view));
    menu.add(undo);
    JMenuItem redo = new JMenuItem(Messages.getString("WeasisWin.redo_drawing"));
    redo.setAccelerator(ShortcutManager.getInstance().getKeyStroke(ShortcutManager.ID_DRAW_REDO));
    redo.setEnabled(history != null && history.isEnabled() && history.canRedo());
    redo.addActionListener(_ -> GraphicEditActions.redo(view));
    menu.add(redo);
  }

  /**
   * <i>Restore previous display</i>: puts the display back to the newest checkpoint (taken before a
   * script, a remote batch, a hanging, a restore), named by what it preceded; disabled when there
   * is none. A rollback that cannot restore everything says what stays.
   */
  private JMenuItem restoreDisplayItem() {
    StateBoundary boundary = CommandManager.stateBoundary();
    Optional<StateBoundary.Latest> latest = boundary.newest();
    String label =
        latest
            .filter(l -> !l.label().isBlank())
            .map(l -> Messages.format("WeasisWin.restore_display_before", l.label()))
            .orElse(Messages.getString("WeasisWin.restore_display"));
    JMenuItem restore = new JMenuItem(label);
    restore.setEnabled(latest.isPresent());
    latest.ifPresent(
        l ->
            restore.addActionListener(
                _ ->
                    CompletableFuture.supplyAsync(() -> boundary.rollback(l.id()))
                        .whenComplete(
                            (notRestored, error) ->
                                GuiExecutor.execute(() -> reportRestore(notRestored, error)))));
    return restore;
  }

  private void reportRestore(List<String> notRestored, Throwable error) {
    if (error != null) {
      Throwable cause = error.getCause() == null ? error : error.getCause();
      LOGGER.warn("Cannot restore the previous display", cause);
      JOptionPane.showMessageDialog(
          frame,
          Messages.format("WeasisWin.restore_failed", cause.getMessage()),
          Messages.getString("WeasisWin.restore_display"),
          JOptionPane.WARNING_MESSAGE);
    } else if (notRestored != null && !notRestored.isEmpty()) {
      StringBuilder message = new StringBuilder(Messages.getString("WeasisWin.restore_partial"));
      notRestored.forEach(line -> message.append("\n- ").append(line));
      JOptionPane.showMessageDialog(
          frame,
          message.toString(),
          Messages.getString("WeasisWin.restore_display"),
          JOptionPane.INFORMATION_MESSAGE);
    }
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

  /** Loads the site and user masking documents; the user one is mirrored remotely when saved. */
  private static void configureMaskingModel() {
    UICore core = GuiUtils.getUICore();
    String prefDir = core.getSystemPreferences().getProperty("weasis.pref.dir"); // NON-NLS
    MaskingModelRegistry.getInstance()
        .configure(
            SiteDocuments.find(MaskingModelRegistry.SITE_FILE).orElse(null),
            DocumentStore.locate(
                Scope.USER,
                MaskingModelRegistry.SITE_FILE,
                StringUtil.hasText(prefDir)
                    ? Path.of(prefDir).resolve(MaskingModelRegistry.USER_FILE)
                    : null),
            path -> core.storeRemotePref(path, JsonUtil.CONTENT_TYPE));
    DocumentStore.registerIds(
        MaskingModel.DOCUMENT_ID,
        () -> {
          MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
          Set<String> ids = new HashSet<>(registry.profiles().origins().keySet());
          ids.addAll(registry.masks().origins().keySet());
          return ids;
        });
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
    Prerelease build = Prerelease.of(AppProperties.WEASIS_VERSION);
    if (build.isPreview()) {
      title += " — " + Messages.getString("WeasisWin.preview_title"); // NON-NLS
    }
    if (IdentityMask.isSessionMasking()) {
      title += " — " + MaskingIndicator.label(IdentityMask.sessionProfile());
    }
    frame.setTitle(title);
  }

  private void buildMenuFile() {
    menuFile.removeAll();
    // The quick path: pick files or a folder and see them, with the default options. The native
    // dialogs of Windows and Linux cannot pick files and folders together, hence two entries, as
    // the two buttons of the import tool. Import and Export below open the tools with options.
    DefaultAction openFiles =
        new DefaultAction(Messages.getString("WeasisWin.open_files"), e -> openFromChooser(false));
    openFiles.putValue(
        Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_SHORTCUT_MASK));
    menuFile.add(new JMenuItem(openFiles));
    DefaultAction openFolder =
        new DefaultAction(Messages.getString("WeasisWin.open_folder"), e -> openFromChooser(true));
    openFolder.putValue(
        Action.ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
    menuFile.add(new JMenuItem(openFolder));

    DynamicMenu importMenu =
        new DynamicMenu(Messages.common("import")) {

          @Override
          protected void fill(JMenu menu) {
            buildImportSubMenu(menu);
          }
        };
    importMenu.addPopupMenuListener();
    menuFile.add(importMenu);

    DynamicMenu exportMenu =
        new DynamicMenu(Messages.common("export")) {

          @Override
          protected void fill(JMenu menu) {
            buildExportSubMenu(menu);
          }
        };
    exportMenu.addPopupMenuListener();

    menuFile.add(exportMenu);

    DynamicMenu launcherMenu =
        new DynamicMenu(Messages.getString("launcher")) {

          @Override
          protected void fill(JMenu menu) {
            buildLauncherSubMenu(menu);
          }
        };
    launcherMenu.addPopupMenuListener();

    menuFile.add(launcherMenu);

    menuFile.add(new JSeparator());
    DynamicMenu printMenu =
        new DynamicMenu(Messages.common("print")) {

          @Override
          protected void fill(JMenu menu) {
            buildPrintSubMenu(menu);
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
        if (support.getComponent() instanceof Workspace target) {
          // the series opens in the window it was dropped on
          workspace().activate(target);
        }
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
        // an archive is scanned by the explorer as a folder is: no codec reads it as one media
        if (Files.isDirectory(file) || isArchive(file)) {
          dirs.add(file);
        } else {
          categorizeMediaFile(file, codecFiles);
        }
      }
    }

    private static boolean isArchive(Path file) {
      Path name = file.getFileName();
      return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".zip"); // NON-NLS
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
