/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.util.concurrent.RecursiveAction;
import java.util.function.IntBinaryOperator;

public class CopyPixelsTask extends RecursiveAction {
  private static final int THRESHOLD = 4096;
  private final int start;
  private final int end;
  private final int width;
  private final IntBinaryOperator setPixel;

  CopyPixelsTask(int start, int end, int width, IntBinaryOperator setPixel) {
    this.start = start;
    this.end = end;
    this.width = width;
    this.setPixel = setPixel;
  }

  @Override
  protected void compute() {
    if (end - start <= THRESHOLD) {
      int x = start % width;
      int y = start / width;
      for (int i = start; i < end; i++) {
        setPixel.applyAsInt(x, y);
        if (++x >= width) {
          x = 0;
          y++;
        }
      }
    } else {
      int mid = (start + end) / 2;
      invokeAll(
          new CopyPixelsTask(start, mid, width, setPixel),
          new CopyPixelsTask(mid, end, width, setPixel));
    }
  }
}
