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
package org.weasis.base.viewer2d;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.util.WtoolBar;

public class ImportToolBar extends WtoolBar {

    public ImportToolBar(int index) {
        super("Images Import Bar", index);

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.images", true)) { //$NON-NLS-1$
            AbstractAction action = new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    ViewerFactory.getOpenImageAction(e);
                }
            };
            action.putValue(Action.LARGE_ICON_KEY, new ImageIcon(SeriesViewerFactory.class.getResource("/icon/32x32/img-import.png")));
            final JButton btnImport = new JButton(action);
            btnImport.setToolTipText("Open images");
            add(btnImport);
        }
    }
}
