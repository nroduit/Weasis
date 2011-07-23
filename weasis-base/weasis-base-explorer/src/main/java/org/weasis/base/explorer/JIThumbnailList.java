package org.weasis.base.explorer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;

public final class JIThumbnailList extends JList implements JIObservable {

    public static final Dimension ICON_DIM = new Dimension(150, 150);
    private static final NumberFormat intGroupFormat = NumberFormat.getIntegerInstance();
    static {
        intGroupFormat.setGroupingUsed(true);
    }
    private final int editingIndex = -1;

    private final FileTreeModel model;
    private final JScrollPane viewport;

    private final JMenuItem jMenuItemOpen;
    private final JMenuItem jMenuItemOpenWith;
    private final JMenuItem jMenuItemRefreshList;
    private final JMenuItem jMenuItemMetadata;
    private final JPopupMenu jDesktopPopupMenu;

    private final ToggleSelectionModel selectionModel;

    private boolean changed;

    private MediaElement lastSelectedDiskObject = null;

    public JIThumbnailList(final FileTreeModel model, final JScrollPane viewport) {
        this(model, viewport, VERTICAL_WRAP, null);
    }

    public JIThumbnailList(final FileTreeModel model, final JScrollPane viewport, final OrderedFileList dObjList) {
        this(model, viewport, VERTICAL_WRAP, dObjList);
    }

    public JIThumbnailList(final FileTreeModel model, final JScrollPane viewport, final int scrollMode,
        final OrderedFileList dObjList) {
        super();

        boolean useSelection = false;
        if (dObjList != null) {
            this.setModel(new JIListModel(this, dObjList));
            useSelection = true;
        } else {
            this.setModel(new JIListModel(this));
        }

        this.model = model;
        this.viewport = viewport;

        this.changed = false;

        this.jMenuItemOpen = new JMenuItem();
        this.jMenuItemOpenWith = new JMenu();
        this.jMenuItemRefreshList = new JMenuItem();
        this.jMenuItemMetadata = new JMenuItem();
        this.jDesktopPopupMenu = new JPopupMenu();

        this.selectionModel = new ToggleSelectionModel();
        this.setBackground(new Color(242, 242, 242));

        setSelectionModel(this.selectionModel);
        setDragEnabled(true);
        // setTransferHandler(new ListTransferHandler());
        ThumbnailRenderer panel = new ThumbnailRenderer();
        Dimension dim = panel.getPreferredSize();
        setCellRenderer(panel);
        setFixedCellHeight(dim.height);
        setFixedCellWidth(dim.width);
        setVisibleRowCount(-1);

        setLayoutOrientation(HORIZONTAL_WRAP);

        initActions();

        addKeyListener(new JIThumbnailKeyAdapter());

        if (useSelection) {
            // JIExplorer.instance().getContext();
        }

        setVerifyInputWhenFocusTarget(false);
        JIThumbnailCache.getInstance().invalidate();
    }

    public JIListModel getThumbnailListModel() {
        return (JIListModel) getModel();
    }

    // public final void setFrame() {
    // this.listCellEditor = new JIListCellEditor(this);
    // }

    public Frame getFrame() {
        return null;
    }

    public JViewport getViewPort() {
        return this.viewport.getViewport();
    }

    public boolean isEditing() {
        if (this.editingIndex > -1)
            return true;
        return false;
    }

