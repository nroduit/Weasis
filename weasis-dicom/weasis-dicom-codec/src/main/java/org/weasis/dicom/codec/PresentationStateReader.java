/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PresentationStateReader implements Taggable {

  public static final int PRIVATE_CREATOR_TAG = 0x71070070;
  public static final int PR_MODEL_PRIVATE_TAG = 0x71077001;
  public static final String PR_MODEL_ID = "weasis/model/xml/2.5"; // NON-NLS

  public static final String TAG_PR_ROTATION = "pr.rotation";
  public static final String TAG_PR_FLIP = "pr.flip";

  private final PRSpecialElement prSpecialElement;
  private final Attributes dicomObject;
  private final HashMap<TagW, Object> tags = new HashMap<>();
  private final PrDicomObject prDicomObject;

  public PresentationStateReader(PRSpecialElement dicom) {
    Objects.requireNonNull(dicom, "Dicom parameter cannot be null");
    this.prSpecialElement = dicom;
    this.prDicomObject = dicom.getPrDicomObject();
    this.dicomObject = prDicomObject.getDicomObject();
  }

  public PRSpecialElement getPrSpecialElement() {
    return prSpecialElement;
  }

  @Override
  public String toString() {
    return prSpecialElement.toString();
  }

  public Attributes getDicomObject() {
    return dicomObject;
  }

  public PrDicomObject getPrDicomObject() {
    return prDicomObject;
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag == null ? null : tags.get(tag);
  }

  @Override
  public void setTag(TagW tag, Object value) {
    DicomMediaUtils.setTag(tags, tag, value);
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (value != null) {
      setTag(tag, value);
    }
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return tags.containsKey(tag);
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    return tags.entrySet().iterator();
  }

  private static Predicate<Attributes> isSequenceApplicable(
      DicomImageElement img, boolean sequenceRequired) {
    return attributes -> isModuleApplicable(attributes, img, sequenceRequired);
  }

  public static boolean isImageApplicable(
      PRSpecialElement prSpecialElement, DicomImageElement img) {
    if (prSpecialElement != null && img != null) {
      PrDicomObject prDcm = prSpecialElement.getPrDicomObject();
      if (prDcm != null) {
        String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
        String imgSop = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        int dicomFrame = 1;
        if (img.getKey() instanceof Integer intVal) {
          dicomFrame = intVal + 1;
        }
        return prDcm.isImageFrameApplicable(seriesUID, imgSop, dicomFrame);
      }
    }
    return false;
  }

  public static boolean isModuleApplicable(
      Attributes refImgSeqParent, DicomImageElement img, boolean sequenceRequired) {
    Objects.requireNonNull(refImgSeqParent);
    Objects.requireNonNull(img);

    String imgSop = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
    int dicomFrame = 1;
    if (img.getKey() instanceof Integer intVal) {
      dicomFrame = intVal + 1;
    }
    return DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
        DicomObjectUtil.getSequence(refImgSeqParent, Tag.ReferencedImageSequence),
        Tag.ReferencedFrameNumber,
        imgSop,
        dicomFrame,
        sequenceRequired);
  }

  public void applySpatialTransformationModule(Map<String, Object> actionsInView) {
    if (dicomObject != null) {
      // Rotation and then Flip
      actionsInView.put(TAG_PR_ROTATION, dicomObject.getInt(Tag.ImageRotation, 0));
      actionsInView.put(
          TAG_PR_FLIP,
          "Y".equalsIgnoreCase(dicomObject.getString(Tag.ImageHorizontalFlip))); // NON-NLS
    }
  }

  public void readDisplayArea(DicomImageElement img) {
    if (dicomObject != null) {
      TagW[] tagList =
          TagD.getTagFromIDs(
              Tag.PresentationPixelSpacing,
              Tag.PresentationPixelAspectRatio,
              Tag.PixelOriginInterpretation,
              Tag.PresentationSizeMode,
              Tag.DisplayedAreaTopLeftHandCorner,
              Tag.DisplayedAreaBottomRightHandCorner,
              Tag.PresentationPixelMagnificationRatio);
      TagSeq.MacroSeqData data =
          new TagSeq.MacroSeqData(dicomObject, tagList, isSequenceApplicable(img, false));
      TagD.get(Tag.DisplayedAreaSelectionSequence).readValue(data, this);
    }
  }
}
