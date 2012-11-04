package org.weasis.dicom.viewer2d.mpr;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOException;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;
import javax.swing.JProgressBar;
import javax.vecmath.Vector3d;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.TagUtils;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.RawImage;
import org.weasis.dicom.viewer2d.mpr.MprView.Type;

public class SeriesBuilder {
    public static final File MPR_CACHE_DIR = new File(AbstractProperties.APP_TEMP_DIR, "mpr"); //$NON-NLS-1$
    static {
        try {
            MPR_CACHE_DIR.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createMissingSeries(Thread thread, MPRContainer mprContainer, final MprView view)
        throws Exception {
        // TODO test images have all the same size and pixel spacing
        MediaSeries<DicomImageElement> series = view.getSeries();
        if (series != null) {
            SeriesComparator sort = (SeriesComparator) view.getActionValue(ActionW.SORTSTACK.cmd());
            // Get the reverse to write coronal and sagittal images from the head to the feet
            Comparator sortFilter = sort.getReversOrderComparator();
            Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
            DicomImageElement img = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, filter, sortFilter);
            if (img != null) {
                int width = (Integer) img.getTagValue(TagW.Columns);
                int height = (Integer) img.getTagValue(TagW.Rows);
                double[] or = (double[]) img.getTagValue(TagW.ImageOrientationPatient);
                if (or != null && or.length == 6) {
                    double[] pos = (double[]) img.getTagValue(TagW.ImagePositionPatient);
                    if (pos != null && pos.length == 3) {
                        HashMap<TagW, Object> tags = img.getMediaReader().getMediaFragmentTags(0);
                        if (tags != null) {
                            Type type1 = view.getType();
                            Type type2;
                            Type type3;
                            TransposeType rotate2;
                            TransposeType rotate3;
                            if (Type.SAGITTAL.equals(type1)) {
                                type2 = Type.CORONAL;
                                type3 = Type.AXIAL;
                                rotate2 = TransposeDescriptor.ROTATE_270;
                                rotate3 = TransposeDescriptor.ROTATE_270;
                                throw new IllegalStateException("Cannot build MPR from Sagittal view!");
                            } else if (Type.CORONAL.equals(type1)) {
                                type2 = Type.AXIAL;
                                type3 = Type.SAGITTAL;
                                rotate2 = null;
                                rotate3 = TransposeDescriptor.ROTATE_270;
                                throw new IllegalStateException("Cannot build MPR from Coronal view!");
                            } else {
                                type2 = Type.CORONAL;
                                type3 = Type.SAGITTAL;
                                rotate2 = null;
                                rotate3 = TransposeDescriptor.ROTATE_270;
                            }

                            final MprView secView = mprContainer.getMprView(type2);
                            final MprView thirdView = mprContainer.getMprView(type3);
                            if (secView == null || thirdView == null) {
                                return;
                            }

                            final int size = series.size(filter);
                            final JProgressBar[] bar = new JProgressBar[2];
                            GuiExecutor.instance().invokeAndWait(new Runnable() {

                                @Override
                                public void run() {
                                    bar[0] = new JProgressBar(0, size);
                                    bar[1] = new JProgressBar(0, size);
                                    Dimension dim = new Dimension(secView.getWidth() / 2, 30);
                                    bar[0].setSize(dim);
                                    bar[0].setPreferredSize(dim);
                                    bar[0].setMaximumSize(dim);
                                    bar[0].setValue(0);
                                    bar[0].setStringPainted(true);
                                    Dimension dim2 = new Dimension(thirdView.getWidth() / 2, 30);
                                    bar[1].setSize(dim2);
                                    bar[1].setPreferredSize(dim2);
                                    bar[1].setMaximumSize(dim2);
                                    bar[1].setValue(0);
                                    bar[1].setStringPainted(true);
                                    secView.setProgressBar(bar[0]);
                                    thirdView.setProgressBar(bar[1]);
                                    secView.repaint();
                                    thirdView.repaint();
                                }
                            });

                            Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);
                            double origPixSize = img.getPixelSize();
                            String seriesID = (String) series.getTagValue(TagW.SubseriesInstanceUID);

                            RawImage[] secSeries = new RawImage[height];
                            double sPixSize = writeBlock(secSeries, series, medias, rotate2, secView, thread);

                            if (origPixSize * width < sPixSize * size) {
                                // origPixSize = origPixSize / sPixSize;
                                sPixSize = 1.0 / sPixSize;
                            }
                            Vector3d vr = new Vector3d(or[0], or[1], or[2]);
                            Vector3d vc = new Vector3d(or[3], or[4], or[5]);

                            Vector3d resc = new Vector3d(0.0, 0.0, 0.0);
                            rotate(vc, vr, -Math.toRadians(90), resc);
                            double[] imgOr = new double[] { or[0], or[1], or[2], resc.x, resc.y, resc.z };

                            if (thread.isInterrupted()) {
                                return;
                            }
                            final DicomSeries dicomSeries =
                                buildDicomSeriesFromRaw(secSeries, new Dimension(width, size), img, false, seriesID,
                                    origPixSize, sPixSize, pos, imgOr);
                            final DicomObject dcmObj = ((DicomMediaIO) img.getMediaReader()).getDicomObject();

                            if (dicomSeries != null) {
                                GuiExecutor.instance().execute(new Runnable() {

                                    @Override
                                    public void run() {
                                        secView.setProgressBar(null);
                                        // Copy tags from original dicom into series
                                        DicomMediaIO.writeMetaData(dicomSeries, dcmObj);
                                        secView.setSeries(dicomSeries);
                                        // Copy the synch values from the main view
                                        for (ActionW action : MPRContainer.DEFAULT_MPR.getActions().keySet()) {
                                            secView.setActionsInView(action.cmd(), view.getActionValue(action.cmd()));
                                        }
                                        secView.repaint();
                                    }
                                });

                            }

                            // Build Third Series
                            RawImage[] thirdSeries = new RawImage[width];
                            writeBlock(thirdSeries, series, medias, rotate3, thirdView, thread);

                            Vector3d resr = new Vector3d(0.0, 0.0, 0.0);
                            rotate(vr, vc, Math.toRadians(90), resr);

                            double[] imgOr2 = new double[] { or[3], or[4], or[5], resr.x, resr.y, resr.z };

                            if (thread.isInterrupted()) {
                                return;
                            }
                            final DicomSeries dicomSeries2 =
                                buildDicomSeriesFromRaw(thirdSeries, new Dimension(height, size), img, true, seriesID,
                                    origPixSize, sPixSize, pos, imgOr2);
                            if (dicomSeries2 != null) {
                                GuiExecutor.instance().execute(new Runnable() {

                                    @Override
                                    public void run() {
                                        thirdView.setProgressBar(null);
                                        // Copy tags from original dicom into series
                                        DicomMediaIO.writeMetaData(dicomSeries2, dcmObj);
                                        thirdView.setSeries(dicomSeries2);
                                        for (ActionW action : MPRContainer.DEFAULT_MPR.getActions().keySet()) {
                                            thirdView.setActionsInView(action.cmd(), view.getActionValue(action.cmd()));
                                        }
                                        thirdView.repaint();
                                    }
                                });

                            }
                        }
                    }
                }
            }
        }
    }

