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

import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicToggleButtonUI;

public class RolloverButtonUI extends BasicToggleButtonUI {

    public RolloverButtonUI() {
        super();
    }

    @Override
    public void paint(Graphics g, JComponent comp) {
        AbstractButton btn = (AbstractButton) comp;
        boolean rollover = btn.getModel().isRollover();
        boolean selected = btn.getModel().isSelected();
        boolean armed = btn.getModel().isArmed();
        btn.setBorderPainted(selected || rollover);
        if (rollover || selected) {
            if (armed) {
                g.translate(1, 1);
            } else {
                if (!selected) {
                    g.setColor(UIManager.getColor("controlHighlight")); //$NON-NLS-1$
                    g.fillRect(1, 1, btn.getWidth() - 2, btn.getHeight() - 2);
                }
            }
        }

        Border b = comp.getBorder();
        if (b instanceof ToolBarButtonBorder) {
            ((ToolBarButtonBorder) b).setPressed(selected || armed);
        }

        super.paint(g, comp);
    }

}
