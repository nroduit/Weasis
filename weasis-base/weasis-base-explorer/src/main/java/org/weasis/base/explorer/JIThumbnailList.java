package org.weasis.base.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.TitleMenuItem;

public final class JIThumbnailList extends JList
    implements JIObservable, DragGestureListener, DragSourceListener, DragSourceMotionListener {

    public static final Dimension ICON_DIM = new Dimension(150, 150);
    private static final NumberFormat intGroupFormat = LocalUtil.getIntegerInstance();

    static {
        intGroupFormat.setGroupingUsed(true);
    }

    private final int editingIndex = -1;
    private final FileTreeModel model;
    private final ToggleSelectionModel selectionModel;

    private boolean changed;
    private Point dragPressed = null;
    private DragSource dragSource = null;
    private MediaElement<?> lastSelectedDiskObject = null;

    public JIThumbnailList(final FileTreeModel model) {
        this(model, VERTICAL_WRAP, null);
    }

    public JIThumbnailList(final FileTreeModel model, final OrderedFileList dObjList) {
        this(model, VERTICAL_WRAP, dObjList);
    }

    public JIThumbnailList(final FileTreeModel model, final int scrollMode, final OrderedFileList dObjList) {
        super();

        boolean useSelection = false;
        if (dObjList != null) {
            this.setModel(new JIListModel(this, dObjList));
            useSelection = true;
        } else {
            this.setModel(new JIListModel(this));
        }

        this.model = model;
        this.changed = false;

        this.selectionModel = new ToggleSelectionModel();
        this.setBackground(new Color(242, 242, 242));

        setSelectionModel(this.selectionModel);
        // setTransferHandler(new ListTransferHandler());
        ThumbnailRenderer panel = new ThumbnailRenderer();
        Dimension dim = panel.getPreferredSize();
        setCellRenderer(panel);
        setFixedCellHeight(dim.height);
        setFixedCellWidth(dim.width);
        setVisibleRowCount(-1);

        setLayoutOrientation(HORIZONTAL_WRAP);

        if (useSelection) {
            // JIExplorer.instance().getContext();
        }

        setVerifyInputWhenFocusTarget(false);
        JIThumbnailCache.getInstance().invalidate();
    }

    public void registerListeners() {
        if (dragSource != null) {
            dragSource.removeDragSourceListener(this);
            dragSource.removeDragSourceMotionListener(this);
        }
        addMouseListener(new PopupTrigger());
        addKeyListener(new JIThumbnailKeyAdapter());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, this);
        dragSource.addDragSourceMotionListener(this);
    }

    public JIListModel getThumbnailListModel() {
        return (JIListModel) getModel();
    }

    public Frame getFrame() {
        return null;
    }

    public boolean isEditing() {
        if (this.editingIndex > -1) {
            return true;
        }
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

                if ((prevR == null) || (prevR.y >= r.y)) {
                    return 0;
                }
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
        if (index < 0) {
            return ""; //$NON-NLS-1$
        }

        // Get item
        final Object item = getModel().getElementAt(index);

        if (((MediaElement) item).getName() == null) {
            return null;
        }

        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        toolTips.append(Messages.getString("JIThumbnailList.1")); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(FileUtil.formatSize(((MediaElement) item).getLength()));
        toolTips.append("<br>"); //$NON-NLS-1$

        toolTips.append(Messages.getString("JIThumbnailList.size")); //$NON-NLS-1$
        toolTips.append(StringUtil.COLON_AND_SPACE);
        toolTips.append(TagW.formatDateTime(new Date(((MediaElement) item).getLastModified())));
        toolTips.append("<br>"); //$NON-NLS-1$
        toolTips.append("</html>"); //$NON-NLS-1$

        return toolTips.toString();
    }

    public void reset() {
        setFixedCellHeight(ICON_DIM.height);
        setFixedCellWidth(ICON_DIM.width);
        setLayoutOrientation(HORIZONTAL_WRAP);

        ((JIListModel) getModel()).reload();
        setVisibleRowCount(-1);
        clearSelection();
        ensureIndexIsVisible(0);
    }

    private MediaSeries buildSeriesFromMediaElement(MediaElement mediaElement) {
        if (mediaElement != null) {
            String cfile = getThumbnailListModel().getFileInCache(mediaElement.getFile().getAbsolutePath());
            File file = cfile == null ? mediaElement.getFile() : new File(JIListModel.EXPLORER_CACHE_DIR, cfile);
            MediaReader reader = ViewerPluginBuilder.getMedia(file);
            if (reader != null && file != null) {

                TagW tname;
                String tvalue;

                Codec codec = reader.getCodec();
                String sUID;
                String gUID;
                if (isDicomMedia(mediaElement) && codec != null && codec.isMimeTypeSupported("application/dicom")) { //$NON-NLS-1$
                    if (reader.getMediaElement() == null) {
                        // DICOM is not readable
                        return null;
                    }
                    sUID = (String) reader.getTagValue(TagW.SeriesInstanceUID);
                    gUID = (String) reader.getTagValue(TagW.PatientID);
                    tname = TagW.PatientName;
                    tvalue = (String) reader.getTagValue(TagW.PatientName);
                } else {
                    sUID = mediaElement.getFile().getAbsolutePath();
                    gUID = sUID;
                    tname = TagW.FileName;
                    tvalue = mediaElement.getFile().getName();
                }

                return ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(reader, gUID, tname, tvalue, sUID);
            }
        }
        return null;
    }

    public void openSelection() {
        Object object = getSelectedValue();
        if (object instanceof MediaElement) {
            MediaElement mediaElement = (MediaElement) object;
            openSelection(new MediaElement[] { mediaElement }, true, true, false);
        }
    }

    public void openSelection(MediaElement<?>[] medias, boolean compareEntryToBuildNewViewer, boolean bestDefaultLayout,
        boolean inSelView) {
        if (medias != null) {
            boolean oneFile = medias.length == 1;
            String sUID = null;
            String gUID = null;
            ArrayList<MediaSeries<? extends MediaElement<?>>> list =
                new ArrayList<MediaSeries<? extends MediaElement<?>>>();
            for (MediaElement<?> mediaElement : medias) {
                String cfile = getThumbnailListModel().getFileInCache(mediaElement.getFile().getAbsolutePath());
                File file = cfile == null ? mediaElement.getFile() : new File(JIListModel.EXPLORER_CACHE_DIR, cfile);
                MediaReader reader = ViewerPluginBuilder.getMedia(file);
                if (reader != null && file != null) {

                    TagW tname;
                    String tvalue;

                    Codec codec = reader.getCodec();
                    if (isDicomMedia(mediaElement) && codec != null && codec.isMimeTypeSupported("application/dicom")) { //$NON-NLS-1$
                        if (reader.getMediaElement() == null) {
                            // DICOM is not readable
                            return;
                        }
                        sUID = (String) reader.getTagValue(TagW.SeriesInstanceUID);
                        gUID = (String) reader.getTagValue(TagW.PatientID);
                        tname = TagW.PatientName;
                        tvalue = (String) reader.getTagValue(TagW.PatientName);
                    } else {
                        sUID = oneFile ? mediaElement.getFile().getAbsolutePath()
                            : sUID == null ? UUID.randomUUID().toString() : sUID;
                        gUID = sUID;
                        tname = TagW.FileName;
                        tvalue = oneFile ? mediaElement.getFile().getName() : sUID;
                    }

                    MediaSeries s =
                        ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(reader, gUID, tname, tvalue, sUID);
                    if (s != null && !list.contains(s)) {
                        list.add(s);
                    }
                }
            }
            if (list.size() > 0) {
                Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
                props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, compareEntryToBuildNewViewer);
                props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, bestDefaultLayout);
                props.put(ViewerPluginBuilder.SCREEN_BOUND, null);
                if (inSelView) {
                    props.put(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW, true);
                }

                ArrayList<String> mimes = new ArrayList<String>();
                for (MediaSeries s : list) {
                    String mime = s.getMimeType();
                    if (mime != null && !mimes.contains(mime)) {
                        mimes.add(mime);
                    }
                }
                for (String mime : mimes) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
                    if (plugin != null) {
                        ArrayList<MediaSeries<? extends MediaElement<?>>> seriesList =
                            new ArrayList<MediaSeries<? extends MediaElement<?>>>();
                        for (MediaSeries s : list) {
                            if (mime.equals(s.getMimeType())) {
                                seriesList.add(s);
                            }
                        }
                        ViewerPluginBuilder builder =
                            new ViewerPluginBuilder(plugin, seriesList, ViewerPluginBuilder.DefaultDataModel, props);
                        ViewerPluginBuilder.openSequenceInPlugin(builder);
                    }
                }
            }
        }
    }

    public void openGroup(MediaElement[] medias, boolean compareEntryToBuildNewViewer, boolean bestDefaultLayout,
        boolean modeLayout, boolean inSelView) {
        if (medias != null) {
            String groupUID = null;

            if (modeLayout) {
                groupUID = UUID.randomUUID().toString();
            }
            Map<SeriesViewerFactory, List<MediaSeries<? extends MediaElement<?>>>> plugins =
                new HashMap<SeriesViewerFactory, List<MediaSeries<? extends MediaElement<?>>>>();
            for (MediaElement m : medias) {
                String mime = m.getMimeType();
                if (mime != null) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
                    if (plugin != null) {
                        List<MediaSeries<? extends MediaElement<?>>> list = plugins.get(plugin);
                        if (list == null) {
                            list = new ArrayList<MediaSeries<? extends MediaElement<?>>>(modeLayout ? 10 : 1);
                            plugins.put(plugin, list);
                        }

                        // Get only application readers from files
                        String cfile = getThumbnailListModel().getFileInCache(m.getFile().getAbsolutePath());
                        File file = cfile == null ? m.getFile() : new File(JIListModel.EXPLORER_CACHE_DIR, cfile);
                        MediaReader mreader = ViewerPluginBuilder.getMedia(file, false);
                        if (mreader != null) {
                            if (modeLayout) {
                                MediaSeries<? extends MediaElement<?>> series = ViewerPluginBuilder
                                    .buildMediaSeriesWithDefaultModel(mreader, groupUID, null, null, null);
                                if (series != null) {
                                    list.add(series);
                                }
                            } else {
                                MediaSeries<? extends MediaElement<?>> series = null;
                                if (list.size() == 0) {
                                    series = ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(mreader);
                                    if (series != null) {
                                        list.add(series);
                                    }
                                } else {
                                    series = list.get(0);
                                    if (series != null) {
                                        MediaElement<?>[] ms = mreader.getMediaElement();
                                        if (ms != null) {
                                            for (MediaElement<?> media : ms) {
                                                media.setTag(TagW.SeriesInstanceUID,
                                                    series.getTagValue(series.getTagID()));
                                                URI uri = media.getMediaURI();
                                                media.setTag(TagW.SOPInstanceUID,
                                                    uri == null ? UUID.randomUUID().toString() : uri.toString());
                                                series.addMedia(media);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
            props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, compareEntryToBuildNewViewer);
            props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, bestDefaultLayout);
            props.put(ViewerPluginBuilder.SCREEN_BOUND, null);
            if (inSelView) {
                props.put(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW, true);
            }

            for (Iterator<Entry<SeriesViewerFactory, List<MediaSeries<? extends MediaElement<?>>>>> iterator =
                plugins.entrySet().iterator(); iterator.hasNext();) {
                Entry<SeriesViewerFactory, List<MediaSeries<? extends MediaElement<?>>>> item = iterator.next();
                ViewerPluginBuilder builder = new ViewerPluginBuilder(item.getKey(), item.getValue(),
                    ViewerPluginBuilder.DefaultDataModel, props);
                ViewerPluginBuilder.openSequenceInPlugin(builder);
            }
        }
    }

    private boolean isDicomMedia(MediaElement mediaElement) {
        if (mediaElement != null) {
            String mime = mediaElement.getMimeType();
            if (mime != null) {
                return mime.indexOf("dicom") != -1; //$NON-NLS-1$
            }
        }
        return false;
    }

    public void nextPage(final KeyEvent e) {
        final int lastIndex = getLastVisibleIndex();

        if (getLayoutOrientation() != JList.HORIZONTAL_WRAP) {
            e.consume();
            final int firstIndex = getFirstVisibleIndex();
            final int visibleRows = getVisibleRowCount();
            final int visibleColums = (int) (((float) (lastIndex - firstIndex) / (float) visibleRows) + .5);
            final int visibleItems = visibleRows * visibleColums;

            final int val = (lastIndex + visibleItems >= getModel().getSize()) ? getModel().getSize() - 1
                : lastIndex + visibleItems;
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

    private Action refreshAction() {
        // TODO set this action in toolbar
        return new AbstractAction(Messages.getString("JIThumbnailList.refresh_list")) { //$NON-NLS-1$

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
        };
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
            if (o.equals(obj)) {
                return cnt;
            }
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

    public JPopupMenu buidContexMenu(final MouseEvent e) {

        try {

            MediaElement[] selMedias = getSelectedValues();
            if (selMedias == null || selMedias.length == 0) {
                return null;
            } else {
                int index = locationToIndex(e.getPoint());
                if (index >= 0) {
                    MediaElement selectedMedia = (MediaElement) getModel().getElementAt(index);
                    if (selectedMedia != null) {
                        boolean isSelected = false;
                        for (MediaElement m : selMedias) {
                            if (m == selectedMedia) {
                                isSelected = true;
                                break;
                            }
                        }
                        if (!isSelected) {
                            selMedias = new MediaElement[] { selectedMedia };
                            setSelectedValue(selectedMedia, false);
                        }
                    }
                }
                final MediaElement[] medias = selMedias;

                JPopupMenu popupMenu = new JPopupMenu();
                TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("JIThumbnailList.sel_menu"), popupMenu.getInsets()); //$NON-NLS-1$
                popupMenu.add(itemTitle);
                popupMenu.addSeparator();

                if (medias.length == 1) {
                    JMenuItem menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.open")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openSelection(medias, true, true, false);
                        }
                    });
                    popupMenu.add(menuItem);

                    menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.open_win")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openSelection(medias, false, true, false);
                        }
                    });

                    popupMenu.add(menuItem);

                    menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.open_sel_win")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openSelection(medias, true, false, true);
                        }
                    });
                    popupMenu.add(menuItem);
                } else {
                    JMenu menu = new JMenu(Messages.getString("JIThumbnailList.open_win")); //$NON-NLS-1$
                    JMenuItem menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.stack_mode")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openGroup(medias, false, true, false, false);
                        }

                    });
                    menu.add(menuItem);
                    menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.layout_mode")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openGroup(medias, false, true, true, false);
                        }

                    });
                    menu.add(menuItem);
                    popupMenu.add(menu);

                    menu = new JMenu(Messages.getString("JIThumbnailList.add_to_win")); //$NON-NLS-1$
                    menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.stack_mode")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openGroup(medias, true, false, false, true);
                        }

                    });
                    menu.add(menuItem);
                    menuItem = new JMenuItem(new AbstractAction(Messages.getString("JIThumbnailList.layout_mode")) { //$NON-NLS-1$

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            openGroup(medias, true, false, true, true);
                        }

                    });
                    menu.add(menuItem);
                    popupMenu.add(menu);

                }
                return popupMenu;

            }
        } catch (final Exception exp) {
        } finally {
            e.consume();
        }
        return null;

    }

    final class PopupTrigger extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                openSelection();
            }
        }

        @Override
        public void mousePressed(final MouseEvent evt) {
            showPopup(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
            showPopup(evt);
        }

        private void showPopup(final MouseEvent evt) {
            // Context menu
            if (SwingUtilities.isRightMouseButton(evt)) {
                JPopupMenu popupMenu = JIThumbnailList.this.buidContexMenu(evt);
                if (popupMenu != null) {
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }
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
            if (!this.changed) {
                return;
            }
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

    // --- DragGestureListener methods -----------------------------------

    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
        Component comp = dge.getComponent();
        try {
            if (comp instanceof JIThumbnailList) {
                int index = getSelectedIndex();
                MediaElement media = (MediaElement) getSelectedValue();
                MediaSeries series = buildSeriesFromMediaElement(media);
                if (series != null) {
                    GhostGlassPane glassPane = AppProperties.glassPane;
                    Icon icon = null;
                    if (media instanceof ImageElement) {
                        icon = JIThumbnailCache.getInstance().getThumbnailFor((ImageElement) media, this, index);
                    }
                    if (icon == null) {
                        icon = JIUtility.getSystemIcon(media);
                    }
                    glassPane.setIcon(icon);
                    dragPressed = new Point(icon.getIconWidth() / 2, icon.getIconHeight() / 2);
                    Point p = (Point) dge.getDragOrigin().clone();
                    SwingUtilities.convertPointToScreen(p, comp);
                    drawGlassPane(p);
                    glassPane.setVisible(true);
                    dge.startDrag(null, series, this);
                }
            }
            return;
        } catch (RuntimeException re) {
        }

    }

    @Override
    public void dragMouseMoved(DragSourceDragEvent dsde) {
        drawGlassPane(dsde.getLocation());
    }

    // --- DragSourceListener methods -----------------------------------

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
    }

    @Override
    public void dragExit(DragSourceEvent dsde) {

    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
        GhostGlassPane glassPane = AppProperties.glassPane;
        dragPressed = null;
        glassPane.setImagePosition(null);
        glassPane.setIcon(null);
        glassPane.setVisible(false);
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    public void drawGlassPane(Point p) {
        if (dragPressed != null) {
            GhostGlassPane glassPane = AppProperties.glassPane;
            SwingUtilities.convertPointFromScreen(p, glassPane);
            p.translate(-dragPressed.x, -dragPressed.y);
            glassPane.setImagePosition(p);
        }
    }
}
