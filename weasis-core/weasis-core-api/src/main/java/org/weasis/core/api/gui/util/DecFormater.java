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
package org.weasis.core.api.gui.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

import org.weasis.core.api.util.LocalUtil;

/**
 * The Class DecFormater.
 *
 */
public class DecFormater {
    
    private DecFormater() {
    }
    
    private static NumberFormat df1 = LocalUtil.getNumberInstance(); // 1 decimals
    private static NumberFormat df2 = LocalUtil.getNumberInstance(); // 2 decimals
    private static NumberFormat df4 = LocalUtil.getNumberInstance(); // 4 decimals
    private static NumberFormat percent2 = LocalUtil.getPercentInstance();
    // Scientific format with 4 decimals
    private static DecimalFormat dfSci = new DecimalFormat("0.####E0"); //$NON-NLS-1$

    static {
        df1.setMaximumFractionDigits(1);
        df2.setMaximumFractionDigits(2);
        df4.setMaximumFractionDigits(4);
        percent2.setMaximumFractionDigits(2);
        dfSci.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(LocalUtil.getLocaleFormat()));
    }

    public static String oneDecimal(Number val) {
        return df1.format(val);
    }

    public static String twoDecimal(Number val) {
        return df2.format(val);
    }

    public static String fourDecimal(Number val) {
        return df4.format(val);
    }

    public static String percentTwoDecimal(Number val) {
        return percent2.format(val);
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
