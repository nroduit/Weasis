package org.weasis.dicom.viewer2d.mip;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.swing.JProgressBar;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.op.MaxCollectionZprojection;
import org.weasis.core.api.image.op.MeanCollectionZprojection;
import org.weasis.core.api.image.op.MinCollectionZprojection;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.viewer2d.RawImage;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;

public class SeriesBuilder {
    public static final File MPR_CACHE_DIR = AppProperties.buildAccessibleTempDirectory(
        AppProperties.FILE_CACHE_DIR.getName(), "mip"); //$NON-NLS-1$

    private SeriesBuilder() {
    }

    // TODO use ProgressMonitor instead JProgressBar
    public static void applyMipParameters(final JProgressBar progressBar, final View2d view,
        List<DicomImageElement> dicoms, Type mipType, Integer extend, boolean fullSeries) {

        PlanarImage curImage = null;
        MediaSeries<DicomImageElement> series = view.getSeries();
        if (series != null) {

            SeriesComparator sort = (SeriesComparator) view.getActionValue(ActionW.SORTSTACK.cmd());
            Boolean reverse = (Boolean) view.getActionValue(ActionW.INVERSESTACK.cmd());
            Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
            Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
            Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);

            // synchronized (series) {

            int curImg = extend - 1;
            ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
            if (sequence instanceof SliderCineListener) {
                SliderCineListener cineAction = (SliderCineListener) sequence;
                curImg = cineAction.getValue() - 1;
            }

            int minImg = fullSeries ? extend : curImg;
            int maxImg = fullSeries ? series.size(filter) - extend : curImg;

            final Attributes cpTags;
            if (fullSeries) {
                DicomImageElement img = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, filter, sortFilter);
                final Attributes attributes = ((DicomMediaIO) img.getMediaReader()).getDicomObject();
                final int[] COPIED_ATTRS =
                    { Tag.SpecificCharacterSet, Tag.IssuerOfPatientID, Tag.IssuerOfAccessionNumberSequence,
                        Tag.ReferringPhysicianName, Tag.ModalityLUTSequence, Tag.VOILUTSequence };
                Arrays.sort(COPIED_ATTRS);
                cpTags = new Attributes(attributes, COPIED_ATTRS);
            } else {
                cpTags = null;
            }

