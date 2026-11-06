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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.data.CIELab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.wsi.model.PyramidLevel.TileKey;

/**
 * Builds the slides of a whole slide series from the headers of its instances, without reading any
 * pixel data (PS3.3 A.32.8, C.8.12.4).
 */
public final class SlideBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlideBuilder.class);

  private static final String TILED_FULL = "TILED_FULL";

  /** Identifier of the single optical path of an instance without Optical Path Sequence. */
  static final String DEFAULT_OPTICAL_PATH = "";

  private record MatrixSize(int columns, int rows) {}

  private record SparseFrame(
      int column, int row, double zOffset, String opticalPath, FrameRef ref) {}

  private SlideBuilder() {}

  public static boolean isWholeSlide(Attributes dcm) {
    return UID.VLWholeSlideMicroscopyImageStorage.equals(dcm.getString(Tag.SOPClassUID));
  }

  /**
   * One slide per pyramid of the series, the largest first; instances of another SOP class are
   * ignored.
   */
  public static List<Slide> build(Collection<Attributes> headers) {
    List<AssociatedImage> associated = new ArrayList<>();
    Map<String, List<Attributes>> pyramids = new LinkedHashMap<>();
    for (Attributes dcm : headers) {
      if (!isWholeSlide(dcm)) {
        continue;
      }
      WsiImageType type = WsiImageType.of(dcm);
      if (type == WsiImageType.VOLUME) {
        pyramids.computeIfAbsent(pyramidKey(dcm), _ -> new ArrayList<>()).add(dcm);
      } else {
        associated.add(AssociatedImage.of(type, dcm));
      }
    }
    return pyramids.values().stream()
        .map(instances -> buildSlide(instances, associated))
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingLong((Slide s) -> (long) s.width() * s.height()).reversed())
        .toList();
  }

  // Pyramid UID (0008,0019) is absent from objects written before it was introduced: their levels
  // are grouped by frame of reference, as all levels of one pyramid share it
  private static String pyramidKey(Attributes dcm) {
    String pyramid = dcm.getString(Tag.PyramidUID);
    if (pyramid != null) {
      return pyramid;
    }
    return "for:" + Objects.requireNonNullElse(dcm.getString(Tag.FrameOfReferenceUID), "");
  }

  private static Slide buildSlide(List<Attributes> instances, List<AssociatedImage> associated) {
    Map<MatrixSize, List<Attributes>> bySize = new HashMap<>();
    for (Attributes dcm : instances) {
      int columns = dcm.getInt(Tag.TotalPixelMatrixColumns, 0);
      int rows = dcm.getInt(Tag.TotalPixelMatrixRows, 0);
      if (columns <= 0 || rows <= 0 || dcm.getInt(Tag.Columns, 0) <= 0) {
        LOGGER.warn("Whole slide level without matrix size: {}", dcm.getString(Tag.SOPInstanceUID));
        continue;
      }
      bySize.computeIfAbsent(new MatrixSize(columns, rows), _ -> new ArrayList<>()).add(dcm);
    }
    if (bySize.isEmpty()) {
      return null;
    }
    List<MatrixSize> sizes =
        bySize.keySet().stream()
            .sorted(Comparator.comparingInt(MatrixSize::columns).reversed())
            .toList();
    MatrixSize baseSize = sizes.getFirst();
    List<PyramidLevel> levels = new ArrayList<>(sizes.size());
    for (MatrixSize size : sizes) {
      levels.add(buildLevel(levels.size(), bySize.get(size), baseSize));
    }
    Attributes first = instances.getFirst();
    Double power =
        instances.stream()
            .map(SlideBuilder::objectiveLensPower)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    return new Slide(
        first.getString(Tag.PyramidUID), levels, associated, power, background(instances));
  }

  private static PyramidLevel buildLevel(int index, List<Attributes> instances, MatrixSize base) {
    Attributes first = instances.getFirst();
    int columns = first.getInt(Tag.TotalPixelMatrixColumns, 0);
    int rows = first.getInt(Tag.TotalPixelMatrixRows, 0);
    LevelGeometry geometry =
        new LevelGeometry(
            columns,
            rows,
            first.getInt(Tag.Columns, 0),
            first.getInt(Tag.Rows, 0),
            pixelSpacing(first, columns, rows));

    Set<String> opticalPaths = new LinkedHashSet<>();
    List<FullTiling> fullTilings = new ArrayList<>();
    List<SparseFrame> sparseFrames = new ArrayList<>();
    for (List<Attributes> frameSet : frameSets(instances)) {
      Attributes head = frameSet.getFirst();
      List<String> paths = opticalPaths(head);
      opticalPaths.addAll(paths);
      if (isTiledFull(head)) {
        fullTilings.add(fullTiling(frameSet, paths, geometry));
      } else {
        frameSet.forEach(part -> collectSparseFrames(part, paths, geometry, sparseFrames));
      }
    }
    sparseFrames.forEach(f -> opticalPaths.add(f.opticalPath()));
    List<String> pathList = List.copyOf(opticalPaths);
    List<Double> zOffsets =
        List.copyOf(new TreeSet<>(sparseFrames.stream().map(SparseFrame::zOffset).toList()));

    Map<TileKey, FrameRef> sparseTiles = new HashMap<>();
    for (SparseFrame f : sparseFrames) {
      TileKey key =
          new TileKey(
              f.column(),
              f.row(),
              zOffsets.indexOf(f.zOffset()),
              pathList.indexOf(f.opticalPath()));
      if (sparseTiles.putIfAbsent(key, f.ref()) != null) {
        LOGGER.debug("Second frame for tile {}, kept the first: {}", key, f.ref());
      }
    }
    int focalPlanes =
        Math.max(
            Math.max(1, zOffsets.size()),
            fullTilings.stream().mapToInt(FullTiling::focalPlanes).max().orElse(1));

    return new PyramidLevel(
        index,
        geometry,
        (double) base.columns() / columns,
        (double) base.rows() / rows,
        pathList,
        focalPlanes,
        fullTilings,
        sparseTiles);
  }

  /** Instances grouped by concatenation; an instance outside a concatenation is its own group. */
  private static Collection<List<Attributes>> frameSets(List<Attributes> instances) {
    Map<String, List<Attributes>> sets = new LinkedHashMap<>();
    for (Attributes dcm : instances) {
      String key =
          Objects.requireNonNullElseGet(
              dcm.getString(Tag.ConcatenationUID), () -> dcm.getString(Tag.SOPInstanceUID));
      sets.computeIfAbsent(key, _ -> new ArrayList<>()).add(dcm);
    }
    sets.values()
        .forEach(s -> s.sort(Comparator.comparingInt(d -> d.getInt(Tag.InConcatenationNumber, 0))));
    return sets.values();
  }

  // Without Dimension Organization Type the frames are TILED_SPARSE, which requires the Plane
  // Position (Slide) Sequence per frame (PS3.3 A.32.8). Lenient: frames without it can only be
  // laid out by their number, so they are read as TILED_FULL
  private static boolean isTiledFull(Attributes dcm) {
    if (TILED_FULL.equals(dcm.getString(Tag.DimensionOrganizationType))) {
      return true;
    }
    Attributes firstFrame = dcm.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence);
    return firstFrame == null
        || firstFrame.getNestedDataset(Tag.PlanePositionSlideSequence) == null;
  }

  private static FullTiling fullTiling(
      List<Attributes> parts, List<String> paths, LevelGeometry geometry) {
    List<FullTiling.Part> list = new ArrayList<>(parts.size());
    int runningOffset = 0;
    for (Attributes dcm : parts) {
      int frames = dcm.getInt(Tag.NumberOfFrames, 1);
      int offset = dcm.getInt(Tag.ConcatenationFrameOffsetNumber, runningOffset);
      list.add(new FullTiling.Part(dcm.getString(Tag.SOPInstanceUID), offset, frames));
      runningOffset = offset + frames;
    }
    FullTiling tiling =
        new FullTiling(
            list,
            paths,
            parts.getFirst().getInt(Tag.TotalPixelMatrixFocalPlanes, 1),
            Math.ceilDiv(geometry.totalColumns(), geometry.tileWidth()),
            Math.ceilDiv(geometry.totalRows(), geometry.tileHeight()));
    if (tiling.numberOfFrames() != tiling.expectedFrames()) {
      LOGGER.warn(
          "TILED_FULL level {}x{} has {} frames, {} expected",
          geometry.totalColumns(),
          geometry.totalRows(),
          tiling.numberOfFrames(),
          tiling.expectedFrames());
    }
    return tiling;
  }

  private static void collectSparseFrames(
      Attributes dcm, List<String> paths, LevelGeometry geometry, List<SparseFrame> out) {
    String uid = dcm.getString(Tag.SOPInstanceUID);
    Sequence perFrame = dcm.getSequence(Tag.PerFrameFunctionalGroupsSequence);
    if (perFrame == null) {
      LOGGER.warn("Sparse whole slide instance without per-frame positions: {}", uid);
      return;
    }
    String sharedPath = opticalPathOf(dcm.getNestedDataset(Tag.SharedFunctionalGroupsSequence));
    int frames = Math.min(dcm.getInt(Tag.NumberOfFrames, 1), perFrame.size());
    for (int i = 0; i < frames; i++) {
      Attributes item = perFrame.get(i);
      Attributes position = item.getNestedDataset(Tag.PlanePositionSlideSequence);
      if (position == null) {
        LOGGER.warn("Frame {} of {} has no position in the total pixel matrix", i + 1, uid);
        continue;
      }
      int column = position.getInt(Tag.ColumnPositionInTotalImagePixelMatrix, 0);
      int row = position.getInt(Tag.RowPositionInTotalImagePixelMatrix, 0);
      if (column < 1 || row < 1) {
        continue;
      }
      String path = opticalPathOf(item);
      if (path == null) {
        path = sharedPath == null ? paths.getFirst() : sharedPath;
      }
      // Scanners write tiles on the grid of the tile size; a position off the grid snaps to the
      // tile that holds its top left corner
      out.add(
          new SparseFrame(
              (column - 1) / geometry.tileWidth(),
              (row - 1) / geometry.tileHeight(),
              position.getDouble(Tag.ZOffsetInSlideCoordinateSystem, 0.0),
              path,
              new FrameRef(uid, i + 1)));
    }
  }

  private static String opticalPathOf(Attributes functionalGroup) {
    if (functionalGroup == null) {
      return null;
    }
    Attributes id = functionalGroup.getNestedDataset(Tag.OpticalPathIdentificationSequence);
    return id == null ? null : id.getString(Tag.OpticalPathIdentifier);
  }

  // Lenient: the Optical Path Sequence is Type 1 (PS3.3 C.8.12.5); without it the instance is read
  // as one unnamed optical path
  private static List<String> opticalPaths(Attributes dcm) {
    Sequence seq = dcm.getSequence(Tag.OpticalPathSequence);
    if (seq == null || seq.isEmpty()) {
      return List.of(DEFAULT_OPTICAL_PATH);
    }
    return seq.stream()
        .map(
            item ->
                Objects.requireNonNullElse(
                    item.getString(Tag.OpticalPathIdentifier), DEFAULT_OPTICAL_PATH))
        .distinct()
        .toList();
  }

  private static Double objectiveLensPower(Attributes dcm) {
    Sequence seq = dcm.getSequence(Tag.OpticalPathSequence);
    if (seq == null) {
      return null;
    }
    return seq.stream()
        .map(item -> item.getDouble(Tag.ObjectiveLensPower, 0.0))
        .filter(p -> p > 0.0)
        .findFirst()
        .orElse(null);
  }

  /**
   * Recommended Absent Pixel CIELab Value (0048,0015) of the first level that has it; white
   * otherwise, the glass of a brightfield slide.
   */
  private static Color background(List<Attributes> instances) {
    for (Attributes dcm : instances) {
      int[] lab = dcm.getInts(Tag.RecommendedAbsentPixelCIELabValue);
      if (lab != null && lab.length == 3) {
        if (lab[1] == 0 && lab[2] == 0) {
          // Lenient: a* and b* written as 0 instead of their encoded neutral 0x8080 (PS3.3
          // C.10.7.1.1), as in the Imaging Data Commons slides; read as neutral, since -128 / -128
          // is a saturated cyan that no slide background has
          lab = new int[] {lab[0], 0x8080, 0x8080};
        }
        int[] rgb = CIELab.dicomLab2rgb(lab);
        if (rgb.length == 3) {
          return new Color(rgb[0], rgb[1], rgb[2]);
        }
      }
    }
    return Color.WHITE;
  }

  /** From the Pixel Measures functional group, else from the imaged volume size in mm. */
  private static PixelSpacing pixelSpacing(Attributes dcm, int columns, int rows) {
    Attributes shared = dcm.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
    Attributes measures =
        shared == null ? null : shared.getNestedDataset(Tag.PixelMeasuresSequence);
    double[] spacing = measures == null ? null : measures.getDoubles(Tag.PixelSpacing);
    if (spacing != null && spacing.length >= 2 && spacing[0] > 0 && spacing[1] > 0) {
      return new PixelSpacing(spacing[0], spacing[1]);
    }
    double width = dcm.getDouble(Tag.ImagedVolumeWidth, 0.0);
    double height = dcm.getDouble(Tag.ImagedVolumeHeight, 0.0);
    if (width > 0 && height > 0) {
      return new PixelSpacing(height / rows, width / columns);
    }
    return null;
  }
}
