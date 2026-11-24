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

import org.weasis.core.api.image.cv.CvUtil;

/**
 * Whether the arrays of a volume fit on the heap with room left for the rest of the application. An
 * allocation that just fits succeeds and leaves the next small one to fail anywhere, so the volume
 * builders ask before they allocate and map a file when the answer is no.
 */
final class HeapRoom {

  /** Share of the maximum heap kept free: decoded slices, the views, the collector's reserve. */
  static final double RESERVE_SHARE = 0.25;

  private HeapRoom() {}

  /** Whether the bytes fit, counting again after a collection when they do not at first. */
  static boolean fits(long bytes) {
    long max = Runtime.getRuntime().maxMemory();
    if (fits(bytes, available(), max)) {
      return true;
    }
    CvUtil.runGarbageCollectorAndWait(100);
    return fits(bytes, available(), max);
  }

  static boolean fits(long bytes, long available, long maxHeap) {
    return bytes <= available - (long) (maxHeap * RESERVE_SHARE);
  }

  /** The heap the application can still take: the unused part of the maximum heap. */
  static long available() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
  }
}
