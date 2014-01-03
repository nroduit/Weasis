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
package org.weasis.base.viewer2d;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.base.viewer2d.dockable.DisplayTool;
import org.weasis.base.viewer2d.dockable.ImageTool;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

public class View2dContainer extends ImageViewerPlugin<ImageElement> implements PropertyChangeListener {

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());
    static {
        SYNCH_LIST.add(SynchView.NONE);
        SYNCH_LIST.add(SynchView.DEFAULT_STACK);
        SYNCH_LIST.add(SynchView.DEFAULT_TILE);
    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST = Collections
        .synchronizedList(new ArrayList<GridBagLayoutModel>());
    static {
        LAYOUT_LIST.add(VIEWS_1x1);
        LAYOUT_LIST.add(VIEWS_1x2);
        LAYOUT_LIST.add(VIEWS_2x1);
        LAYOUT_LIST.add(VIEWS_2x2_f2);
        LAYOUT_LIST.add(VIEWS_2_f1x2);
        LAYOUT_LIST.add(VIEWS_2x2);
        LAYOUT_LIST.add(VIEWS_3x2);
        LAYOUT_LIST.add(VIEWS_3x3);
        LAYOUT_LIST.add(VIEWS_4x3);
        LAYOUT_LIST.add(VIEWS_4x4);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    private static WtoolBar statusBar = null;
    private static volatile boolean INI_COMPONENTS = false;

    public View2dContainer() {
        this(VIEWS_1x1);
    }

    public View2dContainer(GridBagLayoutModel layoutModel) {
        super(EventManager.getInstance(), layoutModel, ViewerFactory.NAME, ViewerFactory.ICON, null);
        setSynchView(SynchView.DEFAULT_STACK);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;
            // Add standard toolbars
            EventManager evtMg = EventManager.getInstance();
            TOOLBARS.add(new ViewerToolBar<ImageElement>(evtMg, evtMg.getMouseActions().getActiveButtons(),
                BundleTools.SYSTEM_PREFERENCES, 10));
            TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            TOOLBARS.add(new ZoomToolBar(evtMg, 20));
            TOOLBARS.add(new RotationToolBar(evtMg, 30));

            PluginTool tool = new MiniTool(Messages.getString("View2dContainer.mini")) { //$NON-NLS-1$

                    @Override
                    public SliderChangeListener[] getActions() {
                        ArrayList<SliderChangeListener> listeners = new ArrayList<SliderChangeListener>(3);
                        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                        if (seqAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) seqAction);
                        }
                        ActionState zoomAction = eventManager.getAction(ActionW.ZOOM);
                        if (zoomAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) zoomAction);
                        }
                        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                        if (rotateAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) rotateAction);
                        }
                        return listeners.toArray(new SliderChangeListener[listeners.size()]);
                    }
                };
            TOOLS.add(tool);

            tool = new ImageTool(Messages.getString("View2dContainer.img_tool"), null); //$NON-NLS-1$
            TOOLS.add(tool);

            tool = new DisplayTool(Messages.getString("View2dContainer.display")); //$NON-NLS-1$
            TOOLS.add(tool);
            eventManager.addSeriesViewerListener((SeriesViewerListener) tool);

            tool = new MeasureTool(eventManager);
            TOOLS.add(tool);

            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                String className = this.getClass().getSimpleName().toLowerCase();
                String bundleName = context.getBundle().getSymbolicName();
                InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, className, Type.TOOLBAR);
                InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, className, Type.TOOL);
            }
        }
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            menuRoot.setText(ViewerFactory.NAME);

            List<Action> actions = getPrintActions();
            if (actions != null) {
                JMenu printMenu = new JMenu("Print");
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
                menuRoot.add(printMenu);
            }
            ActionState lutAction = eventManager.getAction(ActionW.LUT);
            if (lutAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) lutAction).createMenu(Messages.getString("View2dContainer.lut")); //$NON-NLS-1$
                ActionState invlutAction = eventManager.getAction(ActionW.INVERSELUT);
                if (invlutAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invlutAction).createMenu(Messages
                        .getString("View2dContainer.inv_lut"))); //$NON-NLS-1$
                }
                menuRoot.add(menu);
            }
            ActionState filterAction = eventManager.getAction(ActionW.FILTER);
            if (filterAction instanceof ComboItemListener) {
                JMenu menu =
                    ((ComboItemListener) filterAction).createMenu(Messages.getString("View2dContainer.filter")); //$NON-NLS-1$
                menuRoot.add(menu);
            }
            // ActionState stackAction = EventManager.getInstance().getAction(ActionW.SORTSTACK);
            // if (stackAction instanceof ComboItemListener) {
            // JMenu menu = ((ComboItemListener) stackAction).createMenu("Sort Stack by");
            // ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
            // if (invstackAction instanceof ToggleButtonListener) {
            // menu.add(new JSeparator());
            // menu.add(((ToggleButtonListener) invstackAction).createMenu("Inverse Stack"));
            // }
            // menuRoot.add(menu);
            // }
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                menuRoot.add(new JSeparator());
                JMenu menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2dContainer.reset")); //$NON-NLS-1$
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem(Messages.getString("View2dContainer.180")); //$NON-NLS-1$
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 180) % 360);
                    }
                });
                menu.add(menuItem);
                ActionState flipAction = eventManager.getAction(ActionW.FLIP);
                if (flipAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) flipAction).createMenu(Messages.getString("View2dContainer.flip"))); //$NON-NLS-1$
                    menuRoot.add(menu);
                }
            }
            menuRoot.add(new JSeparator());
            menuRoot.add(ResetTools.createUnregisteredJMenu());

        }
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        super.close();
        ViewerFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (DefaultView2d v : view2ds) {
                    v.dispose();
                }
            }
        });

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            if (newVal instanceof SeriesEvent) {
                SeriesEvent event2 = (SeriesEvent) newVal;
                if (ObservableEvent.BasicAction.Add.equals(action)) {
                    SeriesEvent.Action action2 = event2.getActionCommand();
                    Object source = event2.getSource();
                    Object param = event2.getParam();

                    if (SeriesEvent.Action.AddImage.equals(action2)) {
                        if (source instanceof Series) {
                            Series series = (Series) source;
                            DefaultView2d view2DPane = eventManager.getSelectedViewPane();
                            ImageElement img = view2DPane.getImage();
                            if (img != null && view2DPane.getSeries() == series) {
                                ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                if (seqAction instanceof SliderCineListener) {
                                    SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                    if (param instanceof ImageElement) {
                                        Filter<ImageElement> filter =
                                            (Filter<ImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES
                                                .cmd());
                                        int imgIndex =
                                            series.getImageIndex(img, filter, view2DPane.getCurrentSortComparator());
                                        if (imgIndex < 0) {
                                            imgIndex = 0;
                                            // add again the series for registering listeners
                                            // (require at least one image)
                                            view2DPane.setSeries(series, null);
                                        }
                                        if (imgIndex >= 0) {
                                            sliceAction.setMinMaxValue(1, series.size(filter), imgIndex + 1);
                                        }
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.loadImageInMemory.equals(action2)) {
                        if (source instanceof Series) {
                            Series s = (Series) source;
                            for (DefaultView2d<ImageElement> v : view2ds) {
                                if (s == v.getSeries()) {
                                    v.repaint(v.getInfoLayer().getPreloadingProgressBound());
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Remove.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (DefaultView2d<ImageElement> v : view2ds) {
                        MediaSeries<ImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            v.setSeries(null);
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.Replace.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (DefaultView2d<ImageElement> v : view2ds) {
                        MediaSeries<ImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            // Set to null to be sure that all parameters from the view are apply again to the Series
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
    public JComponent createUIcomponent(String clazz) {
        if (DefaultView2d.class.getName().equals(clazz) || View2d.class.getName().equals(clazz)) {
            return createDefaultView(clazz);
        }
        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            return component;

        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }

        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized WtoolBar getStatusBar() {
        return statusBar;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        if (selectedImagePane != null) {
            return selectedImagePane.getExportToClipboardAction();
        }
        return null;
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return ViewerFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<Action>(1);
        final String title = "Print 2D viewer layout";
        AbstractAction printStd =
            new AbstractAction(title, new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png"))) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent e) {
                    ColorLayerUI layer =
                        ColorLayerUI.createTransparentLayerUI(WinUtil.getParentJFrame(View2dContainer.this));
                    Window parent = SwingUtilities.getWindowAncestor(View2dContainer.this);
                    PrintDialog dialog = new PrintDialog(parent, title, eventManager);
                    JMVUtils.showCenterScreen(dialog, parent);
                    if (layer != null) {
                        layer.hideUI();
                    }
                }
            };
        actions.add(printStd);
        return actions;
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
