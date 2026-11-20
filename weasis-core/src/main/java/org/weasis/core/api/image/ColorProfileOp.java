/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import org.opencv.core.CvType;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.ColorTransform;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Converts the displayed colors from the ICC profile of the image, or of the presentation state
 * applied to it, to the profile of the display (PS3.4 N.2: the color pipeline ends in the profile
 * connection space, the display converts to its own profile). It is the last operation of the
 * chain, so it runs on the pixels shown, after fusion, overlays and zoom. Only 8-bit color output
 * is converted: grayscale is consistent through the display calibration (PS3.14), not ICC.
 */
public final class ColorProfileOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("ColorProfileOperation.title");

  /** Profile of the colors entering the operation. ({@link IccProfile}, Optional: sRGB) */
  public static final String P_SOURCE_PROFILE = "icc.source.profile"; // NON-NLS

  /**
   * Profile to convert to, for an export written in a known space. ({@link IccProfile}, Optional:
   * the display profile)
   */
  public static final String P_TARGET_PROFILE = "icc.target.profile"; // NON-NLS

  /**
   * Device id of the monitor the pixels are shown on, whose profile is the target when it has one
   * of its own. (String, Optional: the workstation profile)
   */
  public static final String P_DISPLAY_ID = "icc.display.id"; // NON-NLS

  /**
   * The colors entering the operation come from a color map, not from the image: they are sRGB,
   * whatever profile the image carries. Set with the map of {@link PseudoColorOp}. (Boolean,
   * Optional: false)
   */
  public static final String P_MAPPED_COLORS = "icc.mapped.colors"; // NON-NLS

  public ColorProfileOp() {
    setName(OP_NAME);
  }

  public ColorProfileOp(ColorProfileOp op) {
    super(op);
  }

  @Override
  public ColorProfileOp copy() {
    return new ColorProfileOp(this);
  }

  // The input itself or a new image
  @Override
  protected boolean ownsOutput() {
    return true;
  }

  /** Also moves when color management is switched or the display profile changes. */
  @Override
  public long getRevision() {
    return super.getRevision() + ColorManagement.generation();
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    switch (event.eventType()) {
      case IMAGE_CHANGE, SERIES_CHANGE, RESET_DISPLAY ->
          setParam(P_SOURCE_PROFILE, profileOf(event.image()));
      case APPLY_PR -> {
        // A presentation state with the ICC Profile module (Color, Pseudo-Color, Blending)
        // describes the colors it produces; otherwise the colors are those of the image.
        Object pr = event.params().get(ActionW.PR_STATE.cmd());
        IccProfile profile = pr instanceof Taggable t ? profileOf(t) : null;
        setParam(P_SOURCE_PROFILE, profile == null ? profileOf(event.image()) : profile);
        // A palette of the state is a color map, unless the state describes its colors
        if (event.params().get(PseudoColorOp.P_LUT) != null) {
          setParam(
              P_MAPPED_COLORS,
              profile == null && PseudoColorOp.mapsColors(event.params().get(PseudoColorOp.P_LUT)));
        }
      }
    }
  }

  /**
   * Converts the colors of {@code media} to sRGB, for an export: a file without an embedded profile
   * is read as sRGB, whatever the display of the machine that wrote it.
   */
  public static ColorProfileOp toSrgb(Taggable media) {
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(P_SOURCE_PROFILE, profileOf(media));
    op.setParam(P_TARGET_PROFILE, IccProfile.SRGB);
    return op;
  }

  /** The profile a media declares, or null (sRGB). */
  public static IccProfile profileOf(Taggable taggable) {
    return taggable != null && taggable.getTagValue(TagW.IccProfile) instanceof IccProfile p
        ? p
        : null;
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    params.put(Param.OUTPUT_IMG, convert(source));
  }

  private PlanarImage convert(PlanarImage source) {
    if (source == null || source.type() != CvType.CV_8UC3) {
      return source;
    }
    IccProfile profile =
        Boolean.TRUE.equals(params.get(P_MAPPED_COLORS))
            ? null
            : (IccProfile) params.get(P_SOURCE_PROFILE);
    IccProfile target = (IccProfile) params.get(P_TARGET_PROFILE);
    ColorTransform transform;
    if (target == null) {
      transform = ColorManagement.toDisplay(profile, (String) params.get(P_DISPLAY_ID));
    } else {
      transform =
          ColorManagement.isEnabled()
              ? ColorManagement.to(profile, target)
              : ColorTransform.IDENTITY;
    }
    if (transform.isIdentity()) {
      return source;
    }
    ImageCV result = new ImageCV(source.size(), CvType.CV_8UC3);
    transform.apply(source.toMat(), result);
    return result;
  }
}
