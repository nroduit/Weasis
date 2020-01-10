/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image;

import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.Messages;
import org.weasis.opencv.data.LookupTableCV;

/**
 * @author btja
 */

public final class LutShape {

    public static final LutShape LINEAR = new LutShape(eFunction.LINEAR);
    public static final LutShape SIGMOID = new LutShape(eFunction.SIGMOID);
    public static final LutShape SIGMOID_NORM = new LutShape(eFunction.SIGMOID_NORM);
    public static final LutShape LOG = new LutShape(eFunction.LOG);
    public static final LutShape LOG_INV = new LutShape(eFunction.LOG_INV);

    /**
     * LINEAR and SIGMOID descriptors are defined as DICOM standard LUT function <br>
     * Other LUT functions have their own custom implementation
     */
    public enum eFunction {
        LINEAR(Messages.getString("LutShape.linear")), // //$NON-NLS-1$
        SIGMOID(Messages.getString("LutShape.sigmoid")), // //$NON-NLS-1$
        SIGMOID_NORM(Messages.getString("LutShape.sig_norm")), // //$NON-NLS-1$
        LOG(Messages.getString("LutShape.log")), // //$NON-NLS-1$
        LOG_INV(Messages.getString("LutShape.log_inv")); //$NON-NLS-1$

        final String explanation;

        private eFunction(String explanation) {
            this.explanation = explanation;
        }

        @Override
        public String toString() {
            return explanation;
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final List<LutShape> DEFAULT_FACTORY_FUNCTIONS = new ArrayList<>();

    static {
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LINEAR);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.SIGMOID);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.SIGMOID_NORM);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LOG);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LOG_INV);
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A LutShape can be either a predefined function or a custom shape with a provided lookup table. <br>
     * That is a LutShape can be defined as a function or by a lookup but not both
     */
    protected final eFunction function;
    protected final String explanantion;
    protected final LookupTableCV lookup;

    public LutShape(LookupTableCV lookup, String explanantion) {
        if (lookup == null) {
            throw new IllegalArgumentException();
        }
        this.function = null;
        this.explanantion = explanantion;
        this.lookup = lookup;
    }

    public LutShape(eFunction function) {
        this(function, function.toString());
    }

    public LutShape(eFunction function, String explanantion) {
        if (function == null) {
            throw new IllegalArgumentException();
        }
        this.function = function;
        this.explanantion = explanantion;
        this.lookup = null;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public eFunction getFunctionType() {
        return function;
    }

    public LookupTableCV getLookup() {
        return lookup;
    }

    @Override
    public String toString() {
        return explanantion;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * LutShape objects are defined either by a factory function or by a custom LUT. They can be equal even if they have
     * different explanation property
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LutShape) {
            LutShape shape = (LutShape) obj;
            return (function != null) ? function.equals(shape.function) : lookup.equals(shape.lookup);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (function != null) ? function.hashCode() : lookup.hashCode();
    }

    public static final LutShape getLutShape(String shape) {
        if (shape != null) {
            String val = shape.toUpperCase();
            if ("LINEAR".equals(val)) { //$NON-NLS-1$
                return LutShape.LINEAR;
            } else if ("SIGMOID".equals(val)) { //$NON-NLS-1$
                return LutShape.SIGMOID;
            } else if ("SIGMOID_NORM".equals(val)) { //$NON-NLS-1$
                return LutShape.SIGMOID_NORM;
            } else if ("LOG".equals(val)) { //$NON-NLS-1$
                return LutShape.LOG;
            } else if ("LOG_INV".equals(val)) { //$NON-NLS-1$
                return LutShape.LOG_INV;
            }
        }
        return null;
    }
}
