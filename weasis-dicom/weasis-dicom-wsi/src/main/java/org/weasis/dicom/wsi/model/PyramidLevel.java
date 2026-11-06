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

import java.awt.Rectangle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One resolution of a slide: a total pixel matrix cut into tiles, each tile resolved to the frame
 * that holds it. A tile of a sparse level that no frame covers is background.
 */
public final class PyramidLevel {

  /** Tile indices, focal plane and optical path indices of a sparse frame. */
  record TileKey(int column, int row, int focalPlane, int opticalPath) {}

  private final int index;
  private final int totalColumns;
  private final int totalRows;
  private final int tileWidth;
  private final int tileHeight;
  private final double downsampleX;
  private final double downsampleY;
  private final PixelSpacing pixelSpacing;
  private final List<String> opticalPaths;
  private final int focalPlanes;
  private final List<FullTiling> fullTilings;
  private final Map<TileKey, FrameRef> sparseTiles;

  PyramidLevel(
      int index,
      LevelGeometry geometry,
      double downsampleX,
      double downsampleY,
      List<String> opticalPaths,
      int focalPlanes,
      List<FullTiling> fullTilings,
      Map<TileKey, FrameRef> sparseTiles) {
    this.index = index;
    this.totalColumns = geometry.totalColumns();
    this.totalRows = geometry.totalRows();
    this.tileWidth = geometry.tileWidth();
    this.tileHeight = geometry.tileHeight();
    this.pixelSpacing = geometry.pixelSpacing();
    this.downsampleX = downsampleX;
    this.downsampleY = downsampleY;
    this.opticalPaths = List.copyOf(opticalPaths);
    this.focalPlanes = focalPlanes;
    this.fullTilings = List.copyOf(fullTilings);
    this.sparseTiles = Map.copyOf(sparseTiles);
  }

  /** Position in the pyramid, 0 for the base (largest) level. */
  public int index() {
    return index;
  }

  public int totalColumns() {
    return totalColumns;
  }

  public int totalRows() {
    return totalRows;
  }

  public int tileWidth() {
    return tileWidth;
  }

  public int tileHeight() {
    return tileHeight;
  }

  public int tilesAcross() {
    return Math.ceilDiv(totalColumns, tileWidth);
  }

  public int tilesDown() {
    return Math.ceilDiv(totalRows, tileHeight);
  }

  /**
   * The area of a tile in the pixels of this level, clipped to the matrix: an edge tile is stored
   * at full tile size and its part beyond the matrix is padding.
   */
  public Rectangle tileBounds(int column, int row) {
    int x = column * tileWidth;
    int y = row * tileHeight;
    return new Rectangle(
        x, y, Math.min(tileWidth, totalColumns - x), Math.min(tileHeight, totalRows - y));
  }

  /** Base level pixels per pixel of this level, horizontally. */
  public double downsampleX() {
    return downsampleX;
  }

  /** Base level pixels per pixel of this level, vertically. */
  public double downsampleY() {
    return downsampleY;
  }

  public Optional<PixelSpacing> pixelSpacing() {
    return Optional.ofNullable(pixelSpacing);
  }

  /** Optical path identifiers, the order defining the optical path index. */
  public List<String> opticalPaths() {
    return opticalPaths;
  }

  public int focalPlanes() {
    return focalPlanes;
  }

  public boolean isSparse() {
    return !sparseTiles.isEmpty();
  }

  /** The frame of a tile on the first focal plane and the first optical path. */
  public Optional<FrameRef> frame(int column, int row) {
    return frame(column, row, 0, 0);
  }

  public Optional<FrameRef> frame(int column, int row, int focalPlane, int opticalPath) {
    if (column < 0
        || row < 0
        || column >= tilesAcross()
        || row >= tilesDown()
        || focalPlane < 0
        || opticalPath < 0
        || opticalPath >= opticalPaths.size()) {
      return Optional.empty();
    }
    FrameRef sparse = sparseTiles.get(new TileKey(column, row, focalPlane, opticalPath));
    if (sparse != null) {
      return Optional.of(sparse);
    }
    String pathId = opticalPaths.get(opticalPath);
    for (FullTiling tiling : fullTilings) {
      Optional<FrameRef> frame = tiling.frame(column, row, focalPlane, pathId);
      if (frame.isPresent()) {
        return frame;
      }
    }
    return Optional.empty();
  }

  /**
   * The position of a frame in the pixel matrix of this level, as the top-left pixel of its tile:
   * what a FRAME-relative coordinate of an annotation is measured from.
   */
  public Optional<java.awt.Point> frameOrigin(String sopInstanceUID, int frameNumber) {
    FrameRef wanted = new FrameRef(sopInstanceUID, frameNumber);
    for (int path = 0; path < opticalPaths.size(); path++) {
      for (int plane = 0; plane < focalPlanes; plane++) {
        for (int row = 0; row < tilesDown(); row++) {
          for (int col = 0; col < tilesAcross(); col++) {
            if (frame(col, row, plane, path).filter(wanted::equals).isPresent()) {
              return Optional.of(new java.awt.Point(col * tileWidth, row * tileHeight));
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  /** SOP Instance UIDs of the instances holding the frames of this level. */
  public Set<String> instanceUIDs() {
    Set<String> uids = new LinkedHashSet<>();
    fullTilings.forEach(t -> t.parts().forEach(p -> uids.add(p.sopInstanceUID())));
    sparseTiles.values().forEach(f -> uids.add(f.sopInstanceUID()));
    return uids;
  }

  @Override
  public String toString() {
    return "Level %d: %dx%d, tiles %dx%d, downsample %.2f"
        .formatted(index, totalColumns, totalRows, tileWidth, tileHeight, downsampleX);
  }
}
