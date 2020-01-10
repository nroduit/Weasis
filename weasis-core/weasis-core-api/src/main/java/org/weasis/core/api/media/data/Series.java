/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;

public abstract class Series<E extends MediaElement> extends MediaSeriesGroupNode implements MediaSeries<E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Series.class);

    public static final DataFlavor sequenceDataFlavor =
        createConstant(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Series.class.getName(), null); //$NON-NLS-1$

    private static final Random RANDOM = new Random();
    private static final DataFlavor[] flavors = { sequenceDataFlavor };

    private PropertyChangeSupport propertyChange = null;
    protected final List<E> medias;
    protected final Map<Comparator<E>, List<E>> sortedMedias = new HashMap<>(6);
    protected final Comparator<E> mediaOrder;
    protected SeriesImporter seriesLoader;
    private long fileSize;

    public Series(TagW tagID, Object identifier, TagView displayTag) {
        this(tagID, identifier, displayTag, null);
    }

    public Series(TagW tagID, Object identifier, TagView displayTag, int initialCapacity) {
        this(tagID, identifier, displayTag, new ArrayList<E>(initialCapacity));
    }

    public Series(TagW tagID, Object identifier, TagView displayTag, List<E> list) {
        this(tagID, identifier, displayTag, list, null);
    }

    public Series(TagW tagID, Object identifier, TagView displayTag, List<E> list, Comparator<E> mediaOrder) {
        super(tagID, identifier, displayTag);
        this.mediaOrder = mediaOrder;
        List<E> ls = list;
        if (ls == null) {
            ls = new ArrayList<>();
            fileSize = 0L;
        } else if (mediaOrder != null) {
            Collections.sort(ls, mediaOrder);
        }
        medias = Collections.synchronizedList(ls);
    }

    private static DataFlavor createConstant(String mt, String prn) {
        try {
            return new DataFlavor(mt, prn, Series.class.getClassLoader()); // $NON-NLS-1$
        } catch (Exception e) {
            LOGGER.error("Build series flavor", e); //$NON-NLS-1$
            return null;
        }
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
            return sortedMedias.computeIfAbsent(comparator, k -> {
                List<E> sorted = new ArrayList<>(medias);
                Collections.sort(sorted, comparator);
                return sorted;
            });
        }
        return medias;
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
        synchronized (this) {
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
        synchronized (this) {
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
        return filter == null ? new ArrayList<>(sortedList) : Filter.makeList(filter.filter(sortedList));
    }

    @Override
    public final E getMedia(int index, Filter<E> filter, Comparator<E> sort) {
        List<E> sortedList = getSortedMedias(sort);
        synchronized (this) {
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

    @Override
    public void dispose() {
        // forEach implement synchronized
        medias.forEach(m -> {
            if (m instanceof ImageElement) {
                // Removing from cache will close the image stream
                ((ImageElement) m).removeImageFromCache();
            }
            m.dispose();
        });

        medias.clear();
        resetSortedMediasMap();

        Optional.ofNullable((Thumbnail) getTagValue(TagW.Thumbnail)).ifPresent(t -> t.dispose());
        if (propertyChange != null) {
            Arrays.asList(propertyChange.getPropertyChangeListeners())
                .forEach(propertyChange::removePropertyChangeListener);
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
                SwingUtilities.invokeLater(() -> propertyChange.firePropertyChange(event));
            }
        }
    }

    @Override
    public int size(Filter<E> filter) {
        synchronized (this) {
            return filter == null ? medias.size() : Filter.size(filter.filter(medias));
        }
    }

    @Override
    public boolean isOpen() {
        Boolean open = (Boolean) getTagValue(TagW.SeriesOpen);
        return open == null ? false : open;
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder();
        toolTips.append("<html>"); //$NON-NLS-1$
        E media = this.getMedia(MEDIA_POSITION.MIDDLE, null, null);
        if (media instanceof ImageElement) {
            ImageElement image = (ImageElement) media;
            PlanarImage img = image.getImage();
            if (img != null) {
                toolTips.append(Messages.getString("Series.img_size")); //$NON-NLS-1$
                toolTips.append(StringUtil.COLON_AND_SPACE);
                toolTips.append(img.width());
                toolTips.append('x');
                toolTips.append(img.height());
            }
        }
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    protected void addToolTipsElement(StringBuilder toolTips, String title, TagW tag) {
        toolTips.append(title);
        toolTips.append(StringUtil.COLON_AND_SPACE);
        if (tag != null) {
            toolTips.append(tag.getFormattedTagValue(getTagValue(tag), null));
        }
        toolTips.append("<br>"); //$NON-NLS-1$
    }
    
    protected void addToolTipsElement(StringBuilder toolTips, String title, TagW tag1, TagW tag2) {
        toolTips.append(title);
        toolTips.append(StringUtil.COLON_AND_SPACE);
        if (tag1 != null) {
            toolTips.append(tag1.getFormattedTagValue(getTagValue(tag1), null));
            toolTips.append(" - "); //$NON-NLS-1$
        }
        if (tag2 != null) {
            toolTips.append(tag2.getFormattedTagValue(getTagValue(tag2), null));
        }
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
        return LangUtil.getNULLtoFalse((Boolean) getTagValue(TagW.SeriesSelected));
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

    @Override
    public boolean isFocused() {
        return LangUtil.getNULLtoFalse((Boolean) getTagValue(TagW.SeriesFocused));
    }

    @Override
    public void setFocused(boolean focused) {
        if (this.isFocused() != focused) {
            setTag(TagW.SeriesFocused, focused);
            Thumbnail thumb = (Thumbnail) getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.repaint();
            }
        }
    }

    public boolean hasMediaContains(TagW tag, Object val) {
        if (val != null) {
            synchronized (this) {
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

    @Override
    public int getNearestImageIndex(double location, int offset, Filter<E> filter, Comparator<E> sort) {
        return -1;
    }

    public synchronized void setFileSize(long size) {
        fileSize = size;
    }

    @Override
    public synchronized long getFileSize() {
        return fileSize;
    }

    @Override
    public String getSeriesNumber() {
        Integer val = (Integer) getTagValue(TagW.get("SeriesNumber")); //$NON-NLS-1$
        return Optional.ofNullable(val).map(String::valueOf).orElseGet(() -> ""); //$NON-NLS-1$
    }
}
