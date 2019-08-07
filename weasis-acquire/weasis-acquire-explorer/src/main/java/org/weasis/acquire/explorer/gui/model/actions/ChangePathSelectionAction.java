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
package org.weasis.acquire.explorer.gui.model.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.media.MediaSource;

public class ChangePathSelectionAction extends AbstractAction {
    private static final long serialVersionUID = -65145837841144613L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangePathSelectionAction.class);

    private final AcquireExplorer mainView;

    public ChangePathSelectionAction(AcquireExplorer acquisitionView) {
        this.mainView = acquisitionView;

        putValue(Action.NAME, " ... "); //$NON-NLS-1$
        putValue(Action.ACTION_COMMAND_KEY, "onChangeRootPath"); //$NON-NLS-1$
        putValue(Action.SHORT_DESCRIPTION, Messages.getString("ChangePathSelectionAction.select_folder")); //$NON-NLS-1$
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MediaSource drive = mainView.getSystemDrive();
        if (drive != null && e.getSource() instanceof Component) {
            String newRootPath = openDirectoryChooser(drive.getPath(), (Component) e.getSource());
            if (newRootPath != null) {
                try {
                    mainView.applyNewPath(newRootPath);
                } catch (Exception ex) {
                    LOGGER.warn(ex.getMessage(), ex);
                }
            }
        }
    }

    public static String openDirectoryChooser(String path, Component parent) {

        JFileChooser fc = new JFileChooser(path);
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setControlButtonsAreShown(true);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int returnVal = fc.showOpenDialog(parent);
        String returnStr = null;

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                returnStr = fc.getSelectedFile().toString();
            } catch (SecurityException e) {
                LOGGER.warn("directory cannot be accessed", e); //$NON-NLS-1$
            }
        }
        return returnStr;
    }
}
