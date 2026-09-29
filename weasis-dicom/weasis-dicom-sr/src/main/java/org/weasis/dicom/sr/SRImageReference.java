/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.weasis.dicom.codec.WaveformAnnotation;
import org.weasis.dicom.macro.SOPInstanceReference;

/**
 * The target of a clickable link in the rendered report: a referenced SOP instance (image,
 * waveform, segmentation or any composite object) or, for an SCOORD3D item without image reference,
 * a Frame of Reference; plus what the report attaches to that target: regions to draw on an image,
 * temporal annotations of a waveform, the segment or the presentation state to show.
 */
public class SRImageReference {
  private SOPInstanceReference sopInstanceReference;
  private String frameOfReferenceUID;
  private String presentationStateUID;
  private int[] segmentNumbers;
  private List<SRGraphic> graphics;
  private List<WaveformAnnotation> annotations;
  private final String nodeLevel;

  public SRImageReference(String nodeLevel) {
    super();
    this.nodeLevel = nodeLevel;
  }

  public void addGraphic(SRGraphic g) {
    if (g != null) {
      if (graphics == null) {
        graphics = new ArrayList<>();
      }
      graphics.add(g);
    }
  }

  public boolean hasGraphics() {
    return graphics != null && !graphics.isEmpty();
  }

  public void addAnnotation(WaveformAnnotation annotation) {
    if (annotation != null) {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      annotations.add(annotation);
    }
  }

  public boolean hasAnnotations() {
    return annotations != null && !annotations.isEmpty();
  }

  public SOPInstanceReference getSopInstanceReference() {
    return sopInstanceReference;
  }

  public void setSopInstanceReference(SOPInstanceReference sopInstanceReference) {
    this.sopInstanceReference = sopInstanceReference;
  }

  /** The Frame of Reference an SCOORD3D item applies to when it references no image. */
  public String getFrameOfReferenceUID() {
    return frameOfReferenceUID;
  }

  public void setFrameOfReferenceUID(String frameOfReferenceUID) {
    this.frameOfReferenceUID = frameOfReferenceUID;
  }

  /** SOP Instance UID of the softcopy presentation state to apply when showing the image. */
  public String getPresentationStateUID() {
    return presentationStateUID;
  }

  public void setPresentationStateUID(String presentationStateUID) {
    this.presentationStateUID = presentationStateUID;
  }

  /** Referenced Segment Numbers when the target is a segmentation object, else null. */
  public int[] getSegmentNumbers() {
    return segmentNumbers;
  }

  public void setSegmentNumbers(int[] segmentNumbers) {
    this.segmentNumbers =
        segmentNumbers == null || segmentNumbers.length == 0 ? null : segmentNumbers;
  }

  public boolean isSegmentReference() {
    return segmentNumbers != null;
  }

  /** The drawable items to overlay, never null. */
  public List<SRGraphic> getGraphics() {
    return graphics == null ? Collections.emptyList() : graphics;
  }

  /** The temporal annotations of the referenced waveform, never null. */
  public List<WaveformAnnotation> getAnnotations() {
    return annotations == null ? Collections.emptyList() : annotations;
  }

  /** The node identifier (e.g. "1.4.1") of the content item that produced this link. */
  public String getNodeLevel() {
    return nodeLevel;
  }
}
