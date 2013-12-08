package org.weasis.dicom.viewer2d;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;

import org.dcm4che.data.UID;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.MaxCollectionZprojection;
import org.weasis.core.api.image.op.MeanCollectionZprojection;
import org.weasis.core.api.image.op.MinCollectionZprojection;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;

public class MipView extends View2d {
    public static final ImageIcon MIP_ICON_SETTING = new ImageIcon(
        MipView.class.getResource("/icon/22x22/mip-setting.png"));
    public static final ActionW MIP = new ActionW("MIP", "mip", 0, 0, null);
    public static final ActionW MIP_MIN_SLICE = new ActionW("Min Slice: ", "mip_min", 0, 0, null);
    public static final ActionW MIP_MAX_SLICE = new ActionW("Max Slice: ", "mip_max", 0, 0, null);

    public enum Type {
        MIN("min-MIP"), MEAN("mean-MIP"), MAX("MIP");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    };

    private final ViewButton mip_button;
    private final JProgressBar progressBar;
    private volatile Thread process;

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        this.mip_button = new ViewButton(new MipPopup(), MIP_ICON_SETTING);
        mip_button.setVisible(true);
        // Remove PR and KO buttons
        getViewButtons().clear();
        getViewButtons().add(mip_button);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.BEST_FIT);
        actionsInView.put(MIP_MIN_SLICE.cmd(), 1);
        actionsInView.put(MIP_MAX_SLICE.cmd(), 15);

        // Propagate the preset
        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
        // disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);
    }

    public void setMIPSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        super.setSeries(series, selectedDicom);
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        // If series is updates by other actions than MIP, the view is reseted
        exitMipMode(series, selectedDicom);
    }

    @Override
    protected void drawOnTop(Graphics2D g2d) {
        super.drawOnTop(g2d);
        if (progressBar.isVisible()) {
            int shiftx = getWidth() / 2 - progressBar.getWidth() / 2;
            int shifty = getHeight() / 2 - progressBar.getHeight() / 2;
            g2d.translate(shiftx, shifty);
            progressBar.paint(g2d);
            g2d.translate(-shiftx, -shifty);
        }
    }

    @Override
    protected void setImage(DicomImageElement img) {
        // Avoid to listen synch events
    }

    public void exitMipMode(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        // reset current process
        this.setActionsInView(MipView.MIP.cmd(), null);
        this.setActionsInView(MipView.MIP_MIN_SLICE.cmd(), null);
        this.setActionsInView(MipView.MIP_MAX_SLICE.cmd(), null);
        this.applyMipParameters();
        DicomImageElement img = getImage();
        if (img != null) {
            // Close stream
            img.dispose();
            // Delete file in cache
            File file = img.getFile();
            if (file != null) {
                file.delete();
            }
        }

        ImageViewerPlugin<DicomImageElement> container = this.getEventManager().getSelectedView2dContainer();
        container.setSelectedAndGetFocus();
        View2d newView2d = new View2d(this.getEventManager());
        newView2d.registerDefaultListeners();
        newView2d.setSeries(series, selectedDicom);
        container.replaceView(this, newView2d);
    }

    public void applyMipParameters() {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }

        final Integer min = (Integer) getActionValue(MIP_MIN_SLICE.cmd());
        final Integer max = (Integer) getActionValue(MIP_MAX_SLICE.cmd());
        if (series == null || min == null || max == null) {
            return;
        }
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                progressBar.setMinimum(0);
                progressBar.setMaximum(max - min + 1);
                Dimension dim = new Dimension(getWidth() / 2, 30);
                progressBar.setSize(dim);
                progressBar.setPreferredSize(dim);
                progressBar.setMaximumSize(dim);
                progressBar.setValue(0);
                progressBar.setStringPainted(true);
                // Required for Substance l&f
                progressBar.updateUI();
                progressBar.setVisible(true);
                repaint();
            }
        });

        process = new Thread("Building MIP view") {
            @Override
            public void run() {
                try {
                    MipView imageOperation = MipView.this;
                    Type mipType = (Type) imageOperation.getActionValue(MIP.cmd());
                    PlanarImage curImage = null;
                    MediaSeries<DicomImageElement> series = imageOperation.getSeries();
                    if (series != null) {
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                progressBar.setValue(0);
                                MipView.this.repaint();
                            }
                        });

                        SeriesComparator sort =
                            (SeriesComparator) imageOperation.getActionValue(ActionW.SORTSTACK.cmd());
                        Boolean reverse = (Boolean) imageOperation.getActionValue(ActionW.INVERSESTACK.cmd());
                        Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
                        Filter filter = (Filter) imageOperation.getActionValue(ActionW.FILTERED_SERIES.cmd());
                        Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);
                        DicomImageElement firstDcm = null;
                        // synchronized (series) {
                        Iterator<DicomImageElement> iter = medias.iterator();
                        int startIndex = min - 1;
                        int k = 0;
                        if (startIndex > 0) {
                            while (iter.hasNext()) {
                                DicomImageElement dcm = iter.next();
                                if (k >= startIndex) {
                                    firstDcm = dcm;
                                    break;
                                }
                                k++;
                            }
                        } else {
                            if (iter.hasNext()) {
                                firstDcm = iter.next();
                            }
                        }

                        final List<ImageElement> sources = new ArrayList<ImageElement>();
                        int stopIndex = max - 1;
                        if (firstDcm != null) {
                            sources.add(firstDcm);
                            while (iter.hasNext()) {
                                if (this.isInterrupted()) {
                                    return;
                                }
                                DicomImageElement dcm = iter.next();
                                // TODO check Pixel size, LUTs if they are different from the first image (if yes then
                                // display
                                // confirmation message to continue)
                                sources.add(dcm);

                                if (k >= stopIndex) {
                                    break;
                                }
                                k++;
                            }
                            if (sources.size() > 1) {
                                curImage = addCollectionOperation(mipType, sources, MipView.this, progressBar);
                            } else {
                                curImage = null;
                            }
                        }
                        // }
                        final DicomImageElement dicom;
                        if (curImage != null && firstDcm != null) {
                            DicomImageElement imgRef = (DicomImageElement) sources.get(sources.size() / 2);
                            RawImage raw = null;
                            try {
                                File mipDir =
                                    AbstractProperties.buildAccessibleTempDirectory(
                                        AbstractProperties.FILE_CACHE_DIR.getName(), "mip");
                                raw = new RawImage(File.createTempFile("mip_", ".raw", mipDir));//$NON-NLS-1$ //$NON-NLS-2$);
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
                            int bitsAllocated = imgRef.getBitsAllocated();
                            int bitsStored = imgRef.getBitsStored();

                            // Tags with same values for all the Series
                            rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
                            rawIO.setTag(TagW.Columns, curImage.getWidth());
                            rawIO.setTag(TagW.Rows, curImage.getHeight());

                            rawIO.setTag(TagW.SeriesInstanceUID,
                                "mip." + (String) series.getTagValue(TagW.SubseriesInstanceUID));

                            TagW[] tagList =
                                { TagW.PhotometricInterpretation, TagW.ImageOrientationPatient,
                                    TagW.PixelRepresentation, TagW.Units, TagW.ImageType, TagW.SamplesPerPixel,
                                    TagW.MonoChrome, TagW.Modality };
                            rawIO.copyTags(tagList, imgRef, true);

                            rawIO.setTag(TagW.BitsAllocated, bitsAllocated);
                            rawIO.setTag(TagW.BitsStored, bitsStored);

                            TagW[] tagList2 =
                                { TagW.SmallestImagePixelValue, TagW.LargestImagePixelValue, TagW.ModalityLUTData,
                                    TagW.ModalityLUTType, TagW.ModalityLUTExplanation, TagW.RescaleSlope,
                                    TagW.RescaleIntercept, TagW.RescaleType, TagW.VOILUTsData, TagW.VOILUTsExplanation,
                                    TagW.PixelPaddingValue, TagW.PixelPaddingRangeLimit, TagW.WindowWidth,
                                    TagW.WindowCenter, TagW.WindowCenterWidthExplanation, TagW.VOILutFunction,
                                    TagW.PixelSpacing, TagW.ImagerPixelSpacing,
                                    TagW.PixelSpacingCalibrationDescription, TagW.PixelAspectRatio };
                            rawIO.copyTags(tagList2, imgRef, false);

                            // Image specific tags
                            rawIO.setTag(TagW.SOPInstanceUID, "mip.1");
                            rawIO.setTag(TagW.InstanceNumber, 1);

                            dicom = new DicomImageElement(rawIO, 0);
                            DicomImageElement oldImage = getImage();
                            // Use graphics of the previous image when they belongs to a MIP image
                            if (oldImage != null && "mip.1".equals(oldImage.getTagValue(TagW.SOPInstanceUID))) {
                                dicom.setTag(TagW.MeasurementGraphics, oldImage.getTagValue(TagW.MeasurementGraphics));
                            }
                        } else {
                            dicom = null;
                        }
                        // imageLayer.updateAllImageOperations();
                        // actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
                        // imageLayer.setPreprocessing(manager);

                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                DicomImageElement oldImage = getImage();
                                // Trick: call super to change the image as "this" method is empty
                                MipView.super.setImage(dicom);
                                if (oldImage != null) {
                                    // Close stream
                                    oldImage.dispose();
                                    // Delete file in cache
                                    File file = oldImage.getFile();
                                    if (file != null) {
                                        file.delete();
                                    }
                                }
                                progressBar.setVisible(false);
                            }
                        });

                    }
                } finally {
                    progressBar.setVisible(false);
                }
            }
        };
        process.start();
    }

    public static PlanarImage arithmeticOperation(String operation, PlanarImage img1, PlanarImage img2) {
        ParameterBlockJAI pb2 = new ParameterBlockJAI(operation);
        pb2.addSource(img1);
        pb2.addSource(img2);
        return JAI.create(operation, pb2);
    }

    public static PlanarImage addCollectionOperation(Type mipType, List<ImageElement> sources, MipView mipView,
        JProgressBar progressBar) {
        if (Type.MIN.equals(mipType)) {
            MinCollectionZprojection op = new MinCollectionZprojection(sources, mipView, progressBar);
            return op.computeMinCollectionOpImage();
        }
        if (Type.MEAN.equals(mipType)) {
            MeanCollectionZprojection op = new MeanCollectionZprojection(sources, mipView, progressBar);
            return op.computeMeanCollectionOpImage();
        }
        MaxCollectionZprojection op = new MaxCollectionZprojection(sources, mipView, progressBar);
        return op.computeMaxCollectionOpImage();
    }

    private static void writeRasterInRaw(BufferedImage image, OutputStream out) throws IOException {
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
            }
            out.write(bytesOut);
        }
    }

}
