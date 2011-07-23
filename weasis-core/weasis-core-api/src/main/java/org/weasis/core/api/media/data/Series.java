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
import java.util.List;
import java.util.Random;

import javax.swing.SwingUtilities;

import org.weasis.core.api.Messages;
import org.weasis.core.api.explorer.ObservableEvent;

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
    protected SeriesImporter seriesLoader;
    private double fileSize;

    public Series(TagW tagID, Object identifier, TagW displayTag) {
        this(tagID, identifier, displayTag, null);
    }

    public Series(TagW tagID, Object identifier, TagW displayTag, int initialCapacity) {
        this(tagID, identifier, displayTag, new ArrayList<E>(initialCapacity));
    }

    public Series(TagW tagID, Object identifier, TagW displayTag, List<E> list) {
        super(tagID, identifier, displayTag);
        if (list == null) {
            list = new ArrayList<E>();
            fileSize = 0.0;
        }
        medias = Collections.synchronizedList(list);
    }

    @Override
    public void sort(Comparator<E> comparator) {
        synchronized (medias) {
            Collections.sort(medias, comparator);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public void add(E media) {
        medias.add(media);
    }

    @Override
    public void add(int index, E media) {
        medias.add(index, media);
    }

    @Override
    public void addAll(Collection<? extends E> c) {
        medias.addAll(c);
    }

    @Override
    public void addAll(int index, Collection<? extends E> c) {
        medias.addAll(index, c);
    }

    @Override
    public final E getMedia(MEDIA_POSITION position) {
        synchronized (medias) {
            int size = medias.size();
            if (size == 0)
                return null;
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
            return medias.get(pos);
        }
    }

    public final int getImageIndex(E source) {
        if (source == null)
            return -1;
        synchronized (medias) {
            for (int i = 0; i < medias.size(); i++) {
                if (medias.get(i) == source)
                    return i;
            }
        }
        return -1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.media.data.MediaSeries#getMedias()
     */
    @Override
    public final List<E> getMedias() {
        return medias;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.media.data.MediaSeries#getMedia(int)
     */
    @Override
    public final E getMedia(int index) {
        synchronized (medias) {
            if (index >= 0 && index < medias.size())
                return medias.get(index);
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
                media.dispose();
            }
        }
        medias.clear();
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
        if (sequenceDataFlavor.equals(flavor))
            return this;
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
            if (event == null)
                throw new NullPointerException();
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
    public int size() {
        return medias.size();
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

        E media = this.getMedia(MEDIA_POSITION.MIDDLE);
        if (media instanceof ImageElement) {
            ImageElement image = (ImageElement) media;
            RenderedImage img = image.getImage();
            if (img != null) {
                toolTips.append(Messages.getString("Series.img_size") + img.getWidth() + "x" + img.getHeight()); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        // TODO for other medias
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    protected void addToolTipsElement(StringBuffer toolTips, String title, TagW tag) {
        Object tagValue = getTagValue(tag);
        toolTips.append(title);
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
        }
    }

    @Override
    public boolean isSelected() {
        Boolean selected = (Boolean) getTagValue(TagW.SeriesSelected);
        return selected == null ? false : selected;
    }

    @Override
    public void setSelected(boolean selected, int selectedImage) {
        if (this.isSelected() != selected) {
            setTag(TagW.SeriesSelected, selected);
            Thumbnail thumb = (Thumbnail) getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.repaint();
            }
        }
    }

    public void resetLoaders() {
        synchronized (medias) {
            for (int i = 0; i < medias.size(); i++) {
                E media = medias.get(i);
                if (media.getMediaReader() != null) {
                    media.getMediaReader().reset();
                }
            }
        }
    }

    public boolean hasMediaContains(TagW tag, Object val) {
        if (val != null) {
            synchronized (medias) {
                for (int i = 0; i < medias.size(); i++) {
                    Object val2 = medias.get(i).getTagValue(tag);
                    if (val.equals(val2))
                        return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getNearestIndex(double location) {
        return -1;
    }

    public synchronized void setFileSize(double size) {
        fileSize = size;
    }

    @Override
    public synchronized double getFileSize() {
        return fileSize;
    }

}
