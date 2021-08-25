/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.RotationOp;

public class RotationActionListener extends AcquireObject
    implements ActionListener, OpValueChanged {
  private int angle;
  private final RectifyAction rectifyAction;

  public RotationActionListener(int angle, RectifyAction rectifyAction) {
    this.angle = angle;
    this.rectifyAction = rectifyAction;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AcquireImageInfo imageInfo = getImageInfo();

    int rotation = (imageInfo.getNextValues().getRotation() + 720 + angle) % 360;
    imageInfo.getNextValues().setRotation(rotation);
    applyNextValues();
    rectifyAction.updateCropDisplay();
    imageInfo.applyCurrentProcessing(getView());
  }

  @Override
  public void applyNextValues() {
    applyNRotation(getImageInfo(), rectifyAction);
  }

  static void applyNRotation(AcquireImageInfo imageInfo, RectifyAction rectifyAction) {
    int rotation = (imageInfo.getNextValues().getFullRotation() + 720) % 360;
    ImageOpNode node = imageInfo.getPostProcessOpManager().getNode(RotationOp.OP_NAME);
    if (node != null) {
      node.clearIOCache();
      node.setParam(RotationOp.P_ROTATE, rotation);
    }
    rectifyAction.updateGraphics(imageInfo);
  }
}
