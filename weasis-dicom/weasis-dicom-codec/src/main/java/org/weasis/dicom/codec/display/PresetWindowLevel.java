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
import java.io.FileInputStream;
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
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.LutShape.eFunction;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Messages;

public class PresetWindowLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetWindowLevel.class);

    public static final String fullDynamicExplanation = Messages.getString("PresetWindowLevel.full"); //$NON-NLS-1$
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

    public float getMinBox() {
        return level - window / 2.0f;

    }

    public float getMaxBox() {
        return level + window / 2.0f;
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
            return window.equals(p.window) && level.equals(p.level) && name.equals(p.name) && shape.equals(p.shape);
        }
        return false;
    }

    public static List<PresetWindowLevel> getPresetCollection(DicomImageElement image, HashMap<TagW, Object> params,
        boolean pixelPadding) {
        if (image == null || params == null) {
            return null;
        }

        String dicomKeyWord = " " + Messages.getString("PresetWindowLevel.dcm_preset"); //$NON-NLS-1$ //$NON-NLS-2$

        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();

        Float[] levelList = (Float[]) params.get(TagW.WindowCenter);
        Float[] windowList = (Float[]) params.get(TagW.WindowWidth);
        // optional attributes
        String[] wlExplanationList = (String[]) params.get(TagW.WindowCenterWidthExplanation);
        String lutFunctionDescriptor = (String) params.get(TagW.VOILutFunction);

        LutShape defaultLutShape = LutShape.LINEAR; // Implicitly defined as default function in DICOM standard

        if (lutFunctionDescriptor != null) {
            if ("SIGMOID".equalsIgnoreCase(lutFunctionDescriptor)) { //$NON-NLS-1$
                defaultLutShape = new LutShape(eFunction.SIGMOID, eFunction.SIGMOID + dicomKeyWord);
            } else if ("LINEAR".equalsIgnoreCase(lutFunctionDescriptor)) { //$NON-NLS-1$
                defaultLutShape = new LutShape(eFunction.LINEAR, eFunction.LINEAR + dicomKeyWord);
            }
        }

        if (levelList != null && windowList != null) {

            int windowLevelDefaultCount = (levelList.length == windowList.length) ? levelList.length : 0;
            String defaultExplanation = Messages.getString("PresetWindowLevel.default"); //$NON-NLS-1$

            int k = 1;

            for (int i = 0; i < windowLevelDefaultCount; i++) {
                String explanation = defaultExplanation + " " + k; //$NON-NLS-1$
                if (wlExplanationList != null && i < wlExplanationList.length) {
                    if (StringUtil.hasText(wlExplanationList[i])) {
                        explanation = wlExplanationList[i]; // optional attribute
                    }
                }
                if (windowList[i] == null || levelList[i] == null) {
                    // Level value is not consistent, do not add to the list
                    LOGGER.error("DICOM preset '{}' is not valid. It is not added to the preset list", explanation); //$NON-NLS-1$
                    continue;
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

        LookupTableJAI[] voiLUTsData = (LookupTableJAI[]) params.get(TagW.VOILUTsData);
        String[] voiLUTsExplanation = (String[]) params.get(TagW.VOILUTsExplanation); // optional attribute

        if (voiLUTsData != null) {
            String defaultExplanation = Messages.getString("PresetWindowLevel.voi_lut"); //$NON-NLS-1$

            for (int i = 0; i < voiLUTsData.length; i++) {
                String explanation = defaultExplanation + " " + i; //$NON-NLS-1$

                if (voiLUTsExplanation != null && i < voiLUTsExplanation.length) {
                    if (StringUtil.hasText(voiLUTsExplanation[i])) {
                        explanation = voiLUTsExplanation[i];
                    }
                }

                PresetWindowLevel preset =
                    buildPresetFromLutData(voiLUTsData[i], image, params, pixelPadding, explanation + dicomKeyWord);
                if (preset == null) {
                    continue;
                }
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
            new PresetWindowLevel(fullDynamicExplanation, image.getFullDynamicWidth(params, pixelPadding),
                image.getFullDynamicCenter(params, pixelPadding), defaultLutShape);
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

    public static PresetWindowLevel buildPresetFromLutData(LookupTableJAI voiLUTsData, DicomImageElement image,
        HashMap<TagW, Object> params, boolean pixelPadding, String explanation) {
        if (voiLUTsData == null || explanation == null) {
            return null;
        }

        Object inLut;

        if (voiLUTsData.getDataType() == DataBuffer.TYPE_BYTE) {
            inLut = voiLUTsData.getByteData(0);
        } else if (voiLUTsData.getDataType() <= DataBuffer.TYPE_SHORT) {
            inLut = voiLUTsData.getShortData(0);
        } else {
            return null;
        }

        int minValueLookup = voiLUTsData.getOffset();
        int maxValueLookup = voiLUTsData.getOffset() + Array.getLength(inLut) - 1;

        minValueLookup = Math.min(minValueLookup, maxValueLookup);
        maxValueLookup = Math.max(minValueLookup, maxValueLookup);
        int minAllocatedValue = image.getMinAllocatedValue(params, pixelPadding);
        if (minValueLookup < minAllocatedValue) {
            minValueLookup = minAllocatedValue;
        }
        int maxAllocatedValue = image.getMaxAllocatedValue(params, pixelPadding);
        if (maxValueLookup > maxAllocatedValue) {
            maxValueLookup = maxAllocatedValue;
        }

        float fullDynamicWidth = maxValueLookup - minValueLookup;
        float fullDynamicCenter = minValueLookup + fullDynamicWidth / 2f;

        LutShape newLutShape = new LutShape(voiLUTsData, explanation);
        return new PresetWindowLevel(newLutShape.toString(), fullDynamicWidth, fullDynamicCenter, newLutShape);
    }

    private static Map<String, List<PresetWindowLevel>> getPresetListByModality() {

        Map<String, List<PresetWindowLevel>> presets = new TreeMap<String, List<PresetWindowLevel>>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = new FileInputStream(ResourceUtil.getResource("presets.xml")); //$NON-NLS-1$
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
                                                float level = Float.parseFloat(xmler.getAttributeValue(null, "level")); //$NON-NLS-1$ ;
                                                String shape = xmler.getAttributeValue(null, "shape");//$NON-NLS-1$
                                                Integer keyCode = FileUtil.getIntegerTagAttribute(xmler, "key", null);//$NON-NLS-1$
                                                LutShape lutShape = LutShape.getLutShape(shape);
                                                PresetWindowLevel preset = new PresetWindowLevel(name, window, level,
                                                    lutShape == null ? LutShape.LINEAR : lutShape);
                                                if (keyCode != null) {
                                                    preset.setKeyCode(keyCode);
                                                }
                                                List<PresetWindowLevel> presetList = presets.get(modality);
                                                if (presetList == null) {
                                                    presets.put(modality,
                                                        presetList = new ArrayList<PresetWindowLevel>());
                                                }
                                                presetList.add(preset);
                                            } catch (Exception e) {
                                                LOGGER.error("Preset {} cannot be read from xml file", name); //$NON-NLS-1$
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

        catch (Exception e) {
            LOGGER.error("Cannot read presets file! " + e.getMessage()); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return presets;
    }
}