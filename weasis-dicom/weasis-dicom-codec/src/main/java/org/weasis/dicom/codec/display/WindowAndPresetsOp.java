/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.util.Map;
import java.util.Optional;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

public class WindowAndPresetsOp extends WindowOp {

  public static final String P_PR_ELEMENT = "pr.element";

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    OpEvent type = event.getEventType();
    if (OpEvent.IMAGE_CHANGE.equals(type)) {
      setParam(P_IMAGE_ELEMENT, event.getImage());
      removeParam(P_PR_ELEMENT);
    } else if (OpEvent.RESET_DISPLAY.equals(type) || OpEvent.SERIES_CHANGE.equals(type)) {
      ImageElement img = event.getImage();
      setParam(P_IMAGE_ELEMENT, img);
      PrDicomObject pr = (PrDicomObject) getParam(P_PR_ELEMENT);
      removeParam(P_PR_ELEMENT);
      if (img != null) {
        if (!img.isImageAvailable()) {
          // Ensure to load image before calling the default preset that requires pixel min and max
          img.getImage();
        }

        boolean pixelPadding =
            LangUtil.getNULLtoTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
        PresetWindowLevel preset = null;
        if (img instanceof DicomImageElement imageElement) {
          DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
          if (pr != null) {
            imageElement.getPresetList(wlp, true);
          }
          preset = imageElement.getDefaultPreset(wlp);
        }
        setPreset(preset, img, pixelPadding);
      }
    } else if (OpEvent.APPLY_PR.equals(type)) {
      ImageElement img = event.getImage();
      setParam(P_IMAGE_ELEMENT, img);
      if (img != null) {
        if (!img.isImageAvailable()) {
          // Ensure to load image before calling the default preset that requires pixel min and max
          img.getImage();
        }
        boolean pixelPadding =
            LangUtil.getNULLtoTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
        Map<String, Object> p = event.getParams();
        if (p != null) {
          PRSpecialElement pr =
              Optional.ofNullable(p.get(ActionW.PR_STATE.cmd()))
                  .filter(PRSpecialElement.class::isInstance)
                  .map(PRSpecialElement.class::cast)
                  .orElse(null);
          setParam(P_PR_ELEMENT, pr == null ? null : pr.getPrDicomObject());

          PresetWindowLevel preset = (PresetWindowLevel) p.get(ActionW.PRESET.cmd());
          if (preset == null && img instanceof DicomImageElement imageElement) {
            DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
            preset = imageElement.getDefaultPreset(wlp);
          }
          setPreset(preset, img, pixelPadding);
        }
      }
    }
  }

  private void setPreset(PresetWindowLevel preset, ImageElement img, boolean pixelPadding) {
    boolean p = preset != null;
    PrDicomObject pr = (PrDicomObject) getParam(P_PR_ELEMENT);
    setParam(ActionW.PRESET.cmd(), preset);
    setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    DefaultWlPresentation wlp = new DefaultWlPresentation(pr, pixelPadding);
    setParam(ActionW.WINDOW.cmd(), p ? preset.getWindow() : img.getDefaultWindow(wlp));
    setParam(ActionW.LEVEL.cmd(), p ? preset.getLevel() : img.getDefaultLevel(wlp));
    setParam(ActionW.LEVEL_MIN.cmd(), img.getMinValue(wlp));
    setParam(ActionW.LEVEL_MAX.cmd(), img.getMaxValue(wlp));
    setParam(ActionW.LUT_SHAPE.cmd(), p ? preset.getLutShape() : img.getDefaultShape(wlp));
  }
}
