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
package org.weasis.core.api.media.data;

import java.awt.datatransfer.Transferable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.weasis.core.api.gui.util.Filter;

public interface MediaSeries<E> extends MediaSeriesGroup, Transferable {

    enum MEDIA_POSITION {
        FIRST, MIDDLE, LAST, RANDOM
    }

    List<E> getSortedMedias(Comparator<E> comparator);

    void addMedia(E media);

    void add(E media);

    void add(int index, E media);

    void addAll(Collection<? extends E> c);

    void addAll(int index, Collection<? extends E> c);

    E getMedia(MEDIA_POSITION position, Filter<E> filter, Comparator<E> sort);

    Iterable<E> getMedias(Filter<E> filter, Comparator<E> sort);

    List<E> copyOfMedias(Filter<E> filter, Comparator<E> sort);

    E getMedia(int index, Filter<E> filter, Comparator<E> sort);

    @Override
    void dispose();

    int size(Filter<E> filter);

    SeriesImporter getSeriesLoader();

    void setSeriesLoader(SeriesImporter seriesLoader);

    String getToolTips();

    String getSeriesNumber();

    boolean isOpen();

    boolean isSelected();

    boolean isFocused();

    String getMimeType();

    void setOpen(boolean b);

    void setSelected(boolean b, E selectedMedia);

    void setFocused(boolean b);

    E getNearestImage(double location, int offset, Filter<E> filter, Comparator<E> sort);

    int getNearestImageIndex(double location, int offset, Filter<E> filter, Comparator<E> sort);

    long getFileSize();
}
