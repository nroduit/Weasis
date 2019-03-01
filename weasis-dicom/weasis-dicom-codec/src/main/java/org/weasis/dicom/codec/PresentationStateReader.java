/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PresentationStateReader implements Tagable {

    public static final String TAG_PR_READER = "pr.reader"; //$NON-NLS-1$

    public static final int PRIVATE_CREATOR_TAG = 0x71070070;
    public static final int PR_MODEL_PRIVATE_TAG = 0x71077001;
    public static final String PR_MODEL_ID = "weasis/model/xml/2.5"; //$NON-NLS-1$

    public static final String TAG_PR_ROTATION = "pr.rotation"; //$NON-NLS-1$
    public static final String TAG_PR_FLIP = "pr.flip"; //$NON-NLS-1$

    private static final ICC_ColorSpace LAB = new ICC_ColorSpace(ICC_Profile.getInstance(ICC_ColorSpace.CS_sRGB));

    private final PRSpecialElement prSpecialElement;
    private final Attributes dcmobj;
    private final HashMap<TagW, Object> tags = new HashMap<>();

    public PresentationStateReader(PRSpecialElement dicom) {
        Objects.requireNonNull(dicom, "Dicom parameter cannot be null"); //$NON-NLS-1$
        this.prSpecialElement = dicom;
        DicomMediaIO dicomImageLoader = dicom.getMediaReader();
        this.dcmobj = dicomImageLoader.getDicomObject();
    }

    public PRSpecialElement getDicom() {
        return prSpecialElement;
    }

    @Override
    public String toString() {
        return prSpecialElement.toString();
    }

    public Attributes getDcmobj() {
        return dcmobj;
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

    private static Predicate<Attributes> isSequenceApplicable(DicomImageElement img) {
        return attributes -> isModuleAppicable(attributes, img);
    }

    public static boolean isModuleAppicable(Attributes[] refSeriesSeqParent, DicomImageElement img) {
        if (refSeriesSeqParent != null) {
            for (Attributes refImgSeqParent : refSeriesSeqParent) {
                String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
                if (seriesUID.equals(refImgSeqParent.getString(Tag.SeriesInstanceUID))) {
                    return isModuleAppicable(refImgSeqParent, img);
                }
            }
        }
        return false;
    }

    public static boolean isModuleAppicable(Attributes refImgSeqParent, DicomImageElement img) {
        Objects.requireNonNull(refImgSeqParent);
        Objects.requireNonNull(img);

        Sequence sops = refImgSeqParent.getSequence(Tag.ReferencedImageSequence);
        if (sops == null || sops.isEmpty()) {
            return true;
        }
        String imgSop = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        if (imgSop != null) {
            for (Attributes sop : sops) {
                if (imgSop.equals(sop.getString(Tag.ReferencedSOPInstanceUID))) {
                    int[] frames = DicomMediaUtils.getIntAyrrayFromDicomElement(sop, Tag.ReferencedFrameNumber, null);
                    if (frames == null || frames.length == 0) {
                        return true;
                    }
                    int dicomFrame = 1;
                    if (img.getKey() instanceof Integer) {
                        dicomFrame = (Integer) img.getKey() + 1;
                    }
                    for (int f : frames) {
                        if (f == dicomFrame) {
                            return true;
                        }
                    }
                    // if the frame has been excluded
                    return false;
                }
            }
        }
        return false;
    }

    public List<PresetWindowLevel> getPresetCollection(DicomImageElement img) {
        return Optional.ofNullable(PresetWindowLevel.getPresetCollection(img, prSpecialElement, true, "[PR]")) //$NON-NLS-1$
            .orElseGet(ArrayList::new);
    }

    public void applySpatialTransformationModule(Map<String, Object> actionsInView) {
        if (dcmobj != null) {
            // Rotation and then Flip
            actionsInView.put(TAG_PR_ROTATION, dcmobj.getInt(Tag.ImageRotation, 0));
            actionsInView.put(TAG_PR_FLIP, "Y".equalsIgnoreCase(dcmobj.getString(Tag.ImageHorizontalFlip))); //$NON-NLS-1$
        }
    }

    public void readDisplayArea(DicomImageElement img) {
        if (dcmobj != null) {
            TagW[] tagList = TagD.getTagFromIDs(Tag.PresentationPixelSpacing, Tag.PresentationPixelAspectRatio,
                Tag.PixelOriginInterpretation, Tag.PresentationSizeMode, Tag.DisplayedAreaTopLeftHandCorner,
                Tag.DisplayedAreaBottomRightHandCorner, Tag.PresentationPixelMagnificationRatio);
            TagSeq.MacroSeqData data = new TagSeq.MacroSeqData(dcmobj, tagList, isSequenceApplicable(img));
            TagD.get(Tag.DisplayedAreaSelectionSequence).readValue(data, this);
        }
    }

    public static Color getRGBColor(int pGray, float[] labColour, int[] rgbColour) {
        int r, g, b;
        if (labColour != null) {
            float[] rgb = LAB.toRGB(labColour);
            r = (int) (rgb[0] * 255);
            g = (int) (rgb[1] * 255);
            b = (int) (rgb[2] * 255);
        } else if (rgbColour != null) {
            r = rgbColour[0];
            g = rgbColour[1];
            b = rgbColour[2];
            if (r > 255 || g > 255 || b > 255) {
                r >>= 8;
                g >>= 8;
                b >>= 8;
            }
        } else {
            r = g = b = pGray > 255 ? pGray >> 8 : pGray;
        }
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        int conv = (r << 16) | (g << 8) | b | 0x1000000;
        return new Color(conv);
    }

    public static float[] colorToLAB(Color color) {
        float[] rgb = new float[3];
        rgb[0] = color.getRed() / 255.f;
        rgb[1] = color.getGreen() / 255.f;
        rgb[2] = color.getBlue() / 255.f;

        return LAB.fromRGB(rgb);
    }
}
