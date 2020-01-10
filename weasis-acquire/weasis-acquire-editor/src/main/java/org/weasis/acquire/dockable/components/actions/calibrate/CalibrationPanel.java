/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.calibrate;

import javax.swing.JLabel;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.EditionToolFactory;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.graphics.CalibrationGraphic;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerToolBar;

public class CalibrationPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 3956795043244254606L;

    public static final CalibrationGraphic CALIBRATION_LINE_GRAPHIC = new CalibrationGraphic();

    public CalibrationPanel() {
        add(new JLabel(Messages.getString("CalibrationPanel.draw_line"))); //$NON-NLS-1$
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        EventManager.getInstance().getAction(EditionToolFactory.DRAW_EDITON, ComboItemListener.class)
            .ifPresent(a -> a.setSelectedItem(CalibrationPanel.CALIBRATION_LINE_GRAPHIC));

        ImageViewerPlugin<ImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
        if (container != null) {
            final ViewerToolBar<?> toolBar = container.getViewerToolBar();
            if (toolBar != null) {
                String cmd = EditionToolFactory.EDITON.cmd();
                MouseActions mouseActions = EventManager.getInstance().getMouseActions();
                if (!cmd.equals(mouseActions.getLeft())) {
                    setLastActionCommand(mouseActions.getLeft());
                    mouseActions.setAction(MouseActions.T_LEFT, cmd);
                    container.setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.T_LEFT, cmd);
                }
            }
        }
    }
}
