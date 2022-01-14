/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;

public class ChangePathSelectionAction extends AbstractAction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangePathSelectionAction.class);

  private final AcquireExplorer mainView;

  public ChangePathSelectionAction(AcquireExplorer acquisitionView) {
    this.mainView = acquisitionView;

    putValue(Action.SMALL_ICON, ResourceUtil.getIcon(ActionIcon.MORE_H));
    putValue(Action.ACTION_COMMAND_KEY, "onChangeRootPath");
    putValue(
        Action.SHORT_DESCRIPTION, Messages.getString("ChangePathSelectionAction.select_folder"));
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    MediaSource drive = mainView.getSystemDrive();
    if (drive != null && e.getSource() instanceof Component component) {
      String newRootPath = openDirectoryChooser(drive.getPath(), component);
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
        LOGGER.warn("directory cannot be accessed", e);
      }
    }
    return returnStr;
  }
}
