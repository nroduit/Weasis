/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.dicom.wave.dockable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.TableHeaderRenderer;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.util.SimpleTableModel;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.wave.Messages;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public class MeasureAnnotationTool extends PluginTool implements SeriesViewerListener {
    private static final long serialVersionUID = 1117961156637401550L;

    public static final String BUTTON_NAME = "Measurements"; //$NON-NLS-1$

    private final JScrollPane rootPane;
    private final JPanel tableMarkerContainer = new JPanel();
    private JTable jtableMarker;

    private final JPanel tableTagContainer = new JPanel();
    private JTable jtableTag;

    public MeasureAnnotationTool() {
        super(BUTTON_NAME, BUTTON_NAME, POSITION.EAST, ExtendedMode.NORMALIZED, PluginTool.Type.TOOL, 30);
        this.rootPane = new JScrollPane();
        dockable.setTitleIcon(new ImageIcon(MeasureTool.class.getResource("/icon/16x16/measure.png"))); //$NON-NLS-1$
        setDockableWidth(300);
        jbInit();
    }

    private final void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getMarkerPanel());
        add(getAnnotationsPanel());
    }

    public final JPanel getAnnotationsPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, "Annotations", //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(),
                Color.GRAY)));

        JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transform.add(panel1);
        transform.add(Box.createVerticalStrut(5));
        jtableTag = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtableTag.setFont(FontTools.getFont10());

        jtableTag.getTableHeader().setReorderingAllowed(false);
        tableTagContainer.setBorder(BorderFactory.createEtchedBorder());
        tableTagContainer.setPreferredSize(new Dimension(50, 80));
        tableTagContainer.setLayout(new BorderLayout());
        transform.add(tableTagContainer);

        return transform;
    }

    public JPanel getMarkerPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3),
            new TitledBorder(null, "Markers", //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(),
                Color.GRAY)));

        JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transform.add(panel1);
        transform.add(Box.createVerticalStrut(5));
        jtableMarker = createMultipleRenderingTable(new SimpleTableModel(new String[] {}, new Object[][] {}));
        jtableMarker.setFont(FontTools.getFont10());

        jtableMarker.getTableHeader().setReorderingAllowed(false);
        tableMarkerContainer.setBorder(BorderFactory.createEtchedBorder());
        tableMarkerContainer.setPreferredSize(new Dimension(50, 80));
        tableMarkerContainer.setLayout(new BorderLayout());
        transform.add(tableMarkerContainer);

        return transform;
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Do nothing
    }

    public static JTable createMultipleRenderingTable(TableModel model) {
        JTable table = new JTable(model);
        table.getColumnModel().setColumnMargin(3);
        return table;
    }

    public static void createTableHeaders(JTable table) {
        table.getColumnModel().getColumn(0).setHeaderRenderer(new TableHeaderRenderer());
        table.getColumnModel().getColumn(1).setHeaderRenderer(new TableHeaderRenderer());
    }

    public void readAnnotations(Attributes attributes) {
        tableTagContainer.removeAll();

        // just clear tableContainer if measList is null
        if (attributes != null) {
            List<Object[]> list = new ArrayList<>();
            readAcquisitionContextSequence(attributes, list);
            readFiltersFrequency(attributes, list);
            readWaveformAnnotations(attributes, list);

            Object[][] labels = new Object[list.size()][];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = list.get(i);
            }
            String[] headers = { Messages.getString("MeasureAnnotationTool.tag"), Messages.getString("MeasureAnnotationTool.value") }; //$NON-NLS-1$ //$NON-NLS-2$
            jtableTag.setModel(new SimpleTableModel(headers, labels));
            jtableTag.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
            createTableHeaders(jtableTag);
            int height = (jtableTag.getRowHeight() + jtableTag.getRowMargin()) * jtableTag.getRowCount()
                + jtableTag.getTableHeader().getHeight() + 5;
            tableTagContainer.setPreferredSize(new Dimension(jtableTag.getColumnModel().getTotalColumnWidth(), height));
            tableTagContainer.add(jtableTag.getTableHeader(), BorderLayout.PAGE_START);
            tableTagContainer.add(jtableTag, BorderLayout.CENTER);
            TableColumnAdjuster.pack(jtableTag);
        } else {
            tableTagContainer.setPreferredSize(new Dimension(50, 50));
        }
        tableTagContainer.revalidate();
        tableTagContainer.repaint();
    }

    public void updateMeasuredItems(List<Object[]> list) {
        tableMarkerContainer.removeAll();

        if (list != null && !list.isEmpty()) {
            Object[][] labels = new Object[list.size()][];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = list.get(i);
            }
            String[] headers = { Messages.getString("MeasureAnnotationTool.lead"), Messages.getString("MeasureAnnotationTool.tag"), Messages.getString("MeasureAnnotationTool.value") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            jtableMarker.setModel(new SimpleTableModel(headers, labels));
            jtableMarker.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
            createTableHeaders(jtableMarker);
            int height = (jtableMarker.getRowHeight() + jtableMarker.getRowMargin()) * jtableMarker.getRowCount()
                + jtableMarker.getTableHeader().getHeight() + 5;
            tableMarkerContainer
                .setPreferredSize(new Dimension(jtableMarker.getColumnModel().getTotalColumnWidth(), height));
            tableMarkerContainer.add(jtableMarker.getTableHeader(), BorderLayout.PAGE_START);
            tableMarkerContainer.add(jtableMarker, BorderLayout.CENTER);
            TableColumnAdjuster.pack(jtableMarker);
        } else {
            tableMarkerContainer.setPreferredSize(new Dimension(50, 50));
        }
        tableMarkerContainer.revalidate();
        tableMarkerContainer.repaint();
    }

    public static int getNumberOfMeasures(boolean[] select) {
        int k = 0;
        for (int i = 0; i < select.length; i++) {
            if (select[i]) {
                k++;
            }
        }
        return k;
    }

    private void readAcquisitionContextSequence(Attributes attributes, List<Object[]> list) {

        Sequence ctxSeq = attributes.getSequence(Tag.AcquisitionContextSequence);
        if (ctxSeq != null) {
            for (int i = 0; i < ctxSeq.size(); i++) {
                Attributes item = ctxSeq.get(i);

                try {
                    String value = ""; //$NON-NLS-1$
                    if ("NUMERIC".equalsIgnoreCase(item.getString(Tag.ValueType))) { //$NON-NLS-1$
                        value = item.getString(Tag.NumericValue);
                    } else {
                        Optional<Attributes> cdSeq =
                            Optional.ofNullable(item.getNestedDataset(Tag.ConceptCodeSequence));
                        if (cdSeq.isPresent()) {
                            value = cdSeq.get().getString(Tag.CodeMeaning);
                        }
                    }
                    String name = Optional.of(item.getNestedDataset(Tag.ConceptNameCodeSequence)).get()
                        .getString(Tag.CodeMeaning);
                    addValueToModel(list, name, value);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private static void addValueToModel(List<Object[]> list, Object column1, Object column2) {
        Object[] row = new Object[] { column1, column2 };
        list.add(row);
    }

    private void readFiltersFrequency(Attributes attributes, List<Object[]> list) {

        Attributes dcm = Optional.of(attributes.getNestedDataset(Tag.WaveformSequence)).get();
        Sequence chDefSeq = Optional.of(dcm.getSequence(Tag.ChannelDefinitionSequence)).get();

        if (!chDefSeq.isEmpty()) {
            Attributes item = chDefSeq.get(0);
            double filterLow = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
            addValueToModel(list, TagD.get(Tag.FilterLowFrequency), filterLow + " Hz"); //$NON-NLS-1$
            double filterHigh = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
            addValueToModel(list, TagD.get(Tag.FilterHighFrequency), filterHigh + " Hz"); //$NON-NLS-1$
            double notchFilter = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
            addValueToModel(list, TagD.get(Tag.NotchFilterFrequency), notchFilter + " Hz"); //$NON-NLS-1$

            for (int i = 1; i < chDefSeq.size(); i++) {
                item = chDefSeq.get(i);
                String title = item.getNestedDataset(Tag.ChannelSourceSequence).getString(Tag.CodeMeaning);
                double low = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterLowFrequency, 0.0);
                if (low != filterLow) {
                    addValueToModel(list, title + " - " + TagD.get(Tag.FilterLowFrequency).getDisplayedName(), //$NON-NLS-1$
                        low + " Hz"); //$NON-NLS-1$
                }

                double high = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.FilterHighFrequency, 0.0);
                if (high != filterHigh) {
                    addValueToModel(list, title + " - " + TagD.get(Tag.FilterHighFrequency).getDisplayedName(), //$NON-NLS-1$
                        high + " Hz"); //$NON-NLS-1$
                }

                double notch = DicomMediaUtils.getDoubleFromDicomElement(item, Tag.NotchFilterFrequency, 0.0);
                if (notch != notchFilter) {
                    addValueToModel(list, title + " - " + TagD.get(Tag.NotchFilterFrequency).getDisplayedName(), //$NON-NLS-1$
                        notch + " Hz"); //$NON-NLS-1$
                }
            }
        }
    }

    private void readWaveformAnnotations(Attributes attributes, List<Object[]> list) {
        Sequence anSeq = attributes.getSequence(Tag.WaveformAnnotationSequence);
        if (anSeq != null) {
            for (int i = 0; i < anSeq.size(); i++) {
                Attributes item = anSeq.get(i);

                try {
                    String text = item.getString(Tag.UnformattedTextValue);
                    if (StringUtil.hasText(text)) {
                        addValueToModel(list, "Text", text); //$NON-NLS-1$
                        continue;
                    }
                    Optional<Attributes> mSeq =
                        Optional.ofNullable(item.getNestedDataset(Tag.MeasurementUnitsCodeSequence));
                    if (mSeq.isPresent()) {
                        String name = item.getNestedDataset(Tag.ConceptNameCodeSequence).getString(Tag.CodeMeaning);
                        String value = item.getString(Tag.NumericValue);
                        String unit = mSeq.get().getString(Tag.CodeValue);
                        addValueToModel(list, name, value + " " + unit); //$NON-NLS-1$
                    } else if ("POINT".equals(item.getString(Tag.TemporalRangeType))) { //$NON-NLS-1$
                        String name = item.getNestedDataset(Tag.ConceptNameCodeSequence).getString(Tag.CodeMeaning);
                        String value = item.getString(Tag.ReferencedSamplePositions);
                        String unit = item.getString(Tag.TemporalRangeType);
                        addValueToModel(list, name, value + " " + unit); //$NON-NLS-1$
                    }

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
            setSeries((Series<?>) event.getSeries());
        }
    }

    public void setSeries(Series<?> series) {
        if (series != null) {
            // Should have only one object by series (if more, they are split in several sub-series in dicomModel)
            DicomSpecialElement s = DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
            if (s != null) {
                readAnnotations(s.getMediaReader().getDicomObject());
            }
        }
    }

    public static class TagRenderer extends DefaultTableCellRenderer {

        public TagRenderer() {
            setFont(FontTools.getFont11()); // Default size
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
            Component val = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            TableModel model = table.getModel();
            Object tag = model.getValueAt(row, 0);
            if (tag instanceof TagW) {
                setValue(((TagW) tag).getFormattedTagValue(value, null));
            }
            return val;
        }
    }

}