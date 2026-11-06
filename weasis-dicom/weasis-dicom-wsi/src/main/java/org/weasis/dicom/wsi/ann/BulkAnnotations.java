/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.ann;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;

/**
 * The groups of a Microscopy Bulk Simple Annotations object (PS3.3 C.37.1) read against a slide:
 * each group keeps its points in the pixels of the referenced level and the factor that brings them
 * to the base level. The coordinates stay in the file: a bulk element is memory-mapped, so a group
 * of millions of nuclei costs no heap until it is indexed.
 *
 * <p>Only 2D coordinates with the VOLUME pixel origin are read, the form the converters write; 3D
 * slide coordinates and FRAME origins are skipped with a log line.
 */
public final class BulkAnnotations {
  private static final Logger LOGGER = LoggerFactory.getLogger(BulkAnnotations.class);

  private static final String TWO_D = "2D"; // NON-NLS
  private static final String VOLUME = "VOLUME"; // NON-NLS

  /** Colours of the groups that declare none. */
  private static final Color[] PALETTE = {
    new Color(0, 200, 83),
    new Color(255, 109, 0),
    new Color(41, 121, 255),
    new Color(213, 0, 249),
    new Color(255, 214, 0),
    new Color(0, 184, 212),
    new Color(255, 23, 68),
    new Color(118, 255, 3)
  };

  /**
   * The graphic types of PS3.3 C.37.1.2 with the points one item takes; 0 for a variable number.
   */
  public enum GraphicType {
    POINT(1),
    POLYLINE(0),
    POLYGON(0),
    ELLIPSE(4),
    RECTANGLE(4);

    private final int points;

    GraphicType(int points) {
      this.points = points;
    }

    /** Points per item, 0 when the index list gives them. */
    public int pointsPerItem() {
      return points;
    }

