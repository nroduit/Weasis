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

import org.weasis.core.api.Messages;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.opencv.data.PlanarImage;

public class FilterOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("FilterOperation.title");

  /**
   * Set the filter kernel (Required parameter).
   *
   * <p>org.weasis.core.api.image.util.KernelData value.
   */
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

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    KernelData kernel = (KernelData) params.get(P_KERNEL_DATA);
    if (kernel != null && !kernel.equals(KernelData.NONE)) {
      result = CvUtil.filter(source.toMat(), kernel);
    }
    params.put(Param.OUTPUT_IMG, result);
  }
}
