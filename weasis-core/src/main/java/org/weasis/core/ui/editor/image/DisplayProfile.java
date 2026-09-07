/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.util.LangUtil;

/**
 * What a re-rendered capture of a 2D view shows, set apart from what the view shows on screen: the
 * same choices as the Display tool (image and its DICOM overlay, shutter and pixel padding, the
 * annotation items, the drawings and cross-lines).
 *
 * @param annotationItems the annotation items shown when {@code annotations} is on
 */
public record DisplayProfile(
    boolean image,
    boolean overlay,
    boolean shutter,
    boolean pixelPadding,
    boolean annotations,
    Set<LayerItem> annotationItems,
    boolean drawings,
    boolean crosslines) {

  /**
   * The annotation items a capture of a 2D view can show; pixel value and loading bar are
   * interactive only.
   */
  public static final List<LayerItem> CAPTURE_ITEMS =
      List.of(
          LayerItem.ANNOTATIONS,
          LayerItem.MIN_ANNOTATIONS,
          LayerItem.SCALE,
          LayerItem.LUT,
          LayerItem.FUSION_LUT,
          LayerItem.IMAGE_ORIENTATION,
          LayerItem.WINDOW_LEVEL,
          LayerItem.ZOOM,
          LayerItem.ROTATION,
          LayerItem.FRAME);

  // Operation names and parameters of the DICOM OverlayOp and ShutterOp (weasis-dicom-codec),
  // which weasis-core cannot import
  static final String OVERLAY_OP = ActionW.IMAGE_OVERLAY.getTitle();
  static final String OVERLAY_SHOW = "overlay"; // NON-NLS
  static final String SHUTTER_OP = ActionW.IMAGE_SHUTTER.getTitle();
  static final String SHUTTER_SHOW = "show"; // NON-NLS

  public DisplayProfile {
    annotationItems =
        annotationItems.isEmpty()
            ? EnumSet.noneOf(LayerItem.class)
            : EnumSet.copyOf(annotationItems);
  }

  /** Whether a capture of {@code view} can show something else than the screen. */
  public static boolean supports(ViewCanvas<?> view) {
    return view instanceof DefaultView2d<?> || view instanceof LiveCaptureView;
  }

  /** The annotation items the Display tool of {@code view} offers. */
  public static List<LayerItem> itemsFor(ViewCanvas<?> view) {
    return view instanceof LiveCaptureView live ? live.getCaptureItems() : CAPTURE_ITEMS;
  }

  /** What {@code view} shows now, as set in the Display tool. */
  public static DisplayProfile of(ViewCanvas<?> view) {
    OpManager ops = view.getDisplayOpManager();
    LayerAnnotation<?> info = view.getInfoLayer();
    Set<LayerItem> items = EnumSet.noneOf(LayerItem.class);
    if (info != null) {
      itemsFor(view).stream().filter(info::getDisplayPreferences).forEach(items::add);
    }
    // A view rendered by other means (3D) has neither an image layer nor display operations
    return new DisplayProfile(
        view.getImageLayer() == null || LangUtil.nullToTrue(view.getImageLayer().getVisible()),
        isOn(ops, OVERLAY_OP, OVERLAY_SHOW, false),
        isOn(ops, SHUTTER_OP, SHUTTER_SHOW, false),
        isOn(ops, WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true),
        info != null && LangUtil.nullToTrue(info.getVisible()),
        items,
        LangUtil.nullToTrue((Boolean) view.getActionValue(ActionW.DRAWINGS.cmd())),
        LangUtil.nullToTrue((Boolean) view.getActionValue(LayerType.CROSSLINES.name())));
  }

  /** The image as displayed, without annotations, drawings or cross-lines. */
  public DisplayProfile imageOnly() {
    return new DisplayProfile(
        image, overlay, shutter, pixelPadding, false, annotationItems, false, false);
  }

  /** Whether {@code view} has the DICOM image options: overlay, shutter and pixel padding. */
  public static boolean hasDicomImageOptions(ViewCanvas<?> view) {
    OpManager ops = view.getDisplayOpManager();
    return ops != null
        && (ops.getNode(OVERLAY_OP).isPresent() || ops.getNode(SHUTTER_OP).isPresent());
  }

  /** An unset parameter takes the default its operation applies. */
  private static boolean isOn(OpManager ops, String op, String param, boolean unset) {
    return ops == null ? unset : ops.getParamValue(op, param, Boolean.class).orElse(unset);
  }
}
