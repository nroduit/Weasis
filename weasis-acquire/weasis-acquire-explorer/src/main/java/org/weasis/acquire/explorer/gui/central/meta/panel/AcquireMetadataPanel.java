/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Optional;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.FontTools;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.enums.BodyPartExaminated;

import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;

@SuppressWarnings("serial")
public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
    protected String title;
    protected JLabel label = new JLabel();
    protected JTable table;
    protected JScrollPane tableScroll;
    protected AcquireImageInfo imageInfo;
    protected TitledBorder titleBorder;

    public AcquireMetadataPanel(String title) {
        setLayout(new BorderLayout());
        this.title = title;
        this.titleBorder =
            new TitledBorder(null, getDisplayText(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION);

        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(7, 3, 0, 3), titleBorder));

        tableScroll = new JScrollPane();
        tableScroll.setBorder(BorderFactory.createEmptyBorder(7, 3, 0, 3));
        table = new JTable();
        // Force to commit value when losing the focus
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); //$NON-NLS-1$
        table.setFont(FontTools.getFont11()); // Default size
        table.getTableHeader().setReorderingAllowed(false);
        updateTable();

        setMetaVisible(false);

        add(tableScroll, BorderLayout.CENTER);
    }

    public abstract AcquireMetadataTableModel newTableModel();

    public String getDisplayText() {
        return title;
    }

    @Override
    public Font getFont() {
        return FontTools.getFont12Bold();
    }

    public void setImageInfo(AcquireImageInfo imageInfo) {
        this.imageInfo = imageInfo;
        this.titleBorder.setTitle(getDisplayText());
        setMetaVisible(imageInfo != null);
        update();
    }

    public void setMetaVisible(boolean visible) {
        tableScroll.setVisible(visible);
    }

    public void update() {
        updateLabel();
        updateTable();
    }

    public void updateLabel() {
        this.titleBorder.setTitle(getDisplayText());
    }

    public void updateTable() {
        AcquireMetadataTableModel model = newTableModel();
        model.addTableModelListener(this);
        table.setModel(model);
        table.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
        table.getColumnModel().getColumn(1).setCellEditor(new AcquireImageCellEditor());
        int height = Optional.ofNullable(table.getFont()).map(f -> f.getSize() + 10).orElse(24);
        for (int i = 0; i < table.getRowCount(); i++) {
            table.setRowHeight(i, height);
        }

        JPanel tableContainer = new JPanel(new BorderLayout());
        int cheight =
            (height + table.getRowMargin()) * table.getRowCount() + table.getRowHeight() + table.getRowMargin();
        tableContainer.setPreferredSize(new Dimension(table.getColumnModel().getTotalColumnWidth(), cheight));
        tableContainer.add(table.getTableHeader(), BorderLayout.PAGE_START);
        tableContainer.add(table, BorderLayout.CENTER);
        tableScroll.setViewportView(tableContainer);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        // DO NOTHING
    }

    @SuppressWarnings("serial")
    public static class TagRenderer extends DefaultTableCellRenderer {

        public TagRenderer() {
            setFont(FontTools.getFont11()); // Default size
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
            Component val = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            Object tag = table.getModel().getValueAt(row, 0);
            if (tag instanceof TagW) {
                setValue(((TagW) tag).getFormattedTagValue(value, null));
            }
            return val;
        }
    }

    @SuppressWarnings("serial")
    public static class AcquireImageCellEditor extends AbstractCellEditor implements TableCellEditor {
        private static final JComboBox<BodyPartExaminated> bodyPartsCombo =
            new JComboBox<>(BodyPartExaminated.values());
        private static final JComboBox<TagD.Sex> sexCombo = new JComboBox<>(TagD.Sex.values());
        private static final JComboBox<Modality> modalityCombo = new JComboBox<>(Modality.getAllModalitiesExceptDefault());
        static {
            bodyPartsCombo.setMaximumRowCount(15);
            modalityCombo.setMaximumRowCount(15);
        }

        private Optional<TableCellEditor> editor;

        @Override
        public Object getCellEditorValue() {
            return editor.map(e -> convertValue(e.getCellEditorValue())).orElse(null);
        }
        
        private Object convertValue(Object val) {
            if(val instanceof TagD.Sex){
                return ((TagD.Sex) val).getValue();
            }
            else if(val instanceof Modality){
                return ((Modality) val).name();
            }
            return val;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
            int column) {
            TableCellEditor cellEditor;
            Object tag = table.getModel().getValueAt(row, 0);
            int tagID = 0;
            boolean date = false;
            boolean time = false;
            if (tag instanceof TagW) {
                tagID = ((TagW) tag).getId();
                TagType type = ((TagW) tag).getType();
                date = TagType.DICOM_DATE == type || TagType.DATE == type;
                time = TagType.DICOM_TIME == type || TagType.TIME == type;
            }
            if (tagID == Tag.BodyPartExamined) {
                cellEditor = new DefaultCellEditor(bodyPartsCombo);
            } else if (tagID == Tag.PatientSex) {
                sexCombo.setSelectedItem(TagD.Sex.getSex(value == null ? null : value.toString()));
                cellEditor = new DefaultCellEditor(sexCombo);
            } else if (tagID == Tag.Modality) {
                modalityCombo.setSelectedItem(Modality.getModality(value == null ? null : value.toString()));
                cellEditor = new DefaultCellEditor(modalityCombo);
            } else if (date) {
                DateTableEditor teditor = new DateTableEditor(false, true, true);
                teditor.getDatePickerSettings().setFontInvalidDate(FontTools.getFont11()); // Default size
                teditor.getDatePickerSettings().setFontValidDate(FontTools.getFont11()); // Default size
                teditor.getDatePickerSettings().setFontVetoedDate(FontTools.getFont11());// Default size
                JMVUtils.setPreferredHeight(teditor.getDatePicker().getComponentToggleCalendarButton(),
                    table.getRowHeight(row));
                cellEditor = teditor;
            } else if (time) {
                TimeTableEditor teditor = new TimeTableEditor(false, true, true);
                teditor.getTimePickerSettings().fontInvalidTime = FontTools.getFont11(); // Default size
                teditor.getTimePickerSettings().fontValidTime = FontTools.getFont11(); // Default size
                teditor.getTimePickerSettings().fontVetoedTime = FontTools.getFont11(); // Default size
                JMVUtils.setPreferredHeight(teditor.getTimePicker().getComponentToggleTimeMenuButton(),
                    table.getRowHeight(row));
                cellEditor = teditor;
            } else {
                cellEditor = new DefaultCellEditor(new JTextField());
            }
            editor = Optional.of(cellEditor);
            Component c = cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
            c.setFont(FontTools.getFont11()); // Default size
            return c;
        }
    }
}
