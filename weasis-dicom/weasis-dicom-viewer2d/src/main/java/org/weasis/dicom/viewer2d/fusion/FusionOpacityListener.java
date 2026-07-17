/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import javax.swing.BoundedRangeModel;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.viewer2d.Messages;

/**
 * Opacity slider whose title shows the real DICOM modality of the layer it controls (e.g. "MR
 * Opacity", "NM Opacity") instead of a fixed CT/PET label. The modality is set per selected view
 * via {@link #setModalityLabel}; the slider value (0–100%) is applied to the given {@link FusionOp}
 * parameter.
 */
public class FusionOpacityListener extends SliderChangeListener {

  private final String param;
  private String modalityLabel;
  private JSliderW slider;

  public FusionOpacityListener(
      Feature<SliderChangeListener> feature, String param, int defaultPercent, String modality) {
    super(feature, 0, 100, defaultPercent, true, 0.1);
    this.param = param;
    this.modalityLabel = modality;
  }

  @Override
  public void stateChanged(BoundedRangeModel model) {
    FusionController.applyParam(param, model.getValue() / 100.0);
  }

  @Override
  public JSliderW createSlider(int labelDivision, boolean displayValueInTitle) {
    slider = super.createSlider(labelDivision, displayValueInTitle);
    updateSliderProperties(slider);
    return slider;
  }

  @Override
  public void updateSliderProperties(JSliderW s) {
    String title =
        modalityLabel
            + StringUtil.SPACE
            + Messages.getString("FusionTool.opacity")
            + StringUtil.COLON_AND_SPACE
            + getSliderValue()
            + "%"; // NON-NLS
    SliderChangeListener.updateSliderProperties(s, title);
  }

  /**
   * Updates the modality shown in the title (and refreshes the slider) when it actually changes.
   */
  public void setModalityLabel(String modality) {
    if (modality != null && !modality.equals(modalityLabel)) {
      modalityLabel = modality;
      if (slider != null) {
        updateSliderProperties(slider);
      }
    }
  }
}
