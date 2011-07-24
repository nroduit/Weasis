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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import org.weasis.base.viewer2d.dockable.DisplayTool;
import org.weasis.base.viewer2d.dockable.ImageTool;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.WtoolBar;

public class View2dContainer extends ImageViewerPlugin<ImageElement> implements PropertyChangeListener {

    public static final GridBagLayoutModel[] MODELS = { VIEWS_1x1, VIEWS_2x1, VIEWS_1x2, VIEWS_2x2, VIEWS_3x2,
        VIEWS_3x3, VIEWS_4x3, VIEWS_4x4 };

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    private static PluginTool[] toolPanels;
    private static WtoolBar statusBar = null;
    private static WtoolBar[] toolBars;

    public View2dContainer() {
        this(VIEWS_1x1);
    }

    public View2dContainer(GridBagLayoutModel layoutModel) {
        super(EventManager.getInstance(), layoutModel, ViewerFactory.NAME, ViewerFactory.ICON, null);
        setSynchView(SynchView.DEFAULT_STACK);
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            menuRoot.setText(ViewerFactory.NAME);

            ActionState lutAction = eventManager.getAction(ActionW.LUT);
            if (lutAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) lutAction).createMenu("LUT");
                ActionState invlutAction = eventManager.getAction(ActionW.INVERSELUT);
                if (invlutAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invlutAction).createMenu("Inverse LUT"));
                }
                menuRoot.add(menu);
            }
            ActionState filterAction = eventManager.getAction(ActionW.FILTER);
            if (filterAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) filterAction).createMenu("Filter");
                menuRoot.add(menu);
            }
            ActionState stackAction = EventManager.getInstance().getAction(ActionW.SORTSTACK);
            if (stackAction instanceof ComboItemListener) {
                JMenu menu = ((ComboItemListener) stackAction).createMenu("Sort Stack by");
                ActionState invstackAction = eventManager.getAction(ActionW.INVERSESTACK);
                if (invstackAction instanceof ToggleButtonListener) {
                    menu.add(new JSeparator());
                    menu.add(((ToggleButtonListener) invstackAction).createMenu("Inverse Stack"));
                }
                menuRoot.add(menu);
            }
            ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
            if (rotateAction instanceof SliderChangeListener) {
                menuRoot.add(new JSeparator());
                JMenu menu = new JMenu("Orientation");
                JMenuItem menuItem = new JMenuItem("Reset");
                final SliderChangeListener rotation = (SliderChangeListener) rotateAction;
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue(0);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem("- 90");
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() - 90 + 360) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem("+90");
                menuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        rotation.setValue((rotation.getValue() + 90) % 360);
                    }
                });
                menu.add(menuItem);
                menuItem = new JMenuItem("+180");
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
                    menu.add(((ToggleButtonListener) flipAction).createMenu("Flip Horizontally"));
                    menuRoot.add(menu);
                }
            }
            menuRoot.add(new JSeparator());
            menuRoot.add(ResetTools.createUnregisteredJMenu());

        }
        return menuRoot;
    }

    @Override
    public synchronized PluginTool[] getToolPanel() {
        if (toolPanels == null) {
            toolPanels = new PluginTool[3];
            toolPanels[0] = new MiniTool("Mini", null) {

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
            toolPanels[0].setHide(false);
            toolPanels[0].registerToolAsDockable();
            toolPanels[1] = new ImageTool("Image Tools", null);
            toolPanels[1].registerToolAsDockable();
            toolPanels[2] = new DisplayTool("Display", null);
            toolPanels[2].registerToolAsDockable();
            eventManager.addSeriesViewerListener((SeriesViewerListener) toolPanels[2]);
            // toolPanels[3] = new DrawToolsDockable();
        }
        return toolPanels;
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
                                    if (param instanceof Integer) {
                                        int imgIndex = series.getImageIndex(img);
                                        int size = series.size();

                                        if (imgIndex < 0) {
                                            imgIndex = 0;
                                            // add again the series for registering listeners
                                            // (require at least one image)
                                            view2DPane.setSeries(series, -1);
                                        }
                                        if (imgIndex >= 0) {
                                            sliceAction.setMinMaxValue(1, size, imgIndex + 1);
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
                            v.setSeries(series, -1);
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
        if (DefaultView2d.class.getName().equals(clazz) || View2d.class.getName().equals(clazz))
            return createDefaultView(clazz);
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
    public synchronized WtoolBar[] getToolBar() {
        if (toolBars == null) {
            toolBars = new WtoolBar[2];
            ViewerToolBar<ImageElement> bar = new ViewerToolBar<ImageElement>(eventManager);
            toolBars[0] = bar;
            toolBars[1] = bar.getMeasureToolBar();

        }
        return toolBars;
    }

    @Override
    public List<Action> getExportActions() {
        if (selectedImagePane != null)
            return selectedImagePane.getExportToClipboardAction();
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
}
