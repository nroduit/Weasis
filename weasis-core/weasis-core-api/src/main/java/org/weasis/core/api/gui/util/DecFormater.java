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
package org.weasis.core.api.gui.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * The Class DecFormater.
 * 
 * @author Nicolas Roduit
 */
public class DecFormater {

    private static NumberFormat df1 = NumberFormat.getNumberInstance(); // 1 decimals
    private static NumberFormat df2 = NumberFormat.getNumberInstance(); // 2 decimals
    private static NumberFormat df4 = NumberFormat.getNumberInstance(); // 4 decimals
    static {
        df1.setMaximumFractionDigits(1);
        df2.setMaximumFractionDigits(2);
        df4.setMaximumFractionDigits(4);
    }
    private static DecimalFormat dfSci = new DecimalFormat("0.####E0"); // Scientific format with 4 decimals //$NON-NLS-1$

    public static String oneDecimal(Number val) {
        return df1.format(val);
    }

    public static String twoDecimal(Number val) {
        return df2.format(val);
    }

    public static String fourDecimal(Number val) {
        return df4.format(val);
    }

    public static String scientificFormat(Number val) {
        return dfSci.format(val);
    }

    public static DefaultFormatterFactory setPreciseDoubleFormat(double min, double max) {
        NumberFormatter displayFormatter = new NumberFormatter(new DecimalFormat("#,##0.##")); //$NON-NLS-1$
        displayFormatter.setValueClass(Double.class);
        NumberFormatter editFormatter = new NumberFormatter(new DecimalFormat("#,##0.0#############")); //$NON-NLS-1$
        editFormatter.setValueClass(Double.class);
        editFormatter.setMinimum(min);
        editFormatter.setMaximum(max);
        editFormatter.setAllowsInvalid(true);
        return new DefaultFormatterFactory(displayFormatter, displayFormatter, editFormatter);
    }
}
