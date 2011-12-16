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
package org.weasis.dicom.codec.display;

import java.awt.image.LookupTable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

/**
 * @author btja
 * @version $Rev$ $Date$
 */

public class LutShape {

    public static LutShape LINEAR = new LutShape(eType.LINEAR, "Linear", null);
    public static LutShape SIGMOID = new LutShape(eType.SIGMOID, "Sigmoid", null);
    public static LutShape LOG = new LutShape(eType.LOG, "Logarithmic", null);
    public static LutShape LOG_INV = new LutShape(eType.LOG_INV, "Logarithmic Inv", null);

    /**
     * SEQUENCE stands for a provided lookup table data LINEAR and SIGMOID descriptors are DICOM standard LUT function
     * Other LUT function have their own custom application implementation
     */
    public enum eType {
        SEQUENCE, LINEAR, SIGMOID, LOG, LOG_INV
    }

    static final protected ArrayList<LutShape> DEFAULT_LUT_SHAPE_LIST;

    static {
        DEFAULT_LUT_SHAPE_LIST = new ArrayList<LutShape>();
        DEFAULT_LUT_SHAPE_LIST.add(LutShape.LINEAR);
        DEFAULT_LUT_SHAPE_LIST.add(LutShape.SIGMOID);
        DEFAULT_LUT_SHAPE_LIST.add(LutShape.LOG);
        DEFAULT_LUT_SHAPE_LIST.add(LutShape.LOG_INV);
    }

    volatile protected eType functionType;
    volatile protected String explanation;
    volatile protected LookupTable lookup;

    public LutShape(String explanantion, LookupTable lookup) {
        this(eType.SEQUENCE, explanantion, lookup);
    }

    protected LutShape(eType type, String explanantion, LookupTable lookup) {
        this.functionType = type;
        this.explanation = explanantion;
        this.lookup = lookup;
    }

    public String getExplanation() {
        return explanation;
    }

    public eType getFunctionType() {
        return functionType;
    }

    public LookupTable getLookup() {
        return lookup;
    }

    public static LutShape getShape(String lutFunctionDescriptor) {
        if ("SIGMOID".equalsIgnoreCase(lutFunctionDescriptor)) {
            return LutShape.SIGMOID;
        } else if ("LINEAR".equalsIgnoreCase(lutFunctionDescriptor)) {
            return LutShape.LINEAR;
        } else {
            return null;
        }
    }

    public static LutShape[] getFullShapeArray(Collection<LutShape> lutShape) {
        Set<LutShape> lutShapeSet = new LinkedHashSet<LutShape>(lutShape);
        for (LutShape shape : DEFAULT_LUT_SHAPE_LIST) {
            if (!lutShapeSet.contains(shape)) {
                lutShapeSet.add(shape);
            }
        }
        return lutShapeSet.toArray(new LutShape[lutShapeSet.size()]);
    }

    public static LutShape[] getShapeCollection(ImageElement image) {
        ArrayList<LutShape> lutShapeList = new ArrayList<LutShape>();

        if (image != null) {
            LookupTable[] voiLUTsData = (LookupTable[]) image.getTagValue(TagW.VOILUTsData);
            String[] voiLUTsExplanation = (String[]) image.getTagValue(TagW.VOILUTsExplanation);

            if (voiLUTsData != null) {
                String defaultExplanation = "VOI LUT";
                String dicomTag = " [" + "DICOM" + "]";

                for (int i = 0; i < voiLUTsData.length; i++) {
                    String explanation = (voiLUTsExplanation != null && i < voiLUTsExplanation.length) ? //
                        voiLUTsExplanation[i] : defaultExplanation;
                    lutShapeList.add(new LutShape(eType.SEQUENCE, explanation + dicomTag, voiLUTsData[i]));
                }
            }
        }
        lutShapeList.addAll(DEFAULT_LUT_SHAPE_LIST);

        return lutShapeList.toArray(new LutShape[lutShapeList.size()]);
    }

    @Override
    public String toString() {
        return explanation;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LutShape) {
            LutShape shape = (LutShape) obj;
            if (!functionType.equals(shape.functionType)) {
                return false;
            }
            if (functionType.equals(eType.SEQUENCE)) {
                if ((lookup != null && !lookup.equals(shape.lookup))
                    || (shape.lookup != null && !shape.lookup.equals(lookup))) {
                    return false;
                }

                // TODO lookup should never be null in sequence type, do it better !!!
            }
            return true;
        }
        return super.equals(obj);
    }
}
