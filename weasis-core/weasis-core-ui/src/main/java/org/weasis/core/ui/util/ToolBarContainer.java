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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.weasis.core.ui.util.WtoolBar.TYPE;

public class ToolBarContainer extends JPanel {
    public static final Toolbar EMPTY = ToolBarFactory.buildEmptyToolBar("empty"); //$NON-NLS-1$
    private final Map<String, Toolbar> toolBarsByName = new HashMap<String, Toolbar>();

    public ToolBarContainer() {
        setOpaque(false);
        setLayout(new WrapLayout(FlowLayout.LEADING, 2, 2));
    }

    /**
     * Registers a new ToolBar.
     */
    public void registerToolBar(Toolbar toolbar) {
        if (toolbar == null || toolbar.getComponent() == null) {
            return;
        }
        TYPE type = toolbar.getType();
        String name = TYPE.main.equals(type) ? type.name() : toolbar.getBarName();
        Toolbar oldBar = toolBarsByName.get(name);
        toolBarsByName.put(name, toolbar);
        boolean visible = toolbar.getComponent().isEnabled();
        if (oldBar == null) {
            if (visible) {
                add(toolbar.getComponent());
            }
        } else {
            int index = getComponentIndex(oldBar.getComponent());
            if (index >= 0) {
                super.remove(index);
            } else {
                index = this.getComponentCount();
            }
            if (visible) {
                add(toolbar.getComponent(), index);
            }
        }
    }

    public void showToolbar(WtoolBar bar) {
        boolean show = bar.getComponent().isEnabled();
        int index = getComponentIndex(bar);
        if (index >= 0) {
            super.remove(index);
        } else {
            index = this.getComponentCount();
        }
        if (show) {
            add(bar, index);
        }
        revalidate();
        repaint();
    }

    private int getComponentIndex(JComponent bar) {
        synchronized (this) {
            int size = this.getComponentCount();
            for (int i = 0; i < size; i++) {
                if (bar == getComponent(i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Unregisters a ToolBar.
     */
    public void unregisterToolBar(Toolbar toolbar) {
        if (toolbar == null || toolbar.getComponent() == null) {
            return;
        }
        TYPE type = toolbar.getType();
        String name = TYPE.main.equals(type) ? type.name() : toolbar.getBarName();
        toolBarsByName.remove(name);
        remove(toolbar.getComponent());
    }

    public void unregisterAll() {
        toolBarsByName.clear();
        removeAll();
    }

    /**
     * Returns the registered toolbar associated with the given name, or null if not found
     */
    public Toolbar getToolBarByName(String name) {
        return toolBarsByName.get(name);
    }

    /**
     * Returns the list of currently registered toolbars.
     * 
     * <p>
     * returns a new list at each invocation.
     */
    public List<Toolbar> getRegisteredToolBars() {
        return new ArrayList<Toolbar>(toolBarsByName.values());
    }

}
