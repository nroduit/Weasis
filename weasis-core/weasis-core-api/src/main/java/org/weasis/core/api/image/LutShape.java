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
package org.weasis.core.api.image;

import java.util.ArrayList;

import javax.media.jai.LookupTableJAI;

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
        LINEAR("Linear"), //
        SIGMOID("Sigmoid"), //
        SIGMOID_NORM("Sigmoid Normalize"), //
        LOG("Logarithmic"), //
        LOG_INV("Logarithmic Inv");

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

    public static final ArrayList<LutShape> DEFAULT_FACTORY_FUNCTIONS;

    static {
        DEFAULT_FACTORY_FUNCTIONS = new ArrayList<LutShape>();

        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LINEAR);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.SIGMOID);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.SIGMOID_NORM);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LOG);
        DEFAULT_FACTORY_FUNCTIONS.add(LutShape.LOG_INV);
    }

    public static final LutShape getLutShape(String shape) {
        if (shape != null) {
            String val = shape.toUpperCase();
            if ("LINEAR".equals(val)) {
                return LutShape.LINEAR;
            } else if ("SIGMOID".equals(val)) {
                return LutShape.SIGMOID;
            } else if ("SIGMOID_NORM".equals(val)) {
                return LutShape.SIGMOID_NORM;
            } else if ("LOG".equals(val)) {
                return LutShape.LOG;
            } else if ("LOG_INV".equals(val)) {
                return LutShape.LOG_INV;
            }
        }
        return null;

    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A LutShape can be either a predefined function or a custom shape with a provided lookup table. <br>
     * That is a LutShape can be defined as a function or by a lookup but not both
     */
    protected final eFunction function;
    protected final String explanantion;
    protected final LookupTableJAI lookup;

    public LutShape(LookupTableJAI lookup, String explanantion) {
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

    public LookupTableJAI getLookup() {
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
}
