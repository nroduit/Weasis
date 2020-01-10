/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Point;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

public class ScrollPopupMenu extends JPopupMenu {

    private static final long serialVersionUID = 1L;
    private JPanel panelMenus = null;
    private JScrollPane scroll = null;

    public ScrollPopupMenu(Point screenPt) {
        super();

        scroll = new JScrollPane();
        panelMenus = new JPanel();
        panelMenus.setLayout(new GridLayout(0, 1));
        scroll.setViewportView(panelMenus);
        scroll.setBorder(null);

        GraphicsConfiguration gc = WinUtil.getGraphicsDeviceConfig(screenPt);
        scroll.setMaximumSize(new Dimension(scroll.getMaximumSize().width, this.getToolkit().getScreenSize().height
            - this.getToolkit().getScreenInsets(gc).top - this.getToolkit().getScreenInsets(gc).bottom - 4));
        super.add(scroll);
    }

    @Override
    public void show(Component invoker, int x, int y) {
        this.pack();
        panelMenus.validate();
        int maxsize = scroll.getMaximumSize().height;
        int realsize = panelMenus.getPreferredSize().height;

        int sizescroll = 0;

        if (maxsize < realsize) {
            sizescroll = scroll.getVerticalScrollBar().getPreferredSize().width;
        }
        scroll.setPreferredSize(
            new Dimension(scroll.getPreferredSize().width + sizescroll, scroll.getPreferredSize().height));
        super.show(invoker, x, y);
    }

    public void addMenuItem(JMenuItem item) {
        panelMenus.add(item);
    }

    public void add(JButton menuItem) {
        panelMenus.add(menuItem);
    }

    @Override
    public void addSeparator() {
        panelMenus.add(new JSeparator());
    }
}
