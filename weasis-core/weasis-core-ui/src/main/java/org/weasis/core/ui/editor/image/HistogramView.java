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
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.HistogramData.Model;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.AbstractDragGraphicArea;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableNumberRenderer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

public class HistogramView extends JComponent implements SeriesViewerListener, GraphicSelectionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistogramView.class);

    private final JPanel view = new JPanel();
    private final JPanel histView = new JPanel();
    private final SeriesViewer<?> viewer;
    private final JSpinner spinnerBins = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 8));

    private ViewCanvas<?> view2DPane;

    private final ItemListener modelListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            buildHistogram();
            repaint();
        }
    };
    private final JComboBox<Model> jComboBoxImgChannel = new JComboBox<>();
    private AbstractDragGraphicArea selectedGraphic;

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
                            ((ChannelHistogramPanel) c).getData().updateVoiLut(view2DPane);
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
        if (view2DPane != null && view2DPane.getSourceImage() != null) {
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
            MeasurableLayer layer = view2DPane.getMeasurableLayer();
            int datatype = ImageConversion.convertToDataType(imageSource.type());
            boolean intVal = datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT;
            if (layer != null && intVal) {
                int nbins = (Integer) spinnerBins.getValue();
                int range = (int) (layer.getPixelMax() - layer.getPixelMin());
                if (range < nbins) {
                    spinnerBins.setValue(range);
                }
            }
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
        if (view2DPane == null || histView.getComponentCount() == 0) {
            return;
        }

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
            List<MeasureItem> list =
                ImageRegionStatistics.getStatistics(hist[i].getData(), hist.length == 1 ? null : i);
            if (i > 0) {
                list.remove(0);
            }
            measList.addAll(list);
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
        if (view2DPane != null && view2DPane.getSourceImage() != null) {
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
            MeasurableLayer layer = view2DPane.getMeasurableLayer();
            double pixMin = layer.getPixelMin();
            double pixMax = layer.getPixelMax();
            PlanarImage imageSource = view2DPane.getSourceImage();
            int channels = imageSource.channels();
            Model colorModel = getSelectedColorModel(channels);
            int[] selChannels = new int[channels];
            for (int i = 0; i < selChannels.length; i++) {
                selChannels[i] = i;
            }
            try {
                int nbins = (Integer) spinnerBins.getValue();
                List<Mat> imgPr = ImageRegionStatistics.prepareInputImages(selectedGraphic, layer);
                if (imgPr.size() > 1) {
                    Mat srcImg = imgPr.get(0);
                    Mat mask = imgPr.get(1);
                    List<Mat> listHisto =
                        HistogramData.computeHistogram(srcImg, mask, nbins, selChannels, colorModel, pixMin, pixMax);

                    ByteLut[] lut = colorModel.getByteLut();
                    DisplayByteLut[] displut = getLut(p, colorModel);
                    for (int i = 0; i < lut.length; i++) {
                        ChannelHistogramPanel chartPanel;
                        StringBuilder name = new StringBuilder(lut[i].getName());
                        name.append(StringUtil.SPACE);
                        name.append("Histogram");
                        if (StringUtil.hasText(layer.getPixelValueUnit())) {
                            name.append(" [");
                            name.append(layer.getPixelValueUnit());
                            name.append("]");
                        }
                        if (i >= old.length || old[i] == null) {
                            chartPanel = new ChannelHistogramPanel(name.toString());
                        } else {
                            chartPanel = new ChannelHistogramPanel(name.toString(), old[i].isAccumulate(), old[i].isLogarithmic(),
                                old[i].isShowIntensity());
                        }
                        histView.add(chartPanel);
                        Mat h = listHisto.get(i);
                        float[] histValues = new float[h.rows()];
                        h.get(0, 0, histValues);
                        HistogramData data = new HistogramData(histValues, displut[i], i, p, pixMin, pixMax, layer);
                        data.updateVoiLut(view2DPane);
                        chartPanel.setHistogramBins(data);
                    }
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

    @Override
    public void handle(List<Graphic> selectedGraphicList, MeasurableLayer layer) {
        AbstractDragGraphicArea g = null;

        if (selectedGraphicList != null && selectedGraphicList.size() == 1
            && selectedGraphicList.get(0) instanceof AbstractDragGraphicArea) {
            AbstractDragGraphicArea sel = (AbstractDragGraphicArea) selectedGraphicList.get(0);
            if (!(sel instanceof SelectGraphic)) {
                g = sel;
            }
        }
        boolean update = !Objects.equals(selectedGraphic, g);
        this.selectedGraphic = g;
        if (update) {
            buildHistogram();
        }
    }

    @Override
    public void updateMeasuredItems(List<MeasureItem> measureList) {
        if (selectedGraphic != null) {
            buildHistogram();
        }
    }

}
