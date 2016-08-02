package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import java.awt.Component;
import java.util.Optional;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.enums.BodyPartExaminated;

import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;

public class AcquireImageMetaPanel extends AcquireMetadataPanel {
    private static final long serialVersionUID = -2751941971479265507L;

    private static final String NO_IMAGE = "No image";
    private static final String IMAGE_PREFIX = "Image : ";

    public AcquireImageMetaPanel(String title) {
        super(title);
    }

    @Override
    public AcquireMetadataTableModel newTableModel() {
        return new AcquireImageMeta(imageInfo);
    }

    @Override
    public String getDisplayText() {
        if (imageInfo != null) {
            return new StringBuilder(IMAGE_PREFIX).append(imageInfo.getImage().getName()).toString();
        } else {
            return NO_IMAGE;
        }
    }

    @Override
    public void updateTable() {
        super.updateTable();
        table.getColumnModel().getColumn(1).setCellEditor(new AcquireImageCellEditor());
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
                table.setRowHeight(row, (int)teditor.getTimePicker().getPreferredSize().getHeight());
                cellEditor = teditor;
            } else {
                cellEditor = new DefaultCellEditor(new JTextField());
            }
            editor = Optional.of(cellEditor);
            return cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
        }
    }
}
