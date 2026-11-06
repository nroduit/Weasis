/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.source;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.Transcoder;
import org.weasis.dicom.wsi.model.FrameRef;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.opencv.data.PlanarImage;

/**
 * Tiles of a DICOM slide: each tile is one frame, decoded from its encoded bytes by the DICOM
 * reader, so every transfer syntax and colour model Weasis reads is handled the same way.
 */
public final class DicomTileSource implements TileSource {

  private final Slide slide;
  private final FrameStore store;

  /**
   * @param store the frames of the slide instances, owned by the caller
   */
  public DicomTileSource(Slide slide, FrameStore store) {
    this.slide = Objects.requireNonNull(slide);
    this.store = Objects.requireNonNull(store);
  }

  @Override
  public Slide slide() {
    return slide;
  }

  @Override
  public Optional<PlanarImage> readTile(TileId tile) throws IOException {
    PyramidLevel level = slide.levels().get(tile.level());
    Optional<FrameRef> frame =
        level.frame(tile.column(), tile.row(), tile.focalPlane(), tile.opticalPath());
    return frame.isEmpty() ? Optional.empty() : Optional.ofNullable(decode(frame.get()));
  }

  @Override
  public Optional<PlanarImage> readAssociatedImage(WsiImageType type) throws IOException {
    var image = slide.associatedImage(type);
    if (image.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(decode(new FrameRef(image.get().sopInstanceUID(), 1)));
  }

  // A reader per frame: DicomImageReader keeps per-input state and is not thread-safe, and building
  // one costs nothing next to the decoding
  private PlanarImage decode(FrameRef frame) throws IOException {
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try {
      reader.setInput(store.frames(frame.sopInstanceUID()));
      return reader.getPlanarImage(frame.frameNumber() - 1, null);
    } finally {
      reader.dispose();
    }
  }
}
