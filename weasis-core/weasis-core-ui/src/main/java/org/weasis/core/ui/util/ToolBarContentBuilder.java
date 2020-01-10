/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;

public class ToolBarContentBuilder {

    public static WtoolBar buildEmptyToolBar(String name) {
        WtoolBar toolBar = new WtoolBar(name, 0) {
            @Override
            public Type getType() {
                return Type.EMPTY;
            }
        };
        toolBar.add(ToolBarContentBuilder.buildToolBarSizerComponent());
        return toolBar;
    }

    private static JComponent buildToolBarSizerComponent() {
        return new JButton(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                // Do noting
            }

            @Override
            public int getIconWidth() {
                return 2;
            }

            @Override
            public int getIconHeight() {
                return 32;
            }
        });
    }
}
