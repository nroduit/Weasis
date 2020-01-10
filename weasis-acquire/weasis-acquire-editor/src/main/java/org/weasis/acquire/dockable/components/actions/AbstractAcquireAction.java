/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.AcquireActionButton;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 *
 */
public abstract class AbstractAcquireAction extends AcquireObject implements AcquireAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAcquireAction.class);

    protected final AcquireActionPanel centralPanel;

    protected final AcquireActionButtonsPanel panel;

    public AbstractAcquireAction(AcquireActionButtonsPanel panel) {
        this.panel = panel;
        this.centralPanel = newCentralPanel();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Cmd cmd = Cmd.valueOf(e.getActionCommand());
        switch (cmd) {
            case INIT:
                panel.setSelected((AcquireActionButton) e.getSource());
                break;
            case VALIDATE:
                validate();
                break;
            case CANCEL:
                cancel();
                break;
            case RESET:
                reset(e);
                break;
            default:
                LOGGER.warn("Unknown command : " + e.getActionCommand()); //$NON-NLS-1$
                break;
        }
    }

    @Override
    public void validate() {
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();
        if (imageInfo != null && view != null) {
            validate(imageInfo, view);
        }
    }

    @Override
    public boolean cancel() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        boolean dirty = imageInfo.isDirty();

        if (dirty) {
            centralPanel.initValues(imageInfo, imageInfo.getCurrentValues());
        }
        return dirty;
    }

    @Override
    public boolean reset(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        boolean dirty = imageInfo.isDirtyFromDefault();

        if (dirty) {
            int confirm = JOptionPane.showConfirmDialog((Component) e.getSource(),
                Messages.getString("AbstractAcquireAction.reset_msg"), //$NON-NLS-1$
                "", JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
            if (confirm == 0) {
                centralPanel.initValues(imageInfo, imageInfo.getDefaultValues());
            }
        }
        return dirty;
    }

    @Override
    public AcquireActionPanel getCentralPanel() {
        return centralPanel;
    }

    public abstract AcquireActionPanel newCentralPanel();
}
