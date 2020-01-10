/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import org.weasis.core.api.util.StringUtil;

/**
 * The Class TableHaederRenderer.
 *
 */
@SuppressWarnings("serial")
public class TableHeaderRenderer extends JLabel implements TableCellRenderer {

    public TableHeaderRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
        setHorizontalTextPosition(SwingConstants.LEADING);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
        int row, int column) {
        if (table != null) {
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                Color fgColor = null;
                Color bgColor = null;
                if (hasFocus) {
                    fgColor = UIManager.getColor("TableHeader.focusCellForeground"); //$NON-NLS-1$
                    bgColor = UIManager.getColor("TableHeader.focusCellBackground"); //$NON-NLS-1$
                }
                if (fgColor == null) {
                    fgColor = header.getForeground();
                }
                if (bgColor == null) {
                    bgColor = header.getBackground();
                }
                setForeground(fgColor);
                setBackground(bgColor);
                setFont(header.getFont());
            }
        }
        String val = value == null ? null : value.toString();
        if (!StringUtil.hasText(val)) {
            val = " "; //$NON-NLS-1$
        }
        setText(val);
        setToolTipText(val);
        Border border = null;
        if (hasFocus) {
            border = UIManager.getBorder("TableHeader.focusCellBorder"); //$NON-NLS-1$
        }
        if (border == null) {
            border = UIManager.getBorder("TableHeader.cellBorder"); //$NON-NLS-1$
        }
        setBorder(border);
        return this;
    }

    @Override
    public void validate() {
        // Override for performance reasons
    }

    @Override
    public void revalidate() {
        // Override for performance reasons
    }

    @Override
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        // Override for performance reasons
    }

    @Override
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        // Override for performance reasons
    }
}
