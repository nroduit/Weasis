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
package org.weasis.dicom.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

public enum ResetTools {
    All(Messages.getString("ResetTools.all")), //$NON-NLS-1$

    WindowLevel(Messages.getString("ResetTools.wl")), //$NON-NLS-1$

    Zoom(Messages.getString("ViewerPrefView.zoom")), //$NON-NLS-1$

    Rotation(Messages.getString("ResetTools.rotation")), //$NON-NLS-1$

    Pan(Messages.getString("ResetTools.pan")); //$NON-NLS-1$

    private final String name;

    private ResetTools(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static JMenu createUnregisteredJMenu() {
        ButtonGroup group = new ButtonGroup();
        JMenu menu = new JMenu(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
        for (final ResetTools action : values()) {
            final JMenuItem item = new JMenuItem(action.toString());
            item.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    EventManager.getInstance().reset(action);
                }
            });
            menu.add(item);
            group.add(item);
        }
        return menu;
    }
}
