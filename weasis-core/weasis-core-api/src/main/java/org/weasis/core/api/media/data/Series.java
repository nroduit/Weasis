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
package org.weasis.core.api.media.data;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.SwingUtilities;

import org.weasis.core.api.Messages;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.Filter;

public abstract class Series<E extends MediaElement> extends MediaSeriesGroupNode implements MediaSeries<E> {

    private static final Random RANDOM = new Random();
    public static DataFlavor sequenceDataFlavor;
    static {
        try {
            sequenceDataFlavor =
                new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Series.class.getName(), null, //$NON-NLS-1$
                    Series.class.getClassLoader());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private final DataFlavor[] flavors = { sequenceDataFlavor };
    private PropertyChangeSupport propertyChange = null;
    protected final List<E> medias;
    protected final Map<Comparator<E>, List<E>> sortedMedias = new HashMap<Comparator<E>, List<E>>(6);
    protected final Comparator<E> mediaOrder;
    protected SeriesImporter seriesLoader;
    private double fileSize;

    public Series(TagW tagID, Object identifier, TagW displayTag) {
        this(tagID, identifier, displayTag, null);
    }

    public Series(TagW tagID, Object identifier, TagW displayTag, int initialCapacity) {
        this(tagID, identifier, displayTag, new ArrayList<E>(initialCapacity));
    }

    public Series(TagW tagID, Object identifier, TagW displayTag, List<E> list) {
        this(tagID, identifier, displayTag, list, null);
    }

    public Series(TagW tagID, Object identifier, TagW displayTag, List<E> list, Comparator<E> mediaOrder) {
        super(tagID, identifier, displayTag);
        this.mediaOrder = mediaOrder;
        if (list == null) {
            list = new ArrayList<E>();
            fileSize = 0.0;
        }
        medias = Collections.synchronizedList(list);
    }

    protected void resetSortedMediasMap() {
        if (!sortedMedias.isEmpty()) {
            sortedMedias.clear();
        }
    }

    @Override
    public List<E> getSortedMedias(Comparator<E> comparator) {
        // Do not sort when it is the default order.
        if (comparator != null && !comparator.equals(mediaOrder)) {
            List<E> sorted = sortedMedias.get(comparator);
            if (sorted == null) {
                sorted = new ArrayList<E>(medias);
                Collections.sort(sorted, comparator);
                sortedMedias.put(comparator, sorted);
            }
            return sorted;
        }
        return medias;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public void add(E media) {
        medias.add(media);
        resetSortedMediasMap();
    }

    @Override
    public void add(int index, E media) {
        medias.add(index, media);
        resetSortedMediasMap();
    }

    @Override
    public void addAll(Collection<? extends E> c) {
        medias.addAll(c);
        resetSortedMediasMap();
    }

    @Override
    public void addAll(int index, Collection<? extends E> c) {
        medias.addAll(index, c);
        resetSortedMediasMap();
    }

    @Override
    public final E getMedia(MEDIA_POSITION position, Filter<E> filter, Comparator<E> sort) {
        List<E> sortedList = getSortedMedias(sort);
        synchronized (sortedList) {
            if (filter == null) {
                int size = sortedList.size();
                if (size == 0) {
                    return null;
                }
                int pos = 0;
                if (MEDIA_POSITION.FIRST.equals(position)) {
                    pos = 0;
                } else if (MEDIA_POSITION.MIDDLE.equals(position)) {
                    pos = size / 2;
                } else if (MEDIA_POSITION.LAST.equals(position)) {
                    pos = size - 1;
                } else if (MEDIA_POSITION.RANDOM.equals(position)) {
                    pos = RANDOM.nextInt(size);
                }
                return sortedList.get(pos);
            } else {
                Iterable<E> iter = filter.filter(sortedList);
                Iterator<E> list = iter.iterator();
                if (list.hasNext()) {
                    E val = list.next();
                    if (MEDIA_POSITION.FIRST.equals(position)) {
                        return val;
                    }
                    int pos = 0;
                    int size = Filter.size(iter);
                    if (MEDIA_POSITION.MIDDLE.equals(position)) {
                        pos = size / 2;
                    } else if (MEDIA_POSITION.LAST.equals(position)) {
                        pos = size - 1;
                    } else if (MEDIA_POSITION.RANDOM.equals(position)) {
                        pos = RANDOM.nextInt(size);
                    }
                    int k = 0;
                    for (E elem : iter) {
                        if (k == pos) {
                            return elem;
                        }
                    }
                    return val;
                } else {
                    return null;
                }
            }
        }
    }

    public final int getImageIndex(E source, Filter<E> filter, Comparator<E> sort) {
        if (source == null) {
            return -1;
        }
        Iterable<E> list = getMedias(filter, sort);
        synchronized (list) {
            int index = 0;
            for (E e : list) {
                if (e == source) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    @Override
    public final Iterable<E> getMedias(Filter<E> filter, Comparator<E> sort) {
        List<E> sortedList = getSortedMedias(sort);
        return filter == null ? sortedList : filter.filter(sortedList);
    }

    @Override
    public final List<E> copyOfMedias(Filter<E> filter, Comparator<E> sort) {
        List<E> sortedList = getSortedMedias(sort);
        return filter == null ? new ArrayList<E>(sortedList) : Filter.makeList(filter.filter(sortedList));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.media.data.MediaSeries#getMedia(int)
     */
    @Override
    public final E getMedia(int index, Filter<E> filter, Comparator<E> sort) {
        List<E> sortedList = getSortedMedias(sort);
        synchronized (sortedList) {
            if (filter == null) {
                if (index >= 0 && index < sortedList.size()) {
                    return sortedList.get(index);
                }
            } else {
                if (index >= 0) {
                    Iterable<E> iter = filter.filter(sortedList);
                    int k = 0;
                    for (E elem : iter) {
                        if (k == index) {
                            return elem;
                        }
                        k++;
                    }
                }
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.media.data.MediaSeries#dispose()
     */
    @Override
    public void dispose() {
        synchronized (medias) {
            for (MediaElement media : medias) {
                if (media instanceof ImageElement) {
                    // Removing from cache will close the image stream
                    ((ImageElement) media).removeImageFromCache();
                }
                media.dispose();
            }
        }
        medias.clear();
        resetSortedMediasMap();
        Thumbnail thumb = (Thumbnail) getTagValue(TagW.Thumbnail);
        if (thumb != null) {
            thumb.dispose();
        }
        if (propertyChange != null) {
            PropertyChangeListener[] listeners = propertyChange.getPropertyChangeListeners();
            for (PropertyChangeListener propertyChangeListener : listeners) {
                propertyChange.removePropertyChangeListener(propertyChangeListener);
            }
        }
        seriesLoader = null;
    }

    @Override
    public SeriesImporter getSeriesLoader() {
        return seriesLoader;
    }

    @Override
    public void setSeriesLoader(SeriesImporter seriesLoader) {
        this.seriesLoader = seriesLoader;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return sequenceDataFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (sequenceDataFlavor.equals(flavor)) {
            return this;
        }
        throw new UnsupportedFlavorException(flavor);
    }

    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    public void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        propertyChange.firePropertyChange(event);
                    }
                });
            }
        }
    }

    @Override
    public int size(Filter<E> filter) {
        return filter == null ? medias.size() : Filter.size(filter.filter(medias));
    }

    @Override
    public boolean isOpen() {
        Boolean open = (Boolean) getTagValue(TagW.SeriesOpen);
        return open == null ? false : open;
    }

    @Override
    public String getToolTips() {
        StringBuffer toolTips = new StringBuffer();
        toolTips.append("<html>"); //$NON-NLS-1$

        // int seqSize = this.getLoadSeries() == null ? this.size() :
        // this.getLoadSeries().getProgressBar().getMaximum();
        // toolTips.append("Number of Frames: " + seqSize + "<br>");

        E media = this.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        if (media instanceof ImageElement) {
            ImageElement image = (ImageElement) media;
            RenderedImage img = image.getImage();
            if (img != null) {
                toolTips.append(Messages.getString("Series.img_size")); //$NON-NLS-1$
                toolTips.append(' ');
                toolTips.append(img.getWidth());
                toolTips.append('x');
                toolTips.append(img.getHeight());
            }
        }
        // TODO for other medias
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    protected void addToolTipsElement(StringBuffer toolTips, String title, TagW tag) {
        Object tagValue = getTagValue(tag);
        toolTips.append(title);
        toolTips.append(' ');
        toolTips.append(tagValue == null ? "" : tagValue); //$NON-NLS-1$
        toolTips.append("<br>"); //$NON-NLS-1$
    }

    @Override
    public void setOpen(boolean open) {
        if (this.isOpen() != open) {
            setTag(TagW.SeriesOpen, open);
            Thumbnail thumb = (Thumbnail) getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.repaint();
            }
            if (!open) {
                resetSortedMediasMap();
            }
        }
    }

    @Override
    public boolean isSelected() {
        Boolean selected = (Boolean) getTagValue(TagW.SeriesSelected);
        return selected == null ? false : selected;
    }

    @Override
    public void setSelected(boolean selected, E selectedImage) {
        if (this.isSelected() != selected) {
            setTag(TagW.SeriesSelected, selected);
            Thumbnail thumb = (Thumbnail) getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.repaint();
            }
        }
    }

    public boolean hasMediaContains(TagW tag, Object val) {
        if (val != null) {
            synchronized (medias) {
                for (int i = 0; i < medias.size(); i++) {
                    Object val2 = medias.get(i).getTagValue(tag);
                    if (val.equals(val2)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public E getNearestImage(double location, int offset, Filter<E> filter, Comparator<E> sort) {
        return null;
    }

    public synchronized void setFileSize(double size) {
        fileSize = size;
    }

    @Override
    public synchronized double getFileSize() {
        return fileSize;
    }

}
