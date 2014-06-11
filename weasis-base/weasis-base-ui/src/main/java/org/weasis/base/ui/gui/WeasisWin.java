/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.base.ui.gui;

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
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import javax.swing.LookAndFeel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;
import org.weasis.base.ui.action.ExitAction;
import org.weasis.base.ui.action.OpenPreferencesAction;
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
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ColorLayerUI;
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

    private static final Logger log = LoggerFactory.getLogger(WeasisWin.class);

    private static final JMenu menuFile = new JMenu(Messages.getString("WeasisWin.file")); //$NON-NLS-1$
    private static final JMenu menuDisplay = new JMenu(Messages.getString("WeasisWin.display")); //$NON-NLS-1$
    private static final JMenu menuSelectedPlugin = new JMenu();
    private static ViewerPlugin selectedPlugin = null;

    private static final WeasisWin instance = new WeasisWin();

    private final ToolBarContainer toolbarContainer;

    private volatile boolean busy = false;

    private final List<Runnable> runOnClose = new ArrayList<Runnable>();

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
        }
    };

    private WeasisWin() {

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
            log.debug("Error while receiving main window", e); //$NON-NLS-1$
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
            ImageIcon icon = ResourceUtil.getIconLogo64();
            if (icon != null) {
                frame.setIconImage(icon.getImage()); 
            }
        }
    }

    public static WeasisWin getInstance() {
        return instance;
    }

    public Frame getFrame() {
        return frame;
    }

    public RootPaneContainer getRootPaneContainer() {
        return rootPaneContainer;
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
        runOnClose(new Runnable() {
            @Override
            public void run() {
                control.destroy();
            }
        });
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
            Color ActiveTextColor = javax.swing.UIManager.getColor("TextArea.selectionForeground"); //$NON-NLS-1$

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
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focused", ActiveTextColor); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "stack.tab.text.selected.focuslost", RexSystemColor.getInactiveTextColor()); //$NON-NLS-1$

            colors.put(Priority.CLIENT, "title.flap.active", selection); //$NON-NLS-1$
            colors.put(Priority.CLIENT, "title.flap.active.text", ActiveTextColor); //$NON-NLS-1$
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

    HashMap<MediaSeriesGroup, List<MediaSeries<? extends MediaElement<?>>>> getSeriesByEntry(TreeModel treeModel,
        List<MediaSeries<? extends MediaElement<?>>> series, TreeModelNode entry) {
        HashMap<MediaSeriesGroup, List<MediaSeries<? extends MediaElement<?>>>> map =
            new HashMap<MediaSeriesGroup, List<MediaSeries<? extends MediaElement<?>>>>();
        if (series != null && treeModel != null && entry != null) {
            for (MediaSeries<? extends MediaElement<?>> s : series) {
                MediaSeriesGroup entry1 = treeModel.getParent(s, entry);
                List<MediaSeries<? extends MediaElement<?>>> seriesList = map.get(entry1);
                if (seriesList == null) {
                    seriesList = new ArrayList<MediaSeries<? extends MediaElement<?>>>();
                }
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
        List<MediaSeries<? extends MediaElement<?>>> seriesList = builder.getSeries();
        Map<String, Object> props = builder.getProperties();

        Rectangle screenBound = (Rectangle) props.get(ViewerPluginBuilder.SCREEN_BOUND);
        boolean setInSelection = JMVUtils.getNULLtoFalse(props.get(ViewerPluginBuilder.OPEN_IN_SELECTION));

        if (screenBound == null && factory != null && group != null) {
            boolean bestDefaultLayout = JMVUtils.getNULLtoTrue(props.get(ViewerPluginBuilder.BEST_DEF_LAYOUT));
            synchronized (UIManager.VIEWER_PLUGINS) {
                for (final ViewerPlugin p : UIManager.VIEWER_PLUGINS) {
                    if (p instanceof ImageViewerPlugin && p.getName().equals(factory.getUIName())
                        && group.equals(p.getGroupID())) {
                        ImageViewerPlugin viewer = ((ImageViewerPlugin) p);
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
            props.put(DefaultView2d.class.getName(), seriesList.size());
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
            if (group == null && model instanceof TreeModel && seriesList.size() > 0
                && model.getTreeModelNodeForNewPlugin() != null) {
                TreeModel treeModel = (TreeModel) model;
                MediaSeries s = seriesList.get(0);
                group = treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
            }
            if (group != null) {
                title = group.toString();
                viewer.setGroupID(group);
                if (title.length() > 30) {
                    viewer.setToolTipText(title);
                    title = title.substring(0, 30);
                    title = title.concat("..."); //$NON-NLS-1$
                }
                viewer.setPluginName(title);
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
            }
        }
    }

    private void setExternalPosition(final DefaultSingleCDockable dockable) {
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

    private boolean registerDetachWindow(final ViewerPlugin plugin, Rectangle screenBound) {
        if (plugin != null && screenBound != null) {
            ViewerPlugin oldWin = null;
            synchronized (UIManager.VIEWER_PLUGINS) {
                Dialog old = null;
                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                    ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                    if (p.getDockable().isExternalizable()) {
                        Dialog dialog = WinUtil.getParentDialog(p);
                        old = dialog;
                        if (dialog != null && old != dialog
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
                dock.setLocation(CLocation.external(screenBound.x, screenBound.y, screenBound.width - 150,
                    screenBound.height - 150));
                plugin.showDockable();
                GuiExecutor.instance().execute(new Runnable() {

                    @Override
                    public void run() {
                        if (dock.isVisible()) {
                            UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                            dock.setExtendedMode(ExtendedMode.MAXIMIZED);
                            UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
                        }
                    }
                });
            } else {
                Component parent = WinUtil.getParentOfClass(oldWin, ConfiguredBackgroundPanel.class);
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
            toolbarContainer.registerToolBar(null);
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
        selectedPlugin.fillSelectedPluginMenu(menuSelectedPlugin);

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

        Rectangle bound = null;

        GraphicsConfiguration config = ge.getDefaultScreenDevice().getDefaultConfiguration();
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
        bound = b;

        log.debug("Max main screen bound: {}", bound.toString()); //$NON-NLS-1$
        // setMaximizedBounds(bound);

        // Do not apply to JApplet
        if (frame == rootPaneContainer) {
            // set a valid size, insets of screen is often non consistent
            frame.setBounds(bound.x, bound.y, bound.width - 150, bound.height - 150);
            frame.setVisible(true);

            frame.setExtendedState((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
                ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
        }
        log.info("End of loading the GUI..."); //$NON-NLS-1$
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        buildMenuFile();
        menuBar.add(menuFile);
        buildMenuDisplay();
        menuBar.add(menuDisplay);
        menuBar.add(menuSelectedPlugin);
        final JMenu helpMenuItem = new JMenu(Messages.getString("WeasisWin.help")); //$NON-NLS-1$
        final String helpURL = System.getProperty("weasis.help.url"); //$NON-NLS-1$
        if (helpURL != null) {
            final JMenuItem helpContentMenuItem = new JMenuItem(Messages.getString("WeasisWin.guide")); //$NON-NLS-1$
            helpContentMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        JMVUtils.OpenInDefaultBrowser(helpContentMenuItem, new URL(helpURL));
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                }
            });
            helpMenuItem.add(helpContentMenuItem);
        }

        final JMenuItem webMenuItem = new JMenuItem(Messages.getString("WeasisWin.shortcuts")); //$NON-NLS-1$
        webMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    URL url = new URL(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.shortcuts")); //$NON-NLS-1$
                    JMVUtils.OpenInDefaultBrowser(webMenuItem, url);
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
        helpMenuItem.add(webMenuItem);
        final JMenuItem websiteMenuItem = new JMenuItem(Messages.getString("WeasisWin.online")); //$NON-NLS-1$
        websiteMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    URL url = new URL(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.help.online")); //$NON-NLS-1$
                    JMVUtils.OpenInDefaultBrowser(websiteMenuItem, url);
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
        helpMenuItem.add(websiteMenuItem);
        final JMenuItem aboutMenuItem =
            new JMenuItem(String.format(Messages.getString("WeasisAboutBox.about"), AppProperties.WEASIS_NAME)); //$NON-NLS-1$
        aboutMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootPaneContainer);
                WeasisAboutBox about = new WeasisAboutBox();
                JMVUtils.showCenterScreen(about, rootPaneContainer.getRootPane());
                if (layer != null) {
                    layer.hideUI();
                }
            }
        });
        helpMenuItem.add(aboutMenuItem);
        menuBar.add(helpMenuItem);
        return menuBar;
    }

    private void buildToolBarSubMenu(final JMenu toolBarMenu) {
        List<Toolbar> bars = toolbarContainer.getRegisteredToolBars();
        for (final Toolbar bar : bars) {
            if (!Insertable.Type.EMPTY.equals(bar.getType())) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (e.getSource() instanceof JCheckBoxMenuItem) {
                            toolbarContainer.displayToolbar(bar.getComponent(),
                                ((JCheckBoxMenuItem) e.getSource()).isSelected());
                        }
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
                    item.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (e.getSource() instanceof JCheckBoxMenuItem) {
                                t.setComponentEnabled(((JCheckBoxMenuItem) e.getSource()).isSelected());
                                if (t.isComponentEnabled()) {
                                    t.showDockable();
                                } else {
                                    t.closeDockable();
                                }
                            }
                        }
                    });
                    toolMenu.add(item);
                }
            }
        }
    }

    private static void buildPrintSubMenu(final JMenu printMenu) {
        if (selectedPlugin != null) {
            List<Action> actions = selectedPlugin.getPrintActions();
            if (actions != null) {
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
            }
        }
    }

    private static void buildOpenSubMenu(final JMenu importMenu) {
        synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
            List<SeriesViewerFactory> viewers = UIManager.SERIES_VIEWER_FACTORIES;
            for (final SeriesViewerFactory view : viewers) {
                List<Action> actions = view.getOpenActions();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        importMenu.add(item);
                    }
                }
            }
        }
    }

    private static void buildImportSubMenu(final JMenu importMenu) {
        synchronized (UIManager.EXPLORER_PLUGINS) {
            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
            for (final DataExplorerView dataExplorerView : explorers) {
                List<Action> actions = dataExplorerView.getOpenImportDialogAction();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        importMenu.add(item);
                    }
                }
            }
        }
    }

    private static void buildExportSubMenu(final JMenu exportMenu) {
        // TODO export workspace in as preference

        //                final AbstractAction saveAction = new AbstractAction("Save workspace layout") { //$NON-NLS-1$
        //
        // @Override
        // public void actionPerformed(ActionEvent e) {
        // // Handle workspace ui persistence
        // PersistenceDelegate pstDelegate = UIManager.toolWindowManager.getPersistenceDelegate();
        // try {
        //                                pstDelegate.save(new FileOutputStream(new File("/home/nicolas/Documents/test.xml"))); //$NON-NLS-1$
        // } catch (FileNotFoundException e1) {
        // e1.printStackTrace();
        // }
        // }
        // };
        // exportMenu.add(saveAction);

        synchronized (UIManager.EXPLORER_PLUGINS) {
            if (selectedPlugin != null) {
                List<Action> actions = selectedPlugin.getExportActions();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        exportMenu.add(item);
                    }
                }
            }

            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
            for (final DataExplorerView dataExplorerView : explorers) {
                List<Action> actions = dataExplorerView.getOpenExportDialogAction();
                if (actions != null) {
                    for (Action action : actions) {
                        JMenuItem item = new JMenuItem(action);
                        exportMenu.add(item);
                    }
                }
            }
        }
    }

    private void buildMenuDisplay() {
        menuDisplay.removeAll();

        DynamicMenu toolBarMenu = new DynamicMenu(Messages.getString("WeasisWin.toolbar")) {//$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildToolBarSubMenu(this);

                }
            };
        toolBarMenu.addPopupMenuListener();
        menuDisplay.add(toolBarMenu);

        DynamicMenu toolMenu = new DynamicMenu(Messages.getString("WeasisWin.tools")) { //$NON-NLS-1$

                @Override
                public void popupMenuWillBecomeVisible() {
                    buildToolSubMenu(this);

                }
            };
        toolMenu.addPopupMenuListener();
        menuDisplay.add(toolMenu);
    }

    private static void buildMenuFile() {
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
        menuFile.add(new JMenuItem(OpenPreferencesAction.getInstance()));
        menuFile.add(new JSeparator());
        menuFile.add(new JMenuItem(ExitAction.getInstance()));
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
                || support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
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
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files, support.getDropLocation());
            }
            // When dragging a file or group of files from a Gnome or Kde environment
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.uriListFlavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.uriListFlavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return dropDicomFiles(files, support.getDropLocation());
            }

            Series seq;
            try {
                seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
                // Do not add series without medias. BUG WEA-100
                if (seq == null || seq.size(null) == 0) {
                    return false;
                }

                synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                    for (final SeriesViewerFactory factory : UIManager.SERIES_VIEWER_FACTORIES) {
                        if (factory.canReadMimeType(seq.getMimeType())) {
                            DataExplorerModel model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
                            if (model instanceof TreeModel) {
                                ArrayList<MediaSeries<? extends MediaElement<?>>> list =
                                    new ArrayList<MediaSeries<? extends MediaElement<?>>>(1);
                                list.add(seq);
                                ViewerPluginBuilder builder = new ViewerPluginBuilder(factory, list, model, null);
                                openSeriesInViewerPlugin(builder,
                                    ((TreeModel) model).getParent(seq, model.getTreeModelNodeForNewPlugin()));
                            } else {
                                ViewerPluginBuilder.openSequenceInDefaultPlugin(seq, model == null
                                    ? ViewerPluginBuilder.DefaultDataModel : model, true, true);
                            }
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                return false;
            }
            return true;
        }

        private boolean dropDicomFiles(final List<File> files, DropLocation dropLocation) {
            if (files != null) {
                List<DataExplorerView> explorers = new ArrayList<DataExplorerView>(UIManager.EXPLORER_PLUGINS);
                for (int i = explorers.size() - 1; i >= 0; i--) {
                    if (!explorers.get(i).canImportFiles()) {
                        explorers.remove(i);
                    }
                }

                int size = explorers.size();

                if (size == 1) {
                    explorers.get(0).importFiles(files.toArray(new File[files.size()]), true);
                } else {
                    Point p;
                    if (dropLocation == null) {
                        Rectangle b = WeasisWin.this.getFrame().getBounds();
                        p = new Point((int) b.getCenterX(), (int) b.getCenterY());
                    } else {
                        p = dropLocation.getDropPoint();
                    }

                    JPopupMenu popup = new JPopupMenu();

                    for (final DataExplorerView dataExplorerView : explorers) {
                        JMenuItem item = new JMenuItem(dataExplorerView.getUIName(), dataExplorerView.getIcon());
                        item.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dataExplorerView.importFiles(files.toArray(new File[files.size()]), true);
                            }
                        });
                        popup.add(item);
                    }

                    // popup.addSeparator();
                    // JMenuItem item = new JMenuItem("Open files directly with associated viewers");
                    // item.addActionListener(new ActionListener() {
                    //
                    // @Override
                    // public void actionPerformed(ActionEvent e) {
                    // for (File file : files) {
                    // ViewerPluginBuilder.openSequenceInDefaultPlugin(file, true, true);
                    // }
                    // }
                    // });
                    // popup.add(item);

                    popup.show(WeasisWin.this.getFrame(), p.x, p.y);
                }

                return true;
            }
            return false;
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
}
