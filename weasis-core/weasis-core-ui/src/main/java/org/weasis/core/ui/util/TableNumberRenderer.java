/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
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
        this(2);
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
            setText((value == null) ? "" : value.toString()); //$NON-NLS-1$
        }
    }
}