    private static DicomSeries buildDicomSeriesFromRaw(RawImage[] newSeries, Dimension dim, DicomImageElement img,
        boolean rotate, String seriesID, double origPixSize, double sPixSize, double[] pos, double[] imOr)
        throws Exception {

        String prefix = rotate ? "m3." : "m2.";
        int bitsAllocated = img.getBitsAllocated();
        int bitsStored = img.getBitsStored();
        String photometricInterpretation = img.getPhotometricInterpretation();

        int last = newSeries.length;
        List<DicomImageElement> dcms = new ArrayList<DicomImageElement>();

        for (int i = 0; i < newSeries.length; i++) {
            File inFile = newSeries[i].getFile();
            RawImageIO rawIO = new RawImageIO(inFile.toURI(), null);
            // Tags with same values for all the Series
            rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
            rawIO.setTag(TagW.Columns, dim.width);
            rawIO.setTag(TagW.Rows, dim.height);
            rawIO.setTag(TagW.SliceThickness, origPixSize);
            rawIO.setTag(TagW.PixelSpacing, new double[] { sPixSize, origPixSize });
            rawIO.setTag(TagW.SeriesInstanceUID, prefix + seriesID);
            rawIO.setTag(TagW.ImageOrientationPatient, imOr);

            rawIO.setTag(TagW.BitsAllocated, bitsAllocated);
            rawIO.setTag(TagW.BitsStored, bitsStored);
            rawIO.setTag(TagW.PixelRepresentation, img.getTagValue(TagW.PixelRepresentation));
            rawIO.setTag(TagW.Units, img.getTagValue(TagW.Units));
            rawIO.setTag(TagW.ImageType, img.getTagValue(TagW.ImageType));
            rawIO.setTag(TagW.SamplesPerPixel, img.getTagValue(TagW.SamplesPerPixel));
            rawIO.setTag(TagW.PhotometricInterpretation, photometricInterpretation);
            rawIO.setTag(TagW.MonoChrome, img.getTagValue(TagW.MonoChrome));
            rawIO.setTag(TagW.Modality, img.getTagValue(TagW.Modality));

            // TODO take dicom tags from middle image? what to do when values are not constant in the series?
            rawIO.setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                img.getTagValue(TagW.PixelSpacingCalibrationDescription));
            rawIO.setTagNoNull(TagW.ModalityLUTSequence, img.getTagValue(TagW.ModalityLUTSequence));
            rawIO.setTagNoNull(TagW.RescaleSlope, img.getTagValue(TagW.RescaleSlope));
            rawIO.setTagNoNull(TagW.RescaleIntercept, img.getTagValue(TagW.RescaleIntercept));
            rawIO.setTagNoNull(TagW.RescaleType, img.getTagValue(TagW.RescaleType));
            // rawIO.setTagNoNull(TagW.SmallestImagePixelValue, img.getTagValue(TagW.SmallestImagePixelValue));
            // rawIO.setTagNoNull(TagW.LargestImagePixelValue, img.getTagValue(TagW.LargestImagePixelValue));
            rawIO.setTagNoNull(TagW.PixelPaddingValue, img.getTagValue(TagW.PixelPaddingValue));
            rawIO.setTagNoNull(TagW.PixelPaddingRangeLimit, img.getTagValue(TagW.PixelPaddingRangeLimit));

            rawIO.setTagNoNull(TagW.VOILUTSequence, img.getTagValue(TagW.VOILUTSequence));
            // rawIO.setTagNoNull(TagW.WindowWidth, img.getTagValue(TagW.WindowWidth));
            // rawIO.setTagNoNull(TagW.WindowCenter, img.getTagValue(TagW.WindowCenter));
            // rawIO.setTagNoNull(TagW.WindowCenterWidthExplanation,
            // img.getTagValue(TagW.WindowCenterWidthExplanation));
            rawIO.setTagNoNull(TagW.VOILutFunction, img.getTagValue(TagW.VOILutFunction));

            // Image specific tags
            int index = i;
            rawIO.setTag(TagW.SOPInstanceUID, prefix + (rotate ? last - index : index + 1));
            rawIO.setTag(TagW.InstanceNumber, rotate ? last - index : index + 1);
            double location = (rotate ? pos[0] : pos[1]) + (rotate ? last - index - 1 : index) * origPixSize;

            rawIO.setTag(TagW.SliceLocation, location);
            rawIO.setTag(TagW.ImagePositionPatient, new double[] { rotate ? location : pos[0],
                rotate ? pos[1] : location, pos[2] });

            HashMap<TagW, Object> tagList = rawIO.getMediaFragmentTags(null);
            DicomMediaIO.validateDicomImageValues(tagList);
            DicomMediaIO.computeSlicePositionVector(tagList);
            DicomImageElement dcm = new DicomImageElement(rawIO, 0);
            dcms.add(dcm);
        }

