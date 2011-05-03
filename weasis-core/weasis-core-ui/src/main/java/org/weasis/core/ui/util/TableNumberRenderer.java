package org.weasis.core.ui.util;

import java.text.NumberFormat;

import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * @author Nicolas Roduit
 */
public class TableNumberRenderer extends DefaultTableCellRenderer {
    private final NumberFormat formatter;

    public TableNumberRenderer() {
        this.formatter = NumberFormat.getNumberInstance();
    }

    public TableNumberRenderer(int maxDecimal) {
        this.formatter = NumberFormat.getNumberInstance();
        formatter.setMaximumFractionDigits(maxDecimal);
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Number) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText(formatter.format(value));
        } else {
            setHorizontalAlignment(SwingConstants.LEFT);
            setText((value == null) ? "" : value.toString());
        }
    }
}
