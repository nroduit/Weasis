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
import java.awt.Font;
import java.util.Optional;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.FontTools;
import org.weasis.dicom.codec.enums.BodyPartExaminated;

import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;

public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
    private static final long serialVersionUID = -3479636894557525448L;

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    protected String title;
    protected JLabel label = new JLabel();
    protected JTable table;
    protected JPanel tableContainer;
    protected AcquireImageInfo imageInfo;
    protected TitledBorder titleBorder;

    public AcquireMetadataPanel(String title) {
        setLayout(new BorderLayout());
        this.title = title;
        this.titleBorder =
            new TitledBorder(null, getDisplayText(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION);

        setBorder(BorderFactory.createCompoundBorder(spaceY, titleBorder));

        AcquireMetadataTableModel model = newTableModel();
        model.addTableModelListener(this);
        table = new JTable(model);
        // Force to commit value when losing the focus
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); //$NON-NLS-1$
        table.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());

        tableContainer = new JPanel(new BorderLayout());
        tableContainer.add(table.getTableHeader(), BorderLayout.PAGE_START);
        tableContainer.add(table, BorderLayout.CENTER);
        tableContainer.setBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3));
        setMetaVisible(false);

        add(tableContainer, BorderLayout.CENTER);
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
        tableContainer.setVisible(visible);
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
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        // DO NOTHING
    }

    @SuppressWarnings("serial")
    public static class TagRenderer extends DefaultTableCellRenderer {

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
        private static final JComboBox<BodyPartExaminated> bodyParts = new JComboBox<>(BodyPartExaminated.values());
        static {
            bodyParts.setMaximumRowCount(15);
        }

        private Optional<TableCellEditor> editor;

        @Override
        public Object getCellEditorValue() {
            return editor.map(e -> e.getCellEditorValue()).orElse(null);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
            int column) {
            TableCellEditor cellEditor;
            Object tag = table.getModel().getValueAt(row, 0);
            boolean bodyPartCell = false;
            boolean date = false;
            boolean time = false;
            if (tag instanceof TagW) {
                bodyPartCell = ((TagW) tag).getId() == Tag.BodyPartExamined;
                TagType type = ((TagW) tag).getType();
                date = TagType.DICOM_DATE == type || TagType.DATE == type;
                time = TagType.DICOM_TIME == type || TagType.TIME == type;
            }
            if (bodyPartCell) {
                cellEditor = new DefaultCellEditor(bodyParts);
            } else if (date) {
                DateTableEditor teditor = new DateTableEditor(false, true, true);
                table.setRowHeight(row, (int) teditor.getDatePicker().getPreferredSize().getHeight());
                cellEditor = teditor;
            } else if (time) {
                TimeTableEditor teditor = new TimeTableEditor(false, true, true);
                table.setRowHeight(row, (int) teditor.getTimePicker().getPreferredSize().getHeight());
                cellEditor = teditor;
            } else {
                cellEditor = new DefaultCellEditor(new JTextField());
            }
            editor = Optional.of(cellEditor);
            return cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
        }
    }
}