        DicomSeries dicomSeries = new DicomSeries(TagW.SubseriesInstanceUID, prefix + seriesID, dcms);
        return dicomSeries;
    }

    private static DicomSeries buildDicomSeries(RawImage[] newSeries, Dimension dim, DicomObject dcmObj,
        boolean rotate, String seriesID, double origPixSize, double sPixSize, double[] pos, double[] imOr)
        throws Exception {
        // clean tags
        removeAllPrivateTags(dcmObj);

        String prefix = rotate ? "m3." : "m2.";

        dcmObj.putString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);

        dcmObj.putInt(Tag.Columns, VR.US, dim.width);
        dcmObj.putInt(Tag.Rows, VR.US, dim.height);
        dcmObj.putDouble(Tag.SliceThickness, VR.DS, origPixSize);
        dcmObj.putDoubles(Tag.PixelSpacing, VR.DS, new double[] { sPixSize, origPixSize });
        dcmObj.putString(Tag.SeriesInstanceUID, VR.UI, prefix + seriesID);
        dcmObj.putDoubles(Tag.ImageOrientationPatient, VR.DS, imOr);

        int last = newSeries.length;
        for (int i = 0; i < newSeries.length; i++) {

            byte[] bytesOut = getBytesFromFile(newSeries[i].getFile());
            if (bytesOut == null) {
                throw new IllegalAccessException("Cannot read raw image!");
            }
            int index = i;
            dcmObj.putString(Tag.SOPInstanceUID, VR.UI, prefix + (rotate ? last - index : index + 1));
            dcmObj.putInt(Tag.InstanceNumber, VR.IS, rotate ? last - index : index + 1);
            double location = (rotate ? pos[0] : pos[1]) + (rotate ? last - index - 1 : index) * origPixSize;

            dcmObj.putDouble(Tag.SliceLocation, VR.DS, location);
            dcmObj.putDoubles(Tag.ImagePositionPatient, VR.DS, new double[] { rotate ? location : pos[0],
                rotate ? pos[1] : location, pos[2] });

            dcmObj.putBytes(Tag.PixelData, VR.OW, bytesOut);

            writeDICOM(newSeries[i], dcmObj);

            newSeries[i].getFile().delete();

        }

        DicomSeries dicomSeries = new DicomSeries(prefix + seriesID);
        for (int i = 0; i < newSeries.length; i++) {
            File inFile = newSeries[i].getFile();
            String name = FileUtil.nameWithoutExtension(inFile.getName());
            File file = new File(MPR_CACHE_DIR, name + ".dcm");
            if (file.canRead()) {
                DicomMediaIO dicomReader = new DicomMediaIO(file);
                if (dicomReader.readMediaTags()) {
                    try {
                        if (i == 0) {
                            dicomReader.writeMetaData(dicomSeries);
                        }

                        MediaElement[] medias = dicomReader.getMediaElement();
                        if (medias != null) {
                            for (MediaElement media : medias) {
                                dicomSeries.add((DicomImageElement) media);
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        dicomReader.reset();
                    }
                }
            }
        }

        return dicomSeries;
    }

    private static double writeBlock(RawImage[] newSeries, MediaSeries<DicomImageElement> series,
        Iterable<DicomImageElement> medias, TransposeType rotate, final MprView view, Thread thread) throws IOException {
        boolean abort = false;
        final JProgressBar bar = view.getProgressBar();
        try {
            for (int i = 0; i < newSeries.length; i++) {
                newSeries[i] = new RawImage(File.createTempFile("mpr_", ".raw", MPR_CACHE_DIR));//$NON-NLS-1$ //$NON-NLS-2$);
            }
            // synchronized (series) {
            double epsilon = 1e-3;
            double lastPos = 0.0;
            double lastSpace = 0.0;
            int index = 0;
            Iterator<DicomImageElement> iter = medias.iterator();
            while (iter.hasNext()) {
                if (thread.isInterrupted()) {
                    return lastSpace;
                }
                DicomImageElement dcm = iter.next();
                double[] sp = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (sp == null) {
                    abort = true;
                    throw new IllegalStateException("Slice doesn't have a 3D position!");
                } else {
                    double pos = (sp[0] + sp[1] + sp[2]);
                    if (index > 0) {
                        double space = Math.abs(pos - lastPos);
                        if (space == 0.0 || (index > 1 && lastSpace - space > epsilon)) {
                            abort = true;
                            throw new IllegalStateException("Space between slices is not constant!");
                        }
                        lastSpace = space;
                    }
                    lastPos = pos;
                    index++;
                    if (bar != null) {
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                bar.setValue(bar.getValue() + 1);
                                view.repaint();
                            }
                        });
                    }
                }
                // TODO do not open more than 512 files (Limitation to open 1024 in the same
                // time on Ubuntu)
                PlanarImage image = dcm.getImage();
                if (image == null) {
                    abort = true;
                    throw new IIOException("Cannot read an image!");
                }
                writeRasterInRaw(getRotateImage(image, rotate), newSeries);
            }
            return lastSpace;
            // }
        } finally {
            for (int i = 0; i < newSeries.length; i++) {
                if (newSeries[i] != null) {
                    newSeries[i].disposeOutputStream();
                    if (abort) {
                        newSeries[i].getFile().delete();
                    }
                }
            }
        }
    }

    private static void writeRasterInRaw(BufferedImage image, RawImage[] newSeries) throws IOException {
        if (newSeries != null && image != null && image.getHeight() == newSeries.length) {

            DataBuffer dataBuffer = image.getRaster().getDataBuffer();
            int width = image.getWidth();
            int height = newSeries.length;
            byte[] bytesOut = null;
            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
                for (int j = 0; j < height; j++) {
                    newSeries[j].getOutputStream().write(bytesOut, j * width, width);
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data =
                    dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();
                bytesOut = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 2] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 2 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                }
                width *= 2;
                for (int j = 0; j < height; j++) {
                    newSeries[j].getOutputStream().write(bytesOut, j * width, width);
                }
            }
        }
    }

    private static BufferedImage getRotateImage(PlanarImage source, TransposeType rotate) {
        if (rotate == null) {
            return source == null ? null : source.getAsBufferedImage();
        }
        RenderedOp result;
        // use Transpose operation
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(source);
        pb.add(rotate);
        result = JAI.create("transpose", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        // Handle non square images. Translation is necessary because the transpose operator keeps the same
        // origin (top left not the center of the image)
        float diffw = source.getWidth() / 2.0f - result.getWidth() / 2.0f;
        float diffh = source.getHeight() / 2.0f - result.getHeight() / 2.0f;
        if (diffw != 0.0f || diffh != 0.0f) {
            pb = new ParameterBlock();
            pb.addSource(result);
            pb.add(diffw);
            pb.add(diffh);
            result = JAI.create("translate", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        }
        return result.getAsBufferedImage();
    }

    private static void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x =
            axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.x
                * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y =
            axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.y
                * Math.cos(angle) + (axis.z * vSrc.x + axis.x * vSrc.z) * Math.sin(angle);
        vDst.z =
            axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.z
                * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
    }

    private static void removeAllPrivateTags(DicomObject dcmObj) {
        Iterator it = dcmObj.datasetIterator();
        while (it.hasNext()) {
            DicomElement element = (DicomElement) it.next();
            if (TagUtils.isPrivateDataElement(element.tag())) {
                dcmObj.remove(element.tag());
            }
        }
    }

    private static boolean writeDICOM(RawImage newSeries, DicomObject dcmObj) throws Exception {
        DicomOutputStream out = null;
        DicomInputStream dis = null;
        File inFile = newSeries.getFile();
        String name = FileUtil.nameWithoutExtension(inFile.getName());
        File outFile = new File(MPR_CACHE_DIR, name + ".dcm");

        try {
            out = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            out.writeDicomFile(dcmObj);
        } catch (IOException e) {
            //     LOGGER.warn("", e); //$NON-NLS-1$
            outFile.delete();
            return false;
        } finally {
            FileUtil.safeClose(out);
            FileUtil.safeClose(dis);
        }
        return true;
    }

    public static byte[] getBytesFromFile(File file) {
        FileInputStream is = null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            is = new FileInputStream(file);
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(is);
        }
        return null;
    }

}
