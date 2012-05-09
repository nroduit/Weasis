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
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
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

import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.util.ImageToolkit;
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

    volatile private Histogram histogram = null;
    volatile private int maxHistoCount = Integer.MIN_VALUE;

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
                    // set the aspects to the pixel size of the image to stretch the image rendering (square pixel)
                    if (aspects[1] < aspects[0]) {
                        setPixelSize(1.0, (double) aspects[0] / (double) aspects[1]);
                    } else {
                        setPixelSize((double) aspects[1] / (double) aspects[0], 1.0);
                    }
                }
            }
            pixelValueUnit = (String) getTagValue(TagW.RescaleType);
            if (pixelValueUnit == null) {
                pixelValueUnit = (String) getTagValue(TagW.Units);
            }
            if (pixelValueUnit == null && "CT".equals(modality)) { //$NON-NLS-1$
                pixelValueUnit = "HU"; //$NON-NLS-1$
            }

        }
    }

    /**
     * @return must be rescaled to a value after modality pixel transformation because minValue stands for original raw
     *         dicom min data, taking care of pixel padding if exist
     */
    @Override
    public float getMinValue() {
        return Math.min(pixel2rescale(minPixelValue), pixel2rescale(maxPixelValue)); // because slope can be inverted
        // return pixel2rescale(minValue);
    }

    /**
     * @return must be rescaled to a value after modality pixel transformation because maxValue stands for original raw
     *         dicom max data, taking care of pixel padding if exist
     */
    @Override
    public float getMaxValue() {
        return Math.max(pixel2rescale(minPixelValue), pixel2rescale(maxPixelValue)); // because slope can be inverted
    }

    // cannot be used like this since modality LUT may be not linear
    @Deprecated
    @Override
    public float getPixelWindow(float window) {
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        if (slope != null) {
            return window /= slope;
        }
        return window;
    }

    // TODO - must implement a reverse lookup
    @Override
    public float getPixelLevel(float level) {
        return rescale2pixel(level);
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

        int bitsStored = (Integer) getTagValue(TagW.BitsStored);
        boolean signed = isPixelRepresentationSigned();
        // int outRangeSize = (1 << bitsStored) - 1;
        float maxOutValue = signed ? (1 << (bitsStored - 1)) - 1 : ((1 << bitsStored) - 1);

        // if (maxPixelValue >= maxOutValue) {
        if (maxPixelValue > maxOutValue) {
            bitsStored = (bitsStored > 8) ? bitsStored = 16 : 8;
            // TODO - Do it better
        }

        return bitsStored;
    }

    public int getBitsAllocated() {
        int bitsAllocated = (Integer) getTagValue(TagW.BitsAllocated);
        bitsAllocated = Math.max(getBitsStored(), bitsAllocated);
        bitsAllocated = (bitsAllocated <= 8) ? 8 : ((bitsAllocated <= 16) ? 16 : ((bitsAllocated <= 32) ? 32 : 0));
        return bitsAllocated;
    }

    public int getMinAllocatedValue() {
        boolean signed = isModalityLutOutSigned();
        // int bitsStored = getBitsStored();
        int bitsAllocated = getBitsAllocated();

        int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
        return (signed ? -(maxValue + 1) : 0);
    }

    public int getMaxAllocatedValue() {
        boolean signed = isModalityLutOutSigned();
        // int bitsStored = getBitsStored();
        int bitsAllocated = getBitsAllocated();

        return (signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1));
    }

    public int getAllocatedOutRangeSize() {
        // int bitsStored = getBitsStored();
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
     * If a Pixel Padding Value (0028,0120) only is present in the image then image contrast manipulations shall be not
     * be applied to those pixels with the value specified in Pixel Padding Value (0028,0120). If both Pixel Padding
     * Value (0028,0120) and Pixel Padding Range Limit (0028,0121) are present in the image then image contrast
     * manipulations shall not be applied to those pixels with values in the range between the values of Pixel Padding
     * Value (0028,0120) and Pixel Padding Range Limit (0028,0121), inclusive."
     * 
     * @return
     */

    // TODO - Improve the consuming resources of calling the creation of one lookup for each SOP image in series with
    // same modality LUT attributes, either same slope/rescale pair or same table.
    // A synchronized weakHashMap could be fine...

    // FIXME - Also it seems that sometimes min/max values have not been computed when this method is called.
    // Generally this happen with the calling of getMinValue/getMaxValue which call pixel2rescale function
    // !!! Strange because findMinMaxValue function should have been called before when loading image.

    protected LookupTableJAI getModalityLookup(boolean pixelPadding) {
        // TODO - handle pixel padding input argument
        boolean isSigned = isPixelRepresentationSigned();
        int bitsStored = getBitsStored();
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        Float slope = (Float) getTagValue(TagW.RescaleSlope);

        slope = (slope == null) ? 1.0f : slope;
        intercept = (intercept == null) ? 0.0f : intercept;

        if (bitsStored > 8) {
            isSigned = (minPixelValue * slope + intercept) < 0 ? true : isSigned;
        }

        LutParameters lutparams =
            new LutParameters(intercept, slope, (int) minPixelValue, (int) maxPixelValue, bitsStored, isSigned, false);
        LookupTableJAI modalityLookup = LUT_Cache.get(lutparams);
        if (modalityLookup != null) {
            return modalityLookup;
        }

        // In the case where Rescale Slope and Rescale Intercept are used, the output ranges from
        // (minimum pixel value*Rescale Slope+Rescale Intercept) to
        // (maximum pixel value*Rescale Slope+Rescale Intercept), where the minimum and maximum pixel values are
        // determined by BitsStored and Pixel Representation.
        // Note: This range may be signed even if Pixel Representation is unsigned.

        // LookupTable lookup = (LookupTable) getTagValue(TagW.ModalityLUTData);
        modalityLookup = (LookupTableJAI) getTagValue(TagW.ModalityLUTData);

        if (modalityLookup == null) {
            modalityLookup = DicomImageUtils.createRescaleRampLut(lutparams);
        }

        if (modalityLookup != null) {
            // In the case where the Modality LUT Sequence is used, the output range is from 0 to 2n-1 where n
            // is the third value of LUT Descriptor. This range is always unsigned.
            // String lutType = (String) getTagValue(TagW.ModalityLUTType);
            // String explanation = (String) getTagValue(TagW.ModalityLUTExplanation);

            Integer paddingValue = getPaddingValue();
            Integer paddingLimit = getPaddingLimit();

            if ((modalityLookup.getDataType() <= DataBuffer.TYPE_SHORT) && isPhotometricInterpretationMonochrome()
                && (paddingValue != null)) {

                final boolean isDataTypeByte = modalityLookup.getDataType() == DataBuffer.TYPE_BYTE;
                // if FALSE DataBuffer Type is supposed to be either TYPE_SHORT or TYPE_USHORT

                int lutOffset = modalityLookup.getOffset();
                int numEntries = modalityLookup.getNumEntries();

                int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);

                int numPaddingValues = paddingValueMax - paddingValueMin + 1;
                int paddingValuesStartIndex = paddingValueMin - lutOffset;
                int outLutValuesStartIndex = 0;
                int numNewValues = 0;

                // Test if modality lookupTable takes pixel padding values into account and if not resize it

                if (paddingValueMin < lutOffset) {
                    // add padding value range to the start of the lookup shifting right all existing elements
                    // by numPaddingValues
                    numNewValues = lutOffset - paddingValueMin;
                    lutOffset = paddingValueMin;
                    outLutValuesStartIndex = numNewValues;
                    paddingValuesStartIndex = 0;

                } else if (paddingValueMax > (numEntries + lutOffset)) {
                    // add padding value range to the end of the lookup
                    numNewValues = paddingValueMax - (numEntries + lutOffset);
                    paddingValuesStartIndex =
                        (numPaddingValues > numNewValues) ? numEntries + numNewValues - numPaddingValues : numEntries;
                }

                Object inLut = null;

                if (isDataTypeByte) {
                    inLut = modalityLookup.getByteData(0);
                } else {
                    inLut = modalityLookup.getShortData(0);
                }

                Object outLut = inLut;

                if (numNewValues > 0) {
                    int outLutSize = numEntries + numNewValues;
                    // outLut = Array.newInstance(inLut.getClass(), outLutSize);
                    // if (inLut instanceof byte[]) {
                    // outLut = new byte[outLutSize] ;
                    // } else if (inLut instanceof short[]) {
                    // outLut = new short[outLutSize] ;
                    // }

                    outLut = (bitsStored <= 8) ? new byte[outLutSize] : new short[outLutSize];
                    System.arraycopy(inLut, 0, outLut, outLutValuesStartIndex, numEntries);

                    numPaddingValues = (numPaddingValues < numNewValues) ? numNewValues : numPaddingValues;

                    if (isDataTypeByte) {
                        modalityLookup = new LookupTableJAI((byte[]) outLut, lutOffset);
                    } else {
                        modalityLookup = new LookupTableJAI((short[]) outLut, lutOffset, !isSigned);
                    }
                }

                // Set padding values to minPixelValue or maxPixelValue
                // int fillValue = (Integer) (isPhotometricInterpretationInverse() ? //
                // Array.get(outLut, (int) (maxPixelValue - lutOffset)) : //
                // Array.get(outLut, (int) (minPixelValue - lutOffset)));
                //
                // Arrays.fill((Object[]) outLut, lutPaddingStartIndex, numPaddingValues, fillValue);

                int indexMinPixelValue = (int) (minPixelValue - lutOffset);
                int indexMaxPixelValue = (int) (maxPixelValue - lutOffset);
                int fillValueIndex = isPhotometricInterpretationInverse() ? indexMaxPixelValue : indexMinPixelValue;

                if (isDataTypeByte) {
                    byte fillValue = Array.getByte(outLut, fillValueIndex);
                    Arrays.fill((byte[]) outLut, paddingValuesStartIndex, numPaddingValues, fillValue);
                } else {
                    short fillValue = Array.getShort(outLut, fillValueIndex);
                    Arrays.fill((short[]) outLut, paddingValuesStartIndex, numPaddingValues, fillValue);
                }

                // TODO - add the ability to disable padding by keeping values same as original

                // for (int i = lutPaddingStartIndex; i < lutPaddingStartIndex + numPaddingValues; i++) {
                // Array.set(outLut, i, 0);
                // Array.set(outLut, i, lutPaddingStartIndex-lutOffset);
                // }

            }
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

    public LookupTableJAI getVOILookup(Float window, Float level, LutShape shape) {
        return getVOILookup(window, level, shape, false);
    }

    // TODO must take care of getDataType and floating point entries without the use of a lookup but normal JAI
    // rescaling
    /**
     * 
     * @param window
     * @param level
     * @param shape
     * @param fillLutOutside
     * 
     * @return 8 bits unsigned Lookup Table
     */
    public LookupTableJAI getVOILookup(Float window, Float level, LutShape shape, boolean fillLutOutside) {

        if (window == null || level == null || shape == null) {
            return null;
        }

        boolean inverseLut = isPhotometricInterpretationInverse();

        int minValue = (int) (fillLutOutside ? getMinAllocatedValue() : getMinValue());
        int maxValue = (int) (fillLutOutside ? getMaxAllocatedValue() : getMaxValue());

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
        if (windowingPresetCollection == null) {
            windowingPresetCollection = Arrays.asList(PresetWindowLevel.getPresetCollection(this));
        }
        return windowingPresetCollection;
    }

    public Collection<LutShape> getLutShapeCollection() {
        if (lutShapeCollection != null) {
            return lutShapeCollection;
        }

        lutShapeCollection = new LinkedHashSet<LutShape>();
        for (PresetWindowLevel preset : getPresetList()) {
            lutShapeCollection.add(preset.getLutShape());
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
        if (histogram != null) {
            return histogram; // considered histogram computed only once
        }

        if (imageSource == null) {
            return null;
        }

        // TODO instead of computing histo from image get Dicom attribute if present

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(imageSource);
        pb.add(getModalityLookup());
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
        histogram = (Histogram) op.getProperty("histogram");
        // also possible to get an histogram image
        maxHistoCount = Integer.MIN_VALUE;

        for (int histoCount : histogram.getBins(0)) {
            maxHistoCount = Math.max(maxHistoCount, histoCount);
        }
        return histogram;
    }

    public int getMaxHistoCount() {
        return maxHistoCount;
    }

    @Override
    protected void findMinMaxValues(RenderedImage img) {
        // This function can be called several times from the inner class Load.
        // Not necessary to find again min and max

        if (img != null && minPixelValue == 0.0f && maxPixelValue == 0.0f) {

            Integer min = (Integer) getTagValue(TagW.SmallestImagePixelValue);
            Integer max = (Integer) getTagValue(TagW.LargestImagePixelValue);

            minPixelValue = (min == null) ? 0.0f : min.floatValue();
            maxPixelValue = (max == null) ? 0.0f : max.floatValue();

            if (isPhotometricInterpretationMonochrome()) {
                Integer paddingValue = getPaddingValue();
                if (paddingValue != null) {
                    Integer paddingLimit = getPaddingLimit();
                    int paddingValueMin = (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
                    int paddingValueMax = (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
                    if (minPixelValue != 0.0f || maxPixelValue != 0.0f) {
                        // TODO Is that useful ?
                        if ((paddingValueMin <= minPixelValue && minPixelValue <= paddingValueMax)
                            || (paddingValueMin <= maxPixelValue && maxPixelValue <= paddingValueMax)) {
                            // possible confusing Min/Max image values regarding to padding Min/Max range in order to
                            // get full dynamic range of the image with real pixel only
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

            // Lazily compute image pixel transformation here since inner class Load is called from a separate and
            // dedicated worker Thread. Also, it will be computed only once
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

                // this.minPixelValue = Math.round(min);
                // this.maxPixelValue = Math.round(max);
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

        ParameterBlock pb = new ParameterBlock();

        LookupTableJAI modalityLookup = getModalityLookup(pixelPadding);

        pb.addSource(imageSource);
        pb.add(modalityLookup);
        final RenderedImage imageModalityTransformed = JAI.create("lookup", pb, null);

        LookupTableJAI voiLookup = getVOILookup(window, level, lutShape);
        pb = new ParameterBlock();
        pb.addSource(imageModalityTransformed);
        pb.add(voiLookup);
        final RenderedImage imageVOITransformed = JAI.create("lookup", pb, null);

        return imageVOITransformed;
    }

    // TODO - change name because rescale term is already used, must use modality table to do pixel transform
    @Deprecated
    public float pixel2rescale(float pixelValue) {
        // // Hounsfield units: hu
        // // hu = pixelValue * rescale slope + intercept value
        // Float slope = (Float) getTagValue(TagW.RescaleSlope);
        // Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        // if (slope != null || intercept != null)
        // return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
        // return pixelValue;

        LookupTableJAI lookup = getModalityLookup();

        // TODO assert pixel value is inside bound array
        if (lookup != null) {
            int minValue = lookup.getOffset();
            int maxValue = lookup.getOffset() + lookup.getNumEntries() - 1;
            // System.out.println("min : " + minValue + " max : " + maxValue + " pixelValue : " + pixelValue);
            if (pixelValue >= minValue && pixelValue <= maxValue) {
                return lookup.lookup(0, (int) pixelValue);
            }
            // System.out.println("what the frack !!!");
        }
        return pixelValue;
    }

    // TODO - change name because rescale term is already used
    @Deprecated
    public float rescale2pixel(float hounsfieldValue) {
        // Hounsfield units: hu
        // pixelValue = (hu - intercept value) / rescale slope
        Float slope = (Float) getTagValue(TagW.RescaleSlope);
        Float intercept = (Float) getTagValue(TagW.RescaleIntercept);
        if (slope != null || intercept != null) {
            return (hounsfieldValue - (intercept == null ? 0.0f : intercept)) / (slope == null ? 1.0f : slope);
        }
        return hounsfieldValue;
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
