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
package org.weasis.dicom.explorer;

import java.awt.Color;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JPanel;

import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;

public class SeriesSelectionModel extends ArrayList<Series> {
    private static final Color selectedColor = new Color(82, 152, 219);

    public SeriesSelectionModel(int initialCapacity) {
        super(initialCapacity);
    }

    public SeriesSelectionModel() {
        super();
    }

    @Override
    public void add(int index, Series element) {
        if (!contains(element)) {
            super.add(index, element);
            setBackgroundColor(element, false);
        }
    }

    @Override
    public boolean add(Series e) {
        if (!contains(e)) {
            setBackgroundColor(e, true);
            return super.add(e);
        }
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends Series> c) {
        for (Series series : c) {
            setBackgroundColor(series, true);
        }
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends Series> c) {
        for (Series series : c) {
            setBackgroundColor(series, true);
        }
        return super.addAll(index, c);
    }

    @Override
    public void clear() {
        for (Series s : this) {
            setBackgroundColor(s, false);
        }
        super.clear();
    }

    @Override
    public Series remove(int index) {
        Series s = super.remove(index);
        if (s != null) {
            setBackgroundColor(s, false);
        }
        return s;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Series) {
            setBackgroundColor((Series) o, false);
        }
        return super.remove(o);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        int seriesSize = this.size();
        int length = toIndex > seriesSize ? seriesSize : toIndex;
        int start = fromIndex < 0 ? 0 : fromIndex > length - 1 ? length - 1 : fromIndex;
        for (int i = start; i < length; i++) {
            setBackgroundColor(this.get(i), false);
        }
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public Series set(int index, Series element) {
        Series s = super.set(index, element);
        if (s != null) {
            setBackgroundColor(s, false);
        }
        if (element != null) {
            setBackgroundColor(element, true);
        }
        return s;
    }

    private void setBackgroundColor(Series series, boolean selected) {
        if (series != null) {
            Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                Container parent = thumb.getParent();
                if (parent instanceof JPanel) {
                    Color color = selected ? selectedColor : (Color) javax.swing.UIManager.get("Panel.background"); //$NON-NLS-1$
                    parent.setBackground(color);
                }
            }
        }
    }
}
