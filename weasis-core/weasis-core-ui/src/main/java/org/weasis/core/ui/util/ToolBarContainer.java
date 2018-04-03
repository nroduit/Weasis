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
package org.weasis.core.ui.util;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;

import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;

@SuppressWarnings("serial")
public class ToolBarContainer extends JPanel {
    public static final Toolbar EMPTY = ToolBarContentBuilder.buildEmptyToolBar("empty"); //$NON-NLS-1$
    private final List<Toolbar> bars = new ArrayList<>();

    public ToolBarContainer() {
        setOpaque(false);
        setLayout(new WrapLayout(FlowLayout.LEADING, 2, 2));
    }

    /**
     * Registers a new ToolBar.
     */
    public void registerToolBar(List<Toolbar> toolBars) {
        unregisterAll();

        if (toolBars == null || toolBars.isEmpty()) {
            add(ToolBarContainer.EMPTY.getComponent());
            bars.add(ToolBarContainer.EMPTY);
        } else {
            // Sort toolbars according the the position
            InsertableUtil.sortInsertable(toolBars);

            synchronized (toolBars) {
                for (Toolbar b : toolBars) {
                    WtoolBar bar = b.getComponent();
                    if (bar.isComponentEnabled()) {
                        add(bar);
                    }
                    bars.add(b);
                }
            }
        }

        revalidate();
        repaint();
    }

    public void displayToolbar(WtoolBar bar, boolean show) {
        if (show != bar.isComponentEnabled()) {
            if (show) {
                int barIndex = bar.getComponentPosition();
                int insert = 0;
                for (Iterator<Toolbar> iterator = bars.iterator(); iterator.hasNext();) {
                    Insertable b = iterator.next();
                    if (b.isComponentEnabled() && b.getComponentPosition() < barIndex) {
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
            bar.setComponentEnabled(show);
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
        return new ArrayList<>(bars);
    }

}
