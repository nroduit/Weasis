/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.core.ui.Messages;

/**
 * The Class PopUpMenuOnThumb.
 *
 * @author Nicolas Roduit
 */
public class PopUpMenuOnThumb extends JPopupMenu {

    private final Panner<?> panner;
    private JMenuItem jMenuItemThumb = new JMenuItem();
    private JMenuItem jMenuItemOrigin = new JMenuItem();
    private JMenuItem jMenuItemCenter = new JMenuItem();

    public PopUpMenuOnThumb(Panner<?> panner) {
        this.panner = panner;
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        jMenuItemThumb.setText(Messages.getString("PopUpMenuOnThumb.hide_thumb")); //$NON-NLS-1$
        jMenuItemThumb.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO To implement: show/hide from image tree display (add Panner checkboxtree)
            }
        });
        jMenuItemOrigin.setText(Messages.getString("PopUpMenuOnThumb.mv_origin")); //$NON-NLS-1$
        jMenuItemOrigin.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panner.moveToOrigin();
            }
        });
        ;
        jMenuItemCenter.setText(Messages.getString("PopUpMenuOnThumb.mv_center")); //$NON-NLS-1$
        jMenuItemCenter.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panner.moveToCenter();
            }
        });
        this.add(jMenuItemThumb);
        this.addSeparator();
        this.add(jMenuItemOrigin);
        this.add(jMenuItemCenter);
    }

}
