package org.weasis.core.ui.editor.image;

import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_ENTROPY;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_KURTOSIS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MAX;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEDIAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MIN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_PIXELS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_SKEW;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_STD;

import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.DataBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.HistogramData.Model;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class ImageRegionStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageRegionStatistics.class);

    private ImageRegionStatistics() {
    }

    public static List<Mat> prepareInputImages(AbstractDragGraphicArea graphic, MeasurableLayer layer) {
        if (layer != null && layer.hasContent()) {
            Shape shape = null;
            if (graphic != null) {
                if (!graphic.isShapeValid()) {
                    return Collections.emptyList();
                }
                shape = graphic.getShape();
            }

            PlanarImage image = layer.getSourceRenderedImage();
            if (image == null) {
                return Collections.emptyList();
            }

            Shape roi = null;
            if (shape != null) {
                AffineTransform transform = layer.getShapeTransform();
                Point offset = layer.getOffset();
                if (offset != null) {
                    if (transform == null) {
                        transform = AffineTransform.getTranslateInstance(-offset.getX(), -offset.getY());
                    } else {
                        transform.translate(-offset.getX(), -offset.getY());
                    }
                }
                if (transform != null) {
                    // Rescale ROI, if needed
                    roi = transform.createTransformedShape(shape);
                } else {
                    roi = shape;
                }
            }

            // Always apply pixel padding (deactivate in Display has no effect in statistics)
            Integer paddingValue = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingValue")); //$NON-NLS-1$
            Integer paddingLimit = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingRangeLimit")); //$NON-NLS-1$
            return ImageProcessor.getMaskImage(image.toMat(), roi, paddingValue, paddingLimit);
        }
        return Collections.emptyList();
    }

    public static List<MeasureItem> getImageStatistics(MeasurableLayer layer) {
        return getImageStatistics(null, layer, true);
    }

    public static List<HistogramData> getHistogram(AbstractDragGraphicArea graphic, MeasurableLayer layer) {
        List<Mat> imgPr = prepareInputImages(graphic, layer);
        if (imgPr.size() > 1) {
            Mat srcImg = imgPr.get(0);
            Mat mask = imgPr.get(1);

            double pixMin = layer.getPixelMin();
            double pixMax = layer.getPixelMax();

            int channels = srcImg.channels();
            Model colorModel = channels > 1 ? Model.RGB : Model.GRAY;
            int[] selChannels = new int[channels];
            for (int i = 0; i < selChannels.length; i++) {
                selChannels[i] = i;
            }

            List<HistogramData> data = new ArrayList<>();
            int datatype = ImageConversion.convertToDataType(srcImg.type());
            boolean intVal = datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT;
            try {
                int nbins = intVal ? (int) pixMax - (int) pixMin + 1 : 1024;
                List<Mat> listHisto =
                    HistogramData.computeHistogram(srcImg, mask, nbins, selChannels, colorModel, pixMin, pixMax);

                ByteLut[] lut = colorModel.getByteLut();
                DisplayByteLut[] displut = new DisplayByteLut[lut.length];

                for (int i = 0; i < lut.length; i++) {
                    displut[i] = new DisplayByteLut(lut[i]);
                    Mat h = listHisto.get(i);
                    float[] histValues = new float[h.rows()];
                    h.get(0, 0, histValues);
                    data.add(new HistogramData(histValues, displut[i], i, colorModel, null, pixMin, pixMax, layer));
                }
            } catch (Exception e) {
                LOGGER.error("Build histogram", e); //$NON-NLS-1$
            }
            return data;
        }

        return Collections.emptyList();
    }

    public static List<MeasureItem> getImageStatistics(AbstractDragGraphicArea graphic, MeasurableLayer layer,
        boolean releaseEvent) {
        if (layer != null && layer.hasContent()) {
            List<MeasureItem> measVal = new ArrayList<>();
            if (releaseEvent && (IMAGE_PIXELS.getComputed() || IMAGE_MIN.getComputed() || IMAGE_MAX.getComputed()
                || IMAGE_MEDIAN.getComputed() || IMAGE_MEAN.getComputed() || IMAGE_STD.getComputed()
                || IMAGE_SKEW.getComputed() || IMAGE_KURTOSIS.getComputed() || IMAGE_ENTROPY.getComputed())) {

                List<HistogramData> hists = getHistogram(graphic, layer);
                for (int i = 0; i < hists.size(); i++) {
                    HistogramData data = hists.get(i);
                    List<MeasureItem> mItems = getStatistics(data, hists.size() == 1 ? null : data.getBandIndex());
                    if (i > 0) {
                        mItems.remove(0);
                    }
                    measVal.addAll(mItems);
                }
            }
            return measVal;
        }

        return Collections.emptyList();
    }

    private static void addMeasure(List<MeasureItem> measVal, Measurement measure, Integer channelIndex, Double val,
        String unit) {
        if (measure.getComputed()) {
            if (channelIndex == null) {
                measVal.add(new MeasureItem(measure, val, unit));
            } else {
                measVal.add(new MeasureItem(measure, " " + (channelIndex + 1), val, unit)); //$NON-NLS-1$
            }
        }
    }

    public static List<MeasureItem> getStatistics(HistogramData data, Integer channelIndex) {
        MeasurableLayer layer = data.getLayer();
        if (layer != null && layer.hasContent()) {
            float[] bins = data.getHistValues();
            double offset = data.getPixMin();
            List<MeasureItem> measList = new ArrayList<>();
            double sum = 0;
            double min = Float.MAX_VALUE;
            double max = -Float.MAX_VALUE;
            double mean = 0.0;

            double binFactor = (data.getPixMax() - offset) / (bins.length - 1);

            for (int k = 0; k < bins.length; k++) {
                boolean valid = MathUtil.isDifferentFromZero(bins[k]) && bins[k] > 0.0f;
                float val = bins[k];
                double level = layer.pixelToRealValue(k * binFactor + offset);
                if (valid && level < min) {
                    min = level;
                }
                if (valid && level > max) {
                    max = level;
                }
                sum += val;
                mean += val * level;
            }

            mean /= sum;

            double m2 = 0.0;
            double skew = 0.0;
            double kurtosis = 0.0;
            double entropy = 0.0;
            double log2 = Math.log(2.0);
            for (int k = 0; k < bins.length; k++) {
                double val = bins[k];
                double level = layer.pixelToRealValue(k * binFactor + offset) - mean;
                m2 += val * Math.pow(level, 2);
                skew += val * Math.pow(level, 3);
                kurtosis += val * Math.pow(level, 4);
                double h = val / sum;
                if (MathUtil.isDifferentFromZero(h)) {
                    entropy -= h * (Math.log(h) / log2);
                }
            }

            double variance = m2 / (sum - 1); // variance
            double stdev = Math.sqrt(variance);

            if (bins.length > 3 && variance > MathUtil.DOUBLE_EPSILON) {
                skew = (sum * skew) / ((sum - 1) * (sum - 2) * stdev * variance); //NOSONAR the condition above should exclude the division by 0
                kurtosis = (sum * (sum + 1) * kurtosis - 3 * m2 * m2 * (sum - 1))
                    / ((sum - 1) * (sum - 2) * (sum - 3) * variance * variance);
            } else {
                skew = 0.0;
                kurtosis = 0.0;
            }
            String unit = layer.getPixelValueUnit();
            addMeasure(measList, IMAGE_PIXELS, channelIndex, sum, Unit.PIXEL.getAbbreviation());
            addMeasure(measList, IMAGE_MIN, channelIndex, min, unit);
            addMeasure(measList, IMAGE_MAX, channelIndex, max, unit);
            addMeasure(measList, IMAGE_MEDIAN, channelIndex,
                layer.pixelToRealValue(medianBin(bins, sum / 2.0) * binFactor + offset), unit);
            addMeasure(measList, IMAGE_MEAN, channelIndex, mean, unit);
            addMeasure(measList, IMAGE_STD, channelIndex, stdev, null);
            addMeasure(measList, IMAGE_SKEW, channelIndex, skew, null);
            addMeasure(measList, IMAGE_KURTOSIS, channelIndex, kurtosis, null);
            addMeasure(measList, IMAGE_ENTROPY, channelIndex, entropy, null);

            Double suv = (Double) layer.getSourceTagValue(TagW.SuvFactor);
            if (channelIndex == null && Objects.nonNull(suv)) {
                unit = "SUVbw"; //$NON-NLS-1$
                addMeasure(measList, IMAGE_MIN, channelIndex, min * suv, unit);
                addMeasure(measList, IMAGE_MAX, channelIndex, max * suv, unit);
                addMeasure(measList, IMAGE_MEAN, channelIndex, mean * suv, unit);
            }

            return measList;
        }

        return Collections.emptyList();
    }

    public static double medianBin(final float[] bin, double halfEntries) {
        if (bin == null || bin.length < 1) {
            return 0.0;
        } else {
            double sumBin = 0.0;
            double sum;
            for (int i = 0; i < bin.length; i++) {
                sum = sumBin + bin[i];
                // Check if bin crosses halfTotal point
                if (sum >= halfEntries) {
                    // Scale linearly across the bin
                    double dif = halfEntries - sumBin;
                    double frac = 0.0;
                    if (bin[i] > 0) {
                        frac = (dif) / bin[i];
                    }
                    return (i + frac);
                }
                sumBin = sum;
            }
        }
        return 0.0;
    }
}
