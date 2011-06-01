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

import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * The Class DecFormater.
 * 
 * @author Nicolas Roduit
 */
public class DecFormater {

    private static DecimalFormat df1 = new DecimalFormat("#,##0.##"); // 2 decimals //$NON-NLS-1$
    private static DecimalFormat df2 = new DecimalFormat("#,##0.####"); // 4 decimals //$NON-NLS-1$
    private static DecimalFormat df3 = new DecimalFormat("#,###"); // non decimal //$NON-NLS-1$
    private static DecimalFormat df4 = new DecimalFormat("0.####E0"); // Sientific format with 4 decimals //$NON-NLS-1$

    private static DecimalFormat df5 = new DecimalFormat("###0.#"); //$NON-NLS-1$
    private static DecimalFormat df6 = new DecimalFormat("#,##0.########"); //$NON-NLS-1$
    private static DecimalFormat df7 = new DecimalFormat("###0.#"); //$NON-NLS-1$

    public static String twoDecimal(double val) {
        return df1.format(val);
    }

    public static String oneDecimalUngroup(double val) {
        return df5.format(val);
    }

    public static String twoDecimalUngroup(double val) {
        return df7.format(val);
    }

    public static String zeroDecimal(double val) {
        return df3.format(val);
    }

    public static String fourDecimal(double val) {
        return df2.format(val);
    }

    public static String heightDecimal(double val) {
        return df6.format(val);
    }

    public static String scientificFormat(double val) {
        return df4.format(val);
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
