/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

public interface IThumbnailList<E>
    extends JIObservable, DragGestureListener, DragSourceMotionListener, DragSourceListener {

    public void registerListeners();

    public Component asComponent();

    public void addListSelectionListener(ListSelectionListener listener);

    public void listValueChanged(final ListSelectionEvent e);

    public void setChanged();

    public void clearChanged();

    public int getLastVisibleIndex();

    public int getFirstVisibleIndex();

    public IThumbnailModel<E> getThumbnailListModel();

    public int[] getSelectedIndices();

    List<E> getSelectedValuesList();

    public Object getCellRenderer();
}
