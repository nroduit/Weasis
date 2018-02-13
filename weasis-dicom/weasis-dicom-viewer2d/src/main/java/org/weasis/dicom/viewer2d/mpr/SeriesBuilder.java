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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOException;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TranslateDescriptor;
import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.RawImage;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class SeriesBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeriesBuilder.class);

    static TagW SeriesReferences = new TagW("series.builder.refs", TagType.STRING, 2, 2); //$NON-NLS-1$
    public static final File MPR_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "mpr"); //$NON-NLS-1$

    private SeriesBuilder() {
    }

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
                DicomImageElement img = SliceOrientation.CORONAL.equals(type1)
                    ? series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, filter, SortSeriesStack.slicePosition)
                    : series.getMedia(MediaSeries.MEDIA_POSITION.LAST, filter, SortSeriesStack.slicePosition);
                if (img != null && img.getMediaReader() instanceof DcmMediaReader) {
                    GeometryOfSlice geometry = img.getDispSliceGeometry();
                    if (geometry != null) {
                        int width = TagD.getTagValue(img, Tag.Columns, Integer.class);
                        int height = TagD.getTagValue(img, Tag.Rows, Integer.class);
                        // abort needs to be final array to be changed on "invoqueAndWhait()" block.
                        final boolean[] abort = new boolean[] { false, false };

                        if (MathUtil.isDifferent(img.getRescaleX(), img.getRescaleY())) {
                            // confirmMessage(view, Messages.getString("SeriesBuilder.non_square"), abort);
                            // //$NON-NLS-1$
                            width = img.getRescaleWidth(width);
                            height = img.getRescaleHeight(height);
                        }

                        Double tilt = TagD.getTagValue(img, Tag.GantryDetectorTilt, Double.class);
                        if (tilt != null && MathUtil.isDifferentFromZero(tilt)) {
                            confirmMessage(view, Messages.getString("SeriesBuilder.gantry"), abort); //$NON-NLS-1$
                        }
                        Map<TagW, Object> tags = img.getMediaReader().getMediaFragmentTags(0);
                        if (tags != null) {
                            double[] row = geometry.getRowArray();
                            double[] col = geometry.getColumnArray();
                            Vector3d vr = new Vector3d(row);
                            Vector3d vc = new Vector3d(col);
                            Vector3d resr = new Vector3d();
                            Vector3d resc = new Vector3d();

                            final ViewParameter[] recParams = new ViewParameter[2];
                            String frUID = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                            if (frUID == null) {
                                frUID = UIDUtils.createUID();
                                series.setTag(TagD.get(Tag.FrameOfReferenceUID), frUID);
                            }

                            final String uid1;
                            final String uid2;
                            String[] uidsRef = TagW.getTagValue(series, SeriesReferences, String[].class);
                            if (uidsRef != null && uidsRef.length == 2) {
                                uid1 = uidsRef[0];
                                uid2 = uidsRef[1];
                            } else {
                                uid1 = UIDUtils.createUID();
                                uid2 = UIDUtils.createUID();
                                series.setTag(SeriesReferences, new String[] { uid1, uid2 });
                            }

                            if (SliceOrientation.SAGITTAL.equals(type1)) {
                                // The reference image is the first of the sagittal stack (Left)
                                rotate(vc, vr, Math.toRadians(270), resr);
                                recParams[0] = new ViewParameter(uid1, SliceOrientation.AXIAL, false, null,
                                    new double[] { resr.x, resr.y, resr.z, row[0], row[1], row[2] }, true, true,
                                    new Object[] { 0.0, false }, frUID);
                                recParams[1] = new ViewParameter(uid2, SliceOrientation.CORONAL, false,
                                    TransposeDescriptor.ROTATE_270,
                                    new double[] { resr.x, resr.y, resr.z, col[0], col[1], col[2] }, true, true,
                                    new Object[] { true, 0.0 }, frUID);
                            } else if (SliceOrientation.CORONAL.equals(type1)) {
                                // The reference image is the first of the coronal stack (Anterior)
                                rotate(vc, vr, Math.toRadians(90), resc);
                                recParams[0] = new ViewParameter(uid1, SliceOrientation.AXIAL, false, null,
                                    new double[] { row[0], row[1], row[2], resc.x, resc.y, resc.z }, false, true,
                                    new Object[] { 0.0, false }, frUID);

                                rotate(vc, vr, Math.toRadians(90), resr);
                                recParams[1] = new ViewParameter(uid2, SliceOrientation.SAGITTAL, true,
                                    TransposeDescriptor.ROTATE_270,
                                    new double[] { resr.x, resr.y, resr.z, col[0], col[1], col[2] }, true, false,
                                    new Object[] { true, 0.0 }, frUID);
                            } else {
                                // The reference image is the last of the axial stack (Head)
                                rotate(vc, vr, Math.toRadians(270), resc);
                                recParams[0] = new ViewParameter(uid1, SliceOrientation.CORONAL, true, null,
                                    new double[] { row[0], row[1], row[2], resc.x, resc.y, resc.z }, false, false,
                                    new Object[] { 0.0, false }, frUID);

                                rotate(vr, vc, Math.toRadians(90), resr);
                                recParams[1] = new ViewParameter(uid2, SliceOrientation.SAGITTAL, true,
                                    TransposeDescriptor.ROTATE_270,
                                    new double[] { col[0], col[1], col[2], resr.x, resr.y, resr.z }, false, false,
                                    new Object[] { true, 0.0 }, frUID);

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
                                            treeModel.getHierarchyNode(study, recParams[i].seriesUID);
                                        needBuild[i] = group == null;
                                        if (!needBuild[i]) {
                                            final MprView mprView = recView[i];
                                            GuiExecutor.instance().execute(() -> {
                                                mprView.setSeries((MediaSeries<DicomImageElement>) group);
                                                // Copy the synch values from the main view
                                                for (String action : MPRContainer.DEFAULT_MPR.getSynchData()
                                                    .getActions().keySet()) {
                                                    mprView.setActionsInView(action, view.getActionValue(action));
                                                }
                                                mprView.zoom(mainView.getViewModel().getViewScale());
                                                mprView.center();
                                                mprView.repaint();
                                            });
                                        }
                                    }
                                }
                            }

                            final int size = series.size(filter);
                            final JProgressBar[] bar = new JProgressBar[2];
                            GuiExecutor.instance().invokeAndWait(() -> {
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
                            });

                            // Get the image in the middle of the series for having better default W/L values
                            img = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, filter,
                                SortSeriesStack.slicePosition);
                            final Attributes attributes = ((DcmMediaReader) img.getMediaReader()).getDicomObject();

                            for (int i = 0; i < 2; i++) {
                                if (needBuild[i]) {
                                    final MprView mprView = recView[i];
                                    final ViewParameter viewParams = recParams[i];

                                    Iterable<DicomImageElement> medias = series.copyOfMedias(filter,
                                        viewParams.reverseSeriesOrder
                                            ? SortSeriesStack.slicePosition.getReversOrderComparator()
                                            : SortSeriesStack.slicePosition);
                                    double origPixSize = img.getPixelSize();

                                    RawImage[] secSeries = new RawImage[i == 0 ? height : width];
                                    /*
                                     * Write the new image by tacking the lines (from first to last) of all the images
                                     * of the original series stack
                                     */
                                    double sPixSize = writeBlock(secSeries, series, medias, viewParams, mprView, thread,
                                        abort, seriesID);

                                    if (thread.isInterrupted()) {
                                        return;
                                    }
                                    /*
                                     * Reconstruct dicom files, adapt position, orientation, pixel spacing, instance
                                     * number and UIDs.
                                     */
                                    final DicomSeries dicomSeries =
                                        buildDicomSeriesFromRaw(secSeries, new Dimension(i == 0 ? width : height, size),
                                            img, viewParams, origPixSize, sPixSize, geometry, mprView, attributes);

                                    if (dicomSeries != null && dicomSeries.size(null) > 0) {
                                        ((DcmMediaReader) dicomSeries.getMedia(0, null, null).getMediaReader())
                                            .writeMetaData(dicomSeries);
                                        if (study != null && treeModel != null) {
                                            dicomSeries.setTag(TagW.ExplorerModel, model);
                                            treeModel.addHierarchyNode(study, dicomSeries);
                                            if (treeModel instanceof DicomModel) {
                                                DicomModel dicomModel = (DicomModel) treeModel;
                                                dicomModel.firePropertyChange(new ObservableEvent(
                                                    ObservableEvent.BasicAction.ADD, dicomModel, null, dicomSeries));
                                            }
                                        }

                                        GuiExecutor.instance().execute(() -> {
                                            mprView.setProgressBar(null);
                                            mprView.setSeries(dicomSeries);
                                            // Copy the synch values from the main view
                                            for (String action : MPRContainer.DEFAULT_MPR.getSynchData().getActions()
                                                .keySet()) {
                                                mprView.setActionsInView(action, view.getActionValue(action));
                                            }
                                            mprView.zoom(mainView.getViewModel().getViewScale());
                                            mprView.center();
                                            mprView.repaint();
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

    private static DicomSeries buildDicomSeriesFromRaw(final RawImage[] newSeries, Dimension dim, DicomImageElement img,
        ViewParameter params, double origPixSize, double sPixSize, GeometryOfSlice geometry, final MprView view,
        final Attributes attributes) throws Exception {

        int bitsAllocated = img.getBitsAllocated();
        int bitsStored = img.getBitsStored();
        double[] pixSpacing = new double[] { sPixSize, origPixSize };

        int dataType = 0;
        ColorModel cm = null;
        SampleModel sampleModel = null;
        final JProgressBar bar = view.getProgressBar();

        if (params.rotateOutputImg) {
            if (bar != null) {
                GuiExecutor.instance().execute(() -> {
                    bar.setMaximum(newSeries.length);
                    bar.setValue(0);
                    // Force to reset the progress bar (substance)
                    bar.updateUI();
                    view.repaint();
                });
            }

            pixSpacing = new double[] { origPixSize, sPixSize };

            int samplesPerPixel = TagD.getTagValue(img, Tag.SamplesPerPixel, Integer.class);
            boolean banded = samplesPerPixel > 1
                && DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.PlanarConfiguration, 0) != 0;
            int pixelRepresentation =
                DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.PixelRepresentation, 0);
            dataType = bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE
                : pixelRepresentation != 0 ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
            if (bitsAllocated > 16 && samplesPerPixel == 1) {
                dataType = DataBuffer.TYPE_INT;
            }

            String photometricInterpretation = TagD.getTagValue(img, Tag.PhotometricInterpretation, String.class);
            PhotometricInterpretation pmi = PhotometricInterpretation.fromString(photometricInterpretation);
            cm = pmi.createColorModel(bitsStored, dataType, attributes);
            sampleModel = pmi.createSampleModel(dataType, dim.width, dim.height, samplesPerPixel, banded);

            int tmp = dim.width;
            dim.width = dim.height;
            dim.height = tmp;
        }

        final int[] COPIED_ATTRS = { Tag.SpecificCharacterSet, Tag.PatientID, Tag.PatientName, Tag.PatientBirthDate,
            Tag.PatientBirthTime, Tag.PatientSex, Tag.IssuerOfPatientID, Tag.IssuerOfAccessionNumberSequence,
            Tag.PatientWeight, Tag.PatientAge, Tag.PatientSize, Tag.PatientState, Tag.PatientComments,

            Tag.StudyID, Tag.StudyDate, Tag.StudyTime, Tag.StudyDescription, Tag.StudyComments, Tag.AccessionNumber,
            Tag.ModalitiesInStudy,

            Tag.Modality, Tag.SeriesDate, Tag.SeriesTime, Tag.RetrieveAETitle, Tag.ReferringPhysicianName,
            Tag.InstitutionName, Tag.InstitutionalDepartmentName, Tag.StationName, Tag.Manufacturer,
            Tag.ManufacturerModelName, Tag.SeriesNumber, Tag.KVP, Tag.Laterality, Tag.BodyPartExamined,
            Tag.ModalityLUTSequence, Tag.VOILUTSequence };

        Arrays.sort(COPIED_ATTRS);
        final Attributes cpTags = new Attributes(attributes, COPIED_ATTRS);
        cpTags.setString(Tag.SeriesDescription, VR.LO, attributes.getString(Tag.SeriesDescription, "") + " [MPR]"); //$NON-NLS-1$ //$NON-NLS-2$
        cpTags.setString(Tag.ImageType, VR.CS, new String[] { "DERIVED", "SECONDARY", "MPR" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cpTags.setString(Tag.FrameOfReferenceUID, VR.UI, params.frameOfReferenceUID);

        int last = newSeries.length;
        List<DicomImageElement> dcms = new ArrayList<>();

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
                    dataBuffer = dataType == DataBuffer.TYPE_SHORT ? new DataBufferShort(data, data.length)
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
                } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                    short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                        : ((DataBufferUShort) dataBuffer).getData();

                    byteBuffer = ByteBuffer.allocate(data.length * 2);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
                    shortBuffer.put(data);

                    writToFile(inFile, byteBuffer);
                } else if (dataBuffer instanceof DataBufferInt) {
                    int[] data = ((DataBufferInt) dataBuffer).getData();

                    byteBuffer = ByteBuffer.allocate(data.length * 4);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    IntBuffer intBuffer = byteBuffer.asIntBuffer();
                    intBuffer.put(data);

                    writToFile(inFile, byteBuffer);
                }
                if (bar != null) {
                    GuiExecutor.instance().execute(() -> {
                        bar.setValue(bar.getValue() + 1);
                        view.repaint();
                    });
                }
            }
            RawImageIO rawIO = new RawImageIO(inFile.toURI(), null);
            rawIO.setBaseAttributes(cpTags);

            // Tags with same values for all the Series
            rawIO.setTag(TagD.get(Tag.TransferSyntaxUID), UID.ImplicitVRLittleEndian);
            rawIO.setTag(TagD.get(Tag.Columns), dim.width);
            rawIO.setTag(TagD.get(Tag.Rows), dim.height);
            rawIO.setTag(TagD.get(Tag.SliceThickness), origPixSize);
            rawIO.setTag(TagD.get(Tag.PixelSpacing), pixSpacing);
            rawIO.setTag(TagD.get(Tag.SeriesInstanceUID), params.seriesUID);
            rawIO.setTag(TagD.get(Tag.ImageOrientationPatient), params.imgOrientation);

            rawIO.setTag(TagD.get(Tag.BitsAllocated), bitsAllocated);
            rawIO.setTag(TagD.get(Tag.BitsStored), bitsStored);

            // Mandatory tags
            TagW[] mtagList = TagD.getTagFromIDs(Tag.PatientID, Tag.PatientName, Tag.PatientBirthDate,
                Tag.StudyInstanceUID, Tag.StudyID, Tag.SOPClassUID, Tag.StudyDate, Tag.StudyTime, Tag.AccessionNumber);
            rawIO.copyTags(mtagList, img, true);
            rawIO.setTag(TagW.PatientPseudoUID, img.getTagValue(TagW.PatientPseudoUID));

            TagW[] tagList = TagD.getTagFromIDs(Tag.PhotometricInterpretation, Tag.PixelRepresentation, Tag.Units,
                Tag.SamplesPerPixel, Tag.Modality);
            rawIO.copyTags(tagList, img, true);
            rawIO.setTag(TagW.MonoChrome, img.getTagValue(TagW.MonoChrome));

            TagW[] tagList2 = { TagW.ModalityLUTData, TagW.ModalityLUTType, TagW.ModalityLUTExplanation,
                TagW.VOILUTsData, TagW.VOILUTsExplanation };
            rawIO.copyTags(tagList2, img, false);

            tagList2 = TagD.getTagFromIDs(Tag.RescaleSlope, Tag.RescaleIntercept, Tag.RescaleType,
                Tag.PixelPaddingValue, Tag.PixelPaddingRangeLimit, Tag.WindowWidth, Tag.WindowCenter,
                Tag.WindowCenterWidthExplanation, Tag.VOILUTFunction, Tag.PixelSpacingCalibrationDescription);
            rawIO.copyTags(tagList2, img, false);

            // Clone array, because values are adapted according to the min and max pixel values.
            TagW[] tagList3 = TagD.getTagFromIDs(Tag.WindowWidth, Tag.WindowCenter);
            for (int j = 0; j < tagList3.length; j++) {
                double[] val = (double[]) img.getTagValue(tagList3[j]);
                if (val != null) {
                    img.setTag(tagList3[j], Arrays.copyOf(val, val.length));
                }
            }

            // Image specific tags
            int index = i;
            rawIO.setTag(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
            rawIO.setTag(TagD.get(Tag.InstanceNumber), params.reverseIndexOrder ? last - index : index + 1);

            double x = (params.imgPosition[0] instanceof Double) ? (Double) params.imgPosition[0]
                : (Boolean) params.imgPosition[0] ? last - index - 1 : index;
            double y = (params.imgPosition[1] instanceof Double) ? (Double) params.imgPosition[1]
                : (Boolean) params.imgPosition[1] ? last - index - 1 : index;
            Point3d p = geometry.getPosition(new Point2D.Double(x, y));
            rawIO.setTag(TagD.get(Tag.ImagePositionPatient), new double[] { p.x, p.y, p.z });

            DicomMediaUtils.computeSlicePositionVector(rawIO);

            double[] loc = (double[]) rawIO.getTagValue(TagW.SlicePosition);
            if (loc != null) {
                rawIO.setTag(TagD.get(Tag.SliceLocation), loc[0] + loc[1] + loc[2]);
            }
            DicomImageElement dcm = new DicomImageElement(rawIO, 0) {
                @Override
                public boolean saveToFile(File output) {
                    RawImageIO reader = (RawImageIO) getMediaReader();
                    return FileUtil.nioCopyFile(reader.getDicomFile(), output);
                }
            };
            dcms.add(dcm);
        }
        return new DicomSeries(params.seriesUID, dcms, DicomModel.series.getTagView());
    }

    private static double writeBlock(RawImage[] newSeries, MediaSeries<DicomImageElement> series,
        Iterable<DicomImageElement> medias, ViewParameter params, final MprView view, Thread thread,
        final boolean[] abort, String seriesID) throws IOException {

        // TODO should return the more frequent space!
        final JProgressBar bar = view.getProgressBar();
        try {
            File dir = new File(MPR_CACHE_DIR, params.seriesUID);
            dir.mkdirs();
            for (int i = 0; i < newSeries.length; i++) {
                newSeries[i] = new RawImage(new File(dir, "mpr_" + (i + 1)));//$NON-NLS-1$
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
                boolean validSp = sp != null && sp.length == 3;
                if (!validSp && !abort[1]) {
                    confirmMessage(view, Messages.getString("SeriesBuilder.space_missing"), abort); //$NON-NLS-1$
                } else if (validSp) {
                    double pos = sp[0] + sp[1] + sp[2];
                    if (index > 0) {
                        double space = Math.abs(pos - lastPos);
                        if (!abort[1]
                            && (MathUtil.isEqualToZero(space) || (index > 1 && lastSpace - space > epsilon))) {
                            confirmMessage(view, Messages.getString("SeriesBuilder.space"), abort); //$NON-NLS-1$
                        }
                        lastSpace = space;
                    }
                    lastPos = pos;
                    index++;
                    if (bar != null) {
                        GuiExecutor.instance().execute(() -> {
                            bar.setValue(bar.getValue() + 1);
                            view.repaint();
                        });
                    }
                }
                // TODO do not open more than 512 files (Limitation to open 1024 in the same time on Ubuntu)
                PlanarImage image = dcm.getImage();
                if (image == null) {
                    abort[0] = true;
                    throw new IIOException("Cannot read an image!"); //$NON-NLS-1$
                }

                if (MathUtil.isDifferent(dcm.getRescaleX(), dcm.getRescaleY())) {
                    ParameterBlock pb = new ParameterBlock();
                    pb.addSource(image);
                    pb.add((float) dcm.getRescaleX()).add((float) dcm.getRescaleY()).add(0.0f).add(0.0f);
                    pb.add(Interpolation.getInstance(Interpolation.INTERP_BILINEAR));
                    image = JAI.create("scale", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
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
            byte[] bytesOut;
            if (dataBuffer instanceof DataBufferByte) {
                bytesOut = ((DataBufferByte) dataBuffer).getData();
                for (int j = 0; j < height; j++) {
                    newSeries[j].getOutputStream().write(bytesOut, j * width, width);
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
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
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();
                bytesOut = new byte[data.length * 4];
                for (int i = 0; i < data.length; i++) {
                    bytesOut[i * 4] = (byte) (data[i] & 0xFF);
                    bytesOut[i * 4 + 1] = (byte) ((data[i] >>> 8) & 0xFF);
                    bytesOut[i * 4 + 2] = (byte) ((data[i] >>> 16) & 0xFF);
                    bytesOut[i * 4 + 3] = (byte) ((data[i] >>> 24) & 0xFF);
                }
                width *= 4;
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
        result = TransposeDescriptor.create(source, rotate, ImageToolkit.NOCACHE_HINT);
        // Handle non square images. Translation is necessary because the transpose operator keeps the same
        // origin (top left not the center of the image)
        float diffw = source.getWidth() / 2.0f - result.getWidth() / 2.0f;
        float diffh = source.getHeight() / 2.0f - result.getHeight() / 2.0f;
        if (MathUtil.isDifferentFromZero(diffw) || MathUtil.isDifferentFromZero(diffh)) {
            result = TranslateDescriptor.create(result, diffw, diffh, null, ImageToolkit.NOCACHE_HINT);
        }
        return result.getAsBufferedImage();
    }

    private static void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x = axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.x * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y = axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.y * Math.cos(angle) + (axis.z * vSrc.x - axis.x * vSrc.z) * Math.sin(angle);
        vDst.z = axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.z * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
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
            LOGGER.error("Get bytes", e); //$NON-NLS-1$
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
            LOGGER.error("Write bytes", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(os);
        }
    }

    public static void confirmMessage(final Component view, final String message, final boolean[] abort) {
        GuiExecutor.instance().invokeAndWait(() -> {
            int usrChoice = JOptionPane.showConfirmDialog(view, message + Messages.getString("SeriesBuilder.add_warn"), //$NON-NLS-1$
                MPRFactory.NAME, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (usrChoice == JOptionPane.NO_OPTION) {
                abort[0] = true;
            } else {
                // bypass for other similar messages
                abort[1] = true;
            }
        });
        if (abort[0]) {
            throw new IllegalStateException(message);
        }
    }

    static class ViewParameter {
        final String seriesUID;
        final SliceOrientation sliceOrientation;
        final boolean reverseSeriesOrder;
        final TransposeType transposeImage;
        final double[] imgOrientation;
        final boolean rotateOutputImg;
        final boolean reverseIndexOrder;
        final Object[] imgPosition;
        final String frameOfReferenceUID;

        public ViewParameter(String seriesUID, SliceOrientation sliceOrientation, boolean reverseSeriesOrder,
            TransposeType transposeImage, double[] imgOrientation, boolean rotateOutputImg, boolean reverseIndexOrder,
            Object[] imgPosition, String frameOfReferenceUID) {
            this.seriesUID = seriesUID;
            this.sliceOrientation = sliceOrientation;
            this.reverseSeriesOrder = reverseSeriesOrder;
            this.transposeImage = transposeImage;
            this.imgOrientation = imgOrientation;
            this.rotateOutputImg = rotateOutputImg;
            this.reverseIndexOrder = reverseIndexOrder;
            this.imgPosition = imgPosition;
            this.frameOfReferenceUID = frameOfReferenceUID;
        }
    }
}