    // Subclass JList to workaround bug 4832765, which can cause the
    // scroll pane to not let the user easily scroll up to the beginning
    // of the list. An alternative would be to set the unitIncrement
    // of the JScrollBar to a fixed value. You wouldn't get the nice
    // aligned scrolling, but it should work.
    @Override
    public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
        int row;
        if ((orientation == SwingConstants.VERTICAL) && (direction < 0) && ((row = getFirstVisibleIndex()) != -1)) {
            final Rectangle r = getCellBounds(row, row);
            if ((r.y == visibleRect.y) && (row != 0)) {
                final Point loc = r.getLocation();
                loc.y--;
                final int prevIndex = locationToIndex(loc);
                final Rectangle prevR = getCellBounds(prevIndex, prevIndex);

                if ((prevR == null) || (prevR.y >= r.y))
                    return 0;
                return prevR.height;
            }
        }
        return super.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public String getToolTipText(final MouseEvent evt) {
        // if (!JIPreferences.getInstance().isThumbnailToolTips()) {
        // return null;
        // }
        // Get item index
        final int index = locationToIndex(evt.getPoint());
        if (index < 0)
            return "";

        // Get item
        final Object item = getModel().getElementAt(index);

        if (((MediaElement) item).getName() == null)
            return null;

        return "<html>" + ((MediaElement) item).getName() + "<br> Size: "
            + intGroupFormat.format(((MediaElement) item).getLength() / 1024L) + " KB<br>" + "Date: "
            + new Date(((MediaElement) item).getLastModified()).toString() + "</html>";
    }

    public void reset() {
        setFixedCellHeight(ICON_DIM.height);
        setFixedCellWidth(ICON_DIM.width);
        setLayoutOrientation(HORIZONTAL_WRAP);

        ((JIListModel) getModel()).reload();
        initOpenWith();
        setVisibleRowCount(-1);
        clearSelection();
        ensureIndexIsVisible(0);
    }

    public void openSelection() {
        final int index = getSelectedIndex();
        final OrderedFileList imageList = ((JIListModel) getModel()).getDiskObjectList();
        imageList.setCurrentIndex(index);

        // new JIViewer(imageList);
    }

    public void nextPage(final KeyEvent e) {
        final int lastIndex = getLastVisibleIndex();

        if (getLayoutOrientation() != JList.HORIZONTAL_WRAP) {
            e.consume();
            final int firstIndex = getFirstVisibleIndex();
            final int visibleRows = getVisibleRowCount();
            final int visibleColums = (int) (((float) (lastIndex - firstIndex) / (float) visibleRows) + .5);
            final int visibleItems = visibleRows * visibleColums;

            final int val =
                (lastIndex + visibleItems >= getModel().getSize()) ? getModel().getSize() - 1 : lastIndex
                    + visibleItems;
            // log.debug("Next index is " + val + " " + lastIndex + " " + visibleItems + " " + visibleRows);
            clearSelection();
            setSelectedIndex(val);
            fireSelectionValueChanged(val, val, false);
        } else {
            clearSelection();
            setSelectedIndex(lastIndex);
            fireSelectionValueChanged(lastIndex, lastIndex, false);
        }
    }

    public void lastPage(final KeyEvent e) {
        final int lastIndex = getLastVisibleIndex();

        if (getLayoutOrientation() != JList.HORIZONTAL_WRAP) {
            e.consume();
            final int firstIndex = getFirstVisibleIndex();
            final int visibleRows = getVisibleRowCount();
            final int visibleColums = (int) (((float) (lastIndex - firstIndex) / (float) visibleRows) + .5);
            final int visibleItems = visibleRows * visibleColums;

            final int val = ((firstIndex - 1) - visibleItems < 0) ? 0 : (firstIndex - 1) - visibleItems;
            // log.debug("Next index is " + val + " " + lastIndex + " " + visibleItems + " " + visibleRows);
            clearSelection();
            setSelectedIndex(val);
            fireSelectionValueChanged(val, val, false);
        } else {
            clearSelection();
            setSelectedIndex(lastIndex);
            fireSelectionValueChanged(lastIndex, lastIndex, false);
        }
    }

