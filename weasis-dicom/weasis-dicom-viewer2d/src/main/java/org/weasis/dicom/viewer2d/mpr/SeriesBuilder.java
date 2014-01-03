package org.weasis.dicom.viewer2d.mpr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOException;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Attributes.Visitor;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.image.PhotometricInterpretation;
import org.dcm4che.util.TagUtils;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.RawImage;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class SeriesBuilder {
    public static final File MPR_CACHE_DIR = AbstractProperties.buildAccessibleTempDirectory(
        AbstractProperties.FILE_CACHE_DIR.getName(), "mpr"); //$NON-NLS-1$

    public static void createMissingSeries(Thread thread, MPRContainer mprContainer, final MprView view)
        throws Exception {
        // TODO test images have all the same size and pixel spacing
        MediaSeries<DicomImageElement> series = view.getSeries();
        if (series != null) {
            SliceOrientation type1 = view.getSliceOrientation();
            if (type1 != null) {

                String seriesID = (String) series.getTagValue(TagW.SubseriesInstanceUID);
                Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());

                // Get image stack sort from Reference Coordinates System
                DicomImageElement img =
                    SliceOrientation.CORONAL.equals(type1) ? series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, filter,
                        SortSeriesStack.slicePosition) : series.getMedia(MediaSeries.MEDIA_POSITION.LAST, filter,
                        SortSeriesStack.slicePosition);
                if (img != null && img.getMediaReader() instanceof DicomMediaIO) {
                    GeometryOfSlice geometry = img.getSliceGeometry();
                    if (geometry != null) {
                        // abort needs to be final array to be changed on "invoqueAndWhait()" block.
                        final boolean[] abort = new boolean[] { false, false };
                        Tuple3d voxelSpacing = geometry.getVoxelSpacing();
                        if (voxelSpacing.x != voxelSpacing.y) {
                            confirmMessage(view, "Images have non square pixels!", abort);
                        }

                        int width = (Integer) img.getTagValue(TagW.Columns);
                        int height = (Integer) img.getTagValue(TagW.Rows);

                        Float tilt = (Float) img.getTagValue(TagW.GantryDetectorTilt);
                        if (tilt != null && tilt != 0.0f) {
                            confirmMessage(view, "Images have gantry tilt!", abort);
                        }
                        HashMap<TagW, Object> tags = img.getMediaReader().getMediaFragmentTags(0);
                        if (tags != null) {
                            double[] row = geometry.getRowArray();
                            double[] col = geometry.getColumnArray();
                            Vector3d vr = new Vector3d(row);
                            Vector3d vc = new Vector3d(col);
                            Vector3d resr = new Vector3d();
                            Vector3d resc = new Vector3d();

                            final ViewParameter[] recParams = new ViewParameter[2];

                            if (SliceOrientation.SAGITTAL.equals(type1)) {
                                // The reference image is the first of the saggital stack (Left)
                                rotate(vc, vr, Math.toRadians(270), resr);
                                recParams[0] =
                                    new ViewParameter(".2", SliceOrientation.AXIAL, false, null, new double[] { resr.x,
                                        resr.y, resr.z, row[0], row[1], row[2] }, true, true,
                                        new Object[] { 0.0, false });
                                recParams[1] =
                                    new ViewParameter(".3", SliceOrientation.CORONAL, false,
                                        TransposeDescriptor.ROTATE_270, new double[] { resr.x, resr.y, resr.z, col[0],
                                            col[1], col[2] }, true, true, new Object[] { true, 0.0 });
                            } else if (SliceOrientation.CORONAL.equals(type1)) {
                                // The reference image is the first of the coronal stack (Anterior)
                                rotate(vc, vr, Math.toRadians(90), resc);
                                recParams[0] =
                                    new ViewParameter(".2", SliceOrientation.AXIAL, false, null, new double[] { row[0],
                                        row[1], row[2], resc.x, resc.y, resc.z }, false, true, new Object[] { 0.0,
                                        false });

                                rotate(vc, vr, Math.toRadians(90), resr);
                                recParams[1] =
                                    new ViewParameter(".3", SliceOrientation.SAGITTAL, true,
                                        TransposeDescriptor.ROTATE_270, new double[] { resr.x, resr.y, resr.z, col[0],
                                            col[1], col[2] }, true, false, new Object[] { true, 0.0 });
                            } else {
                                // The reference image is the last of the axial stack (Head)
                                rotate(vc, vr, Math.toRadians(270), resc);
                                recParams[0] =
                                    new ViewParameter(".2", SliceOrientation.CORONAL, true, null, new double[] {
                                        row[0], row[1], row[2], resc.x, resc.y, resc.z }, false, false, new Object[] {
                                        0.0, false });

                                rotate(vr, vc, Math.toRadians(90), resr);
                                recParams[1] =
                                    new ViewParameter(".3", SliceOrientation.SAGITTAL, true,
                                        TransposeDescriptor.ROTATE_270, new double[] { col[0], col[1], col[2], resr.x,
                                            resr.y, resr.z }, false, false, new Object[] { true, 0.0 });

                            }

                            final MprView[] recView = new MprView[2];
                            recView[0] = mprContainer.getMprView(recParams[0].sliceOrientation);
                            recView[1] = mprContainer.getMprView(recParams[1].sliceOrientation);
                            if (recView[0] == null || recView[1] == null) {
                                return;
                            }
                            final MprView mainView = mprContainer.getMprView(type1);
                            mainView.zoom(0.0);
                            mainView.center();

                            final boolean[] needBuild = new boolean[2];
                            MediaSeriesGroup study = null;
                            DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
                            TreeModel treeModel = null;
                            if (model instanceof TreeModel) {
                                treeModel = (TreeModel) model;
                                study = treeModel.getParent(series, DicomModel.study);
                                if (study != null) {
                                    for (int i = 0; i < 2; i++) {
                                        final MediaSeriesGroup group =
                                            treeModel.getHierarchyNode(study, seriesID + recParams[i].suffix);
                                        needBuild[i] = group == null;
                                        if (!needBuild[i]) {
                                            final MprView mprView = recView[i];
                                            GuiExecutor.instance().execute(new Runnable() {

                                                @Override
                                                public void run() {
                                                    mprView.setSeries((MediaSeries<DicomImageElement>) group);
                                                    // Copy the synch values from the main view
                                                    for (String action : MPRContainer.DEFAULT_MPR.getSynchData()
                                                        .getActions().keySet()) {
                                                        mprView.setActionsInView(action, view.getActionValue(action));
                                                    }
                                                    mprView.zoom(mainView.getViewModel().getViewScale());
                                                    mprView.center();
                                                    mprView.repaint();
                                                }
                                            });
                                        }
                                    }
                                }
                            }

                            final int size = series.size(filter);
                            final JProgressBar[] bar = new JProgressBar[2];
                            GuiExecutor.instance().invokeAndWait(new Runnable() {

                                @Override
                                public void run() {
                                    for (int i = 0; i < 2; i++) {
                                        if (needBuild[i]) {
                                            bar[i] = new JProgressBar(0, size);
                                            Dimension dim = new Dimension(recView[i].getWidth() / 2, 30);
                                            bar[i].setSize(dim);
                                            bar[i].setPreferredSize(dim);
                                            bar[i].setMaximumSize(dim);
                                            bar[i].setValue(0);
                                            bar[i].setStringPainted(true);
                                            recView[i].setProgressBar(bar[i]);
                                            recView[i].repaint();
                                        }
                                    }
                                }
                            });

                            // Get the image in the middle of the series for having better default W/L values
                            img =
                                series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, filter,
                                    SortSeriesStack.slicePosition);

                            for (int i = 0; i < 2; i++) {
                                if (needBuild[i]) {
                                    final MprView mprView = recView[i];
                                    final ViewParameter viewParams = recParams[i];

                                    Iterable<DicomImageElement> medias =
                                        series.copyOfMedias(filter, viewParams.reverseSeriesOrder
                                            ? SortSeriesStack.slicePosition.getReversOrderComparator()
                                            : SortSeriesStack.slicePosition);
                                    double origPixSize = img.getPixelSize();

                                    RawImage[] secSeries = new RawImage[i == 0 ? height : width];
                                    /*
                                     * Write the new image by tacking the lines (from first to last) of all the images
                                     * of the original series stack
                                     */
                                    double sPixSize =
                                        writeBlock(secSeries, series, medias, viewParams, mprView, thread, abort,
                                            seriesID);

                                    if (thread.isInterrupted()) {
                                        return;
                                    }
                                    /*
                                     * Reconstruct dicom files, adapt position, orientation, pixel spacing, instance
                                     * number and UIDs.
                                     */
                                    final DicomSeries dicomSeries =
                                        buildDicomSeriesFromRaw(secSeries,
                                            new Dimension(i == 0 ? width : height, size), img, viewParams, seriesID,
                                            origPixSize, sPixSize, geometry, mprView);
                                    final Attributes attributes =
                                        ((DicomMediaIO) img.getMediaReader()).getDicomObject();

                                    if (dicomSeries != null) {
                                        if (study != null && treeModel != null) {
                                            dicomSeries.setTag(TagW.ExplorerModel, model);
                                            treeModel.addHierarchyNode(study, dicomSeries);
                                            if (treeModel instanceof DicomModel) {
                                                DicomModel dicomModel = (DicomModel) treeModel;
                                                dicomModel.firePropertyChange(new ObservableEvent(
                                                    ObservableEvent.BasicAction.Add, dicomModel, null, dicomSeries));
                                            }
                                        }

                                        GuiExecutor.instance().execute(new Runnable() {

                                            @Override
                                            public void run() {
                                                mprView.setProgressBar(null);
                                                // Copy tags from original dicom into series
                                                DicomMediaUtils.writeMetaData(dicomSeries, attributes);
                                                mprView.setSeries(dicomSeries);
                                                // Copy the synch values from the main view
                                                for (String action : MPRContainer.DEFAULT_MPR.getSynchData()
                                                    .getActions().keySet()) {
                                                    mprView.setActionsInView(action, view.getActionValue(action));
                                                }
                                                mprView.zoom(mainView.getViewModel().getViewScale());
                                                mprView.center();
                                                mprView.repaint();
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static DicomSeries buildDicomSeriesFromRaw(final RawImage[] newSeries, Dimension dim,
        DicomImageElement img, ViewParameter params, String seriesID, double origPixSize, double sPixSize,
        GeometryOfSlice geometry, final MprView view) throws Exception {

        String recSeriesID = seriesID + params.suffix;
        int bitsAllocated = img.getBitsAllocated();
        int bitsStored = img.getBitsStored();
        double[] pixSpacing = new double[] { sPixSize, origPixSize };

        Attributes attributes = null;
        int dataType = 0;
        ColorModel cm = null;
        SampleModel sampleModel = null;
        final JProgressBar bar = view.getProgressBar();

        if (params.rotateOutputImg) {
            if (bar != null) {
                GuiExecutor.instance().execute(new Runnable() {

                    @Override
                    public void run() {
                        bar.setMaximum(newSeries.length);
                        bar.setValue(0);
                        // Force to reset the progress bar (substance)
                        bar.updateUI();
                        view.repaint();
                    }
                });
            }

            pixSpacing = new double[] { origPixSize, sPixSize };

            attributes = ((DicomMediaIO) img.getMediaReader()).getDicomObject();

            int samplesPerPixel = (Integer) img.getTagValue(TagW.SamplesPerPixel);
            boolean banded =
                samplesPerPixel > 1
                    && DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.PlanarConfiguration, 0) != 0;
            int pixelRepresentation =
                DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.PixelRepresentation, 0);
            dataType =
                bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE : pixelRepresentation != 0 ? DataBuffer.TYPE_SHORT
                    : DataBuffer.TYPE_USHORT;
            if (bitsAllocated > 16 && samplesPerPixel == 1) {
                dataType = DataBuffer.TYPE_INT;
            }

            String photometricInterpretation = (String) img.getTagValue(TagW.PhotometricInterpretation);
            PhotometricInterpretation pmi = PhotometricInterpretation.fromString(photometricInterpretation);
            cm = pmi.createColorModel(bitsStored, dataType, attributes);
            sampleModel = pmi.createSampleModel(dataType, dim.width, dim.height, samplesPerPixel, banded);

            int tmp = dim.width;
            dim.width = dim.height;
            dim.height = tmp;
        }

        int last = newSeries.length;
        List<DicomImageElement> dcms = new ArrayList<DicomImageElement>();

        for (int i = 0; i < newSeries.length; i++) {
            File inFile = newSeries[i].getFile();
            if (params.rotateOutputImg) {

                ByteBuffer byteBuffer = getBytesFromFile(inFile);
                DataBuffer dataBuffer = null;
                if (dataType == DataBuffer.TYPE_BYTE) {
                    dataBuffer = new DataBufferByte(byteBuffer.array(), byteBuffer.limit());
                } else if (dataType <= DataBuffer.TYPE_SHORT) {
                    ShortBuffer sBuffer = byteBuffer.asShortBuffer();
                    short[] data;
                    if (sBuffer.hasArray()) {
                        data = sBuffer.array();
                    } else {
                        data = new short[byteBuffer.limit() / 2];
                        for (int k = 0; k < data.length; k++) {
                            if (byteBuffer.hasRemaining()) {
                                data[k] = byteBuffer.getShort();
                            }
                        }
                    }
                    dataBuffer =
                        dataType == DataBuffer.TYPE_SHORT ? new DataBufferShort(data, data.length)
                            : new DataBufferUShort(data, data.length);
                } else if (dataType == DataBuffer.TYPE_INT) {
                    IntBuffer sBuffer = byteBuffer.asIntBuffer();
                    int[] data;
                    if (sBuffer.hasArray()) {
                        data = sBuffer.array();
                    } else {
                        data = new int[byteBuffer.limit() / 4];
                        for (int k = 0; k < data.length; k++) {
                            if (byteBuffer.hasRemaining()) {
                                data[k] = byteBuffer.getInt();
                            }
                        }
                    }
                    dataBuffer = new DataBufferInt(data, data.length);
                }

                WritableRaster raster = RasterFactory.createWritableRaster(sampleModel, dataBuffer, null);
                BufferedImage bufImg = new BufferedImage(cm, raster, false, null);
                bufImg = getImage(bufImg, TransposeDescriptor.ROTATE_90);

                dataBuffer = bufImg.getRaster().getDataBuffer();
                if (dataBuffer instanceof DataBufferByte) {
                    byteBuffer = ByteBuffer.wrap(((DataBufferByte) dataBuffer).getData());
                    writToFile(inFile, byteBuffer);
                } else if (dataBuffer instanceof DataBufferShort) {
                    short[] data =
                        dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                            : ((DataBufferUShort) dataBuffer).getData();

                    byteBuffer = ByteBuffer.allocate(data.length * 2);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
                    shortBuffer.put(data);

                    writToFile(inFile, byteBuffer);
                }
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
            RawImageIO rawIO = new RawImageIO(inFile.toURI(), null);
            // Tags with same values for all the Series
            rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
            rawIO.setTag(TagW.Columns, dim.width);
            rawIO.setTag(TagW.Rows, dim.height);
            rawIO.setTag(TagW.SliceThickness, origPixSize);
            rawIO.setTag(TagW.PixelSpacing, pixSpacing);
            rawIO.setTag(TagW.SeriesInstanceUID, recSeriesID);
            rawIO.setTag(TagW.ImageOrientationPatient, params.imgOrientation);

            rawIO.setTag(TagW.BitsAllocated, bitsAllocated);
            rawIO.setTag(TagW.BitsStored, bitsStored);

            TagW[] tagList =
                { TagW.PhotometricInterpretation, TagW.PixelRepresentation, TagW.Units, TagW.ImageType,
                    TagW.SamplesPerPixel, TagW.MonoChrome, TagW.Modality, };
            rawIO.copyTags(tagList, img, true);

            TagW[] tagList2 =
                { TagW.ModalityLUTData, TagW.ModalityLUTType, TagW.ModalityLUTExplanation, TagW.RescaleSlope,
                    TagW.RescaleIntercept, TagW.RescaleType, TagW.VOILUTsData, TagW.VOILUTsExplanation,
                    TagW.PixelPaddingValue, TagW.PixelPaddingRangeLimit, TagW.WindowWidth, TagW.WindowCenter,
                    TagW.WindowCenterWidthExplanation, TagW.VOILutFunction, TagW.PixelSpacingCalibrationDescription, };
            // TagW.SmallestImagePixelValue,TagW.LargestImagePixelValue,
            rawIO.copyTags(tagList2, img, false);

            // Clone array, because values are adapted accordint the min and max pixel values.
            TagW[] tagList3 = { TagW.WindowWidth, TagW.WindowCenter };
            for (int j = 0; j < tagList3.length; j++) {
                Float[] val = (Float[]) img.getTagValue(tagList3[j]);
                if (val != null) {
                    img.setTag(tagList3[j], val.clone());
                }
            }

            // Image specific tags
            int index = i;
            rawIO.setTag(TagW.SOPInstanceUID, (params.reverseIndexOrder ? last - index : index + 1) + params.suffix);
            rawIO.setTag(TagW.InstanceNumber, params.reverseIndexOrder ? last - index : index + 1);

            double x =
                (params.imgPosition[0] instanceof Double) ? (Double) params.imgPosition[0]
                    : (Boolean) params.imgPosition[0] ? last - index - 1 : index;
            double y =
                (params.imgPosition[1] instanceof Double) ? (Double) params.imgPosition[1]
                    : (Boolean) params.imgPosition[1] ? last - index - 1 : index;
            Point3d p = geometry.getPosition(new Point2D.Double(x, y));
            rawIO.setTag(TagW.ImagePositionPatient, new double[] { p.x, p.y, p.z });

            HashMap<TagW, Object> tagList4 = rawIO.getMediaFragmentTags(null);
            DicomMediaUtils.buildLUTs(tagList4);
            DicomMediaUtils.computeSlicePositionVector(tagList4);
            double[] loc = (double[]) tagList4.get(TagW.SlicePosition);
            if (loc != null) {
                rawIO.setTag(TagW.SliceLocation, loc[0] + loc[1] + loc[2]);
            }
            DicomImageElement dcm = new DicomImageElement(rawIO, 0);
            dcms.add(dcm);
        }

        DicomSeries dicomSeries = new DicomSeries(TagW.SubseriesInstanceUID, recSeriesID, dcms);
        return dicomSeries;
    }

    private static double writeBlock(RawImage[] newSeries, MediaSeries<DicomImageElement> series,
        Iterable<DicomImageElement> medias, ViewParameter params, final MprView view, Thread thread,
        final boolean[] abort, String seriesID) throws IOException {

        // TODO should return the more frequent space!
        final JProgressBar bar = view.getProgressBar();
        try {
            File dir = new File(MPR_CACHE_DIR, seriesID + params.suffix);
            dir.mkdirs();
            for (int i = 0; i < newSeries.length; i++) {
                newSeries[i] = new RawImage(new File(dir, "mpr_" + (i + 1)));//$NON-NLS-1$ //$NON-NLS-2$);
            }
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
                if (sp == null && !abort[1]) {
                    confirmMessage(view, "Space between slices is unpredictable!", abort);
                } else {
                    double pos = (sp[0] + sp[1] + sp[2]);
                    if (index > 0) {
                        double space = Math.abs(pos - lastPos);
                        if (!abort[1] && (space == 0.0 || (index > 1 && lastSpace - space > epsilon))) {
                            confirmMessage(view, "Space between slices is not regular!", abort);
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
                    abort[0] = true;
                    throw new IIOException("Cannot read an image!");
                }
                writeRasterInRaw(getImage(image, params.transposeImage), newSeries);
            }
            return lastSpace;
        } finally {
            for (int i = 0; i < newSeries.length; i++) {
                if (newSeries[i] != null) {
                    newSeries[i].disposeOutputStream();
                    if (abort[0]) {
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

    private static BufferedImage getImage(PlanarImage source, TransposeType rotate) {
        if (rotate == null) {
            return source == null ? null : source.getAsBufferedImage();
        }
        return getRotatedImage(source, rotate);
    }

    private static BufferedImage getImage(BufferedImage source, TransposeType rotate) {
        if (rotate == null) {
            return source == null ? null : source;
        }
        return getRotatedImage(source, rotate);
    }

    private static BufferedImage getRotatedImage(RenderedImage source, TransposeType rotate) {
        RenderedOp result;
        if (source instanceof BufferedImage) {
            source = PlanarImage.wrapRenderedImage(source);
        }
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
                * Math.cos(angle) + (axis.z * vSrc.x - axis.x * vSrc.z) * Math.sin(angle);
        vDst.z =
            axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.z
                * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
    }

    private static void removeAllPrivateTags(Attributes item) {
        // TODO remove them or skip when reading?
        Visitor visitor = new Visitor() {

            @Override
            public void visit(Attributes item, int tag, VR vr, Object value) {
                if (TagUtils.isPrivateTag(tag)) {
                    item.setNull(tag, vr);
                }
            }
        };
        item.accept(visitor);
    }

    public static ByteBuffer getBytesFromFile(File file) {
        FileInputStream is = null;
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) file.length());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            is = new FileInputStream(file);
            FileChannel in = is.getChannel();
            in.read(byteBuffer);
            byteBuffer.flip();
            return byteBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(is);
        }
        return null;
    }

    public static void writToFile(File file, ByteBuffer byteBuffer) {
        FileOutputStream os = null;
        try {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            os = new FileOutputStream(file);
            FileChannel out = os.getChannel();
            out.write(byteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(os);
        }
    }

    public static void confirmMessage(final Component view, final String message, final boolean[] abort) {
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                int usrChoice =
                    JOptionPane.showConfirmDialog(view, message
                        + "\nThe image may be displayed incorrectly.\n Do you want to continue anyway?",
                        MPRFactory.NAME, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (usrChoice == JOptionPane.NO_OPTION) {
                    abort[0] = true;
                } else {
                    // bypass for other similar messages
                    abort[1] = true;
                }
            }
        });
        if (abort[0]) {
            throw new IllegalStateException(message);
        }
    }

    static class ViewParameter {
        final String suffix;
        final SliceOrientation sliceOrientation;
        final boolean reverseSeriesOrder;
        final TransposeType transposeImage;
        final double[] imgOrientation;
        final boolean rotateOutputImg;
        final boolean reverseIndexOrder;
        final Object[] imgPosition;

        public ViewParameter(String suffix, SliceOrientation sliceOrientation, boolean reverseSeriesOrder,
            TransposeType transposeImage, double[] imgOrientation, boolean rotateOutputImg, boolean reverseIndexOrder,
            Object[] imgPosition) {
            super();
            this.suffix = suffix;
            this.sliceOrientation = sliceOrientation;
            this.reverseSeriesOrder = reverseSeriesOrder;
            this.transposeImage = transposeImage;
            this.imgOrientation = imgOrientation;
            this.rotateOutputImg = rotateOutputImg;
            this.reverseIndexOrder = reverseIndexOrder;
            this.imgPosition = imgPosition;
        }
    }
}
