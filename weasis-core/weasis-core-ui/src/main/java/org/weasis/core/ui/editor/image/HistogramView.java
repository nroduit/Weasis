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
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.op.ByteLutCollection.Lut;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class HistogramView extends JComponent implements SeriesViewerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistogramView.class);

    private final JPanel view = new JPanel();;
    private final JPanel histView = new JPanel();
    private final SeriesViewer<?> viewer;

    private ViewCanvas<?> view2DPane;

    private enum Model {
        GRAY("Luminance", buildLut(ByteLutCollection.Lut.GRAY)),

        RGB("RGB", buildLut(ByteLutCollection.Lut.RED), buildLut(ByteLutCollection.Lut.GREEN),
                        buildLut(ByteLutCollection.Lut.BLUE)),
        HSV("HSV", buildLut(ByteLutCollection.Lut.HUE), buildLut("Saturation", ByteLutCollection.Lut.GRAY),
                        buildLut("Value", ByteLutCollection.Lut.GRAY)),
        HLS("HLS", buildLut(ByteLutCollection.Lut.HUE), buildLut("Lightness", ByteLutCollection.Lut.GRAY),
                        buildLut("Saturation", ByteLutCollection.Lut.GRAY));

        private final ByteLut[] byteLut;
        private final String title;

        private Model(String name, ByteLut... luts) {
            this.title = name + " (" + Arrays.stream(luts).map(ByteLut::getName).collect(Collectors.joining(",")) + ")";
            this.byteLut = luts;
        }

        public ByteLut[] getByteLut() {
            return byteLut;
        }

        public String getTitle() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }

        private static ByteLut buildLut(Lut lut) {
            return buildLut(lut.getName(), lut);
        }

        private static ByteLut buildLut(String name, Lut lut) {
            return new ByteLut(name, lut.getByteLut().getLutTable(), lut.getByteLut().getInvertedLutTable());
        }

        private static ByteLut buildLut(String name, byte[][] slut) {
            return new ByteLut(name, slut, ByteLutCollection.invert(slut));
        }
    }

    private final ItemListener modelListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            buildHistogram();
            repaint();
        }
    };
    private final JComboBox<Model> jComboBoxImgChannel = new JComboBox<>();

    public HistogramView(SeriesViewer<?> viewer) {
        this.viewer = viewer;
        setLayout(new BorderLayout());
        view.setLayout(new BorderLayout());
        add(view, BorderLayout.CENTER);
        setPreferredSize(new Dimension(400, 300));
        setMinimumSize(new Dimension(150, 50));
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (event.getSeriesViewer() == viewer) {
            if (EVENT.SELECT.equals(type) || EVENT.LAYOUT.equals(type)) {
                updatePane(event);
            } else if (EVENT.WIN_LEVEL.equals(type)) {
                if (view2DPane == null) {
                    updatePane(event);
                } else {
                    WindLevelParameters p = getWinLeveParameters();
                    for (int i = 0; i < histView.getComponentCount(); i++) {
                        Component c = histView.getComponent(i);
                        if (c instanceof ChannelHistogramPanel) {
                            ((ChannelHistogramPanel) c).setWindLevelParameters(p);
                        }
                    }
                }
            }
        }
    }

    private void updatePane(SeriesViewerEvent event) {
        if (event.getSeriesViewer() instanceof ImageViewerPlugin) {
            view2DPane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
            displayHistogram();
        } else {
            view2DPane = null;
        }
    }

    private void displayHistogram() {
        buildLayout();
        buildHistogram();
        repaint();
    }

    private void buildLayout() {
        view.removeAll();
        if (view2DPane != null) {
            PlanarImage imageSource = view2DPane.getSourceImage();
            int channels = imageSource.channels();

            histView.setLayout(new BoxLayout(histView, BoxLayout.Y_AXIS));

            JPanel headerPanel = new JPanel();
            headerPanel.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
            headerPanel.add(new JLabel("Channel" + StringUtil.COLON));

            jComboBoxImgChannel.removeItemListener(modelListener);
            jComboBoxImgChannel.removeAllItems();
            Model[] vals = Model.values();
            int limit = channels > 1 ? imageSource.depth() > 1 ? 2 : vals.length : 1;
            for (int i = channels > 1 ? 1 : 0; i < limit; i++) {
                jComboBoxImgChannel.addItem(vals[i]);
            }
            if (channels > 1) {
                jComboBoxImgChannel.addItem(vals[0]);
            }
            jComboBoxImgChannel.addItemListener(modelListener);

            headerPanel.add(jComboBoxImgChannel);
            headerPanel.add(Box.createHorizontalStrut(15));

            final JLabel lutLabel = new JLabel();
            lutLabel.setText("Channel" + StringUtil.COLON);
            headerPanel
                .add(new JLabel("Pixels" + StringUtil.COLON_AND_SPACE + imageSource.width() * imageSource.height()));
            view.add(headerPanel, BorderLayout.NORTH);
            view.add(histView, BorderLayout.CENTER);
        }
    }

    private WindLevelParameters getWinLeveParameters() {
        if (view2DPane != null) {
            OpManager dispOp = view2DPane.getDisplayOpManager();
            WindowOp wlOp = (WindowOp) dispOp.getNode(WindowOp.OP_NAME);
            if (wlOp != null) {
                return wlOp.getWindLevelParameters();
            }
        }
        return null;
    }

    private void buildHistogram() {
        if (view2DPane != null) {
            histView.removeAll();

            WindLevelParameters p = getWinLeveParameters();
            if (p == null) {
                return;
            }
            PlanarImage imageSource = view2DPane.getSourceImage();
            int channels = imageSource.channels();
            // exportcsv(imageSource);
            Model clorModel = (Model) jComboBoxImgChannel.getSelectedItem();
            if (clorModel == null) {
                clorModel = channels > 1 ? Model.RGB : Model.GRAY;
            }
            int[] selChannels = new int[channels];
            for (int i = 0; i < selChannels.length; i++) {
                selChannels[i] = i;
            }
            try {
                List<Mat> listHisto = getHistogram(imageSource, 512, selChannels, clorModel, p);

                ByteLut[] lut = clorModel.getByteLut();
                for (int i = 0; i < lut.length; i++) {
                    ChannelHistogramPanel chartPanel = new ChannelHistogramPanel(lut[i].getName());
                    histView.add(chartPanel);
                    Mat h = listHisto.get(i);
                    float[] histValues = new float[h.rows()];
                    h.get(0, 0, histValues);
                    chartPanel.setHistogramBins(histValues, lut[i], p);
                }
            } catch (Exception e) {
                LOGGER.error("Build histogram", e);
            }
            histView.revalidate();
            histView.repaint();
        }
    }

    // TODO remove
    private void exportcsv(PlanarImage imageSource) {
        File csvOutputFile = new File("/tmp/" + "image" + ".csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            short[] pix = new short[imageSource.width()];
            for (int i = 0; i < imageSource.height(); i++) {
                imageSource.get(i, 0, pix);
                IntStream is = IntStream.range(0, pix.length).map(k -> pix[k]);
                pw.println(is.mapToObj(String::valueOf).collect(Collectors.joining(",")));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public List<Mat> getHistogram(PlanarImage imageSource, int nbBins, int[] selChannels, Model model,
        WindLevelParameters p) {
        if (selChannels.length == 0) {
            return Collections.emptyList();
        }
        // Number of histogram bins
        MatOfInt histSize = new MatOfInt(nbBins);
        MatOfFloat histRange = new MatOfFloat((float) p.getLevelMin(), (float) p.getLevelMax() + 1.0f);
        Mat img;
        if (CvType.depth(imageSource.type()) == CvType.CV_16S) {
            Mat floatImage = new Mat(imageSource.height(), imageSource.width(), CvType.CV_32F);
            imageSource.toMat().convertTo(floatImage, CvType.CV_32F);
            img = floatImage;
        } else {
            img = imageSource.toMat();
        }

        List<Mat> channels = new ArrayList<>();
        if (selChannels.length == 1) {
            channels.add(img);
        } else {
            if (Model.RGB == model) {
                Core.split(img, channels);
                Collections.reverse(channels);
            } else {
                ImageCV dstImg = new ImageCV();
                int code;
                if (Model.HSV == model) {
                    code = Imgproc.COLOR_BGR2HSV;
                } else if (Model.HLS == model) {
                    code = Imgproc.COLOR_BGR2HLS;
                } else if (Model.GRAY == model) {
                    code = Imgproc.COLOR_BGR2GRAY;
                } else {
                    code = Imgproc.COLOR_BGR2RGB;
                }
                Imgproc.cvtColor(img, dstImg, code);
                Core.split(dstImg, channels);
            }
        }

        if (channels.size() == 1) {
            Mat hist = new Mat();
            Imgproc.calcHist(channels, new MatOfInt(0), new Mat(), hist, histSize, histRange, false);
            return Arrays.asList(hist);
        }

        List<Mat> histograms = new ArrayList<>();
        for (int i = 0; i < selChannels.length; i++) {
            Mat hist = new Mat();
            Imgproc.calcHist(channels, new MatOfInt(selChannels[i]), new Mat(), hist, histSize, histRange, false);
            histograms.add(hist);
        }
        return histograms;
    }
}
