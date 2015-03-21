/*
 * @copyright Copyright (c) 2012 Animati Sistemas de InformÃ¡tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.GUIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;


/**
 * Grid for layout viewers.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 9 ago.
 */
public abstract class ViewsGrid extends JPanel {

    protected GridBagLayoutModel layoutModel;
 
    protected List<GridElement> viewsList;
    protected GridElement selectedView;

    public ViewsGrid() {
        
        setUI(new PanelUI() { /* Empty */ });

        setBackground(Color.BLACK);
        setFocusCycleRoot(true);
        setLayout(new GridBagLayout());

        GridMouseAdapter adapter = new GridMouseAdapter();
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        
        viewsList = new ArrayList<GridElement>();
    }

    public abstract void setLayoutModel(GridBagLayoutModel model);

    public GridBagLayoutModel getLayoutModel() {
        return layoutModel;
    }
    
    /**
     * Replaced one view by another end disposes the old one.
     * @param oldView To be replaced and disposed.
     * @param newView To be added.
     */
    protected void replaceView(final GridElement oldView,
            final GridElement newView) {
        if (oldView != null && newView != null) {
            removeAll();
            final LinkedHashMap<LayoutConstraints, Component> elements =
                    layoutModel.getConstraints();
            Iterator<Entry<LayoutConstraints, Component>> enumVal =
                    elements.entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> element = enumVal.next();

                if (element.getValue() == oldView.getComponent()) {
                    if (selectedView == oldView) {
                        selectedView = newView;
                    }
                    oldView.dispose();
                    int index = viewsList.indexOf(oldView);
                    if (index >= 0) {
                        viewsList.set(index, newView);
                    }
                    elements.put(element.getKey(), newView.getComponent());
                    add(newView.getComponent(), element.getKey());
                    if (newView.getSeries() != null) {
                        newView.getSeries().setOpen(true);
                    }
                    
                    MouseActions actions = GUIManager.getInstance().getMouseActions();
                    newView.enableMouseAndKeyListener(actions);
                    
                } else {
                    add(element.getValue(), element.getKey());
                }
            }
            revalidate();
            setSelectedView();
        }
    }

    public void addSeries(MediaSeries series) throws Exception {
        addSeries(series, null);
    }
    
    public abstract void addSeries(MediaSeries series, Comparator comparator)
            throws Exception;
    
    public List<GridElement> getViews() {
        return viewsList;
    }

    public int getViewsNumber(GridBagLayoutModel layout) {     
        return layout.getConstraints().size();
    }
    
    public void setSelectedView() {
        if (viewsList != null && !viewsList.isEmpty()) {
            if (selectedView != null && viewsList.contains(selectedView)) {
                selectedView.setSelected(true);

                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        this, EventPublisher.VIEWER_SELECTED, null, selectedView));
            } else {
                setSelectedView(viewsList.get(0));
            }
        }
    }

    public void setSelectedView(GridElement givenView) {
        if (selectedView != givenView && givenView != null) {
            if (selectedView != null) {
                selectedView.setSelected(false);
            }
            selectedView = givenView;
            setSelectedView();
        }
    }
    
    /**
     * @return Selected gridElement (can be null). 
     */
    public GridElement getSelectedView() {
        return selectedView;
    }

    public List<MediaSeries<DicomImageElement>> getOpenSeries() {
        final List<MediaSeries<DicomImageElement>> list =
                new ArrayList<MediaSeries<DicomImageElement>>();
        for (GridElement gridElement : getViews()) {
            if (gridElement.hasContent()) {
                MediaSeries series = gridElement.getSeries();
                if (series != null && !list.contains(series)) {
                    list.add(series);
                }
            }
        }
        return list;
    }

    public boolean isContainingView(Object givenView) {
        for (Object view : getViews()) {
            if (view == givenView) {
                return true;
            }
        }
        return false;
    }

    public void setMouseActions(MouseActions mouseActions) {
        if (mouseActions == null) {
            //TODO: set them empty
        } else {
            for (GridElement view : getViews()) {
                view.enableMouseAndKeyListener(mouseActions);
            }
        }
    }
    
    public void updateViewersWithSeries(final MediaSeries mediaSeries,
            final PropertyChangeEvent event) {
        for (GridElement gridElement : getViews()) {
            if (mediaSeries != null
                    && mediaSeries.equals(gridElement.getSeries())) {
                gridElement.propertyChange(event);
            }
        } 
    }

    /**
     * Adds one image if one of the views is showing the given series.
     * 
     * @param series Series do feed.
     * @param image Image do be added.
     */
    public abstract void addImage(DicomSeries series, DicomImageElement image);
 

    public void dispose() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (GridElement gridElement : getViews()) {
                    gridElement.dispose();
                }
                viewsList.clear();
            }
        });
    }
 

    protected int getFirstFreeViewIndex() {
        for (int i = 0; i < viewsList.size(); i++) {
            GridElement gridElement = viewsList.get(i);
            if (!gridElement.hasContent()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Maximazes one view on grid, or goes back to the original layout.
     * @param element Selected element.
     */
    public void maximizeElement(GridElement element) {
        LinkedHashMap<LayoutConstraints, Component> constraints =
                layoutModel.getConstraints();
        if (constraints.size() > 1) {
            int elmts = getComponentCount();
            removeAll();
            if (elmts > 1) {
                Iterator<Entry<LayoutConstraints, Component>> enumVal =
                        constraints.entrySet().iterator();
                while (enumVal.hasNext()) {
                    Entry<LayoutConstraints, Component> entry = enumVal.next();
                    if (entry.getValue().equals(element.getComponent())) {
                        GridBagConstraints c = (GridBagConstraints) entry.getKey().clone();
                        c.weightx = 1.0;
                        c.weighty = 1.0;
                        add(element.getComponent(), c);
                        break;
                    }
                }
            } else {
                Iterator<Entry<LayoutConstraints, Component>> enumVal =
                        constraints.entrySet().iterator();
                while (enumVal.hasNext()) {
                    Entry<LayoutConstraints, Component> entry = enumVal.next();
                    add(entry.getValue(), entry.getKey());
                }
            }
            revalidate();
            repaint();
            setSelectedView(element);
        }
    }

    /**
     * Provides the possibility of moving Layout-limits.
     */
    protected class GridMouseAdapter extends MouseAdapter {
        private Point pickPoint = null;
        private Point point = null;
        private final ArrayList<DragLayoutElement> list =
            new ArrayList<DragLayoutElement>();
        private boolean splitVertical = false; //???

        @Override
        public void mousePressed(final MouseEvent event) {
            pickPoint = event.getPoint();
            point = null;
            list.clear();
            Iterator<Entry<LayoutConstraints, Component>> enumVal =
                layoutModel.getConstraints().entrySet().iterator();
            Entry<LayoutConstraints, Component> entry = null;
            while (enumVal.hasNext()) {
                entry = enumVal.next();
                Component c = entry.getValue();
                if (c != null) {
                    Rectangle rect = c.getBounds();
                    if (Math.abs(rect.x - pickPoint.x) <= LayoutConstraints.SPACE
                        && (pickPoint.y >= rect.y && pickPoint.y <= rect.y + rect.height)
                            && entry.getKey().gridx > 0) {
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
                enumVal = layoutModel.getConstraints().entrySet().iterator();
                while (enumVal.hasNext()) {
                    entry = enumVal.next();
                    Component c = entry.getValue();
                    if (c != null) {
                        list.add(new DragLayoutElement(entry.getKey(), c));
                    }
                }

                Rectangle b = getBounds();
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
            if (pickPoint != null && point != null && list.size() > 1
                    && (mods & InputEvent.BUTTON1_MASK) != 0) {
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
                                limitdx = dx - (minsize - width);
                            }
                        }
                    }
                    for (DragLayoutElement element : list) {
                        LayoutConstraints key = element.getConstraints();
                        LayoutConstraints originkey = element.getOriginalConstraints();
                        if (key.gridx == point.x) {
                            key.weightx =
                                originkey.weightx - limitdx * originkey.weightx
                                    / element.getOriginalBound().width;
                        } else if (key.gridx + key.gridwidth == point.x) {
                            key.weightx =
                                originkey.weightx + limitdx * originkey.weightx
                                    / element.getOriginalBound().width;
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
                                limitdy = dy - (minsize - height);
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
                Rectangle b = getBounds();
                double totalWidth = b.getWidth();
                double totalHeight = b.getHeight();
                removeAll();
                for (DragLayoutElement element : list) {
                    Component c = element.getComponent();
                    LayoutConstraints l = element.getConstraints();
                    c.setPreferredSize(new Dimension((int) Math.round(totalWidth * l.weightx), (int) Math
                        .round(totalHeight * l.weighty)));
                    add(c, l);
                }
                setCursor(Cursor.getPredefinedCursor(
                        splitVertical ? Cursor.E_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR));
                revalidate();
                repaint();
            }
        }

        @Override
        public void mouseMoved(MouseEvent me) {
            setCursor(Cursor.getPredefinedCursor(getCursor(me)));
        }

        @Override
        public void mouseExited(MouseEvent me) {
            setCursor(Cursor.getDefaultCursor());
        }

        private int getCursor(MouseEvent me) {
            Point pt = me.getPoint();
            Iterator<Entry<LayoutConstraints, Component>> enumVal =
                layoutModel.getConstraints().entrySet().iterator();
            while (enumVal.hasNext()) {
                Entry<LayoutConstraints, Component> entry = enumVal.next();
                Component c = entry.getValue();
                if (c != null) {
                    Rectangle rect = c.getBounds();
                    if ((Math.abs(rect.x - pt.x) <= LayoutConstraints.SPACE
                            || Math.abs(rect.x + rect.width - pt.x) <= LayoutConstraints.SPACE)
                        && (pt.y >= rect.y && pt.y <= rect.y + rect.height)) {
                        return Cursor.E_RESIZE_CURSOR;
                    } else if ((Math.abs(rect.y - pt.y) <= LayoutConstraints.SPACE
                            || Math.abs(rect.y + rect.height
                        - pt.y) <= LayoutConstraints.SPACE)
                        && (pt.x >= rect.x && pt.x <= rect.x + rect.width)) {
                        return Cursor.S_RESIZE_CURSOR;
                    }
                }

            }
            return Cursor.DEFAULT_CURSOR;
        }

    }

    /**
     * Internal helper class to handle Layout-dragging.
     */
    private static class DragLayoutElement {
        private final LayoutConstraints originalConstraints;
        private final Rectangle originalBound;
        private final LayoutConstraints constraints;
        private final Component component;

        public DragLayoutElement(final LayoutConstraints constraints,
                final Component component) {
            if (constraints == null || component == null) {
                throw new IllegalArgumentException("Arguments cannot be null");
            }
            this.constraints = constraints;
            this.originalConstraints = (LayoutConstraints) constraints.clone();
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

}
