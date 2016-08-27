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
package org.weasis.acquire.explorer.gui;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;

public class AcquireToolBar<DicomImageElement> extends WtoolBar {
    private static final long serialVersionUID = 3195220259820490950L;

    public AcquireToolBar(int index) {
        super("Dicomization Toolbar", index);

        // TODO add button for publishing, help...
        final JButton printButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png"))); //$NON-NLS-1$
        printButton.setToolTipText(""); //$NON-NLS-1$
        printButton.addActionListener(e -> {
            // Do nothing
        });
        add(printButton);
    }

}
