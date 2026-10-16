/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import org.weasis.core.Messages;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Image flip operation that supports horizontal flipping around the y-axis.
 *
 * <p>This operation applies a horizontal flip transformation to the source image when enabled. The
 * flip behavior is controlled by the {@link #P_FLIP} parameter.
 */
public final class FlipOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("FlipOperation.title");

  /**
   * Parameter key for horizontal flip control.
   *
   * <p>Type: {@link Boolean} (required) Default: {@code false}
   */
  public static final String P_FLIP = "flip"; // NON-NLS

  private static final int HORIZONTAL_FLIP_CODE = 1;

  public FlipOp() {
    setName(OP_NAME);
  }

  public FlipOp(FlipOp op) {
    super(op);
  }

  @Override
  public FlipOp copy() {
    return new FlipOp(this);
  }

  // The input itself or a new image
  @Override
  protected boolean ownsOutput() {
    return true;
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result =
        LangUtil.nullToFalse((Boolean) params.get(P_FLIP))
            ? ImageTransformer.flip(source.toMat(), HORIZONTAL_FLIP_CODE)
            : source;
    params.put(Param.OUTPUT_IMG, result);
  }
}
