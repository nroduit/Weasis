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
import java.util.function.BiConsumer;

public class CopyPixelsTask extends RecursiveAction {
  private static final int THRESHOLD = 1000;
  private final int start;
  private final int end;
  private final int width;
  private final BiConsumer<Integer, Integer> setPixel;

  CopyPixelsTask(int start, int end, int width, BiConsumer<Integer, Integer> setPixel) {
    this.start = start;
    this.end = end;
    this.width = width;
    this.setPixel = setPixel;
  }

  @Override
  protected void compute() {
    if (end - start <= THRESHOLD) {
      for (int i = start; i < end; i++) {
        int x = i % width;
        int y = i / width;
        setPixel.accept(x, y);
      }
    } else {
      int mid = (start + end) / 2;
      CopyPixelsTask leftTask = new CopyPixelsTask(start, mid, width, setPixel);
      CopyPixelsTask rightTask = new CopyPixelsTask(mid, end, width, setPixel);
      invokeAll(leftTask, rightTask);
    }
  }
}
