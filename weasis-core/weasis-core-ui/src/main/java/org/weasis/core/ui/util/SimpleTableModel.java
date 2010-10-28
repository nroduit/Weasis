/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.util;

import javax.swing.table.AbstractTableModel;

import org.weasis.core.ui.Messages;

/**
 * <p>
 * Title: JMicroVision
 * </p>
 * 
 * <p>
 * Description: ImageJai processing and analysis
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2002 -2005
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.2.2
 */
public class SimpleTableModel extends AbstractTableModel {

    private String[] columnNames = { Messages.getString("SimpleTableModel.param"), Messages.getString("SimpleTableModel.val") }; //$NON-NLS-1$ //$NON-NLS-2$
    private Object[][] data = {};
    private final boolean editable;

    public SimpleTableModel(String[] columnNames, Object[][] data) {
        this(columnNames, data, false);
    }

    public SimpleTableModel(String[] columnNames, Object[][] data, boolean editable) {
        if (columnNames != null) {
            this.columnNames = columnNames;
        }
        if (data != null) {
            this.data = data;
        }
        this.editable = editable;
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public int getRowCount() {
        return data.length;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return editable;
    }

    public Object getValueAt(int row, int col) {
        return data[row][col];
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        data[row][col] = value;
        fireTableCellUpdated(row, col);
    }

    @Override
    public Class getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }

}
