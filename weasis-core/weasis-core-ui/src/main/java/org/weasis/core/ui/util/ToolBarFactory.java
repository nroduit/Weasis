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

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.weasis.core.ui.util.WtoolBar.TYPE;

public class ToolBarFactory {

    public static WtoolBar buildEmptyToolBar(String name) {
        WtoolBar toolBar = new WtoolBar(name, TYPE.main);
        toolBar.add(ToolBarFactory.buildToolBarSizerComponent());
        return toolBar;
    }

    public static void buildButtonAction(WtoolBar toolBar, AbstractUIAction action) {
        Border border = new EmptyBorder(2, 9, 2, 9); // top, left, bottom, right

        JButton button = new JButton(action);
        button.setToolTipText(action.getDescription());
        button.setBorder(border);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setText(null);
        button.setFocusable(false);
        toolBar.add(button);
    }

    public static JComponent buildToolBarSizerComponent() {
        return new JButton(new Icon() {

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
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