    public void jiThumbnail_keyPressed(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_PAGE_DOWN:
                nextPage(e);
                break;
            case KeyEvent.VK_PAGE_UP:
                lastPage(e);
                break;
            case KeyEvent.VK_ENTER:
                openSelection();
                e.consume();
                break;
        }
    }

    private void openMedia(SeriesViewerFactory factory, MediaElement media) {
        // TODO should be the SeriesViewer type
        ViewerPlugin view = (ViewerPlugin) factory.createSeriesViewer(null);

        if (view != null) {

            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, model, null, view));

            Object patient = media.getTagValue(TagW.PatientName);

            if (patient != null) {
                view.setPluginName(patient.toString());
            }
            MediaSeries series = media.getMediaReader().getMediaSeries();
            series.dispose();
            boolean multiPatient = false;
            MediaElement[] values = getSelectedValues();
            for (MediaElement element : values) {
                if (media.getClass().isAssignableFrom(element.getClass())) {
                    series.add(element);
                    if (patient != null) {
                        if (!patient.equals(element.getTagValue(TagW.PatientName))) {
                            multiPatient = true;
                        }
                    }
                }
            }
            if (multiPatient) {
                view.setPluginName("Multi-Patient");
            }
            view.setSelectedAndGetFocus();
            view.addSeries(series);

        }
    }

    private void initOpenWith() {
        // Open With
        this.jMenuItemOpenWith.removeAll();
        this.jMenuItemOpenWith.setText("Open With");

        final int index = getSelectedIndex();
        if (index == -1)
            return;

        final OrderedFileList imageList = ((JIListModel) getModel()).getDiskObjectList();
        imageList.setCurrentIndex(index);

        final MediaElement media = imageList.get(index);
        if (media == null)
            return;

        final String mimeType = media.getMimeType();

        synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
            List<SeriesViewerFactory> viewerPlugins = UIManager.SERIES_VIEWER_FACTORIES;
            for (final SeriesViewerFactory factory : viewerPlugins) {
                if (factory.canReadMimeType(mimeType)) {
                    final JMenuItem item4 = new JMenuItem(factory.getUIName(), factory.getIcon());
                    item4.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            openMedia(factory, media);
                        }
                    });
                    this.jMenuItemOpenWith.add(item4);
                }
            }
        }
    }

    private void initActions() {

        // Open
        this.jMenuItemOpen.setText("Open");
        this.jMenuItemOpen.setAction(new AbstractAction("Open") {

            /**
			 *
			 */
            private static final long serialVersionUID = 7770033253414532608L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                final Thread runner = new Thread() {

                    @Override
                    public void run() {
                        Runnable runnable = new Runnable() {

                            @Override
                            public void run() {
                                final int index = getSelectedIndex();
                                if (index < 0)
                                    return;
                                final OrderedFileList imageList = ((JIListModel) getModel()).getDiskObjectList();
                                imageList.setCurrentIndex(index);

                                final MediaElement media = imageList.get(index);
                                if (media == null)
                                    return;

                                final String mimeType = media.getMimeType();

                                synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                                    for (final SeriesViewerFactory factory : UIManager.SERIES_VIEWER_FACTORIES) {
                                        if (factory.canReadMimeType(mimeType)) {
                                            openMedia(factory, media);
                                            break;
                                        }
                                    }
                                }
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                };
                runner.start();
            }
        });

        initOpenWith();

        // Refresh List
        this.jMenuItemRefreshList.setText("Refresh List");
        this.jMenuItemRefreshList.setAction(new AbstractAction("Refresh List") {

            /**
			 *
			 */
            private static final long serialVersionUID = -7251048462021844506L;

            @Override
            public void actionPerformed(final ActionEvent e) {
                final Thread runner = new Thread() {

                    @Override
                    public void run() {
                        Runnable runnable = new Runnable() {

                            @Override
                            public void run() {
                                JIThumbnailList.this.getThumbnailListModel().reload();
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                };
                runner.start();
            }
        });

        addMouseListener(new PopupTrigger());
    }

    @Override
    public MediaElement[] getSelectedValues() {
        final Object[] objs = super.getSelectedValues();
        final MediaElement[] dObjs = new MediaElement[objs.length];
        int cnt = 0;
        for (final Object obj : objs) {
            dObjs[cnt++] = (MediaElement) obj;
        }
        return dObjs;
    }

    public int getLastSelectedIndex() {
        final Object[] objs = super.getSelectedValues();
        final Object obj = super.getSelectedValue();
        int cnt = 0;
        for (final Object o : objs) {
            if (o.equals(obj))
                return cnt;
            cnt++;
        }
        return cnt - 1;
    }

    protected void listValueChanged(final ListSelectionEvent e) {
        if (this.lastSelectedDiskObject == null) {
            this.lastSelectedDiskObject = (MediaElement) getModel().getElementAt(e.getLastIndex());
        }
        DefaultExplorer.getTreeContext().setSelectedDiskObjects(this.getSelectedValues(), this.lastSelectedDiskObject);

        this.lastSelectedDiskObject = null;

        setChanged();
        notifyObservers(JIObservable.SECTION_CHANGED);
        clearChanged();
    }

    public void listMouseClicked(final MouseEvent e) {
        final int index = this.locationToIndex(e.getPoint());

        if (getSelectionModel().isSelectedIndex(index)) {
            long clickTime = new Date().getTime();

            final MediaElement selectedObject = (MediaElement) this.getModel().getElementAt(index);

            File selectedFile;
            if (selectedObject == null) {
                selectedFile = null;
                clearSelection();
                return;
            }

            ensureIndexIsVisible(index);

            selectedFile = selectedObject.getFile();
            if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
                final int indexSel = getSelectedIndex();
                final OrderedFileList imageList = ((JIListModel) getModel()).getDiskObjectList();
                imageList.setCurrentIndex(indexSel);

                final MediaElement media = imageList.get(indexSel);
                if (media == null)
                    return;

                final String mimeType = media.getMimeType();
                synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                    List<SeriesViewerFactory> viewerPlugins = UIManager.SERIES_VIEWER_FACTORIES;
                    for (final SeriesViewerFactory factory : viewerPlugins) {
                        if (factory.canReadMimeType(mimeType)) {
                            openMedia(factory, media);
                            break;
                        }
                    }
                }
                e.consume();
            }
        }
    }

    public void listMouseEvent(final MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            try {
                final Object obj = getSelectedValue();

                if (obj == null) {
                    this.jDesktopPopupMenu.removeAll();
                    this.jDesktopPopupMenu.add(this.jMenuItemRefreshList);

                } else {
                    final File selectedFile = ((MediaElement) getSelectedValue()).getFile();
                    if (!selectedFile.isDirectory()) {
                        this.jDesktopPopupMenu.removeAll();
                        this.jDesktopPopupMenu.add(this.jMenuItemOpen);
                        this.jDesktopPopupMenu.addSeparator();
                        // if ((JIThumbnailService.getInstance().getOpenWith() != null)
                        // && (JIThumbnailService.getInstance().getOpenWith().size() > 0)) {
                        initOpenWith();
                        this.jDesktopPopupMenu.add(this.jMenuItemOpenWith);
                        this.jDesktopPopupMenu.addSeparator();
                        // }
                        this.jDesktopPopupMenu.add(this.jMenuItemRefreshList);

                        this.jDesktopPopupMenu.addSeparator();
                        this.jDesktopPopupMenu.add(this.jMenuItemMetadata);

                        this.jDesktopPopupMenu.show(this, e.getX(), e.getY());

                    }
                }
            } catch (final Exception exp) {
            } finally {
                e.consume();
            }
        }
    }

    final class PopupTrigger extends MouseAdapter {

        @Override
        public void mousePressed(final MouseEvent e) {
            final int index = JIThumbnailList.this.locationToIndex(e.getPoint());
            if (index < selectionModel.getMinSelectionIndex() || index > selectionModel.getMaxSelectionIndex()) {
                JIThumbnailList.this.setSelectedIndex(index);

            }

            // final MediaElement selectedObject = (MediaElement) JIThumbnailList.this.getModel().getElementAt(index);
            // if ((selectedObject != null) && (selectedObject instanceof MediaElement)) {
            // // DefaultExplorer.getTreeContext().setSelectedDiskObjects(this.getSelectedValues(),
            // // JIThumbnailList.this.lastSelectedDiskObject);
            // JIThumbnailList.this.lastSelectedDiskObject = selectedObject;
            // }
            // listMouseEvent(e);
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            // listMouseClicked(e);
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            listMouseEvent(e);
        }
    }

    /**
     * If this object has changed, as indicated by the <code>hasChanged</code> method, then notify all of its observers
     * and then call the <code>clearChanged</code> method to indicate that this object has no longer changed.
     * <p>
     * Each observer has its <code>update</code> method called with two arguments: this observable object and
     * <code>null</code>. In other words, this method is equivalent to: <blockquote><tt>
     * notifyObservers(null)</tt> </blockquote>
     * 
     * @see java.util.Observable#clearChanged()
     * @see java.util.Observable#hasChanged()
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    public void notifyObservers() {
        notifyObservers(null);
    }

    /**
     * If this object has changed, as indicated by the <code>hasChanged</code> method, then notify all of its observers
     * and then call the <code>clearChanged</code> method to indicate that this object has no longer changed.
     * <p>
     * Each observer has its <code>update</code> method called with two arguments: this observable object and the
     * <code>arg</code> argument.
     * 
     * @param arg
     *            any object.
     * @see java.util.Observable#clearChanged()
     * @see java.util.Observable#hasChanged()
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    @Override
    public void notifyObservers(final Object arg) {

        synchronized (this) {
            if (!this.changed)
                return;
            clearChanged();
        }

    }

    public void notifyStatusBar(final Object arg) {
        synchronized (this) {
            clearChanged();
        }

    }

    /**
     * Marks this <tt>Observable</tt> object as having been changed; the <tt>hasChanged</tt> method will now return
     * <tt>true</tt>.
     */
    public synchronized void setChanged() {
        this.changed = true;
    }

    /**
     * Indicates that this object has no longer changed, or that it has already notified all of its observers of its
     * most recent change, so that the <tt>hasChanged</tt> method will now return <tt>false</tt>. This method is called
     * automatically by the <code>notifyObservers</code> methods.
     * 
     * @see java.util.Observable#notifyObservers()
     * @see java.util.Observable#notifyObservers(java.lang.Object)
     */
    public synchronized void clearChanged() {
        this.changed = false;
    }

    /**
     * Tests if this object has changed.
     * 
     * @return <code>true</code> if and only if the <code>setChanged</code> method has been called more recently than
     *         the <code>clearChanged</code> method on this object; <code>false</code> otherwise.
     * @see java.util.Observable#clearChanged()
     * @see java.util.Observable#setChanged()
     */
    @Override
    public synchronized boolean hasChanged() {
        return this.changed;
    }

    final class JIThumbnailKeyAdapter extends java.awt.event.KeyAdapter {

        /** Creates a new instance of JIThumbnailKeyListener */
        public JIThumbnailKeyAdapter() {
        }

        /** key event handlers */
        @Override
        public void keyPressed(final KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                JIThumbnailList.this.selectionModel.setShiftKey(true);
            }
            if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                JIThumbnailList.this.selectionModel.setCntrlKey(true);
            }
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    Runnable runnable = new Runnable() {

                        @Override
                        public void run() {
                            JIThumbnailList.this.jiThumbnail_keyPressed(e);
                        }
                    };
                    SwingUtilities.invokeLater(runnable);
                }
            };
            runner.start();
        }

        @Override
        public void keyReleased(final KeyEvent e) {
            JIThumbnailList.this.selectionModel.setShiftKey(false);
            JIThumbnailList.this.selectionModel.setCntrlKey(false);
        }
    }
}
