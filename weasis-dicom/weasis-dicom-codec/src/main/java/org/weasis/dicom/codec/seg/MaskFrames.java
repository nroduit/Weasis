/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.util.function.Function;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * Helpers around the {@link DicomImageElement#getImage()} → process → release lifecycle of a SEG
 * mask frame.
 *
 * <p>The decoded frames live in the process-wide native image cache, which frees an entry's native
 * buffer as soon as it is evicted or removed — without regard for threads still reading it. A SEG
 * build decodes hundreds of frames back to back and therefore drives that eviction itself, so a
 * frame must be <em>pinned</em> for as long as its pixels are read: reading through an evicted
 * {@code Mat} dereferences a freed pointer and kills the JVM rather than throwing.
 */
public final class MaskFrames {

  private MaskFrames() {}

  /**
   * Decodes {@code frame} and pins it so the native cache cannot free its pixels until the matching
   * {@link #release} call. Returns {@code null} when the frame has no decodable image, in which
   * case {@link #release} must still be called to balance the pin.
   */
  public static PlanarImage acquire(DicomImageElement frame) {
    // Pin first: pinning is independent of the entry being present, so this also covers the frame
    // decoded by the getImage() call below being evicted by a concurrent decode before we read it.
    frame.pinInCache();
    PlanarImage image = frame.getImage();
    return image == null || image.width() <= 0 || image.height() <= 0 ? null : image;
  }

  /**
   * Balances {@link #acquire}. The pixels are freed and the cache entry dropped only once no other
   * reader holds the frame; when one does, the entry is left in the cache for that reader and
   * reclaimed by the normal eviction path afterwards.
   */
  public static void release(DicomImageElement frame, PlanarImage image) {
    frame.unpinFromCache();
    if (frame.isPinnedInCache()) {
      // Another reader is still using these pixels — freeing them now would crash it.
      return;
    }
    ImageConversion.releasePlanarImage(image);
    frame.removeImageFromCache();
  }

  /**
   * Decodes the frame, invokes {@code body} with the decoded image, then guarantees the planar
   * image is released and the cache entry cleared. Returns {@code emptyResult} when the image is
   * missing or has no pixels.
   */
  static <T> T withImage(DicomImageElement frame, T emptyResult, Function<PlanarImage, T> body) {
    PlanarImage image = acquire(frame);
    try {
      return image == null ? emptyResult : body.apply(image);
    } finally {
      release(frame, image);
    }
  }
}
