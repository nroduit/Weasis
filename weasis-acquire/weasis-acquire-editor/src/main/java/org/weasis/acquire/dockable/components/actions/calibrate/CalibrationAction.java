/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.calibrate;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class CalibrationAction extends AbstractAcquireAction {

  public CalibrationAction(AcquireActionButtonsPanel panel) {
    super(panel);
  }

  @Override
  public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
    imageInfo.removeLayer(view);
    this.centralPanel.restoreLastAction();
  }

  @Override
  public AcquireActionPanel newCentralPanel() {
    return new CalibrationPanel();
  }
}
