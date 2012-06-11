/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.renderable.ParameterBlock;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.OpImage;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.LookupDescriptor;

import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomImageUtils;
import org.weasis.dicom.codec.utils.LutParameters;

public class DicomImageElement extends ImageElement {

    private static final SoftHashMap<LutParameters, LookupTableJAI> LUT_Cache =
        new SoftHashMap<LutParameters, LookupTableJAI>() {

            public Reference<? extends LookupTableJAI> getReference(LutParameters key) {
                return hash.get(key);
            }

            @Override
            public void removeElement(Reference<? extends LookupTableJAI> soft) {
                LutParameters key = reverseLookup.remove(soft);
                if (key != null) {
                    hash.remove(key);
                }
            }
        };

    volatile private List<PresetWindowLevel> windowingPresetCollection = null;
    volatile private Collection<LutShape> lutShapeCollection = null;

    public DicomImageElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
        String modality = (String) mediaIO.getTagValue(TagW.Modality);
        if (!"SC".equals(modality) && !"OT".equals(modality)) { //$NON-NLS-1$ //$NON-NLS-2$
            // Physical distance in mm between the center of each pixel (ratio in mm)
            double[] val = (double[]) mediaIO.getTagValue(TagW.PixelSpacing);
            if (val == null) {
                val = (double[]) mediaIO.getTagValue(TagW.ImagerPixelSpacing);
                // Follows D. Clunie recommendations
                pixelSizeCalibrationDescription = val == null ? null : Messages.getString("DicomImageElement.detector"); //$NON-NLS-1$

            } else {
                pixelSizeCalibrationDescription = (String) mediaIO.getTagValue(TagW.PixelSpacingCalibrationDescription);
            }
            if (val != null && val[0] > 0.0 && val[1] > 0.0) {
                /*
                 * Pixel Spacing = Row Spacing \ Column Spacing => (Y,X) The first value is the row spacing in mm, that
                 * is the spacing between the centers of adjacent rows, or vertical spacing. Pixel Spacing must be
                 * always positive, but some DICOMs have negative values
                 */
                setPixelSize(val[1], val[0]);
                pixelSpacingUnit = Unit.MILLIMETER;
            } else if (val == null) {
                int[] aspects = (int[]) mediaIO.getTagValue(TagW.PixelAspectRatio);
                if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
                    /*
                     * Set the Pixel Aspect Ratio to the pixel size of the image to stretch the rendered image (for
                     * having square pixel on the display image)
                     */
                    if (aspects[1] < aspects[0]) {
                        setPixelSize(1.0, (double) aspects[0] / (double) aspects[1]);
                    } else {
                        setPixelSize((double) aspects[1] / (double) aspects[0], 1.0);
                    }
                }
            }
            // DICOM $C.11.1.1.2 Modality LUT and Rescale Type
            // Specifies the units of the output of the Modality LUT or rescale operation.
            // Defined Terms:
            // OD = The number in the LUT represents thousands of optical density. That is, a value of
            // 2140 represents an optical density of 2.140.
            // HU = Hounsfield Units (CT)
            // US = Unspecified
            // Other values are permitted, but are not defined by the DICOM Standard.
            pixelValueUnit = (String) getTagValue(TagW.RescaleType);
            if (pixelValueUnit == null) {
                // For some other modalities like PET
                pixelValueUnit = (String) getTagValue(TagW.Units);
            }
            if (pixelValueUnit == null && "CT".equals(modality)) { //$NON-NLS-1$
                pixelValueUnit = "HU"; //$NON-NLS-1$
            }

        }
    }

    /**
     * @return return the min value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    @Override
    public float getMinValue() {
        // Computes min and max as slope can be negative
        return Math.min(pixel2mLUT(minPixelValue), pixel2mLUT(maxPixelValue));
    }

    /**
     * @return return the max value after modality pixel transformation and after pixel padding operation if padding
     *         exists.
     */
    @Override
    public float getMaxValue() {
        // Computes min and max as slope can be negative
        return Math.max(pixel2mLUT(minPixelValue), pixel2mLUT(maxPixelValue));
    }

    @Override
    protected boolean isGrayImage(RenderedImage source) {
        Boolean val = (Boolean) getTagValue(TagW.MonoChrome);
        return val == null ? true : val;
    }

    /**
     * Data representation of the pixel samples. Each sample shall have the same pixel representation. Enumerated
     * Values: 0000H = unsigned integer. 0001H = 2's complement
     * 
     * @return true if Tag exist and if explicitly defined a signed
     * @see DICOM standard PS 3.3 - §C.7.6.3 - Image Pixel Module
     */

    public boolean isPixelRepresentationSigned() {
        Integer pixelRepresentation = (Integer) getTagValue(TagW.PixelRepresentation);
        return (pixelRepresentation != null) && (pixelRepresentation != 0);
    }

    public boolean isPhotometricInterpretationInverse() {
        return "MONOCHROME1".equalsIgnoreCase(getPhotometricInterpretation());
    }

    /**
     * In the case where Rescale Slope and Rescale Intercept are used for modality pixel transformation, the output
     * ranges may be signed even if Pixel Representation is unsigned.
     * 
     * @return
     */
    public boolean isModalityLutOutSigned() {
        boolean signed = isPixelRepresentationSigned();
        return getMinValue() < 0 ? true : signed;
    }

    public int getBitsStored() {
        return (Integer) getTagValue(TagW.BitsStored);
    }

    public int getBitsAllocated() {
        return (Integer) getTagValue(TagW.BitsAllocated);
    }

    public float getRescaleIntercept() {
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        return (intercept == null) ? 0.0f : intercept.floatValue();
    }

    public float getRescaleSlope() {
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        return (slope == null) ? 1.0f : slope.floatValue();
    }

    public float pixel2mLUT(float pixelValue) {
        LookupTableJAI lookup = getModalityLookup();
        if (lookup != null) {
            if (pixelValue >= lookup.getOffset() && pixelValue < lookup.getOffset() + lookup.getNumEntries()) {
                return lookup.lookup(0, (int) pixelValue);
            }
        }
        return pixelValue;
    }

    public int getMinAllocatedValue() {
        boolean signed = isModalityLutOutSigned();
        int bitsAllocated = getBitsAllocated();
        int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
        return (signed ? -(maxValue + 1) : 0);
    }

    public int getMaxAllocatedValue() {
        boolean signed = isModalityLutOutSigned();
        int bitsAllocated = getBitsAllocated();
        return signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
    }

    public int getAllocatedOutRangeSize() {
        int bitsAllocated = getBitsAllocated();
        return (1 << bitsAllocated) - 1;
    }

    /**
     * The value of Photometric Interpretation specifies the intended interpretation of the image pixel data.
     * 
     * @return following values (MONOCHROME1 , MONOCHROME2 , PALETTE COLOR ....) Other values are permitted but the
     *         meaning is not defined by this Standard.
     */
    public String getPhotometricInterpretation() {
        return (String) getTagValue(TagW.PhotometricInterpretation);
    }

    public boolean isPhotometricInterpretationMonochrome() {
        String photometricInterpretation = getPhotometricInterpretation();

        return (photometricInterpretation != null && //
        ("MONOCHROME1".equalsIgnoreCase(photometricInterpretation) || "MONOCHROME2"
            .equalsIgnoreCase(photometricInterpretation)));
    }

    /**
     * 
     * Pixel Padding Value is used to pad grayscale images (those with a Photometric Interpretation of MONOCHROME1 or
     * MONOCHROME2)<br>
     * Pixel Padding Value specifies either a single value of this padding value, or when combined with Pixel Padding
     * Range Limit, a range of values (inclusive) that are padding.<br>
     * <br>
     * <b>Note :</b> It is explicitly described in order to prevent display applications from taking it into account
     * when determining the dynamic range of an image, since the Pixel Padding Value will be outside the range between
     * the minimum and maximum values of the pixels in the native image
     * 
     * @see DICOM standard PS 3.3 - §C.7.5.1.1.2 - Pixel Padding Value and Pixel Padding Range Limit
     */

    public Integer getPaddingValue() {
        return (Integer) getTagValue(TagW.PixelPaddingValue);
    }

    /**
     * @see getPaddingValue()
     */
    public Integer getPaddingLimit() {
        return (Integer) getTagValue(TagW.PixelPaddingRangeLimit);

    }

    /**
     * DICOM PS 3.3 $C.11.1 Modality LUT Module
     * 
     * The LUT Data contains the LUT entry values.
     * 
     * The output range of the Modality LUT Module depends on whether or not Rescale Slope (0028,1053) and Rescale
     * Intercept (0028,1052) or the Modality LUT Sequence (0028,3000) are used. In the case where Rescale Slope and
     * Rescale Intercept are used, the output ranges from (minimum pixel value*Rescale Slope+Rescale Intercept) to
     * (maximum pixel value*Rescale - Slope+Rescale Intercept), where the minimum and maximum pixel values are
     * determined by Bits Stored and Pixel Representation. Note: This range may be signed even if Pixel Representation
     * is unsigned.
     * 
     * In the case where the Modality LUT Sequence is used, the output range is from 0 to 2n-1 where n is the third
     * value of LUT Descriptor. This range is always unsigned.
     * 
     * @param pixelPadding
     * @return the modality lookup table
     */
    protected LookupTableJAI getModalityLookup(boolean pixelPadding) {
        Integer paddingValue = getPaddingValue();
        LookupTableJAI modalityLookup = (LookupTableJAI) getTagValue(TagW.ModalityLUTData);
        if (modalityLookup != null && (!pixelPadding || paddingValue == null)) {
            return modalityLookup;
        }
        boolean modSeqLUT = modalityLookup != null;
        boolean isSigned = isPixelRepresentationSigned();
        int bitsStored = getBitsStored();
        float intercept = getRescaleIntercept();
        float slope = getRescaleSlope();
        // No need to have a modality lookup table
        if (bitsStored > 16 || (slope == 1.0f && intercept == 0.0f && paddingValue == null)) {
            return null;
        }

        Integer paddingLimit = getPaddingLimit();
        boolean outputSigned = false;
        if (bitsStored > 8 && !modSeqLUT) {
            float minVal = minPixelValue;
            if (paddingValue != null) {
                minVal = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
            }
            outputSigned = (minVal * slope + intercept) < 0 ? true : isSigned;
        }

        LutParameters lutparams =
            new LutParameters(intercept, slope, pixelPadding, paddingValue, paddingLimit, bitsStored, isSigned,
                outputSigned);

        modalityLookup = LUT_Cache.get(lutparams);

        if (modalityLookup != null) {
            return modalityLookup;
        }

        if (modalityLookup == null) {
            if (modSeqLUT) {
                LookupTableJAI lookup = (LookupTableJAI) getTagValue(TagW.ModalityLUTData);
                if (lookup != null && lookup.getNumBands() == 1) {
                    if (lookup.getDataType() == DataBuffer.TYPE_BYTE) {
                        byte[] data = lookup.getByteData(0);
                        if (data != null) {
                            modalityLookup = new LookupTableJAI(data, lookup.getOffset(0));
                        }
                    } else {
                        short[] data = lookup.getShortData(0);
                        if (data != null) {
                            modalityLookup =
                                new LookupTableJAI(data, lookup.getOffset(0),
                                    lookup.getData() instanceof DataBufferUShort);
                        }
                    }
                }
                if (modalityLookup == null) {
                    modalityLookup = lookup == null ? DicomImageUtils.createRescaleRampLut(lutparams) : lookup;
                }
            } else {
                modalityLookup = DicomImageUtils.createRescaleRampLut(lutparams);
            }
        }

        if (isPhotometricInterpretationMonochrome()) {
            // TODO get also is inverse LUT
            DicomImageUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutparams);
        }
        LUT_Cache.put(lutparams, modalityLookup);
        return modalityLookup;
    }

    /**
     * @return a lookupTable for modality transform with pixel padding always set true
     */

    public LookupTableJAI getModalityLookup() {
        return getModalityLookup(true);
    }

    /**
     * 
     * @param window
     * @param level
     * @param shape
     * @param fillLutOutside
     * @param pixelPadding
     * 
     * @return 8 bits unsigned Lookup Table
     */
    public LookupTableJAI getVOILookup(Float window, Float level, LutShape shape, boolean fillLutOutside,
        boolean pixelPadding) {

        if (window == null || level == null || shape == null) {
            return null;
        }

        boolean inverseLut = isPhotometricInterpretationInverse();

        int minValue = (int) (fillLutOutside ? getMinAllocatedValue() : getMinValue());
        int maxValue = (int) (fillLutOutside ? getMaxAllocatedValue() : getMaxValue());
        Integer paddingValue = getPaddingValue();
        if (paddingValue != null && isPhotometricInterpretationMonochrome()) {
            Integer paddingLimit = getPaddingLimit();
            if (pixelPadding) {
                /*
                 * When pixel padding is activated, VOI LUT must extend to the min bit stored value when MONOCHROME2 and
                 * to the max bit stored value when MONOCHROME1.
                 * 
                 * C.7.5.1.1.2 Pixel Padding Value and Pixel Padding Range Limit If Photometric Interpretation
                 * 
                 * (0028,0004) is MONOCHROME2, Pixel Padding Value (0028,0120) shall be less than (closer to or equal to
                 * the minimum possible pixel value) or equal to Pixel Padding Range Limit (0028,0121). If Photometric
                 * Interpretation (0028,0004) is MONOCHROME1, Pixel Padding Value (0028,0120) shall be greater than
                 * (closer to or equal to the maximum possible pixel value) or equal to Pixel Padding Range Limit
                 * (0028,0121).
                 */
                minValue = isPixelRepresentationSigned() ? -(1 << (getBitsStored() - 1)) : 0;
                // TODO max val for handle isPixelRepresentationSigned and invers LUT
            } else {
                int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
                minValue = Math.min(minValue, paddingValueMin);
                maxValue = Math.max(maxValue, paddingValueMax);
            }
        }

        return DicomImageUtils.createWindowLevelLut(shape, window, level, minValue, maxValue, 8, false, inverseLut);
    }

    /**
     * @return default as first element of preset List
     */
    public PresetWindowLevel getDefaultPreset() {
        List<PresetWindowLevel> presetList = getPresetList();
        return (presetList != null && presetList.size() > 0) ? presetList.get(0) : null;
    }

    public List<PresetWindowLevel> getPresetList() {
        if (windowingPresetCollection == null && isImageAvailable()) {
            windowingPresetCollection = Arrays.asList(PresetWindowLevel.getPresetCollection(this));
        }
        return windowingPresetCollection;
    }

    public Collection<LutShape> getLutShapeCollection() {
        if (lutShapeCollection != null) {
            return lutShapeCollection;
        }

        lutShapeCollection = new LinkedHashSet<LutShape>();
        List<PresetWindowLevel> presetList = getPresetList();
        if (presetList != null) {
            for (PresetWindowLevel preset : presetList) {
                lutShapeCollection.add(preset.getLutShape());
            }
        }
        lutShapeCollection.addAll(LutShape.DEFAULT_FACTORY_FUNCTIONS);

        return lutShapeCollection;
    }

    /**
     * 
     * @param imageSource
     * @return Histogram of the image source after modality lookup rescaled
     */

    public Histogram getHistogram(RenderedImage imageSource) {
        LookupTableJAI lookup = getModalityLookup();
        if (imageSource == null || lookup == null) {
            return null;
        }
        // TODO instead of computing histo from image get Dicom attribute if present

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(imageSource);
        pb.add(lookup);
        final RenderedImage imageModalityTransformed = JAI.create("lookup", pb, null);

        pb.removeSources();
        pb.removeParameters();

        pb.addSource(imageModalityTransformed);
        pb.add(null); // No ROI
        pb.add(1); // Sampling
        pb.add(1); // periods
        pb.add(new int[] { getAllocatedOutRangeSize() }); // Num. bins.
        pb.add(new double[] { getMinAllocatedValue() }); // Min value to be considered.
        pb.add(new double[] { getMaxAllocatedValue() }); // Max value to be considered.

        RenderedOp op = JAI.create("histogram", pb, ImageToolkit.NOCACHE_HINT);
        return (Histogram) op.getProperty("histogram");
    }

    @Override
    protected void findMinMaxValues(RenderedImage img) {
        /*
         * This function can be called several times from the inner class Load. min and max will be computed only once.
         */

        if (img != null && minPixelValue == 0.0f && maxPixelValue == 0.0f) {

            Integer min = (Integer) getTagValue(TagW.SmallestImagePixelValue);
            Integer max = (Integer) getTagValue(TagW.LargestImagePixelValue);
            int bitsStored = getBitsStored();
            int bitsAllocated = getBitsAllocated();
            if (bitsStored < bitsAllocated) {
                /*
                 * Do not trust those values because it can contain values bigger than the bit stored max (ex. overlays
                 * stored from the bit 12 to 16, or reading unsigned 12 bits stored jpeg-ls image gives values superior
                 * to 4096). Otherwise, the modality lookup will crash because the value for the index is bigger than
                 * the array length.
                 */
                min = max = null;
            }

            minPixelValue = (min == null) ? 0.0f : min.floatValue();
            maxPixelValue = (max == null) ? 0.0f : max.floatValue();

            if (isPhotometricInterpretationMonochrome()) {
                Integer paddingValue = getPaddingValue();
                if (paddingValue != null) {
                    Integer paddingLimit = getPaddingLimit();
                    int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
                    if (minPixelValue != 0.0f || maxPixelValue != 0.0f) {
                        if (paddingValueMin <= minPixelValue || paddingValueMax >= maxPixelValue) {
                            /*
                             * possible confusing Min/Max image values regarding to padding Min/Max range in order to
                             * get full dynamic range of the image with real pixel only
                             */
                            findMinMaxValues(img, paddingValueMin, paddingValueMax);
                        }
                    } else {
                        findMinMaxValues(img, paddingValueMin, paddingValueMax);
                    }
                }
            }

            if (minPixelValue == 0.0f && maxPixelValue == 0.0f) {
                super.findMinMaxValues(img);
            }

            if (bitsStored < bitsAllocated) {
                boolean isSigned = isPixelRepresentationSigned();
                int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
                int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;
                if ((int) minPixelValue < minInValue || (int) maxPixelValue > maxInValue) {
                    /*
                     * When the image contains values smaller or bigger than the bits stored min max values, the bits
                     * stored is replaced by the bits allocated.
                     */
                    setTag(TagW.BitsStored, bitsAllocated);
                }
            }
            /*
             * Lazily compute image pixel transformation here since inner class Load is called from a separate and
             * dedicated worker Thread. Also, it will be computed only once
             */
            getModalityLookup();
        }
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     * 
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     */
    private void findMinMaxValues(RenderedImage img, double paddingValueMin, double paddingValueMax) {
        if (img != null) {
            int datatype = img.getSampleModel().getDataType();
            if (datatype == DataBuffer.TYPE_BYTE) {
                this.minPixelValue = 0;
                this.maxPixelValue = 255;
            } else {
                RenderedOp dst =
                    ImageStatisticsDescriptor.create(img, (ROI) null, 1, 1, new Double(paddingValueMin), new Double(
                        paddingValueMax), null);
                // To ensure this image won't be stored in tile cache
                ((OpImage) dst.getRendering()).setTileCache(null);

                double[][] extrema = (double[][]) dst.getProperty("statistics"); //$NON-NLS-1$
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                int numBands = dst.getSampleModel().getNumBands();

                for (int i = 0; i < numBands; i++) {
                    min = Math.min(min, extrema[0][i]);
                    max = Math.max(max, extrema[1][i]);
                }
                this.minPixelValue = (int) min;
                this.maxPixelValue = (int) max;
            }
        }
    }

    public float getFullDynamicWidth() {
        return getMaxValue() - getMinValue();
    }

    public float getFullDynamicCenter() {
        float minValue = getMinValue();
        float maxValue = getMaxValue();
        return minValue + (maxValue - minValue) / 2.f;
    }

    @Override
    public LutShape getDefaultShape() {
        PresetWindowLevel defaultPreset = getDefaultPreset();
        return (defaultPreset != null) ? defaultPreset.getLutShape() : super.getDefaultShape();
    }

    @Override
    public float getDefaultWindow() {
        PresetWindowLevel defaultPreset = getDefaultPreset();
        return (defaultPreset != null) ? defaultPreset.getWindow() : super.getDefaultWindow();
    }

    @Override
    public float getDefaultLevel() {
        PresetWindowLevel defaultPreset = getDefaultPreset();
        return (defaultPreset != null) ? defaultPreset.getLevel() : super.getDefaultLevel();

    }

    @Override
    public RenderedImage getRenderedImage(final RenderedImage imageSource, Float window, Float level,
        LutShape lutShape, Boolean pixelPadding) {
        if (imageSource == null) {
            return null;
        }
        window = (window == null) ? getDefaultWindow() : window;
        level = (level == null) ? getDefaultLevel() : level;
        lutShape = (lutShape == null) ? getDefaultShape() : lutShape;
        pixelPadding = (pixelPadding == null) ? true : pixelPadding;

        SampleModel sampleModel = imageSource.getSampleModel();
        if (sampleModel == null) {
            return null;
        }
        int datatype = sampleModel.getDataType();

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            LookupTableJAI modalityLookup = getModalityLookup(pixelPadding);
            if (modalityLookup == null && datatype == DataBuffer.TYPE_BYTE && window == 255.0f && level == 127.5f
                && LutShape.LINEAR.equals(lutShape)) {
                return imageSource;
            }
            // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, new ImageLayout(imageSource));
            RenderedImage imageModalityTransformed =
                modalityLookup == null ? imageSource : LookupDescriptor.create(imageSource, modalityLookup, null);
            LookupTableJAI voiLookup = getVOILookup(window, level, lutShape, false, pixelPadding);
            // BUG fix: for some images the color model is null. Creating 8 bits gray model layout fixes this issue.
            return LookupDescriptor.create(imageModalityTransformed, voiLookup, LayoutUtil.createGrayRenderedImage());

        } else if (datatype == DataBuffer.TYPE_INT || datatype == DataBuffer.TYPE_FLOAT
            || datatype == DataBuffer.TYPE_DOUBLE) {
            double low = level - window / 2.0;
            double high = level + window / 2.0;
            double range = high - low;
            if (range < 1.0) {
                range = 1.0;
            }
            double slope = 255.0 / range;
            double y_int = 255.0 - slope * high;

            ParameterBlock pb = new ParameterBlock();
            pb.addSource(imageSource);
            pb.add(new double[] { slope });
            pb.add(new double[] { y_int });
            RenderedOp rescale = JAI.create("rescale", pb, null); //$NON-NLS-1$

            // produce a byte image
            pb = new ParameterBlock();
            pb.addSource(rescale);
            pb.add(DataBuffer.TYPE_BYTE);
            return JAI.create("format", pb, null); //$NON-NLS-1$
        }
        return null;
    }

    public GeometryOfSlice getSliceGeometry() {
        double[] imgOr = (double[]) getTagValue(TagW.ImageOrientationPatient);
        if (imgOr != null && imgOr.length == 6) {
            double[] pos = (double[]) getTagValue(TagW.ImagePositionPatient);
            if (pos != null && pos.length == 3) {
                double[] spacing = { getPixelSize(), getPixelSize(), 0.0 };
                Float sliceTickness = (Float) getTagValue(TagW.SliceThickness);
                Integer rows = (Integer) getTagValue(TagW.Rows);
                Integer columns = (Integer) getTagValue(TagW.Columns);
                if (rows != null && columns != null && rows > 0 && columns > 0) {
                    // If no sliceTickness: set 0, sliceTickness is only use in IntersectVolume
                    // Multiply rows and columns by getZoomScale() to have square pixel image size
                    return new GeometryOfSlice(new double[] { imgOr[0], imgOr[1], imgOr[2] }, new double[] { imgOr[3],
                        imgOr[4], imgOr[5] }, pos, spacing, sliceTickness == null ? 0.0 : sliceTickness.doubleValue(),
                        new double[] { rows * getRescaleY(), columns * getRescaleX(), 1 });
                }
            }
        }
        return null;
    }

}
