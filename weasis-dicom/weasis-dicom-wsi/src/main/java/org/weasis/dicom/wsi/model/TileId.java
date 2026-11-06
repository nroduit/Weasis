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

/**
 * A tile of a slide: level index, tile indices, focal plane and optical path indices.
 *
 * @param level the index in {@link Slide#levels()}
 */
public record TileId(int level, int column, int row, int focalPlane, int opticalPath) {

  /** A tile of the first focal plane and the first optical path. */
  public static TileId of(int level, int column, int row) {
    return new TileId(level, column, row, 0, 0);
  }
}
