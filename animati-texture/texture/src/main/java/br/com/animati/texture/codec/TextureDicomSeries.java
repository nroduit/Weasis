/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.codec;

import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.SwingWorker;

import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.TextureData;

/**
 * Implements methods to get the ImageSeries of texturedicom more usable by weasis components.
 *
 * @author Rafaelo Pinheiro (rafaelo@animati.com.br)
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 08, Aug.
 */
public class TextureDicomSeries<E extends ImageElement> extends ImageSeries implements MediaSeriesGroup {

    private static final NumberFormat DF3 = NumberFormat.getNumberInstance(Locale.US);

    static {
        DF3.setMaximumFractionDigits(3); // 3 decimals
    }

    private TagW tagID;
    private Map<TagW, Object> tags;
    private Comparator<TagW> comparator;

    /** Original series. */
    private MediaSeries<E> series;
    /** Information about the build process of the series`s texture. */
    public TextureLogInfo textureLogInfo;

    /** Map of slice-spacing occurrences. */
    private Map<String, Integer> zSpacings;

    /** Window / Level presets list. */
    private List<PresetWindowLevel> windowingPresets;

    protected String pixelValueUnit = null;

    /** ImageOrientationPatient from original series, if unique. */
    private double[] originalSeriesOrientationPatient;

    /** Series comparator used to build the texture. */
    private final Comparator<E> seriesComparator;
    private double[] acquisitionPixelSpacing;

    private volatile boolean isFactoryDone = false;
    private ImageSeriesFactory.LoaderThread factoryReference;

    private boolean[] inVideo;

    /**
     * Builds an empty TextureImageSeries. Its best to use ImageSeriesFactory.
     *
     * @param sliceWidth
     * @param sliceHeight
     * @param sliceCount
     * @param format
     * @param series
     * @throws Exception
     */
    public TextureDicomSeries(final int sliceWidth, final int sliceHeight, final int sliceCount,
        final TextureData.Format format, MediaSeries series, Comparator sorter) throws Exception {
        super(sliceWidth, sliceHeight, sliceCount, format);

        this.series = series;
        textureLogInfo = new TextureLogInfo();

        tags = new HashMap<TagW, Object>();
        tagID = series.getTagID();
        tags.put(tagID, series.getTagValue(tagID));

        seriesComparator = sorter;

        inVideo = new boolean[sliceCount];
        Arrays.fill(inVideo, false);

        // DICOM $C.11.1.1.2 Modality LUT and Rescale Type
        // Specifies the units of the output of the Modality LUT or rescale operation.
        // Defined Terms:
        // OD = The number in the LUT represents thousands of optical density. That is, a value of
        // 2140 represents an optical density of 2.140.
        // HU = Hounsfield Units (CT)
        // US = Unspecified
        // Other values are permitted, but are not defined by the DICOM Standard.
        String modality = (String) getTagValue(TagW.Modality, 0);
        pixelValueUnit = (String) getTagValue(TagW.RescaleType, 0);
        if (pixelValueUnit == null) {
            // For some other modalities like PET
            pixelValueUnit = (String) getTagValue(TagW.Units, 0);
        }
        if (pixelValueUnit == null && "CT".equals(modality)) {
            pixelValueUnit = "HU";
        }
    }

    public boolean isFactoryDone() {
        return isFactoryDone;
    }

    protected void setFactoryDone(boolean done) {
        isFactoryDone = done;
    }

    public void setFactorySW(ImageSeriesFactory.LoaderThread thread) {
        factoryReference = thread;
    }

    public SwingWorker getFactorySW() {
        return factoryReference;
    }

    /**
     * @return The original series.
     */
    public MediaSeries getSeries() {
        return series;
    }

    @Override
    public TagW getTagID() {
        return tagID;
    }