    static Optional<GraphicType> of(String value) {
      if (value == null) {
        return Optional.empty();
      }
      try {
        return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
  }

  /** The points of a group, two values each, as read from the file. */
  public interface Points {
    int count();

    double x(int point);

    double y(int point);
  }

  /**
   * One annotation group.
   *
   * @param number the Annotation Group Number, from 1
   * @param starts the first point of each item, with one more entry for the end
   * @param scaleX base level pixels per pixel of the referenced level
   */
  public record Group(
      int number,
      String uid,
      String label,
      String description,
      GraphicType type,
      int count,
      Color color,
      String category,
      String propertyType,
      String algorithm,
      Points points,
      int[] starts,
      double scaleX,
      double scaleY) {

    /** The index of the first point of an item. */
    public int start(int item) {
      return starts[item];
    }

    /** The index after the last point of an item. */
    public int end(int item) {
      return starts[item + 1];
    }
  }

  private final List<Group> groups;
  private final Set<String> referencedInstanceUIDs;

  private BulkAnnotations(List<Group> groups, Set<String> referencedInstanceUIDs) {
    this.groups = List.copyOf(groups);
    this.referencedInstanceUIDs = Set.copyOf(referencedInstanceUIDs);
  }

  public List<Group> groups() {
    return groups;
  }

  public Set<String> referencedInstanceUIDs() {
    return referencedInstanceUIDs;
  }

  /** The instances the object annotates, from its Referenced Image and Series Sequences. */
  public static Set<String> referencedInstances(Attributes dcm) {
    Set<String> uids = new LinkedHashSet<>();
    addInstances(dcm.getSequence(Tag.ReferencedImageSequence), uids);
    Sequence series = dcm.getSequence(Tag.ReferencedSeriesSequence);
    if (series != null) {
      for (Attributes item : series) {
        addInstances(item.getSequence(Tag.ReferencedInstanceSequence), uids);
      }
    }
    return uids;
  }

  private static void addInstances(Sequence seq, Set<String> uids) {
    if (seq != null) {
      for (Attributes item : seq) {
        String uid = item.getString(Tag.ReferencedSOPInstanceUID);
        if (uid != null) {
          uids.add(uid);
        }
      }
    }
  }

  /**
   * Reads the groups of an object against the slide they annotate.
   *
   * @return the groups, empty when the object annotates another slide or uses coordinates that are
   *     not read
   */
  public static Optional<BulkAnnotations> read(Attributes dcm, Slide slide) {
    Set<String> referenced = referencedInstances(dcm);
    PyramidLevel level = referencedLevel(slide, referenced).orElse(null);
    if (level == null) {
      return Optional.empty();
    }
    String coordinateType = dcm.getString(Tag.AnnotationCoordinateType, TWO_D);
    String origin = dcm.getString(Tag.PixelOriginInterpretation, VOLUME);
    if (!TWO_D.equalsIgnoreCase(coordinateType) || !VOLUME.equalsIgnoreCase(origin)) {
      LOGGER.info(
          "Bulk annotations in {} coordinates with a {} origin are not shown",
          coordinateType,
          origin);
      return Optional.empty();
    }
    Sequence seq = dcm.getSequence(Tag.AnnotationGroupSequence);
    if (seq == null || seq.isEmpty()) {
      return Optional.empty();
    }
    List<Group> groups = new ArrayList<>();
    int index = 0;
    for (Attributes item : seq) {
      try {
        readGroup(item, level, index).ifPresent(groups::add);
      } catch (IOException | RuntimeException e) {
        LOGGER.warn(
            "Cannot read the annotation group {}", item.getString(Tag.AnnotationGroupLabel), e);
      }
      index++;
    }
    return groups.isEmpty()
        ? Optional.empty()
        : Optional.of(new BulkAnnotations(groups, referenced));
  }

  /** The level of the slide one of the instances belongs to. */
  static Optional<PyramidLevel> referencedLevel(Slide slide, Set<String> instances) {
    for (PyramidLevel level : slide.levels()) {
      for (String uid : level.instanceUIDs()) {
        if (instances.contains(uid)) {
          return Optional.of(level);
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<Group> readGroup(Attributes item, PyramidLevel level, int index)
      throws IOException {
    GraphicType type = GraphicType.of(item.getString(Tag.GraphicType)).orElse(null);
    if (type == null) {
      LOGGER.info("Annotation graphic type {} is not shown", item.getString(Tag.GraphicType));
      return Optional.empty();
    }
    Points points = points(item);
    if (points == null || points.count() == 0) {
      return Optional.empty();
    }
    int declared = item.getInt(Tag.NumberOfAnnotations, 0);
    int[] starts = starts(item, type, points.count(), declared);
    if (starts == null) {
      return Optional.empty();
    }
    int count = starts.length - 1;
    Color color =
        color(item.getInts(Tag.RecommendedDisplayCIELabValue))
            .orElse(PALETTE[index % PALETTE.length]);
    return Optional.of(
        new Group(
            item.getInt(Tag.AnnotationGroupNumber, index + 1),
            item.getString(Tag.AnnotationGroupUID),
            item.getString(Tag.AnnotationGroupLabel, "Group " + (index + 1)), // NON-NLS
            item.getString(Tag.AnnotationGroupDescription),
            type,
            count,
            color,
            codeMeaning(item, Tag.AnnotationPropertyCategoryCodeSequence),
            codeMeaning(item, Tag.AnnotationPropertyTypeCodeSequence),
            algorithm(item),
            points,
            starts,
            level.downsampleX(),
            level.downsampleY()));
  }

  // The first point of each item, from the index list (1-based value indices) or the fixed size
  private static int[] starts(Attributes item, GraphicType type, int points, int declared) {
    if (type.pointsPerItem() > 0) {
      int count = points / type.pointsPerItem();
      int[] starts = new int[count + 1];
      for (int i = 0; i <= count; i++) {
        starts[i] = i * type.pointsPerItem();
      }
      return starts;
    }
    IntBuffer indices;
    try {
      indices = ints(item.getValue(Tag.LongPrimitivePointIndexList), item.bigEndian());
    } catch (IOException e) {
      LOGGER.warn("Cannot read the point index list", e);
      return null;
    }
    if (indices == null || indices.remaining() == 0) {
      if (declared == 1) {
        return new int[] {0, points}; // a single polyline needs no index list
      }
      return null;
    }
    int count = indices.remaining();
    int[] starts = new int[count + 1];
    for (int i = 0; i < count; i++) {
      long value = Integer.toUnsignedLong(indices.get(i));
      starts[i] = (int) Math.min(points, Math.max(0, (value - 1) / 2));
    }
    starts[count] = points;
    return starts;
  }

  private static Points points(Attributes item) throws IOException {
    Object single = item.getValue(Tag.PointCoordinatesData);
    if (single != null) {
      ByteBuffer bytes = bytes(single, item.bigEndian());
      if (bytes != null) {
        return new FloatPoints(bytes.asFloatBuffer());
      }
      if (single instanceof float[] values) {
        return new FloatPoints(FloatBuffer.wrap(values));
      }
    }
    Object doubles = item.getValue(Tag.DoublePointCoordinatesData);
    if (doubles != null) {
      ByteBuffer bytes = bytes(doubles, item.bigEndian());
      if (bytes != null) {
        return new DoublePoints(bytes.asDoubleBuffer());
      }
      if (doubles instanceof double[] values) {
        return new DoublePoints(DoubleBuffer.wrap(values));
      }
    }
    return null;
  }

  private static IntBuffer ints(Object value, boolean bigEndian) throws IOException {
    ByteBuffer bytes = bytes(value, bigEndian);
    if (bytes != null) {
      return bytes.asIntBuffer();
    }
    return value instanceof int[] values ? IntBuffer.wrap(values) : null;
  }

  // The bytes of an element: mapped from the file when the reader left it there, else as stored
  private static ByteBuffer bytes(Object value, boolean bigEndian) throws IOException {
    ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    if (value instanceof BulkData bulk) {
      try (FileChannel channel =
          FileChannel.open(bulk.getFile().toPath(), StandardOpenOption.READ)) {
        return channel
            .map(MapMode.READ_ONLY, bulk.offset(), bulk.length())
            .order(bulk.bigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
      }
    }
    if (value instanceof byte[] bytes) {
      return ByteBuffer.wrap(bytes).order(order);
    }
    return null;
  }

  private static String codeMeaning(Attributes item, int sequence) {
    Attributes code = item.getNestedDataset(sequence);
    return code == null ? null : code.getString(Tag.CodeMeaning);
  }

  private static String algorithm(Attributes item) {
    Attributes id = item.getNestedDataset(Tag.AnnotationGroupAlgorithmIdentificationSequence);
    return id == null ? null : id.getString(Tag.AlgorithmName);
  }

  /** The sRGB colour of a DICOM CIELab value (PS3.3 C.10.7.1.1), D65 white. */
  static Optional<Color> color(int[] lab) {
    if (lab == null || lab.length < 3) {
      return Optional.empty();
    }
    double l = lab[0] * 100.0 / 65535.0;
    double a = lab[1] * 255.0 / 65535.0 - 128.0;
    double b = lab[2] * 255.0 / 65535.0 - 128.0;
    double fy = (l + 16.0) / 116.0;
    double fx = fy + a / 500.0;
    double fz = fy - b / 200.0;
    double x = 0.95047 * labInverse(fx);
    double y = labInverse(fy);
    double z = 1.08883 * labInverse(fz);
    double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
    double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
    double bl = 0.0557 * x - 0.2040 * y + 1.0570 * z;
    return Optional.of(new Color(gamma(r), gamma(g), gamma(bl)));
  }

  private static double labInverse(double t) {
    return t > 6.0 / 29.0 ? t * t * t : 3.0 * (6.0 / 29.0) * (6.0 / 29.0) * (t - 4.0 / 29.0);
  }

  private static int gamma(double linear) {
    double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
    return (int) Math.round(Math.clamp(c, 0.0, 1.0) * 255);
  }

  private record FloatPoints(FloatBuffer values) implements Points {
    @Override
    public int count() {
      return values.limit() / 2;
    }

    @Override
    public double x(int point) {
      return values.get(2 * point);
    }

    @Override
    public double y(int point) {
      return values.get(2 * point + 1);
    }
  }

  private record DoublePoints(DoubleBuffer values) implements Points {
    @Override
    public int count() {
      return values.limit() / 2;
    }

    @Override
    public double x(int point) {
      return values.get(2 * point);
    }

    @Override
    public double y(int point) {
      return values.get(2 * point + 1);
    }
  }

  /** For the tests: groups built in memory. */
  static BulkAnnotations of(List<Group> groups) {
    return new BulkAnnotations(groups, Collections.emptySet());
  }
}