            for (int index = minImg; index <= maxImg; index++) {
                Iterator<DicomImageElement> iter = medias.iterator();
                final List<ImageElement> sources = new ArrayList<ImageElement>();
                int startIndex = index - extend;
                if (startIndex < 0) {
                    startIndex = 0;
                }
                int stopIndex = index + extend;
                int k = 0;
                while (iter.hasNext()) {
                    // if (this.isInterrupted()) {
                    // return;
                    // }
                    DicomImageElement dcm = iter.next();
                    if (k >= startIndex) {
                        // TODO check Pixel size, LUTs if they are different from the first image (if yes
                        // then
                        // display
                        // confirmation message to continue)
                        sources.add(dcm);
                    }

                    if (k >= stopIndex) {
                        break;
                    }
                    k++;
                }

                if (sources.size() > 1) {
                    curImage = addCollectionOperation(mipType, sources, view, progressBar);
                } else {
                    curImage = null;
                }

                // }
                final DicomImageElement dicom;
                if (curImage != null) {
                    String seriesUID = UIDUtils.createUID();

                    DicomImageElement imgRef = (DicomImageElement) sources.get(sources.size() / 2);
                    RawImage raw = null;
                    try {
                        File mipDir =
                            AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "mip"); //$NON-NLS-1$
                        raw = new RawImage(File.createTempFile("mip_", ".raw", mipDir));//$NON-NLS-1$ //$NON-NLS-2$
                        writeRasterInRaw(curImage.getAsBufferedImage(), raw.getOutputStream());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (raw != null) {
                            raw.disposeOutputStream();
                        }
                    }
                    if (raw == null) {
                        return;
                    }
                    RawImageIO rawIO = new RawImageIO(raw.getFile().toURI(), null);

                    // Tags with same values for all the Series
                    rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
                    rawIO.setTag(TagW.Columns, curImage.getWidth());
                    rawIO.setTag(TagW.Rows, curImage.getHeight());
                    rawIO.setTag(TagW.BitsAllocated, imgRef.getBitsAllocated());
                    rawIO.setTag(TagW.BitsStored, imgRef.getBitsStored());

                    rawIO.setTag(TagW.SliceThickness, getThickness(sources));
                    double[] loc = (double[]) imgRef.getTagValue(TagW.SlicePosition);
                    if (loc != null) {
                        rawIO.setTag(TagW.SlicePosition, loc);
                        rawIO.setTag(TagW.SliceLocation, (float) (loc[0] + loc[1] + loc[2]));
                    }

                    rawIO.setTag(TagW.SeriesInstanceUID, seriesUID);

                    // Mandatory tags
                    TagW[] mtagList =
                        { TagW.PatientID, TagW.PatientName, TagW.PatientBirthDate, TagW.PatientSex,
                            TagW.PatientPseudoUID, TagW.StudyInstanceUID, TagW.StudyID, TagW.SOPClassUID,
                            TagW.StudyDate, TagW.StudyTime, TagW.AccessionNumber };
                    rawIO.copyTags(mtagList, imgRef, true);

                    TagW[] tagList =
                        { TagW.PhotometricInterpretation, TagW.PixelRepresentation, TagW.Units, TagW.ImageType,
                            TagW.SamplesPerPixel, TagW.MonoChrome, TagW.Modality };
                    rawIO.copyTags(tagList, imgRef, true);

                    TagW[] tagList2 =
                        { TagW.ImageOrientationPatient, TagW.ImagePositionPatient, TagW.SmallestImagePixelValue,
                            TagW.LargestImagePixelValue, TagW.ModalityLUTData, TagW.ModalityLUTType,
                            TagW.ModalityLUTExplanation, TagW.RescaleSlope, TagW.RescaleIntercept, TagW.RescaleType,
                            TagW.VOILUTsData, TagW.VOILUTsExplanation, TagW.PixelPaddingValue,
                            TagW.PixelPaddingRangeLimit, TagW.WindowWidth, TagW.WindowCenter,
                            TagW.WindowCenterWidthExplanation, TagW.VOILutFunction, TagW.PixelSpacing,
                            TagW.ImagerPixelSpacing, TagW.PixelSpacingCalibrationDescription, TagW.PixelAspectRatio };
                    rawIO.copyTags(tagList2, imgRef, false);

                    // Image specific tags
                    rawIO.setTag(TagW.SOPInstanceUID, UIDUtils.createUID());
                    rawIO.setTag(TagW.InstanceNumber, index + 1);

                    if (cpTags == null) {
                        dicom = new DicomImageElement(rawIO, 0);
                    } else {
                        dicom = new DicomImageElement(rawIO, 0) {
                            @Override
                            public boolean saveToFile(File output) {
                                RawImageIO reader = (RawImageIO) getMediaReader();
                                return FileUtil.nioCopyFile(reader.getDicomFile(cpTags), output);
                            }
                        };
                    }
                    dicoms.add(dicom);
                }

            }
        }
    }

    static double getThickness(List<ImageElement> sources) {
        ImageElement firstDcm = sources.get(0);
        ImageElement lastDcm = sources.get(sources.size() - 1);

        double[] p1 = (double[]) firstDcm.getTagValue(TagW.SlicePosition);
        double[] p2 = (double[]) lastDcm.getTagValue(TagW.SlicePosition);
        if (p1 != null && p2 != null) {
            double diff = Math.abs((p2[0] + p2[1] + p2[2]) - (p1[0] + p1[1] + p1[2]));

            Double t1 = (Double) firstDcm.getTagValue(TagW.SliceThickness);
            if (t1 != null) {
                diff += t1 / 2;
            }

            t1 = (Double) lastDcm.getTagValue(TagW.SliceThickness);
            if (t1 != null) {
                diff += t1 / 2;
            }

            return diff;
        }

        return 1.0;

    }

    public static PlanarImage arithmeticOperation(String operation, PlanarImage img1, PlanarImage img2) {
        ParameterBlockJAI pb2 = new ParameterBlockJAI(operation);
        pb2.addSource(img1);
        pb2.addSource(img2);
        return JAI.create(operation, pb2, ImageToolkit.NOCACHE_HINT);
    }

    public static PlanarImage addCollectionOperation(Type mipType, List<ImageElement> sources, View2d view,
        JProgressBar progressBar) {
        if (Type.MIN.equals(mipType)) {
            MinCollectionZprojection op = new MinCollectionZprojection(sources, view, progressBar);
            return op.computeMinCollectionOpImage();
        }
        if (Type.MEAN.equals(mipType)) {
            MeanCollectionZprojection op = new MeanCollectionZprojection(sources, view, progressBar);
            return op.computeMeanCollectionOpImage();
        }
        MaxCollectionZprojection op = new MaxCollectionZprojection(sources, view, progressBar);
        return op.computeMaxCollectionOpImage();
    }

    static void writeRasterInRaw(BufferedImage image, OutputStream out) throws IOException {
        if (out != null && image != null) {
            DataBuffer dataBuffer = image.getRaster().getDataBuffer();
            byte[] bytesOut = null;
            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data =
                    dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();
                bytesOut = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 2] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                }
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();
                bytesOut = new byte[data.length * 4];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 4] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 4 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                    bytesOut[i * 4 + 2] = (byte) ((data[i] >>> 16) & 0xFF);
                    bytesOut[i * 4 + 3] = (byte) ((data[i] >>> 24) & 0xFF);
                }
            }
            out.write(bytesOut);
        }
    }
}
