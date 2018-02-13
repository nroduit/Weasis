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
package org.weasis.core.ui.editor.image;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.core.ui.Messages;

/**
 * The Class PopUpMenuOnThumb.
 *
 * @author Nicolas Roduit
 */
@SuppressWarnings("serial")
public class PopUpMenuOnThumb extends JPopupMenu {

    private final Panner<?> panner;
    // private JMenuItem jMenuItemThumb = new JMenuItem();
    private JMenuItem jMenuItemOrigin = new JMenuItem();
    private JMenuItem jMenuItemCenter = new JMenuItem();

    public PopUpMenuOnThumb(Panner<?> panner) {
        this.panner = panner;
        init();
    }

    private void init() {
        // jMenuItemThumb.setText(Messages.getString("PopUpMenuOnThumb.hide_thumb")); //$NON-NLS-1$
        // TODO To implement: show/hide from image tree display (add Panner checkboxtree)

        jMenuItemOrigin.setText(Messages.getString("PopUpMenuOnThumb.mv_origin")); //$NON-NLS-1$
        jMenuItemOrigin.addActionListener(e -> panner.moveToOrigin());

        jMenuItemCenter.setText(Messages.getString("PopUpMenuOnThumb.mv_center")); //$NON-NLS-1$
        jMenuItemCenter.addActionListener(e -> panner.moveToCenter());
        // this.add(jMenuItemThumb);
        this.addSeparator();
        this.add(jMenuItemOrigin);
        this.add(jMenuItemCenter);
    }

}
