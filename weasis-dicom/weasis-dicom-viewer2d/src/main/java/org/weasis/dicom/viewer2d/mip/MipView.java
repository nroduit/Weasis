package org.weasis.dicom.viewer2d.mip;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;

import org.dcm4che3.data.UID;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.RawImage;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.View2dFactory;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;

public class MipView extends View2d {
    public static final ImageIcon MIP_ICON_SETTING = new ImageIcon(
        MipView.class.getResource("/icon/22x22/mip-setting.png")); //$NON-NLS-1$
    public static final ActionW MIP = new ActionW(Messages.getString("MipView.mip"), "mip", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW MIP_THICKNESS = new ActionW("Image Extension", "mip_thick", 0, 0, null); //$NON-NLS-2$

    public enum Type {
        MIN, MEAN, MAX;
    };

    private final JProgressBar progressBar;
    private volatile Thread process;

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.BEST_FIT);
        actionsInView.put(MIP_THICKNESS.cmd(), 2);
        actionsInView.put(MipView.MIP.cmd(), MipView.Type.MAX);
        actionsInView.put("no.ko", true); //$NON-NLS-1$

        // Propagate the preset
        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
        // disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);
    }

    public void setMIPSeries(DefaultView2d selView) {
        if (selView != null) {
            actionsInView.put(ActionW.SORTSTACK.cmd(), selView.getActionValue(ActionW.SORTSTACK.cmd()));
            actionsInView.put(ActionW.INVERSESTACK.cmd(), selView.getActionValue(ActionW.INVERSESTACK.cmd()));
            actionsInView.put(ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
            MediaSeries s = selView.getSeries();
            super.setSeries(s, null);
        }
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        MediaSeries<DicomImageElement> oldsequence = this.series;
        this.series = series;
        if (oldsequence != null && !oldsequence.equals(series)) {
            closingSeries(oldsequence);
        }
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
        this.setActionsInView(MipView.MIP_THICKNESS.cmd(), null);
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }

        setMip(null);

        ImageViewerPlugin<DicomImageElement> container = this.getEventManager().getSelectedView2dContainer();
        container.setSelectedAndGetFocus();
        View2d newView2d = new View2d(this.getEventManager());
        newView2d.registerDefaultListeners();
        newView2d.setSeries(series, selectedDicom);
        container.replaceView(this, newView2d);
    }

    public void buildMip(final boolean fullSeries) {

        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }

        final Type mipType = (Type) getActionValue(MipView.MIP.cmd());
        final Integer extend = (Integer) getActionValue(MIP_THICKNESS.cmd());
        if (series == null || extend == null || mipType == null) {
            return;
        }
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                progressBar.setMinimum(0);
                progressBar.setMaximum(2 * extend + 1);
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

        process = new Thread(Messages.getString("MipView.build")) { //$NON-NLS-1$
                @Override
                public void run() {
                    final List<DicomImageElement> dicoms = new ArrayList<DicomImageElement>();
                    try {
                        SeriesBuilder
                            .applyMipParameters(progressBar, MipView.this, dicoms, mipType, extend, fullSeries);
                    } finally {
                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    if (dicoms.size() == 1) {
                                        setMip(dicoms.get(0));
                                    } else if (dicoms.size() > 1) {
                                        try {
                                            DicomImageElement dcm = dicoms.get(0);
                                            Series s =
                                                new DicomSeries((String) dcm.getTagValue(TagW.SeriesInstanceUID));
                                            s.addAll(dicoms);
                                            s.setTag(TagW.FrameOfReferenceUID,
                                                series.getTagValue(TagW.FrameOfReferenceUID));
                                            ((DicomMediaIO) series.getMedia(MEDIA_POSITION.MIDDLE, null, null)
                                                .getMediaReader()).writeMetaData(s);
                                            DataExplorerModel model =
                                                (DataExplorerModel) MipView.this.getSeries().getTagValue(
                                                    TagW.ExplorerModel);
                                            if (model instanceof DicomModel) {
                                                DicomModel dicomModel = (DicomModel) model;
                                                MediaSeriesGroup study = dicomModel.getParent(series, DicomModel.study);
                                                if (study != null) {
                                                    s.setTag(TagW.ExplorerModel, dicomModel);
                                                    dicomModel.addHierarchyNode(study, s);
                                                    dicomModel.firePropertyChange(new ObservableEvent(
                                                        ObservableEvent.BasicAction.Add, dicomModel, null, s));
                                                }

                                                View2dFactory factory = new View2dFactory() {
                                                    @Override
                                                    public javax.swing.Icon getIcon() {
                                                        return new ImageIcon(MipView.class
                                                            .getResource("/icon/16x16/mip.png"));
                                                    };

                                                };
                                                ViewerPluginBuilder.openSequenceInPlugin(factory, s, model, false,
                                                    false);
                                            }
                                        } finally {
                                            MipView.this.exitMipMode(series, null);
                                        }
                                    }
                                } finally {
                                    progressBar.setVisible(false);
                                }
                            }
                        });
                    }
                }
            };
        process.start();

    }

    protected void setMip(DicomImageElement dicom) {
        DicomImageElement oldImage = getImage();
        if (dicom != null) {
            // Trick: call super to change the image as "this" method is empty
            super.setImage(dicom);
        }

        if (oldImage == null) {
            eventManager.updateComponentsListener(MipView.this);
        } else {
            // Close stream
            oldImage.dispose();
            // Delete file in cache
            File file = oldImage.getFile();
            if (file != null) {
                file.delete();
            }
        }
    }

    public void applyMipParameters() {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }

        final Integer extend = (Integer) getActionValue(MIP_THICKNESS.cmd());
        if (series == null || extend == null) {
            return;
        }
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                progressBar.setMinimum(0);
                progressBar.setMaximum(2 * extend + 1);
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

        process = new Thread(Messages.getString("MipView.build")) { //$NON-NLS-1$
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
                            Comparator sortFilter =
                                (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
                            Filter filter = (Filter) imageOperation.getActionValue(ActionW.FILTERED_SERIES.cmd());
                            Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);

                            // synchronized (series) {
                            Iterator<DicomImageElement> iter = medias.iterator();
                            int curImg = 2;
                            ActionState sequence = imageOperation.getEventManager().getAction(ActionW.SCROLL_SERIES);
                            if (sequence instanceof SliderCineListener) {
                                SliderCineListener cineAction = (SliderCineListener) sequence;
                                curImg = cineAction.getValue();
                            }

                            int startIndex = curImg - 1 - extend;

                            if (startIndex < 0) {
                                startIndex = 0;
                            }

                            final List<ImageElement> sources = new ArrayList<ImageElement>();
                            int stopIndex = curImg - 1 + extend;
                            int k = 0;
                            while (iter.hasNext()) {
                                if (this.isInterrupted()) {
                                    return;
                                }
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

                            if (sources.size() > 2) {
                                curImage =
                                    SeriesBuilder.addCollectionOperation(mipType, sources, MipView.this, progressBar);
                            } else {
                                curImage = null;
                            }

                            // }
                            final DicomImageElement dicom;
                            if (curImage != null) {
                                DicomImageElement imgRef = (DicomImageElement) sources.get(sources.size() / 2);
                                RawImage raw = null;
                                try {
                                    File mipDir =
                                        AppProperties.buildAccessibleTempDirectory(
                                            AppProperties.FILE_CACHE_DIR.getName(), "mip"); //$NON-NLS-1$
                                    raw = new RawImage(File.createTempFile("mip_", ".raw", mipDir));//$NON-NLS-1$ //$NON-NLS-2$);
                                    SeriesBuilder
                                        .writeRasterInRaw(curImage.getAsBufferedImage(), raw.getOutputStream());
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

                                rawIO.setTag(TagW.SliceThickness, SeriesBuilder.getThickness(sources));
                                double[] loc = (double[]) imgRef.getTagValue(TagW.SlicePosition);
                                if (loc != null) {
                                    rawIO.setTag(TagW.SlicePosition, loc);
                                    rawIO.setTag(TagW.SliceLocation, (float) (loc[0] + loc[1] + loc[2]));
                                }

                                rawIO.setTag(TagW.SeriesInstanceUID,
                                    "mip." + (String) series.getTagValue(TagW.SubseriesInstanceUID)); //$NON-NLS-1$

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
                                        TagW.RescaleIntercept, TagW.RescaleType, TagW.VOILUTsData,
                                        TagW.VOILUTsExplanation, TagW.PixelPaddingValue, TagW.PixelPaddingRangeLimit,
                                        TagW.WindowWidth, TagW.WindowCenter, TagW.WindowCenterWidthExplanation,
                                        TagW.VOILutFunction, TagW.PixelSpacing, TagW.ImagerPixelSpacing,
                                        TagW.PixelSpacingCalibrationDescription, TagW.PixelAspectRatio };
                                rawIO.copyTags(tagList2, imgRef, false);

                                // Image specific tags
                                rawIO.setTag(TagW.SOPInstanceUID, UIDUtils.createUID());
                                rawIO.setTag(TagW.InstanceNumber, 1);

                                dicom = new DicomImageElement(rawIO, 0);
                                DicomImageElement oldImage = getImage();
                                // Use graphics of the previous image when they belongs to a MIP image
                                if (oldImage != null && "mip.1".equals(oldImage.getTagValue(TagW.SOPInstanceUID))) { //$NON-NLS-1$
                                    dicom.setTag(TagW.MeasurementGraphics,
                                        oldImage.getTagValue(TagW.MeasurementGraphics));
                                }
                            } else {
                                dicom = null;
                            }

                            // Following actions need to be executed in EDT thread
                            GuiExecutor.instance().execute(new Runnable() {

                                @Override
                                public void run() {
                                    DicomImageElement oldImage = getImage();
                                    // Trick: call super to change the image as "this" method is empty
                                    MipView.super.setImage(dicom);
                                    if (oldImage == null) {
                                        eventManager.updateComponentsListener(MipView.this);
                                    } else {
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

}
