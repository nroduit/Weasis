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
// Placed in public domain by Dmitry Olshansky, 2006
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.util.MouseEventDouble;

public abstract class ImageViewerPlugin<E extends ImageElement> extends ViewerPlugin<E> {
    private static final long serialVersionUID = -5804430771962614157L;

    public static final String F_VIEWS = Messages.getString("ImageViewerPlugin.2"); //$NON-NLS-1$

    // A model must have at least one view that inherited of DefaultView2d
    public static final Class<?> view2dClass = ViewCanvas.class;
    public static final GridBagLayoutModel VIEWS_1x1 = new GridBagLayoutModel("1x1", //$NON-NLS-1$
        String.format(Messages.getString("ImageViewerPlugin.1"), "1x1"), 1, 1, view2dClass.getName()); //$NON-NLS-1$ //$NON-NLS-2$
    public static final GridBagLayoutModel VIEWS_2x1 = new GridBagLayoutModel("2x1", //$NON-NLS-1$
        String.format(F_VIEWS, "2x1"), 2, 1, view2dClass.getName()); //$NON-NLS-1$
    public static final GridBagLayoutModel VIEWS_1x2 = new GridBagLayoutModel("1x2", //$NON-NLS-1$
        String.format(F_VIEWS, "1x2"), 1, 2, view2dClass.getName()); //$NON-NLS-1$
    public static final GridBagLayoutModel VIEWS_2x2_f2 =
        new GridBagLayoutModel(ImageViewerPlugin.class.getResourceAsStream("/config/layoutModel2x2_f2.xml"), //$NON-NLS-1$
            "layout_c2x1", Messages.getString("ImageViewerPlugin.layout_c2x1")); //$NON-NLS-1$ //$NON-NLS-2$
    public static final GridBagLayoutModel VIEWS_2_f1x2 =
        new GridBagLayoutModel(ImageViewerPlugin.class.getResourceAsStream("/config/layoutModel2_f1x2.xml"), //$NON-NLS-1$
            "layout_c1x2", Messages.getString("ImageViewerPlugin.layout_c1x2")); //$NON-NLS-1$ //$NON-NLS-2$
    public static final GridBagLayoutModel VIEWS_2x2 = new GridBagLayoutModel("2x2", //$NON-NLS-1$
        String.format(F_VIEWS, "2x2"), 2, 2, view2dClass.getName()); //$NON-NLS-1$

    /**
     * The current focused <code>ImagePane</code>. The default is 0.
     */

    protected ViewCanvas<E> selectedImagePane = null;
    /**
     * The array of display panes located in this image view panel.
     */

    protected final ArrayList<ViewCanvas<E>> view2ds;
    protected final ArrayList<Component> components;

    protected SynchView synchView = SynchView.NONE;

    protected final ImageViewerEventManager<E> eventManager;
    protected final JPanel grid;
    protected GridBagLayoutModel layoutModel;

    private final MouseHandler mouseHandler;

    public ImageViewerPlugin(ImageViewerEventManager<E> eventManager, String pluginName) {
        this(eventManager, VIEWS_1x1, pluginName, null, null, null);
    }

    public ImageViewerPlugin(ImageViewerEventManager<E> eventManager, GridBagLayoutModel layoutModel, String uid,
        String pluginName, Icon icon, String tooltips) {
        super(uid, pluginName, icon, tooltips);
        if (eventManager == null) {
            throw new IllegalArgumentException("EventManager cannot be null"); //$NON-NLS-1$
        }
        this.eventManager = eventManager;
        view2ds = new ArrayList<>();
        components = new ArrayList<>();
        grid = new JPanel();
        // For having a black background with any Look and Feel
        grid.setUI(new PanelUI() {
        });
        grid.setBackground(Color.BLACK);
        grid.setFocusCycleRoot(true);
        grid.setLayout(new GridBagLayout());
        add(grid, BorderLayout.CENTER);

        setLayoutModel(layoutModel);
        this.mouseHandler = new MouseHandler();
        grid.addMouseListener(mouseHandler);
        grid.addMouseMotionListener(mouseHandler);
    }

