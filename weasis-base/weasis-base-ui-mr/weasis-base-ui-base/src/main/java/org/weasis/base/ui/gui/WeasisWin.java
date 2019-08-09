/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.ui.gui;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
import javax.swing.ImageIcon;
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
import javax.swing.LookAndFeel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
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
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
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

import bibliothek.extension.gui.dock.theme.EclipseTheme;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabDockActionLocation;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabStateInfo;
import bibliothek.extension.gui.dock.theme.eclipse.rex.RexSystemColor;
import bibliothek.gui.DockUI;
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
import bibliothek.gui.dock.util.Priority;
import bibliothek.gui.dock.util.color.ColorManager;
import bibliothek.gui.dock.util.laf.LookAndFeelColors;
import bibliothek.util.Colors;

public class WeasisWin {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWin.class);

    public static final List<String> functions = Collections.unmodifiableList(Arrays.asList( "info", "ui" )); //$NON-NLS-1$ //$NON-NLS-2$

    private final JMenu menuFile = new JMenu(Messages.getString("WeasisWin.file")); //$NON-NLS-1$
    private final JMenu menuView = new JMenu(Messages.getString("WeasisWin.display")); //$NON-NLS-1$
    private final DynamicMenu menuSelectedPlugin = new DynamicMenu("") { //$NON-NLS-1$

        @Override
        public void popupMenuWillBecomeVisible() {
            buildSelectedPluginMenu(this);
        }
    };
    private ViewerPlugin<?> selectedPlugin = null;

    private final ToolBarContainer toolbarContainer;

    private volatile boolean busy = false;

    private final List<Runnable> runOnClose = new ArrayList<>();

    private final Frame frame;
    private final RootPaneContainer rootPaneContainer;

    private CFocusListener selectionListener = new CFocusListener() {

        @Override
        public void focusGained(CDockable dockable) {
            if (dockable != null && dockable.getFocusComponent() instanceof ViewerPlugin) {
                setSelectedPlugin((ViewerPlugin) dockable.getFocusComponent());
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
            ObjectName objectName = ObjectName.getInstance("weasis:name=MainWindow"); //$NON-NLS-1$
            Object containerObj = server.getAttribute(objectName, "RootPaneContainer"); //$NON-NLS-1$
            if (containerObj instanceof RootPaneContainer) {
                container = (RootPaneContainer) containerObj;
                container.getRootPane().updateUI();
                if (container.getContentPane() instanceof JPanel) {
                    ((JPanel) container.getContentPane()).updateUI();
                }
                container.getContentPane().removeAll();
            }
        } catch (InstanceNotFoundException ignored) {
        } catch (JMException e) {
            LOGGER.debug("Error while receiving main window", e); //$NON-NLS-1$
        }

        if (container == null || container instanceof JFrame) {
            JFrame jFrame = container == null ? new JFrame() : (JFrame) container;
            frame = jFrame;
            jFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    closeWindow();
                }
            });
            rootPaneContainer = jFrame;
        } else {
            rootPaneContainer = container;
            // Get Frame of JApplet to pass a parent frame to JDialog.
            frame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, (Component) rootPaneContainer);
        }

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.menu.menubar", true)) { //$NON-NLS-1$
            rootPaneContainer.getRootPane().setJMenuBar(createMenuBar());
        }
        toolbarContainer = new ToolBarContainer();
        setSelectedPlugin(null);
        rootPaneContainer.getContentPane().add(toolbarContainer, BorderLayout.NORTH);

        rootPaneContainer.setGlassPane(AppProperties.glassPane);

        if (frame != null) {
            frame.setTitle(AppProperties.WEASIS_NAME + " v" + AppProperties.WEASIS_VERSION); //$NON-NLS-1$
            ImageIcon icon =  AppProperties.WEASIS_NAME.endsWith("Dicomizer") ? ResourceUtil.getLogo("images" + File.separator + "dicomizer.png") :  ResourceUtil.getIconLogo64(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (icon != null) {
                frame.setIconImage(icon.getImage());
            }
        }
        
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
        if (busy) {
            // TODO add a message, Please wait or kill
            return false;
        }
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.confirm.closing", false)) { //$NON-NLS-1$
            int option = JOptionPane.showConfirmDialog(frame, Messages.getString("WeasisWin.exit_mes")); //$NON-NLS-1$
            if (option == JOptionPane.YES_OPTION) {
                closeAllRunnable();
                System.exit(0);
                return true;
            }
        } else {
            closeAllRunnable();
            System.exit(0);
            return true;
        }
        return false;
    }

    private void closeAllRunnable() {
        for (Runnable onClose : runOnClose) {
            onClose.run();
        }
    }

    public void runOnClose(Runnable run) {
        runOnClose.add(run);
    }

    public void destroyOnClose(final CControl control) {
        runOnClose(control::destroy);
    }

    public void createMainPanel() throws Exception {

        // Do not disable check when debugging
        if (System.getProperty("maven.localRepository") == null) { //$NON-NLS-1$
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

        // Fix substance
        LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
        if (laf.getClass().getName().startsWith("org.pushingpixels")) { //$NON-NLS-1$
            ColorManager colors = control.getController().getColors();

            Color selection = javax.swing.UIManager.getColor("TextArea.selectionBackground"); //$NON-NLS-1$
            Color inactiveColor = DockUI.getColor(LookAndFeelColors.TITLE_BACKGROUND).darker();
            Color inactiveColorGradient = DockUI.getColor(LookAndFeelColors.PANEL_BACKGROUND);
            Color activeColor = selection.darker();
            Color activeTextColor = javax.swing.UIManager.getColor("TextArea.selectionForeground"); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "stack.tab.border.selected", inactiveColorGradient); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.border.selected.focused", selection); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.border.selected.focuslost", inactiveColor); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "stack.tab.top.selected", inactiveColor); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.top.selected.focused", activeColor); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.top.selected.focuslost", inactiveColor); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "stack.tab.bottom.selected", inactiveColorGradient); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.bottom.selected.focused", selection); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.bottom.selected.focuslost", inactiveColor); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "stack.tab.text.selected", RexSystemColor.getInactiveTextColor()); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focused", activeTextColor); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focuslost", RexSystemColor.getInactiveTextColor()); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "title.flap.active", selection); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "title.flap.active.text", activeTextColor); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "title.flap.active.knob.highlight", Colors.brighter(selection)); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "title.flap.active.knob.shadow", Colors.darker(selection)); //$NON-NLS-1$
        }

        control.addFocusListener(selectionListener);

        // control.setDefaultLocation(UIManager.BASE_AREA.
        // this.add(UIManager.EAST_AREA, BorderLayout.EAST);
        rootPaneContainer.getContentPane().add(UIManager.BASE_AREA, BorderLayout.CENTER);
        // Allow to drop series into the empty main area
        UIManager.MAIN_AREA.getComponent().setTransferHandler(new SequenceHandler());
        UIManager.MAIN_AREA.setLocation(CLocation.base().normalRectangle(0, 0, 1, 1));
        UIManager.MAIN_AREA.setVisible(true);

    }

    HashMap<MediaSeriesGroup, List<MediaSeries<?>>> getSeriesByEntry(TreeModel treeModel,
        List<? extends MediaSeries<?>> series, TreeModelNode entry) {
        HashMap<MediaSeriesGroup, List<MediaSeries<?>>> map = new HashMap<>();
        if (series != null && treeModel != null && entry != null) {
            for (MediaSeries<?> s : series) {
                MediaSeriesGroup entry1 = treeModel.getParent(s, entry);
                List<MediaSeries<?>> seriesList = Optional.ofNullable(map.get(entry1)).orElseGet(ArrayList::new);
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
        boolean setInSelection = LangUtil.getNULLtoFalse((Boolean) props.get(ViewerPluginBuilder.OPEN_IN_SELECTION));

        if (screenBound == null && group != null) {
            boolean bestDefaultLayout =
                LangUtil.getNULLtoTrue((Boolean) props.get(ViewerPluginBuilder.BEST_DEF_LAYOUT));
            synchronized (UIManager.VIEWER_PLUGINS) {
                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                    final ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                    // Remove the views not attached to any window (Fix bugs with external window)
                    if (WinUtil.getParentWindow(p) == null) {
                        UIManager.VIEWER_PLUGINS.remove(i);
                        continue;
                    }
                    if (p instanceof ImageViewerPlugin && p.getName().equals(factory.getUIName())
                        && group.equals(p.getGroupID())) {
                        ImageViewerPlugin viewer = (ImageViewerPlugin) p;
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
        SeriesViewer seriesViewer = factory.createSeriesViewer(props);
        if (seriesViewer instanceof MimeSystemAppViewer) {
            for (MediaSeries m : seriesList) {
                seriesViewer.addSeries(m);
            }
        } else if (seriesViewer instanceof ViewerPlugin) {
            ViewerPlugin viewer = (ViewerPlugin) seriesViewer;
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
            if (group == null && model instanceof TreeModel && !seriesList.isEmpty()
                && model.getTreeModelNodeForNewPlugin() != null) {
                TreeModel treeModel = (TreeModel) model;
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
            if (val instanceof Icon) {
                viewer.getDockable().setTitleIcon((Icon) val);
            }

            boolean isregistered;
            if (screenBound != null) {
                isregistered = registerDetachWindow(viewer, screenBound);
            } else {
                isregistered = registerPlugin(viewer);
            }
            if (isregistered) {
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
        // TODO should be set dynamically. Maximize button of external window does not support multi-screens.
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();
        if (gd.length > 1) {
            // dockable.setExternalizable(true);
            Rectangle bound = WinUtil.getClosedScreenBound(rootPaneContainer.getRootPane().getBounds());
            // LocationHint hint =
            // new LocationHint(LocationHint.DOCKABLE, bibliothek.gui.dock.action.LocationHint.LEFT_OF_ALL);
            // DefaultDockActionSource source = new DefaultDockActionSource(hint);
            // source.add(setupDropDownMenu(viewer.getDockable()));
            // source.addSeparator();

            for (int i = 0; i < gd.length; i++) {
                GraphicsConfiguration config = gd[i].getDefaultConfiguration();
                final Rectangle b = config.getBounds();
                if (!b.contains(bound)) {
                    Insets inset = toolkit.getScreenInsets(config);
                    b.x += inset.left;
                    b.y += inset.top;
                    b.width -= (inset.left + inset.right);
                    b.height -= (inset.top + inset.bottom);
                    dockable.setDefaultLocation(ExtendedMode.EXTERNALIZED,
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
                        if (dialog != null && screenBound.equals(WinUtil.getClosedScreenBound(dialog.getBounds()))) {
                            oldWin = p;
                            break;
                        }
                    }
                }
            }

            final DefaultSingleCDockable dock = plugin.getDockable();
            dock.setExternalizable(true);

            if (oldWin == null) {
                dock.setLocation(CLocation.external(screenBound.x, screenBound.y, screenBound.width - 150,
                    screenBound.height - 150));
                plugin.showDockable();
                GuiExecutor.instance().execute(() -> {
                    if (dock.isVisible()) {
                        UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                        dock.setExtendedMode(ExtendedMode.MAXIMIZED);
                        UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                    }
                });
            } else {
                ConfiguredBackgroundPanel parent = WinUtil.getParentOfClass(oldWin, ConfiguredBackgroundPanel.class);
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
                for (int i = 0; i < tool.size(); i++) {
                    DockableTool p = tool.get(i);
                    if (p.isComponentEnabled()) {
                        p.showDockable();
                    }
                }
            }
        }

        updateToolbars(oldPlugin == null ? null : oldPlugin.getToolBar(), selectedPlugin.getToolBar(), false);
    }

    void updateToolbars(List<Toolbar> oldToolBars, List<Toolbar> toolBars, boolean force) {
        if (force || toolBars != oldToolBars) {
            toolbarContainer.registerToolBar(toolBars);
        }
    }

    public void showWindow() throws Exception {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Toolkit kit = Toolkit.getDefaultToolkit();

        Monitor defMonitor = Monitor.getDefaultMonitor();
        GraphicsConfiguration config;

        if (defMonitor == null) {
            config = ge.getDefaultScreenDevice().getDefaultConfiguration();
        } else {
            config = defMonitor.getGraphicsConfiguration();
        }

        Rectangle b;
        if (config != null) {
            b = config.getBounds();
            Insets inset = kit.getScreenInsets(config);
            b.x += inset.left;
            b.y += inset.top;
            b.width -= (inset.left + inset.right);
            b.height -= (inset.top + inset.bottom);
        } else {
            b = new Rectangle(new Point(0, 0), kit.getScreenSize());
        }
        LOGGER.debug("Max main screen bound: {}", b); //$NON-NLS-1$

        // Do not apply to JApplet
        if (frame == rootPaneContainer) {
            // set a valid size, insets of screen is often non consistent
            frame.setBounds(b.x, b.y, b.width - 150, b.height - 150);
            frame.setVisible(true);

            frame.setExtendedState((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
                ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
        }
        LOGGER.info("End of loading the GUI..."); //$NON-NLS-1$
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        buildMenuFile();
        menuBar.add(menuFile);
        buildMenuView();
        menuBar.add(menuView);
        menuBar.add(menuSelectedPlugin);
        menuSelectedPlugin.addPopupMenuListener();

        final JMenu helpMenuItem = new JMenu(Messages.getString("WeasisWin.help")); //$NON-NLS-1$
        final String helpURL = System.getProperty("weasis.help.url"); //$NON-NLS-1$
        if (helpURL != null) {
            final JMenuItem helpContentMenuItem = new JMenuItem(Messages.getString("WeasisWin.guide")); //$NON-NLS-1$
            helpContentMenuItem.addActionListener(e -> openBrowser(helpContentMenuItem, helpURL));
            helpMenuItem.add(helpContentMenuItem);
        }

        final JMenuItem webMenuItem = new JMenuItem(Messages.getString("WeasisWin.shortcuts")); //$NON-NLS-1$
        webMenuItem.addActionListener(
            e -> openBrowser(webMenuItem, BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.shortcuts"))); //$NON-NLS-1$
        helpMenuItem.add(webMenuItem);

        final JMenuItem websiteMenuItem = new JMenuItem(Messages.getString("WeasisWin.online")); //$NON-NLS-1$
        websiteMenuItem.addActionListener(
            e -> openBrowser(websiteMenuItem, BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online"))); //$NON-NLS-1$
        helpMenuItem.add(websiteMenuItem);
        final JMenuItem aboutMenuItem =
            new JMenuItem(String.format(Messages.getString("WeasisAboutBox.about"), AppProperties.WEASIS_NAME)); //$NON-NLS-1$
        aboutMenuItem.addActionListener(e -> {
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
            JMVUtils.openInDefaultBrowser(c, url);
        } catch (MalformedURLException e) {
            LOGGER.error("Open URL in default browser", e); //$NON-NLS-1$
        }
    }

    private void buildToolBarSubMenu(final JMenu toolBarMenu) {
        List<Toolbar> bars = toolbarContainer.getRegisteredToolBars();
        for (final Toolbar bar : bars) {
            if (!Insertable.Type.EMPTY.equals(bar.getType())) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
                item.addActionListener(e -> {
                    if (e.getSource() instanceof JCheckBoxMenuItem) {
                        toolbarContainer.displayToolbar(bar.getComponent(),
                            ((JCheckBoxMenuItem) e.getSource()).isSelected());
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
                if (!Insertable.Type.EMPTY.equals(t.getType())) {
                    JCheckBoxMenuItem item = new JCheckBoxMenuItem(t.getComponentName(), t.isComponentEnabled());
                    item.addActionListener(e -> {
                        if (e.getSource() instanceof JCheckBoxMenuItem) {
                            t.setComponentEnabled(((JCheckBoxMenuItem) e.getSource()).isSelected());
                            if (t.isComponentEnabled()) {
                                t.showDockable();
                            } else {
                                t.closeDockable();
                            }
                        }
                    });
                    toolMenu.add(item);
                }
            }
        }
    }

    private static void buildEplorerSubMenu(final JMenu explorerMenu) {
        synchronized (UIManager.EXPLORER_PLUGINS) {
            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
            for (final DataExplorerView dataExplorerView : explorers) {
                if (dataExplorerView instanceof DockableTool) {
                    final DockableTool t = (DockableTool) dataExplorerView;
                    if (!Insertable.Type.EMPTY.equals(t.getType())) {
                        JCheckBoxMenuItem item = new JCheckBoxMenuItem(t.getComponentName(), t.isComponentEnabled());
                        item.addActionListener(e -> {
                            if (e.getSource() instanceof JCheckBoxMenuItem) {
                                t.setComponentEnabled(((JCheckBoxMenuItem) e.getSource()).isSelected());
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
            }
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
        Optional.ofNullable(actions).ifPresent(l -> l.forEach(a -> menu.add(new JMenuItem(a))));
    }

    private void buildSelectedPluginMenu(final JMenu selectedPluginMenu) {
        if (selectedPlugin != null) {
            selectedPlugin.fillSelectedPluginMenu(selectedPluginMenu);
        }
    }

    private void buildMenuView() {
        menuView.removeAll();

        DynamicMenu toolBarMenu = new DynamicMenu(Messages.getString("WeasisWin.toolbar")) {//$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildToolBarSubMenu(this);

            }
        };
        toolBarMenu.addPopupMenuListener();
        menuView.add(toolBarMenu);

        DynamicMenu toolMenu = new DynamicMenu(Messages.getString("WeasisWin.tools")) { //$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildToolSubMenu(this);

            }
        };
        toolMenu.addPopupMenuListener();
        menuView.add(toolMenu);

        DynamicMenu explorerMenu = new DynamicMenu("Explorer") { //$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildEplorerSubMenu(this);
            }

        };
        explorerMenu.addPopupMenuListener();
        menuView.add(explorerMenu);

        // TODO add save workspace layout
        // final AbstractAction saveAction = new AbstractAction("Save workspace layout") { //$NON-NLS-1$
        //
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // // Handle workspace ui persistence
        // try {
        // UIManager.DOCKING_CONTROL.save("lastLayout", false);
        // final BundleContext context = FrameworkUtil.getBundle(WeasisWin.class).getBundleContext();
        // File file = new File(BundlePreferences.getDataFolder(context), "lastLayout.xml");
        // UIManager.DOCKING_CONTROL.writeXML(file);
        // } catch (IOException e1) {
        // e1.printStackTrace();
        // }
        // }
        // };
        // menuView.add(saveAction);
        //
        // final AbstractAction loadAction = new AbstractAction("Restore last workspace layout") { //$NON-NLS-1$
        //
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // try {
        // final BundleContext context = FrameworkUtil.getBundle(WeasisWin.class).getBundleContext();
        // File file = new File(BundlePreferences.getDataFolder(context), "lastLayout.xml");
        // if (file.canRead()) {
        // UIManager.DOCKING_CONTROL.readXML(file);
        // UIManager.DOCKING_CONTROL.load("lastLayout", false);
        // }
        // } catch (IOException e1) {
        // e1.printStackTrace();
        // }
        // }
        // };
        // menuView.add(loadAction);
    }

    private void buildMenuFile() {
        menuFile.removeAll();
        DynamicMenu openMenu = new DynamicMenu(Messages.getString("WeasisWin.open")) { //$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildOpenSubMenu(this);
            }
        };
        openMenu.addPopupMenuListener();
        menuFile.add(openMenu);

        DynamicMenu importMenu = new DynamicMenu(Messages.getString("WeasisWin.import")) {//$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildImportSubMenu(this);
            }
        };
        importMenu.addPopupMenuListener();
        menuFile.add(importMenu);

        DynamicMenu exportMenu = new DynamicMenu(Messages.getString("WeasisWin.export")) {//$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildExportSubMenu(this);
            }
        };
        exportMenu.addPopupMenuListener();

        menuFile.add(exportMenu);
        menuFile.add(new JSeparator());
        DynamicMenu printMenu = new DynamicMenu(Messages.getString("WeasisWin.print")) { //$NON-NLS-1$

            @Override
            public void popupMenuWillBecomeVisible() {
                buildPrintSubMenu(this);
            }
        };
        printMenu.addPopupMenuListener();
        menuFile.add(printMenu);

        menuFile.add(new JSeparator());
        Consumer<ActionEvent> prefAction = e -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(getRootPaneContainer());
            PreferenceDialog dialog = new PreferenceDialog(getFrame());
            ColorLayerUI.showCenterScreen(dialog, layer);
        };
        DefaultAction preferencesAction =
            new DefaultAction(org.weasis.core.ui.Messages.getString("OpenPreferencesAction.title"), //$NON-NLS-1$
                prefAction);
        preferencesAction.putValue(Action.ACCELERATOR_KEY,
            KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK));
        menuFile.add(new JMenuItem(preferencesAction));

        menuFile.add(new JSeparator());
        DefaultAction exitAction = new DefaultAction(Messages.getString("ExitAction.title"), //$NON-NLS-1$
            e -> closeWindow());
        menuFile.add(new JMenuItem(exitAction));
    }

    private class SequenceHandler extends TransferHandler {

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
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
            if (support.isDataFlavorSupported(Series.sequenceDataFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(UriListFlavor.flavor)) {
                return true;
            }
            return false;
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
                    LOGGER.error("Get dragable files", e); //$NON-NLS-1$
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
                    LOGGER.error("Get dragable URIs", e); //$NON-NLS-1$
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
                            if (model instanceof TreeModel) {
                                ArrayList<MediaSeries<MediaElement>> list = new ArrayList<>(1);
                                list.add(seq);
                                ViewerPluginBuilder builder = new ViewerPluginBuilder(factory, list, model, null);
                                openSeriesInViewerPlugin(builder,
                                    ((TreeModel) model).getParent(seq, model.getTreeModelNodeForNewPlugin()));
                            } else {
                                ViewerPluginBuilder.openSequenceInDefaultPlugin(seq,
                                    model == null ? ViewerPluginBuilder.DefaultDataModel : model, true, true);
                            }
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Open series", e); //$NON-NLS-1$
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
                        List<File> cFiles = codecs.get(c);
                        if (cFiles == null) {
                            cFiles = new ArrayList<>();
                            codecs.put(c, cFiles);
                        }
                        cFiles.add(file);
                    }
                }
            }

            if (!dirs.isEmpty() && !explorers.isEmpty()) {
                importInExplorer(explorers, dirs, dropLocation);
            }

            for (Iterator<Entry<Codec, List<File>>> it = codecs.entrySet().iterator(); it.hasNext();) {
                Entry<Codec, List<File>> entry = it.next();
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

    private void importInExplorer(List<DataExplorerView> exps, final List<File> vals, DropLocation dropLocation) {
        if (exps.size() == 1) {
            exps.get(0).importFiles(vals.toArray(new File[vals.size()]), true);
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
                item.addActionListener(
                    e -> dataExplorerView.importFiles(vals.toArray(new File[vals.size()]), true));
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

    public void info(String[] argv) throws IOException {
        final String[] usage = { "Show information about Weasis", "Usage: weasis:info (-v | -a)", //$NON-NLS-1$ //$NON-NLS-2$
            "  -v --version    show version", //$NON-NLS-1$
            "  -a --all        show weasis specifications", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$

        Option opt = Options.compile(usage).parse(argv);

        if (opt.isSet("version")) { //$NON-NLS-1$
            System.out.println(AppProperties.WEASIS_VERSION);
        } else if (opt.isSet("all")) { //$NON-NLS-1$
            PrintStream out = System.out;
            out.println("  " + AppProperties.WEASIS_NAME + " " + AppProperties.WEASIS_VERSION); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  Installation path: " + AppProperties.WEASIS_PATH); //$NON-NLS-1$
            out.println("  Path for temporary files: " + AppProperties.APP_TEMP_DIR); //$NON-NLS-1$
            out.println("  Profile: " + AppProperties.WEASIS_PROFILE); //$NON-NLS-1$
            out.println("  User: " + AppProperties.WEASIS_USER); //$NON-NLS-1$
            out.println("  OSGI native specs: " + System.getProperty("native.library.spec")); //$NON-NLS-1$ //$NON-NLS-2$
            out.format("  Operating system: %s %s %s", System.getProperty("os.name"), System.getProperty("os.version"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                System.getProperty("os.arch")); //$NON-NLS-1$
            out.println();
            out.println("  Java vendor: " + System.getProperty("java.vendor")); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  Java version: " + System.getProperty("java.version")); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  Java Path: " + System.getProperty("java.home")); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            opt.usage();
        }
    }

    public void ui(String[] argv) throws IOException {
        final String[] usage = { "Manage user interface", "Usage: weasis:ui (-q | -v)", //$NON-NLS-1$ //$NON-NLS-2$
            "  -q --quit        shutdown Weasis", //$NON-NLS-1$
            "  -v --visible     set window on top", //$NON-NLS-1$
            "  -? --help        show help" }; //$NON-NLS-1$

        Option opt = Options.compile(usage).parse(argv);
        if (opt.isSet("quit")) { //$NON-NLS-1$
            System.exit(0);
        } else if (opt.isSet("visible")) { //$NON-NLS-1$
            GuiExecutor.instance().execute(() -> {
                Frame app = getFrame();
                app.setVisible(true);
                int state = app.getExtendedState();
                state &= ~Frame.ICONIFIED;
                app.setExtendedState(state);
                app.setVisible(true);
                /*
                 * Sets the window to be "always on top" instead using toFront() method that does not always bring the
                 * window to the front. It depends the platform, Windows XP or Ubuntu has the facility to prevent
                 * windows from stealing focus; instead it flashes the taskbar icon.
                 */
                if (app.isAlwaysOnTopSupported()) {
                    app.setAlwaysOnTop(true);

                    try {
                        Thread.sleep(500L);
                        Robot robot = new Robot();
                        Point p = app.getLocationOnScreen();
                        robot.mouseMove(p.x + app.getWidth() / 2, p.y + 5);
                        // Simulate a mouse click
                        robot.mousePress(InputEvent.BUTTON1_MASK);
                        robot.mouseRelease(InputEvent.BUTTON1_MASK);
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
