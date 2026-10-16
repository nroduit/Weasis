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
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.opencv.data.PlanarImage;

/**
 * Filter operation that applies a kernel-based image filter.
 *
 * <p>This operation requires a {@link KernelData} parameter to be set via {@link #P_KERNEL_DATA}.
 * If no valid kernel is provided, the source image is returned unchanged.
 */
public final class FilterOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("FilterOperation.title");

  /** Parameter key for filter kernel (KernelData, required). */
  public static final String P_KERNEL_DATA = "kernel"; // NON-NLS

  public FilterOp() {
    setName(OP_NAME);
  }

  public FilterOp(FilterOp op) {
    super(op);
  }

  @Override
  public FilterOp copy() {
    return new FilterOp(this);
  }

  // The input itself or a new image
  @Override
  protected boolean ownsOutput() {
    return true;
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    KernelData kernel = (KernelData) params.get(P_KERNEL_DATA);

    PlanarImage result =
        kernel != null && !kernel.equals(KernelData.NONE)
            ? CvUtil.filter(source.toMat(), kernel)
            : source;
    params.put(Param.OUTPUT_IMG, result);
  }
}
