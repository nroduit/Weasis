/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
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

    enum MEDIA_POSITION {
        FIRST, MIDDLE, LAST, RANDOM
    };

    void sort(Comparator<E> comparator);

    void addMedia(MediaElement media);

    void add(E media);

    void add(int index, E media);

    void addAll(Collection<? extends E> c);

    void addAll(int index, Collection<? extends E> c);

    E getMedia(MEDIA_POSITION position);

    List<E> getMedias();

    E getMedia(int index);

    @Override
    void dispose();

    int size();

    SeriesImporter getSeriesLoader();

    void setSeriesLoader(SeriesImporter seriesLoader);

    String getToolTips();

    boolean isOpen();

    boolean isSelected();

    String getMimeType();

    void setOpen(boolean b);

    void setSelected(boolean b, int selectedImage);

    int getNearestIndex(double location);

    double getFileSize();
}
