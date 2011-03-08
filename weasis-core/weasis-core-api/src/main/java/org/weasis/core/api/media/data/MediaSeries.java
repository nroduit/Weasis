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

import java.awt.datatransfer.Transferable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public interface MediaSeries<E> extends MediaSeriesGroup, Transferable {

    public enum MEDIA_POSITION {
        FIRST, MIDDLE, LAST, RANDOM
    };

    public void sort(Comparator<E> comparator);

    public void addMedia(MediaElement media);

    public void add(E media);

    public void add(int index, E media);

    public void addAll(Collection<? extends E> c);

    public void addAll(int index, Collection<? extends E> c);

    public E getMedia(MEDIA_POSITION position);

    public List<E> getMedias();

    public E getMedia(int index);

    public void dispose();

    public int size();

    public SeriesImporter getSeriesLoader();

    public void setSeriesLoader(SeriesImporter seriesLoader);

    public String getToolTips();

    public boolean isOpen();

    public boolean isSelected();

    public String getMimeType();

    public void setOpen(boolean b);

    public void setSelected(boolean b, int selectedImage);

    public int getNearestIndex(double location);

    public double getFileSize();
}