    @Override
    public void setTag(TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Object getTagValue(TagW tag) {
        if (containTagKey(tag)) {
            return tags.get(tag);
        }
        return series.getTagValue(tag);
    }

    @Override
    public TagW getTagElement(int id) {
        Iterator<TagW> enumVal = tags.keySet().iterator();
        while (enumVal.hasNext()) {
            TagW e = enumVal.next();
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        if (factoryReference != null) {
            factoryReference.setToCancel();
            factoryReference = null;
        }
    }

    @Override
    public void setComparator(Comparator<TagW> comparator) {
        this.comparator = comparator;
    }

    @Override
    public Comparator<TagW> getComparator() {
        return comparator;
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    /**
     * Stores an occurrence of the given slice-spacing om a Map<String, Integer>.
     *
     * Uses a String conversion of 3-decimals to limit the tolerance to 0.001, like the MPR of weasis.dicom.view2d.
     *
     * @param space
     */
    protected void addZSpacingOccurence(double space) {
        if (zSpacings == null) {
            zSpacings = new HashMap<String, Integer>();
        }
        String sp = DF3.format(space);
        Integer number = zSpacings.get(sp);
        if (number != null) {
            zSpacings.put(sp, number + 1);
        } else {
            zSpacings.put(sp, 1);
        }
    }

    /**
     * @return True if the original series has known and regular slice-spacing.
     */
    public boolean isSliceSpacingRegular() {
        if (zSpacings != null && !zSpacings.isEmpty() && zSpacings.size() == 1) {
            return true;
        }
        return false;
    }

    public boolean hasNegativeSliceSpacing() {
        if (zSpacings != null && !zSpacings.isEmpty()) {
            Iterator<String> iterator = zSpacings.keySet().iterator();
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (Double.parseDouble(next) < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Consults the slice-spacing map to get the most common one. If there are two or more spacing with the same higher
     * occurrence value, the first one found is returned.
     *
     * @return The most common slice spacing. Can be negative!
     */
    public double getMostCommonSpacing() {
        if (zSpacings == null && zSpacings.isEmpty()) {
            return 0;
        }
        if (zSpacings.size() == 1) {
            String[] toArray = zSpacings.keySet().toArray(new String[1]);
            return Double.parseDouble(toArray[0]);
        }
        String[] toArray = zSpacings.keySet().toArray(new String[zSpacings.size()]);
        String maxKey = toArray[0];
        for (int i = 1; i < toArray.length; i++) {
            if (zSpacings.get(maxKey) < zSpacings.get(toArray[i])) {
                maxKey = toArray[i];
            }
        }
        return Double.parseDouble(maxKey);
    }

    /**
     * Returns a presset List.
     *
     * @param pixelPadding
     * @param force
     *            Set true to recalculate presetList.
     * @return preset list
     */
    public List<PresetWindowLevel> getPresetList(final boolean pixelPadding, final boolean force) {
        if (windowingPresets == null || force) {
            windowingPresets = buildPresetsList(pixelPadding);
        }
        return windowingPresets;
    }

    private List<PresetWindowLevel> buildPresetsList(boolean pixelPadding) {
        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();

        Float[] window = (Float[]) getTagValue(TagW.WindowWidth);
        Float[] level = (Float[]) getTagValue(TagW.WindowCenter);
        // optional attributes
        String[] wlExplanationList = (String[]) getTagValue(TagW.WindowCenterWidthExplanation, 0);

        // Implicitly defined as default function in DICOM standard
        // TODO: expect other cases.
        LutShape defaultLutShape = LutShape.LINEAR;

        // Adds Dicom presets
        if (level != null && window != null) {
            int wlDefaultCount = (level.length == window.length) ? level.length : 0;
            String defaultExp = "Default";

            int presCount = 1;
            for (int i = 0; i < wlDefaultCount; i++) {
                String name = defaultExp + " " + presCount;

                if (wlExplanationList != null && i < wlExplanationList.length) {
                    if (wlExplanationList[i] != null && !wlExplanationList[i].equals("")) {
                        name = wlExplanationList[i]; // optional attribute
                    }
                }

                if (window[i] == null || level[i] == null) {
                    textureLogInfo.writeText("Could not load preset: " + name);
                } else {
                    PresetWindowLevel preset =
                        new PresetWindowLevel(name + " [Dicom]", window[i], level[i], defaultLutShape);
                    if (presCount == 1) {
                        preset.setKeyCode(KeyEvent.VK_1);
                    } else if (presCount == 2) {
                        preset.setKeyCode(KeyEvent.VK_2);
                    }
                    presetList.add(preset);
                    presCount++;
                }
            }
        }

        // TODO VoiLut !!

        // AutoLevel
        PresetWindowLevel autoLevel = new PresetWindowLevel(PresetWindowLevel.fullDynamicExplanation,
            getFullDynamicWidth(pixelPadding), getFullDynamicCenter(pixelPadding), defaultLutShape);
        presetList.add(autoLevel);

        // Arbitrary Presets by Modality
        // TODO: need to exclude 8-bits images from here.
        List<PresetWindowLevel> modPresets = StaticHelpers.getPresetListByModality().get(getTagValue(TagW.Modality));
        if (modPresets != null) {
            presetList.addAll(modPresets);
        }

        return presetList;
    }

    public float getFullDynamicWidth(boolean pixelPadding) {
        // TODO: needs to change if we use pixelPadding optional.
        return windowingMaxInValue - windowingMinInValue;
    }

    public float getFullDynamicCenter(boolean pixelPadding) {
        // TODO: needs to change if we use pixelPadding optional.
        return windowingMinInValue + (windowingMaxInValue - windowingMinInValue) / 2.0f;
    }

    /**
     * Correct the level value by rescale factors and the transformation made to the values to fit the video-card range.
     *
     * Still not tested for SignedShort with RescaleSlope not 1.
     *
     * @param level
     *            Level value as shown to the user.
     * @return the corrected level to be applied to this series.
     */
    public int getCorrectedValueForLevel(int level) {
        Float slopeVal = (Float) getTagValue(TagW.RescaleSlope);
        final double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();

        Float interceptVal = (Float) getTagValue(TagW.RescaleIntercept);
        if (interceptVal == null) {
            interceptVal = 0.0F;
        }
        double intercept = 0.0d;
        if (TextureData.Format.UnsignedShort.equals(getTextureData().getFormat())) {
            intercept = (windowingMinInValue - (interceptVal / slope));
        }

        double lev = (level / slope) + intercept;
        return (int) Math.round(lev);
    }

    /**
     * Correct the level value by rescale-slope to fit the video-card range.
     *
     * Still not tested for SignedShort with RescaleSlope not 1.
     *
     * @param window
     *            Window value as shown to the user.
     * @return the corrected window to be applied to this series.
     */
    public int getCorrectedValueForWindow(int window) {
        Float slopeVal = (Float) getTagValue(TagW.RescaleSlope);
        final double slope = slopeVal == null ? 1.0f : slopeVal.doubleValue();

        return (int) Math.round(window / slope);
    }

    /**
     * Valid if has 6 double s. Set to a double[] of one element to make not-valid.
     *
     * @param imOri
     */
    public void setOrientationPatient(double[] imOri) {
        originalSeriesOrientationPatient = imOri;
    }

    /**
     * Valid if has 6 double s. Set to a double[] of one element to make not-valid.
     *
     * @return
     */
    public double[] getOriginalSeriesOrientationPatient() {
        return originalSeriesOrientationPatient;
    }

    public Comparator<E> getSeriesSorter() {
        return seriesComparator;
    }

    public void setAcquisitionPixelSpacing(double[] pixSpacing) {
        acquisitionPixelSpacing = pixSpacing;
    }

    public double[] getAcquisitionPixelSpacing() {
        return acquisitionPixelSpacing;
    }

    public Unit getPixelSpacingUnit() {
        if (acquisitionPixelSpacing != null) {
            return Unit.MILLIMETER;
        }
        return Unit.PIXEL;
    }

    /**
     * Gets a tagValue from the original DicomImageElement on this location.
     *
     * @param tag
     *            Tag object
     * @param currentSlice
     *            Slices: 0 to N-1.
     * @return Tag value, if it exists.
     */
    public Object getTagValue(TagW tag, int currentSlice) {
        if (getSliceCount() == series.size(null)) {
            Object media = getSeries().getMedia(currentSlice, null, seriesComparator);
            if (media instanceof DicomImageElement) {
                return ((DicomImageElement) media).getTagValue(tag);
            }
        }
        return null;
    }

    public boolean isPhotometricInterpretationInverse(int currentSlice) {
        Object media = getSeries().getMedia(currentSlice, null, seriesComparator);
        if (media instanceof DicomImageElement) {
            return ((DicomImageElement) media).isPhotometricInterpretationInverse(null);
        }
        return false;
    }

    public String getPixelValueUnit() {
        return pixelValueUnit;
    }

    public void setInVideo(int sliceIndex, boolean isComplete) {
        if (sliceIndex < inVideo.length && sliceIndex >= 0) {
            inVideo[sliceIndex] = isComplete;
        }
    }

    public boolean isAllInVideo() {
        for (boolean loaded : inVideo) {
            if (!loaded) {
                return false;
            }
        }
        return true;
    }

    public boolean[] getPlacesInVideo() {
        return inVideo.clone();
    }

    public boolean isDownloadDone() {
        if (getSliceCount() > series.size(null)) {
            return false;
        }
        return true;
    }

    // ////////////////////////////////////////////////////////////

    private MonitorThread monitor;
    private int seriesSize = 0;
    protected volatile boolean isToCountObjects = false;

    public void countObjects() {
        if (getSeries() != null && isToCountObjects) {
            if (monitor == null) {
                monitor = new MonitorThread();
                seriesSize = getSliceCount();
                // Never use start() (it will start 2 threads).
                monitor.restart();
            } else {
                if (getSeries().size(null) != seriesSize) {
                    monitor.restart();
                    seriesSize = getSeries().size(null);
                }
            }
        }
    }

    /**
     * Get the GeometryOfSlice of on slice from the original series.
     *
     * @param currentSlice
     *            Slice to get the geometry from.
     * @return Geometry of given slice.
     */
    public GeometryOfSlice getSliceGeometry(int currentSlice) {
        Object media = getSeries().getMedia(currentSlice - 1, null, seriesComparator);
        if (media instanceof DicomImageElement) {
            return ((DicomImageElement) media).getSliceGeometry();
        }
        return null;
    }

    public int getNearestSliceIndex(final Double location) {
        Iterable<E> mediaList = series.getMedias(null, seriesComparator);
        int index = 0;
        int bestIndex = -1;
        synchronized (this) {
            double bestDiff = Double.MAX_VALUE;
            for (Iterator<E> iter = mediaList.iterator(); iter.hasNext();) {
                E dcm = iter.next();
                double[] val = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestIndex = index;
                        if (diff == 0.0) {
                            break;
                        }
                    }
                }
                index++;
            }
        }
        return bestIndex;
    }

    public void interruptFactory() {
        if (factoryReference != null) {
            factoryReference.setToCancel();
            factoryReference = null;
        }
        if (monitor != null) {
            monitor.interrupt();
            if (monitor.internalThread != null) {
                monitor.internalThread.interrupt();
                monitor.internalThread = null;
            }
            monitor = null;
        }
        ImageSeriesFactory.removeFromCache(this);
    }

    @Override
    public Iterator<Map.Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    protected class MonitorThread extends Thread {

        @Override
        public void run() {
            try {
                Thread.sleep(5000);
                ImageSeriesFactory.fireProperyChange(TextureDicomSeries.this, "RefreshTexture",
                    TextureDicomSeries.this);
                internalThread = null;
            } catch (InterruptedException e) {
            }
        }

        public Thread internalThread;

        public void restart() {
            if (internalThread != null) {
                internalThread.interrupt();
            }
            internalThread = new MonitorThread();
            internalThread.start();
        }
    }

}
