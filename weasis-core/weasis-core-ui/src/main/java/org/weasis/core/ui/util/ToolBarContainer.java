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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;

@SuppressWarnings("serial")
public class ToolBarContainer extends JPanel {
    public static final Toolbar EMPTY = ToolBarContentBuilder.buildEmptyToolBar("empty"); //$NON-NLS-1$
    private final List<Toolbar> bars = new ArrayList<>();

    public ToolBarContainer() {
        setOpaque(false);
        setLayout(new WrapLayout(FlowLayout.LEADING, 2, 2));
        addMouseListener(new PopClickListener());
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

            synchronized (toolBars) { //NOSONAR lock object is the list for iterating its elements safely
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

    class PopClickListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger())
                doPop(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger())
                doPop(e);
        }

        private void doPop(MouseEvent e) {
            PopUpToolbars menu = new PopUpToolbars();
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    class PopUpToolbars extends JPopupMenu {
        public PopUpToolbars() {
            for (final Toolbar bar : getRegisteredToolBars()) {
                if (!Insertable.Type.EMPTY.equals(bar.getType())) {
                    JCheckBoxMenuItem item = new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
                    item.addActionListener(e -> {
                        if (e.getSource() instanceof JCheckBoxMenuItem) {
                            displayToolbar(bar.getComponent(), ((JCheckBoxMenuItem) e.getSource()).isSelected());
                        }
                    });
                    add(item);
                }
            }
        }
    }
}
