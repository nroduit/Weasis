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

import java.awt.event.KeyEvent;
import java.awt.image.DataBuffer;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.media.jai.LookupTableJAI;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.LutShape.eFunction;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Messages;

public class PresetWindowLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetWindowLevel.class);

    public static final String fullDynamicExplanation = Messages.getString("PresetWindowLevel.full");
    private static final Map<String, List<PresetWindowLevel>> presetListByModality = getPresetListByModality();

    private final String name;
    private final Float window;
    private final Float level;
    private final LutShape shape;
    private int keyCode = 0;

    public PresetWindowLevel(String name, Float window, Float level, LutShape shape) {
        if (name == null || window == null || level == null || shape == null) {
            throw new IllegalArgumentException();
        }
        this.name = name;
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

    public int getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public LutShape getShape() {
        return shape;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PresetWindowLevel) {
            PresetWindowLevel p = (PresetWindowLevel) obj;
            return window == p.window && level == p.level && name.equals(p.name) && shape.equals(p.shape);
        }
        return false;
    }

    public static List<PresetWindowLevel> getPresetCollection(DicomImageElement image, HashMap<TagW, Object> tags,
        boolean pixelPadding) {
        if (image == null || tags == null) {
            return null;
        }

        String dicomKeyWord = " [" + "Dicom" + "]";

        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();

        Float[] levelList = (Float[]) tags.get(TagW.WindowCenter);
        Float[] windowList = (Float[]) tags.get(TagW.WindowWidth);
        // optional attributes
        String[] wlExplanationList = (String[]) tags.get(TagW.WindowCenterWidthExplanation);
        String lutFunctionDescriptor = (String) tags.get(TagW.VOILutFunction);

        LutShape defaultLutShape = LutShape.LINEAR; // Implicitly defined as default function in DICOM standard

        if (lutFunctionDescriptor != null) {
            if ("SIGMOID".equalsIgnoreCase(lutFunctionDescriptor)) {
                defaultLutShape = new LutShape(eFunction.SIGMOID, eFunction.SIGMOID + dicomKeyWord);
            } else if ("LINEAR".equalsIgnoreCase(lutFunctionDescriptor)) {
                defaultLutShape = new LutShape(eFunction.LINEAR, eFunction.LINEAR + dicomKeyWord);
            }
        }

        if (levelList != null && windowList != null) {

            int windowLevelDefaultCount = (levelList.length == windowList.length) ? levelList.length : 0;
            String defaultExplanation = "Default";

            float minModLUT = image.getMinValue(pixelPadding);
            float maxModLUT = image.getMaxValue(pixelPadding);
            int k = 1;

            for (int i = 0; i < windowLevelDefaultCount; i++) {
                String explanation = defaultExplanation + " " + k;
                if (wlExplanationList != null && i < wlExplanationList.length) {
                    if (wlExplanationList[i] != null && !wlExplanationList[i].equals("")) {
                        explanation = wlExplanationList[i]; // optional attribute
                    }
                }
                if (windowList[i] == null || levelList[i] == null) {
                    // Level value is not consistent, do not add to the list
                    LOGGER.error("DICOM preset '{}' is not valid. It is not added to the preset list", explanation);
                    continue;
                }

                // When window is larger than the dynamic, adapt it according the dynamic.
                if (windowList[i] > maxModLUT - minModLUT) {
                    float range = windowList[i];
                    windowList[i] = maxModLUT - minModLUT;
                    levelList[i] = (windowList[i] / range) * levelList[i];
                }

                // Level values seems not valid, set min or max pixel value
                if (levelList[i] < minModLUT) {
                    levelList[i] = minModLUT;
                }
                if (levelList[i] > maxModLUT) {
                    levelList[i] = maxModLUT;
                }

                PresetWindowLevel preset =
                    new PresetWindowLevel(explanation + dicomKeyWord, windowList[i], levelList[i], defaultLutShape);
                // Only set shortcuts for the two first presets
                if (k == 1) {
                    preset.setKeyCode(KeyEvent.VK_1);
                } else if (k == 2) {
                    preset.setKeyCode(KeyEvent.VK_2);
                }
                if (!presetList.contains(preset)) {
                    presetList.add(preset);
                    k++;
                }
            }
        }

        LookupTableJAI[] voiLUTsData = (LookupTableJAI[]) tags.get(TagW.VOILUTsData);
        String[] voiLUTsExplanation = (String[]) tags.get(TagW.VOILUTsExplanation); // optional attribute

        if (voiLUTsData != null) {
            String defaultExplanation = "VOI LUT";

            for (int i = 0; i < voiLUTsData.length; i++) {
                if (voiLUTsData[i] == null) {
                    continue;
                }
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
                int minAllocatedValue = image.getMinAllocatedValue(pixelPadding);
                if (minValueLookup < minAllocatedValue) {
                    minValueLookup = minAllocatedValue;
                }
                int maxAllocatedValue = image.getMaxAllocatedValue(pixelPadding);
                if (maxValueLookup > maxAllocatedValue) {
                    maxValueLookup = maxAllocatedValue;
                }

                float fullDynamicWidth = maxValueLookup - minValueLookup;
                float fullDynamicCenter = minValueLookup + fullDynamicWidth / 2f;

                LutShape newLutShape = new LutShape(voiLUTsData[i], explanation + dicomKeyWord);
                PresetWindowLevel preset =
                    new PresetWindowLevel(newLutShape.toString(), fullDynamicWidth, fullDynamicCenter, newLutShape);
                // Only set shortcuts for the two first presets
                int k = presetList.size();
                if (k == 0) {
                    preset.setKeyCode(KeyEvent.VK_1);
                } else if (k == 1) {
                    preset.setKeyCode(KeyEvent.VK_2);
                }
                presetList.add(preset);
            }
        }

        PresetWindowLevel autoLevel =
            new PresetWindowLevel(fullDynamicExplanation, image.getFullDynamicWidth(pixelPadding),
                image.getFullDynamicCenter(pixelPadding), defaultLutShape);
        // Set O shortcut for auto levels
        autoLevel.setKeyCode(KeyEvent.VK_0);
        presetList.add(autoLevel);

        // Exclude Secondary Capture CT
        if (image.getBitsStored() > 8) {
            List<PresetWindowLevel> modPresets = presetListByModality.get(image.getTagValue(TagW.Modality));
            if (modPresets != null) {
                presetList.addAll(modPresets);
            }
        }

        return presetList;
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
                                        if ("preset".equals(key) && xmler.getAttributeCount() >= 4) { //$NON-NLS-1$
                                            String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                                            try {
                                                String modality = xmler.getAttributeValue(null, "modality");//$NON-NLS-1$
                                                float window =
                                                    Float.parseFloat(xmler.getAttributeValue(null, "window"));//$NON-NLS-1$
                                                float level = Float.parseFloat(xmler.getAttributeValue(null, "level")); //$NON-NLS-1$;
                                                String shape = xmler.getAttributeValue(null, "shape");//$NON-NLS-1$
                                                String keyCode = xmler.getAttributeValue(null, "key");//$NON-NLS-1$
                                                LutShape lutShape = LutShape.getLutShape(shape);
                                                PresetWindowLevel preset =
                                                    new PresetWindowLevel(name, window, level, lutShape == null
                                                        ? LutShape.LINEAR : lutShape);
                                                if (keyCode != null) {
                                                    preset.setKeyCode(Integer.parseInt(keyCode));
                                                }
                                                List<PresetWindowLevel> presetList = presetListByModality.get(modality);
                                                if (presetList == null) {
                                                    presetListByModality.put(modality, presetList =
                                                        new ArrayList<PresetWindowLevel>());
                                                }
                                                presetList.add(preset);
                                            } catch (Exception e) {
                                                LOGGER.error("Preset {} cannot be read from xml file", name);
                                            }
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