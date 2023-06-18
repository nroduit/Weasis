/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public abstract class AbstractAcquireAction extends AcquireObject implements AcquireAction {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAcquireAction.class);

  protected final AcquireActionPanel centralPanel;

  protected final AcquireActionButtonsPanel panel;

  protected AbstractAcquireAction(AcquireActionButtonsPanel panel) {
    this.panel = panel;
    this.centralPanel = newCentralPanel();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Cmd cmd = Cmd.valueOf(e.getActionCommand());
    switch (cmd) {
      case INIT -> panel.setSelected((AcquireActionButton) e.getSource());
      case VALIDATE -> validate();
      case CANCEL -> cancel();
      case RESET -> reset(e);
      default -> LOGGER.warn("Unknown command : {}", e.getActionCommand());
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
  public void cancel() {
    AcquireImageInfo imageInfo = getImageInfo();
    imageInfo.removeLayer(getView());
    boolean dirty = imageInfo.isDirty();

    if (dirty) {
      centralPanel.initValues(imageInfo, imageInfo.getCurrentValues());
    }
  }

  @Override
  public void reset(ActionEvent e) {
    AcquireImageInfo imageInfo = getImageInfo();
    imageInfo.removeLayer(getView());
    boolean dirty = imageInfo.isDirtyFromDefault();

    if (dirty) {
      int confirm =
          JOptionPane.showConfirmDialog(
              (Component) e.getSource(),
              Messages.getString("AbstractAcquireAction.reset_msg"),
              "",
              JOptionPane.YES_NO_OPTION);
      if (confirm == 0) {
        centralPanel.initValues(imageInfo, imageInfo.getDefaultValues());
      }
    }
  }

  @Override
  public AcquireActionPanel getCentralPanel() {
    return centralPanel;
  }

  public abstract AcquireActionPanel newCentralPanel();
}
