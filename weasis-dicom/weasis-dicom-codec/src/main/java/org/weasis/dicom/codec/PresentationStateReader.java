package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.HashMap;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class PresentationStateReader implements TagReadable {
    private static final ICC_ColorSpace LAB = new ICC_ColorSpace(ICC_Profile.getInstance(ICC_ColorSpace.CS_sRGB));

    private final PRSpecialElement prSpecialElement;
    private final Attributes dcmobj;
    private final HashMap<String, Object> tags = new HashMap<>();

    public PresentationStateReader(PRSpecialElement dicom) {
        if (dicom == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.prSpecialElement = dicom;
        DicomMediaIO dicomImageLoader = dicom.getMediaReader();
        dcmobj = dicomImageLoader.getDicomObject();
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

    public Object getTagValue(String key, Object defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        Object object = tags.get(key);
        return object == null ? defaultValue : object;
    }

    private boolean isModuleAppicable(Attributes item, DicomImageElement img) {
        if (dcmobj != null && img != null) {
            Sequence sops = item.getSequence(Tag.ReferencedImageSequence);
            if (sops == null || sops.isEmpty()) {
                return true;
            }
            String imgSop = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
            if (imgSop != null) {
                for (Attributes sop : sops) {
                    if (imgSop.equals(sop.getString(Tag.ReferencedSOPInstanceUID))) {
                        int[] frames =
                            DicomMediaUtils.getIntAyrrayFromDicomElement(sop, Tag.ReferencedFrameNumber, null);
                        if (frames == null) {
                            return true;
                        }
                        int frame = 0;
                        if (img.getKey() instanceof Integer) {
                            frame = (Integer) img.getKey();
                        }
                        for (int f : frames) {
                            if (f == frame) {
                                return true;
                            }
                        }
                        // if the frame has been excluded
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public void readGrayscaleSoftcopyModule(DicomImageElement img) {
        if (dcmobj != null) {
            List<PresetWindowLevel> presets =
                PresetWindowLevel.getPresetCollection(img, prSpecialElement, true);
            if (presets != null && !presets.isEmpty()) {
                tags.put(ActionW.PRESET.cmd(), presets);
            }
        }
    }

    public void readSpatialTransformationModule() {
        if (dcmobj != null) {
            // Rotation and then Flip
            tags.put(ActionW.ROTATION.cmd(), dcmobj.getInt(Tag.ImageRotation, 0));
            tags.put(ActionW.FLIP.cmd(), "Y".equalsIgnoreCase(dcmobj.getString(Tag.ImageHorizontalFlip))); //$NON-NLS-1$
        }
    }

    public void readDisplayArea(DicomImageElement img) {
        if (dcmobj != null) {
            Sequence srcSeq = dcmobj.getSequence(Tag.DisplayedAreaSelectionSequence);
            if (srcSeq == null || srcSeq.isEmpty()) {
                return;
            }
            for (Attributes item : srcSeq) {
                if (isModuleAppicable(item, img)) {
                    float[] spacing =
                        DicomMediaUtils.getFloatArrayFromDicomElement(item, Tag.PresentationPixelSpacing, null);
                    if (spacing != null && spacing.length == 2) {
                        tags.put(TagD.get(Tag.PixelSpacing).getKeyword(), new double[] { spacing[1], spacing[0] });
                    }
                    if (spacing == null) {
                        int[] aspects =
                            DicomMediaUtils.getIntAyrrayFromDicomElement(item, Tag.PresentationPixelAspectRatio, null);
                        if (aspects != null) {
                            tags.put(TagD.get(Tag.PixelAspectRatio).getKeyword(), aspects);
                        }
                    }

                    String presentationMode = item.getString(Tag.PresentationSizeMode);
                    int[] tlhc =
                        DicomMediaUtils.getIntAyrrayFromDicomElement(item, Tag.DisplayedAreaTopLeftHandCorner, null);
                    int[] brhc = DicomMediaUtils.getIntAyrrayFromDicomElement(item,
                        Tag.DisplayedAreaBottomRightHandCorner, null);

                    if (tlhc != null && tlhc.length == 2 && brhc != null && brhc.length == 2) {
                        // Lots of systems encode topLeft as 1,1, even when they mean 0,0
                        if (tlhc[0] == 1) {
                            tlhc[0] = 0;
                        }
                        if (tlhc[1] == 1) {
                            tlhc[1] = 0;

                        }
                        Rectangle rect = new Rectangle();
                        rect.setFrameFromDiagonal(tlhc[0], tlhc[1], brhc[0], brhc[1]);
                        tags.put(ActionW.CROP.cmd(), rect);
                    }
                    tags.put("presentationMode", presentationMode); //$NON-NLS-1$
                    if ("SCALE TO FIT".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
                        tags.put(ActionW.ZOOM.cmd(), -200.0);
                    } else if ("MAGNIFY".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
                        tags.put(ActionW.ZOOM.cmd(),
                            (double) item.getFloat(Tag.PresentationPixelMagnificationRatio, 1.0f));
                    } else if ("TRUE SIZE".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
                        // Required to calibrate the screen in preferences
                        tags.put(ActionW.ZOOM.cmd(), -100.0);
                    }
                    // Cannot apply a second DisplayedAreaModule to the image. It makes no sense.
                    break;
                }
            }
        }
    }

    public static Color getRGBColor(int pGray, float[] labColour, int[] rgbColour) {
        int r, g, b;
        if (labColour != null) {
            if (LAB == null) {
                r = g = b = (int) (labColour[0] * 2.55f);
            } else {
                float[] rgb = LAB.toRGB(labColour);
                r = (int) (rgb[0] * 255);
                g = (int) (rgb[1] * 255);
                b = (int) (rgb[2] * 255);
            }
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
            r = g = b = pGray >> 8;
        }
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        int conv = (r << 16) | (g << 8) | b | 0x1000000;
        return new Color(conv);
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

}
