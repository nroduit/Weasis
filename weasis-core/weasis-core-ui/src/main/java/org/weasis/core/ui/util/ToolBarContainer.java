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
package org.weasis.core.ui.util;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;

public class ToolBarContainer extends JPanel {
    public static final Toolbar EMPTY = ToolBarFactory.buildEmptyToolBar("empty"); //$NON-NLS-1$
    private final List<Toolbar> bars = new ArrayList<Toolbar>();

    public ToolBarContainer() {
        setOpaque(false);
        setLayout(new WrapLayout(FlowLayout.LEADING, 2, 2));
    }

    /**
     * Registers a new ToolBar.
     */
    public void registerToolBar(List<Toolbar> toolBar) {
        unregisterAll();
        if (toolBar == null) {
            toolBar = new ArrayList<Toolbar>(1);
            toolBar.add(ToolBarContainer.EMPTY);
        }

        // Sort toolbars according the index
        Collections.sort(toolBar, new Comparator<Toolbar>() {

            @Override
            public int compare(Toolbar t1, Toolbar t2) {
                int val1 = t1.getIndex();
                int val2 = t2.getIndex();
                return val1 < val2 ? -1 : (val1 == val2 ? 0 : 1);
            }
        });

        for (Toolbar b : toolBar) {
            WtoolBar bar = b.getComponent();
            if (bar.isEnabled()) {
                add(bar);
            }
            bars.add(b);
        }

        revalidate();
        repaint();
    }

    public void displayToolbar(WtoolBar bar, boolean show) {
        if (show != bar.getComponent().isEnabled()) {
            if (show) {
                int barIndex = bar.getIndex();
                int insert = 0;
                for (Iterator<Toolbar> iterator = bars.iterator(); iterator.hasNext();) {
                    Toolbar b = iterator.next();
                    if (b.isEnabled() && b.getIndex() < barIndex) {
                        insert++;
                    }
                }
                if (insert >= getComponentCount()) {
                    // -1 => inserting after the last component
                    insert = -1;
                }
                add(bar, insert);
            } else {
                super.remove(bar);
            }
            bar.getComponent().setEnabled(show);
            revalidate();
            repaint();
        }
    }

    private void unregisterAll() {
        bars.clear();
        removeAll();
    }

    /**
     * Returns the list of currently registered toolbars.
     * 
     * <p>
     * returns a new list at each invocation.
     */
    public List<Toolbar> getRegisteredToolBars() {
        return new ArrayList<Toolbar>(bars);
    }

}
