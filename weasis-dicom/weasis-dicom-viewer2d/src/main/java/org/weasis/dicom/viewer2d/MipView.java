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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;

import org.dcm4che2.data.UID;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.dicom.viewer2d.mpr.SeriesBuilder;

public class MipView extends View2d {

    public static final ActionW MIP = new ActionW("MIP", "mip", 0, 0, null);
    public static final ActionW MIP_MIN_SLICE = new ActionW("Min Slice: ", "mip_min", 0, 0, null);
    public static final ActionW MIP_MAX_SLICE = new ActionW("Max Slice: ", "mip_max", 0, 0, null);

    public enum Type {
        MIN("min-MIP"), MAX("MIP");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    };

    private static final ViewButton MIP_BUTTON = new ViewButton(new MipPopup(), new ImageIcon(
        MediaSeries.class.getResource("/icon/22x22/dicom-3d.png")));

    private final JProgressBar progressBar;
    private volatile Thread process;

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        viewButtons.add(MIP_BUTTON);
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        // TODO PREPROCESSING conflict with PR, handle globally?
        // actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
        // imageLayer.setPreprocessing(manager);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();

        actionsInView.put(MIP.cmd(), Type.MAX);
        int index = 7;
        ActionState sequence = eventManager.getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            cineAction.stop();
            int val = cineAction.getValue();
            if (val > 7) {
                index = val;
            }
            // TODO handle scroll position with index
            // actionsInView.put(ActionW.SCROLL_SERIES.cmd(), index);
        }
        // Force to extend VOI LUT to pixel allocated
        actionsInView.put(DicomImageElement.FILL_OUTSIDE_LUT, true);
        actionsInView.put(MIP_MIN_SLICE.cmd(), index - 7);
        actionsInView.put(MIP_MAX_SLICE.cmd(), index + 7);
    }

    @Override
    protected void drawExtendedAtions(Graphics2D g2d) {
        Icon icon = MIP_BUTTON.getIcon();
        int x = getWidth() - icon.getIconWidth() - 5;
        int y = (int) ((getHeight() - 1) * 0.5);
        MIP_BUTTON.x = x;
        MIP_BUTTON.y = y;
        icon.paintIcon(this, g2d, x, y);

        if (progressBar.isVisible()) {
            // Draw in the bottom right corner of thumbnail space;
            int shiftx = getWidth() / 2 - progressBar.getWidth() / 2;
            int shifty = getHeight() / 2 - progressBar.getHeight() / 2;
            g2d.translate(shiftx, shifty);
            progressBar.paint(g2d);
            g2d.translate(-shiftx, -shifty);
        }
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
                progressBar.setVisible(true);
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
                        final String operator = mipType.name().toLowerCase();
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
                                    curImage = dcm.getImage();
                                    break;
                                }
                                k++;
                            }
                        } else {
                            if (iter.hasNext()) {
                                DicomImageElement dcmCur = iter.next();
                                firstDcm = dcmCur;
                                curImage = dcmCur.getImage();
                            }
                        }

                        int stopIndex = max - 1;
                        if (curImage != null) {
                            while (iter.hasNext()) {
                                if (this.isInterrupted()) {
                                    return;
                                }
                                DicomImageElement dcm = iter.next();
                                PlanarImage img = dcm.getImage();
                                if (img == null) {
                                    return;
                                }
                                curImage = arithmeticOperation(operator, curImage, img);
                                GuiExecutor.instance().execute(new Runnable() {

                                    @Override
                                    public void run() {
                                        progressBar.setValue(progressBar.getValue() + 1);
                                        MipView.this.repaint();
                                    }
                                });

                                if (k >= stopIndex) {
                                    break;
                                }
                                k++;
                            }
                        }
                        // }

                        if (curImage != null && firstDcm != null) {
                            RawImage raw = null;
                            try {
                                raw = new RawImage(File.createTempFile("mip_", ".raw", SeriesBuilder.MPR_CACHE_DIR));//$NON-NLS-1$ //$NON-NLS-2$);
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
                            int bitsAllocated = firstDcm.getBitsAllocated();
                            int bitsStored = firstDcm.getBitsStored();
                            String photometricInterpretation = firstDcm.getPhotometricInterpretation();
                            // Tags with same values for all the Series
                            rawIO.setTag(TagW.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
                            rawIO.setTag(TagW.Columns, curImage.getWidth());
                            rawIO.setTag(TagW.Rows, curImage.getHeight());
                            // rawIO.setTag(TagW.SliceThickness, origPixSize);
                            double origPixSize = firstDcm.getPixelSize();
                            rawIO.setTag(TagW.PixelSpacing, new double[] { origPixSize, origPixSize });
                            rawIO.setTag(TagW.SeriesInstanceUID,
                                "mip." + (String) series.getTagValue(TagW.SubseriesInstanceUID));
                            rawIO.setTag(TagW.ImageOrientationPatient,
                                firstDcm.getTagValue(TagW.ImageOrientationPatient));

                            rawIO.setTag(TagW.BitsAllocated, bitsAllocated);
                            rawIO.setTag(TagW.BitsStored, bitsStored);
                            rawIO.setTag(TagW.PixelRepresentation, firstDcm.getTagValue(TagW.PixelRepresentation));
                            rawIO.setTag(TagW.Units, firstDcm.getTagValue(TagW.Units));
                            rawIO.setTag(TagW.ImageType, firstDcm.getTagValue(TagW.ImageType));
                            rawIO.setTag(TagW.SamplesPerPixel, firstDcm.getTagValue(TagW.SamplesPerPixel));
                            rawIO.setTag(TagW.PhotometricInterpretation, photometricInterpretation);
                            rawIO.setTag(TagW.MonoChrome, firstDcm.getTagValue(TagW.MonoChrome));
                            rawIO.setTag(TagW.Modality, firstDcm.getTagValue(TagW.Modality));

                            // TODO take dicom tags from middle image? what to do when values are not constant in the
                            // series?
                            rawIO.setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                                firstDcm.getTagValue(TagW.PixelSpacingCalibrationDescription));
                            rawIO
                                .setTagNoNull(TagW.ModalityLUTSequence, firstDcm.getTagValue(TagW.ModalityLUTSequence));
                            rawIO.setTagNoNull(TagW.RescaleSlope, firstDcm.getTagValue(TagW.RescaleSlope));
                            rawIO.setTagNoNull(TagW.RescaleIntercept, firstDcm.getTagValue(TagW.RescaleIntercept));
                            rawIO.setTagNoNull(TagW.RescaleType, firstDcm.getTagValue(TagW.RescaleType));
                            // rawIO.setTagNoNull(TagW.SmallestImagePixelValue,
                            // img.getTagValue(TagW.SmallestImagePixelValue));
                            // rawIO.setTagNoNull(TagW.LargestImagePixelValue,
                            // img.getTagValue(TagW.LargestImagePixelValue));
                            rawIO.setTagNoNull(TagW.PixelPaddingValue, firstDcm.getTagValue(TagW.PixelPaddingValue));
                            rawIO.setTagNoNull(TagW.PixelPaddingRangeLimit,
                                firstDcm.getTagValue(TagW.PixelPaddingRangeLimit));

                            rawIO.setTagNoNull(TagW.VOILUTSequence, firstDcm.getTagValue(TagW.VOILUTSequence));
                            // rawIO.setTagNoNull(TagW.WindowWidth, img.getTagValue(TagW.WindowWidth));
                            // rawIO.setTagNoNull(TagW.WindowCenter, img.getTagValue(TagW.WindowCenter));
                            // rawIO.setTagNoNull(TagW.WindowCenterWidthExplanation,
                            // img.getTagValue(TagW.WindowCenterWidthExplanation));
                            rawIO.setTagNoNull(TagW.VOILutFunction, firstDcm.getTagValue(TagW.VOILutFunction));

                            // Image specific tags
                            rawIO.setTag(TagW.SOPInstanceUID, "mip.1");
                            rawIO.setTag(TagW.InstanceNumber, 1);

                            DicomImageElement dicom = new DicomImageElement(rawIO, 0);
                            imageLayer.setImage(dicom, null);
                        }
                        // TODO check images have similar modality and VOI LUT, W/L, LUT shape...
                        // imageLayer.updateAllImageOperations();

                        // actionsInView.put(ActionW.PREPROCESSING.cmd(), manager);
                        // imageLayer.setPreprocessing(manager);

                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                progressBar.setVisible(false);
                                DicomImageElement image = imageLayer.getSourceImage();
                                if (image != null) {
                                    // Update statistics
                                    List<Graphic> list = (List<Graphic>) image.getTagValue(TagW.MeasurementGraphics);
                                    if (list != null) {
                                        for (Graphic graphic : list) {
                                            graphic.updateLabel(true, MipView.this);
                                        }
                                    }
                                }
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

    public JProgressBar getProgressBar() {
        return progressBar;
    }
}
