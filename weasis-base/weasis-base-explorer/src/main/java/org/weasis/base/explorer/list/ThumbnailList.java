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
package org.weasis.base.explorer.list;

import java.awt.Component;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.util.List;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public interface ThumbnailList<E>
    extends ListObservable, DragGestureListener, DragSourceMotionListener, DragSourceListener {

    void registerListeners();

    Component asComponent();

    void addListSelectionListener(ListSelectionListener listener);

    void listValueChanged(final ListSelectionEvent e);

    void setChanged();

    void clearChanged();

    int getLastVisibleIndex();

    int getFirstVisibleIndex();

    IThumbnailModel<E> getThumbnailListModel();

    int[] getSelectedIndices();

    List<E> getSelectedValuesList();

    Object getCellRenderer();
}
