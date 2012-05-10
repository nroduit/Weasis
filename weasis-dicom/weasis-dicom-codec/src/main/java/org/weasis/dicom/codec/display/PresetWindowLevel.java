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

import java.awt.image.DataBuffer;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.media.jai.LookupTableJAI;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.LutShape.eFunction;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;

public class PresetWindowLevel {

    private static final Map<String, List<PresetWindowLevel>> presetListByModality = getPresetListByModality();

    private final String name;
    private final Float window;
    private final Float level;
    private final LutShape shape;

    public PresetWindowLevel(String name, Float window, Float level, LutShape shape) {

        if (name == null || window == null || level == null || shape == null) {
            throw new IllegalArgumentException();
        }

        this.name = name;
        // this.modality = modality;
        this.window = window;
        this.level = level;
        this.shape = shape;
    }

    public String getName() {
        return name;
    }

    public Float getWindow() {
        return window;
    }

    public Float getLevel() {
        return level;
    }

    public LutShape getLutShape() {
        return shape;
    }

    @Override
    public String toString() {
        return name;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // @Override
    // public boolean equals(Object obj) {
    // if (obj instanceof PresetWindowLevel) {
    // return name.equals(((PresetWindowLevel) obj).name);
    // }
    // return super.equals(obj);
    // }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static PresetWindowLevel[] getPresetCollection(DicomImageElement image) {
        if (image == null) {
            return null;
        }

        // String dicomKeyWord = " [" + Messages.getString("PresetWindowLevel.dicomKeyWord") + "]";
        String dicomKeyWord = " [" + "Dicom" + "]";

        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();

        Float[] windowCenterDefaultTagArray = (Float[]) image.getTagValue(TagW.WindowCenter);
        Float[] windowWidthDefaultTagArray = (Float[]) image.getTagValue(TagW.WindowWidth);
        // optional attributes
        String[] windowCenterWidthExplanationTagArray = (String[]) image.getTagValue(TagW.WindowCenterWidthExplanation);
        String lutFunctionDescriptor = (String) image.getTagValue(TagW.VOILutFunction);

        LutShape defaultLutShape = LutShape.LINEAR; // Implicitly defined as default function in DICOM standard

        if (lutFunctionDescriptor != null) {
            if ("SIGMOID".equalsIgnoreCase(lutFunctionDescriptor)) {
                defaultLutShape = new LutShape(eFunction.SIGMOID, eFunction.SIGMOID + dicomKeyWord);
            } else if ("LINEAR".equalsIgnoreCase(lutFunctionDescriptor)) {
                defaultLutShape = new LutShape(eFunction.LINEAR, eFunction.LINEAR + dicomKeyWord);
            }
        }

        if (windowCenterDefaultTagArray != null && windowWidthDefaultTagArray != null) {

            int windowLevelDefaultCount = (windowCenterDefaultTagArray.length == windowWidthDefaultTagArray.length) ? //
                windowCenterDefaultTagArray.length : 0;

            // String defaultExplanation = Messages.getString("PresetWindowLevel.default");
            // NOTE : PresetWindowLevel.default property should not be use anymore !!
            String defaultExplanation = "Default";

            for (int i = 0; i < windowLevelDefaultCount; i++) {
                String explanation = defaultExplanation + " " + i;

                if (windowCenterWidthExplanationTagArray != null && i < windowCenterWidthExplanationTagArray.length) {
                    if (windowCenterWidthExplanationTagArray[i] != null
                        && !windowCenterWidthExplanationTagArray[i].equals("")) {
                        explanation = windowCenterWidthExplanationTagArray[i]; // optional attribute
                    }
                }
                presetList.add(new PresetWindowLevel(explanation + dicomKeyWord, windowWidthDefaultTagArray[i],
                    windowCenterDefaultTagArray[i], defaultLutShape));
            }
        }

        LookupTableJAI[] voiLUTsData = (LookupTableJAI[]) image.getTagValue(TagW.VOILUTsData);
        String[] voiLUTsExplanation = (String[]) image.getTagValue(TagW.VOILUTsExplanation); // optional attribute

        if (voiLUTsData != null) {
            String defaultExplanation = "VOI LUT";

            for (int i = 0; i < voiLUTsData.length; i++) {
                String explanation = defaultExplanation + " " + i;

                if (voiLUTsExplanation != null && i < voiLUTsExplanation.length) {
                    if (voiLUTsExplanation[i] != null && !voiLUTsExplanation[i].equals("")) {
                        explanation = voiLUTsExplanation[i];
                    }
                }

                Object inLut;

                if (voiLUTsData[i].getDataType() == DataBuffer.TYPE_BYTE) {
                    inLut = voiLUTsData[i].getByteData(0);
                } else if (voiLUTsData[i].getDataType() <= DataBuffer.TYPE_SHORT) {
                    inLut = voiLUTsData[i].getShortData(0);
                } else {
                    continue;
                }

                int minValueLookup = voiLUTsData[i].getOffset();
                int maxValueLookup = voiLUTsData[i].getOffset() + Array.getLength(inLut) - 1;

                minValueLookup = Math.min(minValueLookup, maxValueLookup);
                maxValueLookup = Math.max(minValueLookup, maxValueLookup);
                int minAllocatedValue = image.getMinAllocatedValue();
                if (minValueLookup < minAllocatedValue) {
                    minValueLookup = minAllocatedValue;
                }
                int maxAllocatedValue = image.getMaxAllocatedValue();
                if (maxValueLookup > maxAllocatedValue) {
                    maxValueLookup = maxAllocatedValue;
                }

                float fullDynamicWidth = maxValueLookup - minValueLookup;
                float fullDynamicCenter = minValueLookup + fullDynamicWidth / 2f;

                LutShape newLutShape = new LutShape(voiLUTsData[i], explanation + dicomKeyWord);

                presetList.add(new PresetWindowLevel(newLutShape.toString(), fullDynamicWidth, fullDynamicCenter,
                    newLutShape));
            }
        }

        // String fullDynamicExplanation = Messages.getString("PresetWindowLevel.full");
        // NOTE : PresetWindowLevel.full property should not be use anymore !!
        String fullDynamicExplanation = "Auto Level";
        // String imageTag = " [" + Messages.getString("PresetWindowLevel.imageTag") + "]";
        String imageTag = " [" + "Image" + "]";

        presetList.add(new PresetWindowLevel(fullDynamicExplanation + imageTag, image.getFullDynamicWidth(), image
            .getFullDynamicCenter(), defaultLutShape));

        String modalityName = (String) image.getTagValue(TagW.Modality);
        if (presetListByModality.containsKey(modalityName)) {
            presetList.addAll(presetListByModality.get(modalityName));
        }

        return presetList.toArray(new PresetWindowLevel[presetList.size()]);
    }

