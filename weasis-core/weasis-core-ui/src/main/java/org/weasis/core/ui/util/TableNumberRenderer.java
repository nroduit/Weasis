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
package org.weasis.core.ui.util;

import java.text.NumberFormat;

import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import org.weasis.core.api.util.LocalUtil;

/**
 * @author Nicolas Roduit
 */
@SuppressWarnings("serial")
public class TableNumberRenderer extends DefaultTableCellRenderer {
    private final NumberFormat formatter;

    public TableNumberRenderer() {
        this(2);
    }

    public TableNumberRenderer(int maxDecimal) {
        this.formatter = LocalUtil.getNumberInstance();
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
