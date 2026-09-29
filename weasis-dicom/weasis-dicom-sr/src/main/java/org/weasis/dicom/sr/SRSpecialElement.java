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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.SpecialElementOverlay;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.SOPInstanceReference;

/**
 * A Structured Report. Beyond being displayed in the SR viewer, it draws its SCOORD and SCOORD3D
 * items over the images they reference through {@link SpecialElementOverlay}. The content tree is
 * indexed once, on first use, by the SOP instances and frames of reference it points to.
 */
public class SRSpecialElement extends DicomSpecialElement implements SpecialElementOverlay {

  /** The drawable items of the document and where they apply. */
  private record OverlayIndex(
      List<SRImageReference> references,
      Set<String> sopInstanceUIDs,
      Set<String> frameOfReferenceUIDs) {}

  private volatile OverlayIndex index;

  /** Node ids of the items to emphasize, set when the user clicks a region in the report. */
  private volatile Set<String> highlightedNodes = Set.of();

  public SRSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  @Override
  protected void initLabel() {
    /*
     * DICOM PS 3.3 - 2011 - C.17.3 SR Document Content Module
     *
     * Concept Name Code Sequence: mandatory when type is CONTAINER or the root content item.
     */
    StringBuilder buf = new StringBuilder(getLabelPrefix());

    Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
    Attributes item = dicom.getNestedDataset(Tag.ConceptNameCodeSequence);
    if (item != null) {
      Code code = new Code(item);
      buf.append(code.getCodeMeaning());
    }
    label = buf.toString();
  }

  // ================================================================================
  // SpecialElementOverlay
  // ================================================================================

  @Override
  public LayerType getOverlayLayerType() {
    return LayerType.DICOM_SR;
  }

  @Override
  public String getOverlayLayerName() {
    return getLabel();
  }

  @Override
  public boolean appliesTo(DicomImageElement image) {
    if (image == null) {
      return false;
    }
    OverlayIndex idx = getIndex();
    if (idx.references().isEmpty()) {
      return false;
    }
    String sop = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
    if (sop != null && idx.sopInstanceUIDs().contains(sop)) {
      return true;
    }
    return !idx.frameOfReferenceUIDs().isEmpty()
        && idx.frameOfReferenceUIDs().contains(image.getFrameOfReferenceUID());
  }

  /**
   * Builds the graphics of every item applying to the image, the highlighted ones emphasized. An
   * SCOORD item applies through its SOP instance and frame, an SCOORD3D item through its frame of
   * reference and the slice plane.
   */
  @Override
  public List<Graphic> buildOverlayGraphics(DicomImageElement image) {
    OverlayIndex idx = getIndex();
    if (image == null || idx.references().isEmpty()) {
      return Collections.emptyList();
    }
    String sopUID = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
    Integer frame = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
    boolean multiframe =
        image.getMediaReader() != null && image.getMediaReader().getMediaElementNumber() > 1;
    String imageFor = idx.frameOfReferenceUIDs().isEmpty() ? null : image.getFrameOfReferenceUID();
    Set<String> highlighted = highlightedNodes;

    Map<String, Graphic> graphics = new LinkedHashMap<>();
    for (SRImageReference ref : idx.references()) {
      boolean sameInstance =
          referencesInstance(ref.getSopInstanceReference(), sopUID, frame, multiframe);
      for (SRGraphic item : ref.getGraphics()) {
        if (graphics.containsKey(item.nodeId())) {
          continue;
        }
        boolean emphasized = highlighted.contains(item.nodeId());
        if (!emphasized && !item.isPresentationRequired()) {
          // Optional or suppressed CAD marks are shown only when the user asks for them
          continue;
        }
        boolean applies =
            item.threeD()
                ? imageFor != null && imageFor.equals(item.getFrameOfReferenceUID())
                : sameInstance;
        if (applies) {
          Graphic g = item.build(image, emphasized);
          if (g != null) {
            graphics.put(item.nodeId(), g);
          }
        }
      }
    }
    return new ArrayList<>(graphics.values());
  }

  /** The link targets of the document that carry drawable items. */
  public List<SRImageReference> getGraphicReferences() {
    return getIndex().references();
  }

  public Set<String> getHighlightedNodes() {
    return highlightedNodes;
  }

  /** Marks the items to emphasize on the images; an empty set draws every item alike. */
  public void setHighlightedNodes(Set<String> nodeIds) {
    this.highlightedNodes = nodeIds == null ? Set.of() : Set.copyOf(nodeIds);
  }

  private OverlayIndex getIndex() {
    OverlayIndex idx = index;
    if (idx == null) {
      synchronized (this) {
        idx = index;
        if (idx == null) {
          idx = buildIndex();
          index = idx;
        }
      }
    }
    return idx;
  }

  private OverlayIndex buildIndex() {
    Attributes dicom = getMediaReader() == null ? null : getMediaReader().getDicomObject();
    if (dicom == null) {
      return new OverlayIndex(List.of(), Set.of(), Set.of());
    }
    Map<String, SRImageReference> map = new HashMap<>();
    new SRReader(this, dicom).readDocumentGeneralModule(new StringBuilder(), map);

    List<SRImageReference> refs = new ArrayList<>();
    Set<String> sops = new HashSet<>();
    Set<String> fors = new HashSet<>();
    for (SRImageReference ref : map.values()) {
      if (!ref.hasGraphics()) {
        continue;
      }
      refs.add(ref);
      SOPInstanceReference sop = ref.getSopInstanceReference();
      if (sop != null && sop.getReferencedSOPInstanceUID() != null) {
        sops.add(sop.getReferencedSOPInstanceUID());
      }
      for (SRGraphic item : ref.getGraphics()) {
        if (item.threeD() && item.getFrameOfReferenceUID() != null) {
          fors.add(item.getFrameOfReferenceUID());
        }
      }
    }
    return new OverlayIndex(List.copyOf(refs), Set.copyOf(sops), Set.copyOf(fors));
  }

  static boolean referencesInstance(
      SOPInstanceReference ref, String sopUID, Integer frame, boolean multiframe) {
    if (ref == null || sopUID == null || !sopUID.equals(ref.getReferencedSOPInstanceUID())) {
      return false;
    }
    int[] frames = ref.getReferencedFrameNumber();
    if (!multiframe || frames == null || frames.length == 0 || frame == null) {
      return true;
    }
    return IntStream.of(frames).anyMatch(f -> f == frame);
  }
}
