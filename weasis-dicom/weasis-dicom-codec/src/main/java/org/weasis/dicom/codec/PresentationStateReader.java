package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.HashMap;
import java.util.List;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.iod.module.general.SeriesAndInstanceReference;
import org.dcm4che2.iod.module.macro.ImageSOPInstanceReference;
import org.dcm4che2.iod.module.pr.DisplayedAreaModule;
import org.dcm4che2.iod.module.pr.SpatialTransformationModule;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.display.PresetWindowLevel;

public class PresentationStateReader {
    private static final ICC_ColorSpace LAB = new ICC_ColorSpace(ICC_Profile.getInstance(ICC_ColorSpace.CS_sRGB));

    public static final String TAG_OLD_PIX_SIZE = "original.pixel.spacing";
    public static final String TAG_DICOM_LAYERS = "prSpecialElement.layers";

    private final PRSpecialElement prSpecialElement;
    private final DicomObject dcmobj;
    private final HashMap<String, Object> tags = new HashMap<String, Object>();

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

    public DicomObject getDcmobj() {
        return dcmobj;
    }

    public HashMap<String, Object> getTags() {
        return tags;
    }

    public Object getTagValue(String key, Object defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        Object object = tags.get(key);
        return object == null ? defaultValue : object;
    }

    public SeriesAndInstanceReference getSeriesAndInstanceReference() {
        if (dcmobj != null) {
            return new SeriesAndInstanceReference(dcmobj);
        }
        return null;
    }

    private boolean isModuleAppicable(ImageSOPInstanceReference[] sops, DicomImageElement img) {
        if (dcmobj != null && img != null) {
            if (sops == null) {
                return true;
            }
            String imgSop = (String) img.getTagValue(TagW.SOPInstanceUID);
            if (imgSop != null) {
                for (ImageSOPInstanceReference sop : sops) {
                    if (imgSop.equals(sop.getReferencedSOPInstanceUID())) {
                        int[] frames = sop.getReferencedFrameNumber();
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
                PresetWindowLevel.getPresetCollection(img, prSpecialElement.geTags(), true);
            if (presets != null && presets.size() > 0) {
                PresetWindowLevel p = presets.get(0);
                tags.put(ActionW.PRESET.cmd(), p);
            }
        }
    }

    public void readSpatialTransformationModule() {
        if (dcmobj != null) {
            // Rotation and then Flip
            SpatialTransformationModule spat = new SpatialTransformationModule(dcmobj);
            tags.put(ActionW.ROTATION.cmd(), spat.getRotation());
            tags.put(ActionW.FLIP.cmd(), spat.isHorizontalFlip());
        }
    }

    public void readDisplayArea(DicomImageElement img) {
        if (dcmobj != null) {
            DicomElement sq = dcmobj.get(Tag.DisplayedAreaSelectionSequence);
            if (sq == null || !sq.hasItems()) {
                return;
            }
            for (int i = 0; i < sq.countItems(); i++) {
                DisplayedAreaModule dam = new DisplayedAreaModule(sq.getDicomObject(i));
                if (dam != null && isModuleAppicable(dam.getImageSOPInstanceReferences(), img)) {
                    double[] pixelsize = null;
                    float[] spacing = dam.getPresentationPixelSpacing();
                    if (spacing != null && spacing.length == 2) {
                        pixelsize = new double[] { spacing[1], spacing[0] };
                    }
                    if (spacing == null) {
                        int[] aspects = dam.getPresentationPixelAspectRatio();
                        if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
                            // set the aspects to the pixel size of the image to stretch the image rendering (square
                            // pixel)
                            if (aspects[1] < aspects[0]) {
                                pixelsize = new double[] { 1.0, (double) aspects[0] / (double) aspects[1] };
                            } else {
                                pixelsize = new double[] { (double) aspects[1] / (double) aspects[0], 1.0 };
                            }
                        }
                    }
                    if (pixelsize != null) {
                        tags.put(TagW.PixelSpacing.getName(), pixelsize);
                    }

                    String presentationMode = dam.getPresentationSizeMode();
                    int[] tlhc = dam.getDisplayedAreaTopLeftHandCorner();
                    int[] brhc = dam.getDisplayedAreaBottomRightHandCorner();

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
                        tags.put(ActionW.ZOOM.cmd(), 0.0);
                    } else if ("MAGNIFY".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
                        tags.put(ActionW.ZOOM.cmd(), (double) dam.getPresentationPixelMagnificationRatio());
                    } else if ("TRUE SIZE".equalsIgnoreCase(presentationMode)) { //$NON-NLS-1$
                        // TODO required to calibrate the screen (Measure physically two lines displayed on screen, must
                        // be
                        // square pixel)
                        // tags.put(ActionW.ZOOM.cmd(), 0.0);
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
            r = g = b = (pGray >> 8);
        }
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        int conv = (r << 16) | (g << 8) | b | 0x1000000;
        return new Color(conv);
    }

}
