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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;

/**
 * A study may store one VOI per SEG file while giving every file the same Series Instance UID —
 * cardiac studies routinely ship thirty of them — and {@code DicomModel} keeps such files in a
 * single series on purpose. {@link DicomSeries#addMedia} orders frames by Instance Number, which
 * {@code DicomMediaIO} renumbers 1..N <em>per file</em>, so the series interleaves the files
 * frame-major and an ordinal into it names an arbitrary file's frame. Each segmentation must
 * therefore resolve its own frames through its own reader.
 */
class SegFrameResolutionTest {

  private static final int FRAMES = 4;
  private static final String SERIES_UID = "1.2.826.0.1.3680043.2.1143.100";
  private static final String FRAME_OF_REF_UID = "1.2.826.0.1.3680043.2.1143.2";

  @Test
  void contoursAreBuiltFromTheOwnFramesNotFromTheSharedSeries() {
    DicomImageElement[] ownFrames = frames(FRAMES);
    SegSpecialElement seg = segmentation("1.2.826.0.1.3680043.2.1143.101", ownFrames);

    seg.initContours(sharedSeries(), List.of());

    assertEquals(FRAMES, seg.getPositionMap().size(), "one entry per frame of this segmentation");
    // FrameRange spans frames 0..3, so the measurable layer is taken from frame 1.
    assertSame(ownFrames[1], sourceImage(seg));
  }

  @Test
  void aSeriesCrowdedWithOtherSegmentationsIsIgnored() {
    // The decoys stand in for the twenty-nine other SEG files of the series. Reading the masks by
    // ordinal picks them up and silently overlays one segmentation with another's frames.
    DicomImageElement[] ownFrames = frames(FRAMES);
    SegSpecialElement seg = segmentation("1.2.826.0.1.3680043.2.1143.102", ownFrames);
    DicomSeries series = sharedSeries();
    for (DicomImageElement decoy : frames(FRAMES)) {
      series.addMedia(decoy);
    }

    seg.initContours(series, List.of());

    assertEquals(FRAMES, seg.getPositionMap().size());
    assertSame(ownFrames[1], sourceImage(seg));
  }

  @Test
  void framesInterleaveWhenSeveralSegmentationsShareASeries() {
    // Why the ordinal is wrong in the first place: every file restarts its frame numbering at 1,
    // so the series groups frame 0 of all files, then frame 1 of all files, and so on.
    DicomImageElement[] first = frames(FRAMES);
    DicomImageElement[] second = frames(FRAMES);
    DicomSeries series = sharedSeries();
    for (int i = 0; i < FRAMES; i++) {
      series.addMedia(first[i]);
      series.addMedia(second[i]);
    }

    assertSame(first[0], series.getMedia(0, null, null));
    assertSame(second[0], series.getMedia(1, null, null), "ordinal 1 is another file's frame 0");
    assertSame(first[1], series.getMedia(2, null, null));
  }

  private static DicomImageElement sourceImage(SegSpecialElement seg) {
    SegRegion<DicomImageElement> region = seg.getSegAttributes().get(1);
    assertNotNull(region, "the segment declared in the Segment Sequence");
    assertNotNull(region.getMeasurableLayer(), "the measurable layer built from the SEG frames");
    return region.getMeasurableLayer().getSourceImage();
  }

  private static DicomSeries sharedSeries() {
    DicomSeries series = new DicomSeries(SERIES_UID);
    series.setTag(TagD.get(Tag.FrameOfReferenceUID), FRAME_OF_REF_UID);
    return series;
  }

  /** Mask frames of one SEG file, numbered 1..n as {@code DicomMediaIO} numbers a multiframe. */
  private static DicomImageElement[] frames(int count) {
    DicomImageElement[] frames = new DicomImageElement[count];
    for (int i = 0; i < count; i++) {
      DicomImageElement frame = mock(DicomImageElement.class);
      when(frame.getMediaReader()).thenReturn(mock(DicomMediaIO.class));
      when(frame.getTagValue(TagD.get(Tag.InstanceNumber))).thenReturn(i + 1);
      frames[i] = frame;
    }
    return frames;
  }

  private static SegSpecialElement segmentation(String sopUid, DicomImageElement[] frames) {
    DicomMediaIO reader = mock(DicomMediaIO.class);
    when(reader.getDicomObject()).thenReturn(segDataset(sopUid));
    when(reader.getMediaElement()).thenReturn(frames);
    return new SegSpecialElement(reader);
  }

  private static Attributes segDataset(String sopUid) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, UID.SegmentationStorage);
    dcm.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
    dcm.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID);
    dcm.setString(Tag.FrameOfReferenceUID, VR.UI, FRAME_OF_REF_UID);
    dcm.setString(Tag.Modality, VR.CS, "SEG");
    dcm.setString(Tag.SegmentationType, VR.CS, "BINARY");

    Attributes segment = new Attributes();
    segment.setInt(Tag.SegmentNumber, VR.US, 1);
    segment.setString(Tag.SegmentLabel, VR.LO, "VOI");
    dcm.newSequence(Tag.SegmentSequence, 1).add(segment);

    Attributes orientation = new Attributes();
    orientation.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    Attributes shared = new Attributes();
    shared.newSequence(Tag.PlaneOrientationSequence, 1).add(orientation);
    dcm.newSequence(Tag.SharedFunctionalGroupsSequence, 1).add(shared);

    Sequence perFrame = dcm.newSequence(Tag.PerFrameFunctionalGroupsSequence, FRAMES);
    for (int i = 0; i < FRAMES; i++) {
      Attributes identification = new Attributes();
      identification.setInt(Tag.ReferencedSegmentNumber, VR.US, 1);
      Attributes position = new Attributes();
      position.setDouble(Tag.ImagePositionPatient, VR.DS, 0, 0, i);
      Attributes frame = new Attributes();
      frame.newSequence(Tag.SegmentIdentificationSequence, 1).add(identification);
      frame.newSequence(Tag.PlanePositionSequence, 1).add(position);
      perFrame.add(frame);
    }
    return dcm;
  }
}
