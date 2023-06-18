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

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.acquire.utils.GraphicHelper;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class RectifyOrientationChangeListener extends AcquireObject
    implements ChangeListener, OpValueChanged {

  private final RectifyAction rectifyAction;

  public RectifyOrientationChangeListener(RectifyAction rectifyAction) {
    this.rectifyAction = rectifyAction;
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    JSlider s = (JSlider) e.getSource();
    if (s instanceof AbstractSliderComponent sliderComponent) {
      sliderComponent.updatePanelTitle();
    }
    AcquireImageInfo imageInfo = getImageInfo();
    imageInfo.getNextValues().setOrientation(s.getValue());

    imageInfo.getNextValues().setCropZone(null);
    applyNextValues();
    ViewCanvas<ImageElement> view = getView();
    if (view != null && s.getValueIsAdjusting()) {
      GraphicHelper.newGridLayer(view);
    }
    imageInfo.applyCurrentProcessing(getView());
    rectifyAction.updateCropDisplay();
  }

  @Override
  public void applyNextValues() {
    AcquireImageInfo imageInfo = getImageInfo();
    imageInfo.applyNRotation(getView());
    rectifyAction.updateGraphics(imageInfo);
  }
}