    /**
     * Returns true if type is instance of defaultClass. This operation is delegated in each bundle to be sure all
     * classes are visible.
     *
     * @param defaultClass
     * @param type
     * @return
     */
    public abstract boolean isViewType(Class<?> defaultClass, String type);

    public abstract int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass);

    public abstract ViewCanvas<E> createDefaultView(String classType);

    public abstract Component createUIcomponent(String clazz);

    public ViewCanvas<E> getSelectedImagePane() {
        return selectedImagePane;
    }

    @Override
    public void close() {
        super.close();

        GuiExecutor.instance().execute(() -> {
            removeComponents();
            for (ViewCanvas v : view2ds) {
                resetMaximizedSelectedImagePane(v);
                v.disposeView();
            }
        });
    }

    /**
     * Get the layout of this panel.
     *
     * @return the layoutModel
     */
    public synchronized GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }

    public GridBagLayoutModel getOriginalLayoutModel() {
        // Get the non clone layout from the list
        ActionState layout = eventManager.getAction(ActionW.LAYOUT);
        if (layout instanceof ComboItemListener) {
            for (Object element : ((ComboItemListener) layout).getAllItem()) {
                if (element instanceof GridBagLayoutModel) {
                    GridBagLayoutModel gbm = (GridBagLayoutModel) element;
                    if ((layoutModel.getIcon() != null && gbm.getIcon() == layoutModel.getIcon())
                        || layoutModel.toString().equals(gbm.toString())) {
                        return gbm;
                    }
                }
            }
        }
        return layoutModel;
    }

    public static GridBagLayoutModel buildGridBagLayoutModel(int rows, int cols, String type) {
        StringBuilder buf = new StringBuilder();
        buf.append(rows);
        buf.append("x"); //$NON-NLS-1$
        buf.append(cols);
        return new GridBagLayoutModel(buf.toString(), String.format(ImageViewerPlugin.F_VIEWS, buf.toString()), rows,
            cols, type);
    }

    @Override
    public void addSeries(MediaSeries<E> sequence) {
        if (sequence != null && selectedImagePane != null) {
            if (SynchData.Mode.TILE.equals(synchView.getSynchData().getMode())) {
                selectedImagePane.setSeries(sequence, null);
                updateTileOffset();
                return;
            }
            ViewCanvas<E> viewPane = getSelectedImagePane();
            if (viewPane != null) {
                viewPane.setSeries(sequence);
                viewPane.getJComponent().repaint();

                // Set selection to the next view
                setSelectedImagePane(getNextSelectedImagePane());
            }
        }
    }

    @Override
    public void removeSeries(MediaSeries<E> series) {
        if (series != null) {
            for (int i = 0; i < view2ds.size(); i++) {
                ViewCanvas<E> v = view2ds.get(i);
                if (v.getSeries() == series) {
                    v.setSeries(null, null);
                }
            }
        }
    }

    @Override
    public List<MediaSeries<E>> getOpenSeries() {
        List<MediaSeries<E>> list = new ArrayList<>();
        for (ViewCanvas<E> v : view2ds) {
            MediaSeries<E> s = v.getSeries();
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    public void changeLayoutModel(GridBagLayoutModel layoutModel) {
        ActionState layout = eventManager.getAction(ActionW.LAYOUT);
        if (layout instanceof ComboItemListener) {
            ((ComboItemListener) layout).setSelectedItem(layoutModel);
        }
    }

    protected void removeComponents() {
        for (Component c : components) {
            if (c instanceof SeriesViewerListener) {
                eventManager.removeSeriesViewerListener((SeriesViewerListener) c);
            }
        }
        components.clear();
    }

    protected JComponent buildInstance(Class<?> cl) throws Exception {
        JComponent component;
        if (hasSeriesViewerConstructor(cl)) {
            component = (JComponent) cl.getConstructor(SeriesViewer.class).newInstance(this);
        } else {
            component = (JComponent) cl.newInstance();
        }

        if (component instanceof SeriesViewerListener) {
            eventManager.addSeriesViewerListener((SeriesViewerListener) component);
        }
        return component;
    };

    private boolean hasSeriesViewerConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 1 && types[0].isAssignableFrom(SeriesViewer.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set a layout to this view panel. The layout is defined by the provided number corresponding the layout definition
     * in the property file.
     */
    protected synchronized void setLayoutModel(GridBagLayoutModel layoutModel) {
        this.layoutModel = layoutModel == null ? VIEWS_1x1.copy() : layoutModel.copy();
        grid.removeAll();
        // Keep views containing images
        ArrayList<ViewCanvas<E>> oldViews = new ArrayList<>();
        for (ViewCanvas<E> v : view2ds) {
            if (v.getSeries() != null && v.getImage() != null) {
                oldViews.add(v);
            } else {
                v.disposeView();
            }
        }
        view2ds.clear();

        int nbview = getViewTypeNumber(layoutModel, view2dClass);
        if (oldViews.size() > nbview) {
            for (int i = oldViews.size() - 1; i >= nbview; i--) {
                oldViews.remove(i).disposeView();
            }
        }
        removeComponents();

        final Map<LayoutConstraints, Component> elements = this.layoutModel.getConstraints();
        Iterator<LayoutConstraints> enumVal = elements.keySet().iterator();
        while (enumVal.hasNext()) {
            LayoutConstraints e = enumVal.next();
            boolean typeView2d = isViewType(view2dClass, e.getType());
            if (typeView2d) {
                ViewCanvas<E> oldView;
                if (oldViews.isEmpty()) {
                    oldView = createDefaultView(e.getType());
                    oldView.registerDefaultListeners();
                } else {
                    oldView = oldViews.remove(0);
                }
                view2ds.add(oldView);
                elements.put(e, oldView.getJComponent());
                grid.add(oldView.getJComponent(), e);
                if (oldView.getSeries() != null) {
                    oldView.getSeries().setOpen(true);
                }
            } else {
                Component component = createUIcomponent(e.getType());
                if (component != null) {
                    if (component instanceof JComponent) {
                        ((JComponent) component).setOpaque(true);
                    }
                    components.add(component);
                    elements.put(e, component);
                    grid.add(component, e);
                }
            }
        }
        grid.revalidate();

        if (!view2ds.isEmpty()) {
            selectedImagePane = view2ds.get(0);

            MouseActions mouseActions = eventManager.getMouseActions();
            boolean tiledMode = SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());
            for (int i = 0; i < view2ds.size(); i++) {
                ViewCanvas<E> v = view2ds.get(i);
                // Close lens because update does not work
                v.closeLens();
                if (tiledMode) {
                    v.setTileOffset(i);
                    v.setSeries(selectedImagePane.getSeries(), null);
                }
                v.enableMouseAndKeyListener(mouseActions);
            }
            selectedImagePane.setSelected(true);
            eventManager.updateComponentsListener(selectedImagePane);
            if (selectedImagePane.getSeries() instanceof Series) {
                eventManager.fireSeriesViewerListeners(new SeriesViewerEvent(this, selectedImagePane.getSeries(),
                    selectedImagePane.getImage(), EVENT.LAYOUT));
            }
        }
    }

    public void replaceView(ViewCanvas<E> oldView2d, ViewCanvas<E> newView2d) {
        if (oldView2d != null && newView2d != null) {
            grid.removeAll();
            final Map<LayoutConstraints, Component> elements = this.layoutModel.getConstraints();
            Iterator<Entry<LayoutConstraints, Component>> enumVal = elements.entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> element = enumVal.next();

                if (element.getValue() == oldView2d.getJComponent()) {
                    if (selectedImagePane == oldView2d) {
                        selectedImagePane = newView2d;
                    }
                    oldView2d.disposeView();
                    int index = view2ds.indexOf(oldView2d);
                    if (index >= 0) {
                        view2ds.set(index, newView2d);
                    }
                    elements.put(element.getKey(), newView2d.getJComponent());
                    grid.add(newView2d.getJComponent(), element.getKey());
                    if (newView2d.getSeries() != null) {
                        newView2d.getSeries().setOpen(true);
                    }
                } else {
                    grid.add(element.getValue(), element.getKey());
                }
            }
            grid.revalidate();

            if (!view2ds.isEmpty()) {
                if (selectedImagePane == null) {
                    selectedImagePane = view2ds.get(0);
                }
                MouseActions mouseActions = eventManager.getMouseActions();
                boolean tiledMode = SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());
                for (int i = 0; i < view2ds.size(); i++) {
                    ViewCanvas<E> v = view2ds.get(i);
                    // Close lens because update does not work
                    v.closeLens();
                    if (tiledMode) {
                        v.setTileOffset(i);
                        v.setSeries(selectedImagePane.getSeries(), null);
                    }
                    v.enableMouseAndKeyListener(mouseActions);
                }
                selectedImagePane.setSelected(true);
                eventManager.updateComponentsListener(selectedImagePane);
            }
        }
    }

    public void setSelectedImagePaneFromFocus(ViewCanvas<E> viewCanvas) {
        setSelectedImagePane(viewCanvas);
    }

    public void setSelectedImagePane(ViewCanvas<E> viewCanvas) {
        if (this.selectedImagePane != null && this.selectedImagePane.getSeries() != null) {
            this.selectedImagePane.getSeries().setSelected(false, null);
            this.selectedImagePane.getSeries().setFocused(false);
        }
        if (viewCanvas != null && viewCanvas.getSeries() != null) {
            viewCanvas.getSeries().setSelected(true, viewCanvas.getImage());
            viewCanvas.getSeries().setFocused(eventManager.getSelectedView2dContainer() == this);
        }

        boolean newView = this.selectedImagePane != viewCanvas && viewCanvas != null;
        if (newView) {
            if (this.selectedImagePane != null) {
                this.selectedImagePane.setSelected(false);
            }
            viewCanvas.setSelected(true);
            this.selectedImagePane = viewCanvas;
            eventManager.updateComponentsListener(viewCanvas);
        }
        if (newView && viewCanvas.getSeries() instanceof Series) {
            eventManager.fireSeriesViewerListeners(
                new SeriesViewerEvent(this, selectedImagePane.getSeries(), selectedImagePane.getImage(), EVENT.SELECT));
        }
        eventManager.fireSeriesViewerListeners(
            new SeriesViewerEvent(this, viewCanvas == null ? null : viewCanvas.getSeries(), null, EVENT.SELECT_VIEW));
    }

    public void resetMaximizedSelectedImagePane(final ViewCanvas<E> viewCanvas) {
        if (grid.getComponentCount() == 1) {
            Dialog fullscreenDialog = WinUtil.getParentDialog(grid);
            if (fullscreenDialog != null
                && fullscreenDialog.getTitle().equals(Messages.getString("ImageViewerPlugin.fullscreen"))) { //$NON-NLS-1$
                maximizedSelectedImagePane(viewCanvas, null);
            }
        }
    }

    public void maximizedSelectedImagePane(final ViewCanvas<E> defaultView2d, MouseEvent evt) {
        final Map<LayoutConstraints, Component> elements = layoutModel.getConstraints();
        // Prevent conflict with double click for stopping to draw a graphic (like polyline)

        List<DragGraphic> selGraphics = defaultView2d.getGraphicManager().getSelectedDragableGraphics();

        // Check if there is at least one graphic not complete (numer of pts == UNDEFINED)
        if (selGraphics.stream().anyMatch(g -> Objects.equals(g.getPtsNumber(), Graphic.UNDEFINED))) {
            return;
        }

        if (evt != null) {
            // Do not maximize when click hits a graphic
            MouseEventDouble mouseEvt = new MouseEventDouble(evt);
            mouseEvt.setImageCoordinates(defaultView2d.getImageCoordinatesFromMouse(evt.getX(), evt.getY()));

            if (defaultView2d.getGraphicManager().getFirstGraphicIntersecting(mouseEvt).isPresent()) {
                return;
            }
        }

        for (ViewCanvas<E> v : view2ds) {
            v.getJComponent().removeFocusListener(v);
        }

        String titleDialog = Messages.getString("ImageViewerPlugin.fullscreen"); //$NON-NLS-1$
        Dialog fullscreenDialog = WinUtil.getParentDialog(grid);
        // Handle the case when the dialog is a detached window and not the fullscreen window.
        final boolean detachedWindow = fullscreenDialog != null && !titleDialog.equals(fullscreenDialog.getTitle());

        grid.removeAll();
        if (detachedWindow || fullscreenDialog == null) {
            remove(grid);
            Iterator<Entry<LayoutConstraints, Component>> enumVal = elements.entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> entry = enumVal.next();
                if (entry.getValue().equals(defaultView2d.getJComponent())) {
                    GridBagConstraints c = entry.getKey().copy();
                    c.weightx = 1.0;
                    c.weighty = 1.0;
                    grid.add(defaultView2d.getJComponent(), c);
                    defaultView2d.getJComponent().addFocusListener(defaultView2d);
                    break;
                }
            }
            Dialog oldDialog = fullscreenDialog;
            Frame frame = WinUtil.getParentFrame(this);
            fullscreenDialog =
                new JDialog(detachedWindow ? oldDialog : frame, titleDialog, ModalityType.APPLICATION_MODAL);
            fullscreenDialog.add(grid, BorderLayout.CENTER);
            fullscreenDialog.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent e) {
                    maximizedSelectedImagePane(defaultView2d, null);
                }
            });

            if (!detachedWindow && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                fullscreenDialog.setBounds(frame.getBounds());
                fullscreenDialog.setVisible(true);
            } else {
                Monitor monitor = Monitor.getMonitor(
                    detachedWindow ? oldDialog.getGraphicsConfiguration() : frame.getGraphicsConfiguration());
                if (monitor != null) {
                    fullscreenDialog.setBounds(monitor.getFullscreenBounds());
                    fullscreenDialog.setVisible(true);
                }
            }

        } else {
            Iterator<Entry<LayoutConstraints, Component>> enumVal = elements.entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> entry = enumVal.next();
                grid.add(entry.getValue(), entry.getKey());
            }
            for (ViewCanvas<E> v : view2ds) {
                v.getJComponent().addFocusListener(v);
            }

            fullscreenDialog.removeAll();
            fullscreenDialog.dispose();
            add(grid, BorderLayout.CENTER);
        }

        defaultView2d.getJComponent().requestFocusInWindow();
    }

    /** Return the image in the image display panel. */

    public E getImage(int i) {
        if (i >= 0 && i < view2ds.size()) {
            return view2ds.get(i).getImage();
        }
        return null;
    }

    /** Return all the <code>ImagePanel</code>s. */

    public List<ViewCanvas<E>> getImagePanels() {
        return getImagePanels(false);
    }

    public List<ViewCanvas<E>> getImagePanels(boolean selectedImagePaneLast) {
        List<ViewCanvas<E>> viewList = new ArrayList<>(view2ds);
        if (selectedImagePaneLast) {
            ViewCanvas<E> selectedView = getSelectedImagePane();

            if (selectedView != null && viewList.size() > 1) {
                viewList.remove(selectedView);
                viewList.add(selectedView);
            }
            return viewList;
        }
        return viewList;
    }

    public ViewCanvas<E> getNextSelectedImagePane() {
        for (int i = 0; i < view2ds.size() - 1; i++) {
            if (view2ds.get(i) == selectedImagePane) {
                return view2ds.get(i + 1);
            }
        }
        return selectedImagePane;
    }

    public abstract List<SynchView> getSynchList();

    public abstract List<GridBagLayoutModel> getLayoutList();

    public Boolean isContainingView(ViewCanvas<?> view2DPane) {
        return view2ds.stream().filter(v -> Objects.equals(v, view2DPane)).findFirst().map(v -> Boolean.TRUE)
            .orElse(Boolean.FALSE);
    }

    public SynchView getSynchView() {
        return synchView;
    }

    public void setSynchView(SynchView synchView) {
        Objects.requireNonNull(synchView);
        this.synchView = synchView;
        updateTileOffset();
        eventManager.updateAllListeners(this, synchView);
    }

    @SuppressWarnings("unchecked")
    public void updateTileOffset() {
        if (SynchData.Mode.TILE.equals(synchView.getSynchData().getMode()) && selectedImagePane != null) {
            MediaSeries<E> series = null;
            ViewCanvas<E> selectedView = selectedImagePane;
            if (selectedImagePane.getSeries() != null) {
                series = selectedImagePane.getSeries();
            } else {
                for (ViewCanvas<E> v : view2ds) {
                    if (v.getSeries() != null) {
                        series = v.getSeries();
                        selectedView = v;
                        break;
                    }
                }
            }
            if (series != null) {
                int limit = series.size((Filter<E>) selectedView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
                for (int i = 0; i < view2ds.size(); i++) {
                    ViewCanvas<E> v = view2ds.get(i);
                    if (i < limit) {
                        v.setTileOffset(i);
                        v.setSeries(series, null);
                    } else {
                        v.setSeries(null, null);
                    }
                }
            }
        } else {
            for (ViewCanvas<E> v : view2ds) {
                v.setTileOffset(0);
            }
        }
    }

    public synchronized void setMouseActions(MouseActions mouseActions) {
        if (mouseActions == null) {
            for (ViewCanvas<E> v : view2ds) {
                v.disableMouseAndKeyListener();
                // Let the possibility to get the focus
                v.iniDefaultMouseListener();
            }
        } else {
            for (ViewCanvas<E> v : view2ds) {
                v.enableMouseAndKeyListener(mouseActions);
            }
        }
    }

    public GridBagLayoutModel getBestDefaultViewLayout(int size) {
        if (size <= 1) {
            return VIEWS_1x1;
        }
        ActionState layout = eventManager.getAction(ActionW.LAYOUT);
        if (layout instanceof ComboItemListener) {
            Object[] list = ((ComboItemListener) layout).getAllItem();
            for (Object m : list) {
                if (m instanceof GridBagLayoutModel) {
                    if (getViewTypeNumber((GridBagLayoutModel) m, view2dClass) >= size) {
                        return (GridBagLayoutModel) m;
                    }
                }
            }
        }

        return VIEWS_2x2;
    }

    public GridBagLayoutModel getViewLayout(String title) {
        if (title != null) {
            ActionState layout = eventManager.getAction(ActionW.LAYOUT);
            if (layout instanceof ComboItemListener) {
                Object[] list = ((ComboItemListener) layout).getAllItem();
                for (Object m : list) {
                    if (m instanceof GridBagLayoutModel && title.equals(((GridBagLayoutModel) m).getId())) {
                        return (GridBagLayoutModel) m;
                    }
                }
            }
        }
        return VIEWS_1x1;
    }

    public void addSeriesList(List<MediaSeries<E>> seriesList, boolean bestDefaultLayout) {
        if (seriesList != null && !seriesList.isEmpty()) {
            if (SynchData.Mode.TILE.equals(synchView.getSynchData().getMode())) {
                addSeries(seriesList.get(0));
                return;
            }
            setSelectedAndGetFocus();
            if (bestDefaultLayout) {
                changeLayoutModel(getBestDefaultViewLayout(seriesList.size()));

                // If the layout is larger than the list of series, clean other views.
                if (view2ds.size() > seriesList.size()) {
                    setSelectedImagePane(view2ds.get(seriesList.size()));
                    for (int i = seriesList.size(); i < view2ds.size(); i++) {
                        ViewCanvas<E> viewPane = getSelectedImagePane();
                        if (viewPane != null) {
                            viewPane.setSeries(null, null);
                        }
                        getNextSelectedImagePane();
                    }
                }
                if (!view2ds.isEmpty()) {
                    setSelectedImagePane(view2ds.get(0));
                }
                for (MediaSeries mediaSeries : seriesList) {
                    addSeries(mediaSeries);
                }
            } else {
                int emptyView = 0;
                for (ViewCanvas<E> v : view2ds) {
                    if (v.getSeries() == null) {
                        emptyView++;
                    }
                }
                if (emptyView < seriesList.size()) {
                    changeLayoutModel(getBestDefaultViewLayout(view2ds.size() + seriesList.size()));
                }
                int index = 0;
                for (ViewCanvas<E> v : view2ds) {
                    if (v.getSeries() == null && index < seriesList.size()) {
                        setSelectedImagePane(v);
                        addSeries(seriesList.get(index));
                        index++;
                    }
                }
            }
            repaint();
        }
    }

    class MouseHandler extends MouseAdapter {
        private Point pickPoint = null;
        private Point point = null;
        private boolean splitVertical = false;
        private final ArrayList<ImageViewerPlugin.DragLayoutElement> list = new ArrayList<>();

        @Override
        public void mousePressed(MouseEvent e) {
            pickPoint = e.getPoint();
            point = null;
            list.clear();
            Iterator<Entry<LayoutConstraints, Component>> enumVal =
                ImageViewerPlugin.this.layoutModel.getConstraints().entrySet().iterator();
            Entry<LayoutConstraints, Component> entry;
            while (enumVal.hasNext()) {
                entry = enumVal.next();
                Component c = entry.getValue();
                if (c != null) {
                    Rectangle rect = c.getBounds();
                    if (Math.abs(rect.x - pickPoint.x) <= LayoutConstraints.SPACE
                        && (pickPoint.y >= rect.y && pickPoint.y <= rect.y + rect.height) && entry.getKey().gridx > 0) {
                        splitVertical = true;
                        point = new Point(entry.getKey().gridx, entry.getKey().gridy);
                        break;
                    }

                    else if (Math.abs(rect.y - pickPoint.y) <= LayoutConstraints.SPACE
                        && (pickPoint.x >= rect.x && pickPoint.x <= rect.x + rect.width) && entry.getKey().gridy > 0) {
                        splitVertical = false;
                        point = new Point(entry.getKey().gridx, entry.getKey().gridy);
                        break;
                    }
                }
            }
            if (point != null) {
                enumVal = ImageViewerPlugin.this.layoutModel.getConstraints().entrySet().iterator();
                while (enumVal.hasNext()) {
                    entry = enumVal.next();
                    Component c = entry.getValue();
                    if (c != null) {
                        list.add(new DragLayoutElement(entry.getKey(), c));
                    }
                }

                Rectangle b = grid.getBounds();
                double totalWidth = b.getWidth();
                double totalHeight = b.getHeight();

                for (DragLayoutElement el : list) {
                    el.originalConstraints.weightx = el.originalBound.width / totalWidth;
                    el.originalConstraints.weighty = el.originalBound.height / totalHeight;
                    el.constraints.weightx = el.originalConstraints.weightx;
                    el.constraints.weighty = el.originalConstraints.weighty;
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent mouseevent) {
            pickPoint = null;
            point = null;
            list.clear();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            int mods = e.getModifiers();
            if (pickPoint != null && point != null && list.size() > 1 && (mods & InputEvent.BUTTON1_MASK) != 0) {
                Point p = e.getPoint();
                if (splitVertical) {
                    int dx = p.x - pickPoint.x;
                    int limitdx = dx;
                    for (DragLayoutElement element : list) {
                        LayoutConstraints key = element.getConstraints();
                        if (key.gridx == point.x) {
                            int width = element.getOriginalBound().width - dx;
                            Dimension min = element.getComponent().getMinimumSize();
                            int minsize = min == null ? 50 : min.width;
                            if (width < minsize) {
                                limitdx = dx - (minsize - width);
                            }
                        } else if (key.gridx + key.gridwidth == point.x) {
                            int width = element.getOriginalBound().width + dx;
                            Dimension min = element.getComponent().getMinimumSize();
                            int minsize = min == null ? 50 : min.width;
                            if (width < minsize) {
                                limitdx = dx + (minsize - width);
                            }
                        }
                    }
                    for (DragLayoutElement element : list) {
                        LayoutConstraints key = element.getConstraints();
                        LayoutConstraints originkey = element.getOriginalConstraints();
                        if (key.gridx == point.x) {
                            key.weightx =
                                originkey.weightx - limitdx * originkey.weightx / element.getOriginalBound().width;
                        } else if (key.gridx + key.gridwidth == point.x) {
                            key.weightx =
                                originkey.weightx + limitdx * originkey.weightx / element.getOriginalBound().width;
                        }
                    }
                } else {
                    int dy = p.y - pickPoint.y;
                    int limitdy = dy;
                    for (DragLayoutElement element : list) {
                        LayoutConstraints key = element.getConstraints();
                        if (key.gridy == point.y) {
                            int height = element.getOriginalBound().height - dy;
                            Dimension min = element.getComponent().getMinimumSize();
                            int minsize = min == null ? 50 : min.height;
                            if (height < minsize) {
                                limitdy = dy - (minsize - height);
                            }
                        } else if (key.gridy + key.gridheight == point.y) {
                            int height = element.getOriginalBound().height + dy;
                            Dimension min = element.getComponent().getMinimumSize();
                            int minsize = min == null ? 50 : min.height;
                            if (height < minsize) {
                                limitdy = dy + (minsize - height);
                            }
                        }
                    }
                    for (DragLayoutElement element : list) {
                        LayoutConstraints key = element.getConstraints();
                        LayoutConstraints originkey = element.getOriginalConstraints();
                        if (key.gridy == point.y) {
                            key.weighty =
                                originkey.weighty - limitdy * originkey.weighty / element.getOriginalBound().height;
                        } else if (key.gridy + key.gridheight == point.y) {
                            key.weighty =
                                originkey.weighty + limitdy * originkey.weighty / element.getOriginalBound().height;
                        }

                    }
                }
                Rectangle b = grid.getBounds();
                double totalWidth = b.getWidth();
                double totalHeight = b.getHeight();
                grid.removeAll();
                for (DragLayoutElement element : list) {
                    Component c = element.getComponent();
                    LayoutConstraints l = element.getConstraints();
                    c.setPreferredSize(new Dimension((int) Math.round(totalWidth * l.weightx),
                        (int) Math.round(totalHeight * l.weighty)));
                    grid.add(c, l);
                }
                setCursor(Cursor.getPredefinedCursor(splitVertical ? Cursor.E_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR));
                grid.revalidate();
                grid.repaint();
            }
        }

        @Override
        public void mouseMoved(MouseEvent me) {
            setCursor(Cursor.getPredefinedCursor(getCursor(me)));
        }

        @Override
        public void mouseExited(MouseEvent mouseEvent) {
            setCursor(Cursor.getDefaultCursor());
        }

        private int getCursor(MouseEvent me) {
            Point p = me.getPoint();
            Iterator<Entry<LayoutConstraints, Component>> enumVal =
                ImageViewerPlugin.this.layoutModel.getConstraints().entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> entry = enumVal.next();
                Component c = entry.getValue();
                if (c != null) {
                    Rectangle rect = c.getBounds();
                    if ((Math.abs(rect.x - p.x) <= LayoutConstraints.SPACE
                        || Math.abs(rect.x + rect.width - p.x) <= LayoutConstraints.SPACE)
                        && (p.y >= rect.y && p.y <= rect.y + rect.height)) {
                        return Cursor.E_RESIZE_CURSOR;
                    } else if ((Math.abs(rect.y - p.y) <= LayoutConstraints.SPACE
                        || Math.abs(rect.y + rect.height - p.y) <= LayoutConstraints.SPACE)
                        && (p.x >= rect.x && p.x <= rect.x + rect.width)) {
                        return Cursor.S_RESIZE_CURSOR;
                    }
                }

            }
            return Cursor.DEFAULT_CURSOR;
        }
    }

    static class DragLayoutElement {
        private final LayoutConstraints originalConstraints;
        private final Rectangle originalBound;
        private final LayoutConstraints constraints;
        private final Component component;

        public DragLayoutElement(LayoutConstraints constraints, Component component) {
            if (constraints == null || component == null) {
                throw new IllegalArgumentException("Arguments cannot be null"); //$NON-NLS-1$
            }
            this.constraints = constraints;
            this.originalConstraints = constraints.copy();
            this.component = component;
            this.originalBound = component.getBounds();
        }

        public LayoutConstraints getOriginalConstraints() {
            return originalConstraints;
        }

        public Rectangle getOriginalBound() {
            return originalBound;
        }

        public LayoutConstraints getConstraints() {
            return constraints;
        }

        public Component getComponent() {
            return component;
        }

    }

    public void selectLayoutPositionForAddingSeries(List<MediaSeries<E>> seriesList) {
        int nbSeriesToAdd = 1;
        if (seriesList != null) {
            nbSeriesToAdd = seriesList.size();
            if (nbSeriesToAdd < 1) {
                nbSeriesToAdd = 1;
            }
        }
        int pos = view2ds.size() - nbSeriesToAdd;
        if (pos < 0) {
            pos = 0;
        }
        setSelectedImagePane(view2ds.get(pos));
    }

}
