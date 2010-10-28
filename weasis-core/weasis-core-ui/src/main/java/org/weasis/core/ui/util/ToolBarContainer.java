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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JToolBar;

import org.weasis.core.ui.util.WtoolBar.TYPE;

public class ToolBarContainer extends JPanel {

    private Map<String, WtoolBar> toolBarsByName = new HashMap<String, WtoolBar>();

    public ToolBarContainer() {
    }

    /**
     * Registers a new ToolBar.
     */
    public void registerToolBar(WtoolBar toolbar) {
        if (toolbar == null) {
            return;
        }
        TYPE type = toolbar.getType();
        String name = TYPE.tool.equals(type) ? toolbar.toString() : type.name();
        WtoolBar oldBar = toolBarsByName.get(name);
        toolBarsByName.put(name, toolbar);
        toolbar.setAlignmentX(LEFT_ALIGNMENT);
        if (oldBar == null) {
            add(toolbar);
        } else {
            int index = getComponentIndex(oldBar);
            if (index >= 0) {
                super.remove(index);
            } else {
                index = this.getComponentCount();
            }
            add(toolbar, index);
        }
    }

    private int getComponentIndex(JToolBar bar) {
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
    public void unregisterToolBar(WtoolBar toolbar) {
        if (toolbar == null) {
            return;
        }
        TYPE type = toolbar.getType();
        String name = TYPE.tool.equals(type) ? toolbar.toString() : type.name();
        toolBarsByName.remove(name);
        remove(toolbar);
    }

    /**
     * Returns the registered toolbar associated with the given name, or null if not found
     */
    public JToolBar getToolBarByName(String name) {
        return toolBarsByName.get(name);
    }

    /**
     * Returns the list of currently registered toolbars.
     * 
     *<p>
     * returns a new list at each invocation.
     */
    public List getRegisteredToolBars() {
        return new ArrayList(toolBarsByName.values());
    }

}
