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
import java.util.Optional;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.opencv.data.PlanarImage;

/**
 * Decoded tiles of one slide, whatever the backend. Reads block and are called from worker threads,
 * concurrently.
 */
public interface TileSource {

  Slide slide();

  /**
   * The tile at full tile size (see {@link org.weasis.dicom.wsi.model.PyramidLevel#tileBounds}),
   * empty for a background tile of a sparse level. The caller releases the image.
   */
  Optional<PlanarImage> readTile(TileId tile) throws IOException;

  /** The thumbnail, overview or label image, empty when the slide has none. */
  Optional<PlanarImage> readAssociatedImage(WsiImageType type) throws IOException;
}
