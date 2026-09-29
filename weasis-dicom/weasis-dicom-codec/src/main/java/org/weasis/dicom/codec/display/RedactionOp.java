/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.Redaction;
import org.weasis.opencv.data.PlanarImage;

/**
 * Burns the redaction regions into the pixels, so hidden areas do not survive in any downstream
 * copy: a screenshot, an animation frame or a request payload all read the masked image.
 *
 * <p>Distinct from {@code MaskOp}, which dims everything outside a rectangle for cropping.
 */
public class RedactionOp extends AbstractOp {

  public static final String OP_NAME = OpManager.REDACTION_OP_NAME;

  /** Whether the redaction is applied. Boolean value, default true. */
  public static final String P_SHOW = "show"; // NON-NLS

  /** The regions to burn, as a {@link Redaction.Mask}. */
  public static final String P_MASK = "mask"; // NON-NLS

  /** The image the regions belong to, used to resolve them again at each pass. */
  public static final String P_IMAGE = "image"; // NON-NLS

  /** The series of that image. */
  public static final String P_SERIES = "series"; // NON-NLS

  public RedactionOp() {
    setName(OP_NAME);
    setParam(P_SHOW, Boolean.TRUE);
  }

  public RedactionOp(RedactionOp op) {
    super(op);
  }

  @Override
  public RedactionOp copy() {
    return new RedactionOp(this);
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    OpEvent type = event.eventType();
    if (OpEvent.IMAGE_CHANGE.equals(type) || OpEvent.RESET_DISPLAY.equals(type)) {
      setParam(P_IMAGE, event.image());
      setParam(P_SERIES, event.series());
      setParam(P_MASK, Redaction.effective(event.image(), event.series()));
    }
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result = source;
    Redaction.Mask mask = mask();
    if (!Boolean.FALSE.equals(params.get(P_SHOW)) && mask != null && !mask.isEmpty()) {
      result = RedactionRenderer.burn(source.toMat(), mask);
    }
    params.put(Param.OUTPUT_IMG, result);
  }

  /**
   * The regions of this pass. They are resolved again from the image when one is known, because a
   * device entry of the library depends on the mask in force: a copy of this operation rendering an
   * export under a stricter profile must hide what that profile hides, not what the screen hid.
   */
  private Redaction.Mask mask() {
    if (params.get(P_IMAGE) instanceof ImageElement image) {
      return Redaction.effective(
          image, params.get(P_SERIES) instanceof MediaSeries<?> series ? series : null);
    }
    return params.get(P_MASK) instanceof Redaction.Mask mask ? mask : null;
  }
}
