/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlParams;

public class WindLevelParameters implements WlParams {
  private final double window;
  private final double level;
  private final double levelMin;
  private final double levelMax;
  private final boolean pixelPadding;
  private final boolean inverseLut;
  private final boolean fillOutsideLutRange;
  private final boolean allowWinLevelOnColorImage;
  private final LutShape lutShape;
  private final PresentationStateLut presentationStateLut;

  public WindLevelParameters(ImageElement img, Map<String, Object> params) {
    Objects.requireNonNull(img);

    Double win = null;
    Double lev = null;
    Double levMin = null;
    Double levMax = null;
    LutShape shape = null;
    Boolean padding = null;
    Boolean invLUT = null;
    Boolean fillLutOut = null;
    Boolean wlOnColor = null;
    PresentationStateLut pr = null;

    if (params != null) {
      win = (Double) params.get(ActionW.WINDOW.cmd());
      lev = (Double) params.get(ActionW.LEVEL.cmd());
      levMin = (Double) params.get(ActionW.LEVEL_MIN.cmd());
      levMax = (Double) params.get(ActionW.LEVEL_MAX.cmd());
      shape = (LutShape) params.get(ActionW.LUT_SHAPE.cmd());
      padding = (Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd());
      invLUT = (Boolean) params.get(PseudoColorOp.P_LUT_INVERSE);
      fillLutOut = (Boolean) params.get(WindowOp.P_FILL_OUTSIDE_LUT);
      wlOnColor = (Boolean) params.get(WindowOp.P_APPLY_WL_COLOR);
      pr = (PresentationStateLut) params.get("pr.element");
    }

    this.presentationStateLut = pr;
    this.fillOutsideLutRange = LangUtil.getNULLtoFalse(fillLutOut);
    this.allowWinLevelOnColorImage = LangUtil.getNULLtoFalse(wlOnColor);
    this.pixelPadding = LangUtil.getNULLtoTrue(padding);
    this.inverseLut = LangUtil.getNULLtoFalse(invLUT);
    DefaultWlPresentation wlp = new DefaultWlPresentation(pr, pixelPadding);
    this.window = (win == null) ? img.getDefaultWindow(wlp) : win;
    this.level = (lev == null) ? img.getDefaultLevel(wlp) : lev;
    this.lutShape = (shape == null) ? img.getDefaultShape(wlp) : shape;
    if (levMin == null || levMax == null) {
      this.levelMin = Math.min(level - window / 2.0, img.getMinValue(wlp));
      this.levelMax = Math.max(level + window / 2.0, img.getMaxValue(wlp));
    } else {
      this.levelMin = Math.min(levMin, img.getMinValue(wlp));
      this.levelMax = Math.max(levMax, img.getMaxValue(wlp));
    }
  }

  @Override
  public double getWindow() {
    return window;
  }

  @Override
  public double getLevel() {
    return level;
  }

  @Override
  public double getLevelMin() {
    return levelMin;
  }

  @Override
  public double getLevelMax() {
    return levelMax;
  }

  @Override
  public boolean isPixelPadding() {
    return pixelPadding;
  }

  @Override
  public boolean isInverseLut() {
    return inverseLut;
  }

  @Override
  public boolean isFillOutsideLutRange() {
    return fillOutsideLutRange;
  }

  @Override
  public boolean isAllowWinLevelOnColorImage() {
    return allowWinLevelOnColorImage;
  }

  @Override
  public LutShape getLutShape() {
    return lutShape;
  }

  @Override
  public PresentationStateLut getPresentationState() {
    return presentationStateLut;
  }
}
