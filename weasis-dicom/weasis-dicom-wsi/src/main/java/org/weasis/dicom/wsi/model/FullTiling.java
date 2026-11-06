/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import java.util.List;
import java.util.Optional;

/**
 * Frames of one instance or one concatenation organized as TILED_FULL (PS3.3 C.7.6.17.3): the tile
 * position follows from the frame number, columns first, then rows, then focal planes, then optical
 * paths.
 *
 * @param parts the instances of the concatenation sorted by frame offset, or the single instance
 * @param opticalPaths the optical path identifiers in the order of the Optical Path Sequence
 */
record FullTiling(
    List<Part> parts, List<String> opticalPaths, int focalPlanes, int tilesAcross, int tilesDown) {

  /**
   * @param frameOffset the Concatenation Frame Offset Number, 0 for a single instance
   */
  record Part(String sopInstanceUID, int frameOffset, int numberOfFrames) {}

  Optional<FrameRef> frame(int column, int row, int focalPlane, String opticalPath) {
    int path = opticalPaths.indexOf(opticalPath);
    if (path < 0 || focalPlane >= focalPlanes) {
      return Optional.empty();
    }
    long index =
        (((long) path * focalPlanes + focalPlane) * tilesDown + row) * tilesAcross + column;
    for (Part part : parts) {
      long local = index - part.frameOffset();
      if (local >= 0 && local < part.numberOfFrames()) {
        return Optional.of(new FrameRef(part.sopInstanceUID(), (int) local + 1));
      }
    }
    return Optional.empty();
  }

  int numberOfFrames() {
    return parts.stream().mapToInt(Part::numberOfFrames).sum();
  }

  long expectedFrames() {
    return (long) tilesAcross * tilesDown * focalPlanes * opticalPaths.size();
  }
}
