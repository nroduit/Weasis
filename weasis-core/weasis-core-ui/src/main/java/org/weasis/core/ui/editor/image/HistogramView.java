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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.op.ByteLutCollection.Lut;
import org.weasis.core.api.image.util.Statistics;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableNumberRenderer;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class HistogramView extends JComponent implements SeriesViewerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistogramView.class);

    private final JPanel view = new JPanel();
    private final JPanel histView = new JPanel();
    private final SeriesViewer<?> viewer;
    private final JSpinner spinnerBins = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 16));

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
            return new ByteLut(name, lut.getByteLut().getLutTable());
        }

        private static ByteLut buildLut(String name, byte[][] slut) {
            return new ByteLut(name, slut);
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
            } else if (EVENT.LUT.equals(type)) {
                WindLevelParameters p = getWinLeveParameters();
                if (p == null) {
                    return;
                }
                PlanarImage imageSource = view2DPane.getSourceImage();
                int channels = imageSource.channels();
                Model colorModel = getSelectedColorModel(channels);
                DisplayByteLut[] lut = getLut(p, colorModel);
                for (int i = 0; i < histView.getComponentCount(); i++) {
                    Component c = histView.getComponent(i);
                    if (c instanceof ChannelHistogramPanel) {
                        ((ChannelHistogramPanel) c).setLut(lut[i]);
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
            headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
            headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 7, 7, 7),
                new TitledBorder(null, "Histogram Parameters", TitledBorder.DEFAULT_JUSTIFICATION,
                    TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));

            JPanel row1 = new JPanel();
            row1.add(new JLabel("Channel" + StringUtil.COLON));

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

            row1.add(jComboBoxImgChannel);
            headerPanel.add(row1);

            JPanel row2 = new JPanel();
            row2.add(new JLabel("Bins" + StringUtil.COLON));
            JMVUtils.formatCheckAction(spinnerBins);
            row2.add(spinnerBins);
            spinnerBins.addChangeListener(e -> buildHistogram());
            row2.add(Box.createHorizontalStrut(15));

            final JButton stats = new JButton("Statistics");
            stats.addActionListener((ActionEvent e) -> showStatistics());
            row2.add(stats);
            headerPanel.add(row2);

            view.add(headerPanel, BorderLayout.NORTH);
            view.add(histView, BorderLayout.CENTER);
        }
    }

    private void showStatistics() {
        ChannelHistogramPanel[] hist = new ChannelHistogramPanel[histView.getComponentCount()];
        for (int i = 0; i < hist.length; i++) {
            Component c = histView.getComponent(i);
            if (c instanceof ChannelHistogramPanel) {
                hist[i] = (ChannelHistogramPanel) c;
            }
        }

        if (hist.length == 0) {
            return;
        }
       
        List<MeasureItem> measList = new ArrayList<>();
        for (int i = 0; i < hist.length; i++) {
            String unit = hist[i].getLut().getName();
            float[] b = hist[i].getHistValues();
            double[] bins = new double[b.length];
            double nb = 0;
            double min = Double.MAX_VALUE;
            double max = 0;
            for (int k = 0; k < bins.length; k++) {
                bins[k] = b[k];
                nb += bins[k];
                if (bins[k] < min) {
                    min = bins[k];
                }
                if (bins[k] > max) {
                    max = bins[k];
                }
            }
            double mean = nb / bins.length;
            Statistics.stDev(bins, mean);
            if (i == 0) {
                measList.add(new MeasureItem(ImageStatistics.IMAGE_PIXELS, nb, Unit.PIXEL.getAbbreviation()));
            }
            measList.add(new MeasureItem(ImageStatistics.IMAGE_MIN, min, unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_MAX, max, unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_MEDIAN, Statistics.median(bins), unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_MEAN, mean, unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_STD, Statistics.stDev(bins, mean), unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_SKEW, Statistics.skewness(bins), unit));
            measList.add(new MeasureItem(ImageStatistics.IMAGE_KURTOSIS, Statistics.kurtosis(bins), unit));
        }
        JPanel tableContainer = new JPanel();
        tableContainer.setBorder(BorderFactory.createEtchedBorder());
        tableContainer.setLayout(new BorderLayout());

        JTable jtable =
            MeasureTool.createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtable.setFont(FontTools.getFont10());
        jtable.getTableHeader().setReorderingAllowed(false);

        String[] headers = { Messages.getString("MeasureTool.param"), Messages.getString("MeasureTool.val") }; //$NON-NLS-1$ //$NON-NLS-2$
        jtable.setModel(new SimpleTableModel(headers, MeasureTool.getLabels(measList)));
        jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
        MeasureTool.createTableHeaders(jtable);
        tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
        tableContainer.add(jtable, BorderLayout.CENTER);
        jtable.getColumnModel().getColumn(0).setPreferredWidth(120);
        jtable.getColumnModel().getColumn(1).setPreferredWidth(80);
        JOptionPane.showMessageDialog(spinnerBins, tableContainer, "Statistics", JOptionPane.PLAIN_MESSAGE, null);
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

    private DisplayByteLut[] getLut(WindLevelParameters p, Model colorModel) {
        DisplayByteLut[] lut = null;
        if (view2DPane != null) {
            int channels = view2DPane.getSourceImage().channels();
            if (channels == 1) {
                DisplayByteLut disLut = null;
                OpManager dispOp = view2DPane.getDisplayOpManager();
                PseudoColorOp lutOp = (PseudoColorOp) dispOp.getNode(PseudoColorOp.OP_NAME);
                if (lutOp != null) {
                    ByteLut lutTable = (ByteLut) lutOp.getParam(PseudoColorOp.P_LUT);
                    if (lutTable != null && lutTable.getLutTable() != null) {
                        disLut = new DisplayByteLut(lutTable);
                    }
                }

                if (disLut == null) {
                    disLut = new DisplayByteLut(Model.GRAY.getByteLut()[0]);
                }
                disLut.setInvert(p.isInverseLut());
                lut = new DisplayByteLut[] { disLut };
            }
        }

        if (lut == null) {
            ByteLut[] l = colorModel.getByteLut();
            lut = new DisplayByteLut[l.length];
            for (int i = 0; i < l.length; i++) {
                lut[i] = new DisplayByteLut(l[i]);
            }
        }
        return lut;
    }

    private Model getSelectedColorModel(int channels) {
        Model clorModel = (Model) jComboBoxImgChannel.getSelectedItem();
        if (clorModel == null) {
            clorModel = channels > 1 ? Model.RGB : Model.GRAY;
        }
        return clorModel;
    }

    private void buildHistogram() {
        if (view2DPane != null) {
            ChannelHistogramPanel[] old = new ChannelHistogramPanel[histView.getComponentCount()];
            for (int i = 0; i < old.length; i++) {
                Component c = histView.getComponent(i);
                if (c instanceof ChannelHistogramPanel) {
                    old[i] = (ChannelHistogramPanel) c;
                }
            }
            histView.removeAll();

            WindLevelParameters p = getWinLeveParameters();
            if (p == null) {
                return;
            }
            PlanarImage imageSource = view2DPane.getSourceImage();
            int channels = imageSource.channels();
            Model colorModel = getSelectedColorModel(channels);
            int[] selChannels = new int[channels];
            for (int i = 0; i < selChannels.length; i++) {
                selChannels[i] = i;
            }
            try {
                List<Mat> listHisto =
                    getHistogram(imageSource, (Integer) spinnerBins.getValue(), selChannels, colorModel, p);

                ByteLut[] lut = colorModel.getByteLut();
                DisplayByteLut[] displut = getLut(p, colorModel);
                for (int i = 0; i < lut.length; i++) {
                    ChannelHistogramPanel chartPanel;
                    if (i >= old.length || old[i] == null) {
                        chartPanel = new ChannelHistogramPanel(lut[i].getName());
                    } else {
                        chartPanel = new ChannelHistogramPanel(lut[i].getName(), old[i].isAccumulate(),
                            old[i].isLogarithmic(), old[i].isShowIntensity());
                    }
                    histView.add(chartPanel);
                    Mat h = listHisto.get(i);
                    float[] histValues = new float[h.rows()];
                    h.get(0, 0, histValues);
                    chartPanel.setHistogramBins(histValues, displut[i], p);
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