    private static Map<String, List<PresetWindowLevel>> getPresetListByModality() {

        Map<String, List<PresetWindowLevel>> presetListByModality = new TreeMap<String, List<PresetWindowLevel>>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = PresetWindowLevel.class.getResourceAsStream("/config/presets.xml"); //$NON-NLS-1$
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("presets".equals(key)) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("preset".equals(key) && xmler.getAttributeCount() == 4) { //$NON-NLS-1$
                                            // TODO add LUTShape argument in xml
                                            String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                                            String modality = xmler.getAttributeValue(null, "modality");//$NON-NLS-1$
                                            float window = Float.parseFloat(xmler.getAttributeValue(null, "window"));//$NON-NLS-1$
                                            float level = Float.parseFloat(xmler.getAttributeValue(null, "level")); //$NON-NLS-1$;

                                            PresetWindowLevel preset =
                                                new PresetWindowLevel(name, window, level, LutShape.LINEAR);

                                            List<PresetWindowLevel> presetList = presetListByModality.get(modality);
                                            if (presetList == null) {
                                                presetListByModality.put(modality, presetList =
                                                    new ArrayList<PresetWindowLevel>());
                                            }
                                            presetList.add(preset);
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (XMLStreamException e) {
            e.printStackTrace();
            // logger.error("Cannot read presets file!");
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return presetListByModality;
    }
}
