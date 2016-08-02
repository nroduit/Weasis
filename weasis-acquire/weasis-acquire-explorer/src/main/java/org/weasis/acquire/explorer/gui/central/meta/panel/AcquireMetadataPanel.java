package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;

public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
    private static final long serialVersionUID = -3479636894557525448L;

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

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
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
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
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        // DO NOTHING
    }

    public static class TagRenderer extends DefaultTableCellRenderer {

        @Override
        public void setValue(Object value) {
            setText((value == null) ? "" : TagW.getFormattedText(value, null));
        }
    }
}
