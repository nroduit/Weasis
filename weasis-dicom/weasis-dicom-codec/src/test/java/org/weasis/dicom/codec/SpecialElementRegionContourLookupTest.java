/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * Covers the order in which {@link SpecialElementRegion#getContours} consults its sources. Building
 * a canonical volume costs one byte per voxel of the whole segmentation extent, so it must never
 * happen for a plane the segmentation already carries a frame for.
 */
class SpecialElementRegionContourLookupTest {

  private static final double POSITION = 10.0;

  @Test
  void alignedFrameIsUsedWithoutBuildingAVolume() {
    Region region = new Region(positionMap(POSITION), null);

    Set<LazyContourLoader> contours = region.getContours(image(POSITION));

    assertNotNull(contours);
    assertEquals(1, contours.size());
    assertFalse(region.volumeRequested, "an in-plane frame must not trigger a canonical build");
  }

  @Test
  void volumeIsBuiltWhenNoFrameLiesInTheQueriedPlane() {
    Region region = new Region(positionMap(POSITION), mock(SegmentationVolume.class));

    Set<LazyContourLoader> contours = region.getContours(image(POSITION + 50));

    assertNotNull(contours);
    assertEquals(1, contours.size());
    assertTrue(region.volumeRequested, "an off-plane image can only be answered by a reslice");
  }

  @Test
  void nothingIsReturnedWhenNeitherSourceMatches() {
    Region region = new Region(positionMap(POSITION), null);

    assertNull(region.getContours(image(POSITION + 50)));
    assertTrue(region.volumeRequested);
  }

  @Test
  void slicesOutsideTheSegmentationRangeDoNotTriggerAVolumeBuild() {
    // The segmentation's frames lie in this plane on this grid, so its "nothing here" is the whole
    // answer. Building a volume for a slice the SEG does not cover would migrate it to the reslice
    // sampler for every later slice, and the overlay would change appearance as the user scrolls.
    Region region = new Region(positionMap(POSITION), mock(SegmentationVolume.class));
    region.referenceNormal = new Vector3d(0, 0, 1);

    assertNull(region.getContours(orientedImage(POSITION + 50)));
    assertFalse(region.volumeRequested, "an out-of-range slice must not build a canonical volume");
  }

  @Test
  void slicesInsideTheRangeStillMatchThroughTheOrientedPath() {
    Region region = new Region(positionMap(POSITION), mock(SegmentationVolume.class));
    region.referenceNormal = new Vector3d(0, 0, 1);

    assertNotNull(region.getContours(orientedImage(POSITION)));
    assertFalse(region.volumeRequested);
  }

  @Test
  void nearestPicksOneFrameWhereToleranceSpansSeveral() {
    // An overlapping reconstruction: 0.6 mm slices 0.3 mm apart, so the tolerance reaches both
    // neighbours even though the grids match 1:1. The volume reslice samples the middle plane
    // only, and the 2D overlay must agree with it rather than union the three.
    NavigableMap<Double, Set<LazyContourLoader>> map = new TreeMap<>();
    Set<LazyContourLoader> below = Set.of(Set::<SegContour>of);
    Set<LazyContourLoader> exact = Set.of(Set::<SegContour>of);
    Set<LazyContourLoader> above = Set.of(Set::<SegContour>of);
    map.put(-276.7, below);
    map.put(-276.4, exact);
    map.put(-276.1, above);

    assertEquals(3, SpecialElementRegion.findByTolerance(map, -276.4, 0.3001).size());
    assertSame(exact, SpecialElementRegion.findNearest(map, -276.4, 0.3001));
  }

  @Test
  void nearestResolvesOffGridPositionsAndRespectsTheTolerance() {
    NavigableMap<Double, Set<LazyContourLoader>> map = new TreeMap<>();
    Set<LazyContourLoader> low = Set.of(Set::<SegContour>of);
    Set<LazyContourLoader> high = Set.of(Set::<SegContour>of);
    map.put(0.0, low);
    map.put(1.0, high);

    assertSame(low, SpecialElementRegion.findNearest(map, 0.4, 1.0));
    assertSame(high, SpecialElementRegion.findNearest(map, 0.6, 1.0));
    // Exact tie resolves upwards, matching Math.round() in the volume reslice.
    assertSame(high, SpecialElementRegion.findNearest(map, 0.5, 1.0));
    assertNull(SpecialElementRegion.findNearest(map, 5.0, 1.0));
  }

  private static NavigableMap<Double, Set<LazyContourLoader>> positionMap(double position) {
    NavigableMap<Double, Set<LazyContourLoader>> map = new TreeMap<>();
    map.put(position, Set.of(Set::<SegContour>of));
    return map;
  }

  /** Axial image carrying the orientation metadata the reference-normal path needs. */
  private static DicomImageElement orientedImage(double z) {
    DicomImageElement img = mock(DicomImageElement.class);
    when(img.getTagValue(TagD.get(Tag.SeriesInstanceUID))).thenReturn("1.2.3");
    when(img.getTagValue(TagD.get(Tag.ImageOrientationPatient)))
        .thenReturn(new double[] {1, 0, 0, 0, 1, 0});
    when(img.getTagValue(TagD.get(Tag.ImagePositionPatient))).thenReturn(new double[] {0, 0, z});
    return img;
  }

  /** Image matched through the legacy SlicePosition key, so no orientation metadata is needed. */
  private static DicomImageElement image(double position) {
    DicomImageElement img = mock(DicomImageElement.class);
    when(img.getTagValue(TagD.get(Tag.SeriesInstanceUID))).thenReturn("1.2.3");
    when(img.getTagValue(TagW.SlicePosition)).thenReturn(position);
    return img;
  }

  /** Minimal segmentation exposing a position map and counting canonical-volume requests. */
  private static final class Region implements SpecialElementRegion {

    private final NavigableMap<Double, Set<LazyContourLoader>> positionMap;
    private final SegmentationVolume volume;
    private Vector3d referenceNormal;
    private boolean volumeRequested;

    Region(NavigableMap<Double, Set<LazyContourLoader>> positionMap, SegmentationVolume volume) {
      this.positionMap = positionMap;
      this.volume = volume;
    }

    @Override
    public SegmentationVolume getOrBuildSegmentationVolume() {
      volumeRequested = true;
      return volume;
    }

    @Override
    public Vector3d getReferenceNormal() {
      return referenceNormal;
    }

    @Override
    public NavigableMap<Double, Set<LazyContourLoader>> getPositionMap() {
      return positionMap;
    }

    @Override
    public Map<String, Map<String, Set<LazyContourLoader>>> getRefMap() {
      return Map.of();
    }

    @Override
    public Map<Integer, ? extends RegionAttributes> getSegAttributes() {
      return Map.of();
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    public void setVisible(boolean visible) {
      // no state to keep
    }

    @Override
    public float getOpacity() {
      return 1f;
    }

    @Override
    public void setOpacity(float opacity) {
      // no state to keep
    }
  }
}
