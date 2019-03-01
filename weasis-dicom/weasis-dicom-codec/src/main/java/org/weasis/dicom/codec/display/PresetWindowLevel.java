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
package org.weasis.dicom.codec.display;

import java.awt.event.KeyEvent;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.LutShape.eFunction;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.data.LookupTableCV;

public class PresetWindowLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetWindowLevel.class);

    private static final Map<String, List<PresetWindowLevel>> presetListByModality = getPresetListByModality();

    private final String name;
    private final Double window;
    private final Double level;
    private final LutShape shape;
    private int keyCode = 0;

    public PresetWindowLevel(String name, Double window, Double level, LutShape shape) {
        this.name = Objects.requireNonNull(name);
        this.window = Objects.requireNonNull(window);
        this.level = Objects.requireNonNull(level);
        this.shape = Objects.requireNonNull(shape);
    }

    public String getName() {
        return name;
    }

    public Double getWindow() {
        return window;
    }

    public Double getLevel() {
        return level;
    }

    public LutShape getLutShape() {
        return shape;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public double getMinBox() {
        return level - window / 2.0;

    }

    public double getMaxBox() {
        return level + window / 2.0;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public LutShape getShape() {
        return shape;
    }

    public boolean isAutoLevel() {
        return keyCode == KeyEvent.VK_0;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + level.hashCode();
        result = prime * result + name.hashCode();
        result = prime * result + shape.hashCode();
        result = prime * result + window.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PresetWindowLevel other = (PresetWindowLevel) obj;
        return name.equals(other.name) && level.equals(other.level) && window.equals(other.window)
            && shape.equals(other.shape);
    }

    public static List<PresetWindowLevel> getPresetCollection(DicomImageElement image, TagReadable tagable,
        boolean pixelPadding, String type) {
        if (image == null || tagable == null) {
            return null;
        }

        String dicomKeyWord = " " + type; //$NON-NLS-1$

        ArrayList<PresetWindowLevel> presetList = new ArrayList<>();

        double[] levelList = TagD.getTagValue(tagable, Tag.WindowCenter, double[].class);
        double[] windowList = TagD.getTagValue(tagable, Tag.WindowWidth, double[].class);

        // optional attributes
        String[] wlExplanationList = TagD.getTagValue(tagable, Tag.WindowCenterWidthExplanation, String[].class);
        String lutFunctionDescriptor = TagD.getTagValue(tagable, Tag.VOILUTFunction, String.class);

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

        LookupTableCV[] voiLUTsData = (LookupTableCV[]) tagable.getTagValue(TagW.VOILUTsData);
        String[] voiLUTsExplanation = (String[]) tagable.getTagValue(TagW.VOILUTsExplanation); // optional attribute

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
                    buildPresetFromLutData(voiLUTsData[i], image, tagable, pixelPadding, explanation + dicomKeyWord);
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

        PresetWindowLevel autoLevel = new PresetWindowLevel(Messages.getString("PresetWindowLevel.full"), //$NON-NLS-1$
            image.getFullDynamicWidth(tagable, pixelPadding), image.getFullDynamicCenter(tagable, pixelPadding),
            defaultLutShape);
        // Set O shortcut for auto levels
        autoLevel.setKeyCode(KeyEvent.VK_0);
        presetList.add(autoLevel);

        // Exclude Secondary Capture CT and when PR preset
        if (image.getBitsStored() > 8 && !"[PR]".equals(type)) { //$NON-NLS-1$
            List<PresetWindowLevel> modPresets = presetListByModality.get(TagD.getTagValue(image, Tag.Modality));
            if (modPresets != null) {
                presetList.addAll(modPresets);
            }
        }

        return presetList;
    }

    public static PresetWindowLevel buildPresetFromLutData(LookupTableCV voiLUTsData, DicomImageElement image,
        TagReadable tagable, boolean pixelPadding, String explanation) {
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
        int minAllocatedValue = image.getMinAllocatedValue(tagable, pixelPadding);
        if (minValueLookup < minAllocatedValue) {
            minValueLookup = minAllocatedValue;
        }
        int maxAllocatedValue = image.getMaxAllocatedValue(tagable, pixelPadding);
        if (maxValueLookup > maxAllocatedValue) {
            maxValueLookup = maxAllocatedValue;
        }

        double fullDynamicWidth = (double) maxValueLookup - minValueLookup;
        double fullDynamicCenter = minValueLookup + fullDynamicWidth / 2f;

        LutShape newLutShape = new LutShape(voiLUTsData, explanation);
        return new PresetWindowLevel(newLutShape.toString(), fullDynamicWidth, fullDynamicCenter, newLutShape);
    }

    public static Map<String, List<PresetWindowLevel>> getPresetListByModality() {

        Map<String, List<PresetWindowLevel>> presets = new TreeMap<>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            File file = ResourceUtil.getResource("presets.xml"); //$NON-NLS-1$
            if (!file.canRead()) {
                return Collections.emptyMap();
            }
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // disable external entities for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            stream = new FileInputStream(file); // $NON-NLS-1$
            xmler = factory.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("presets".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                readPresetListByModality(xmler, presets);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (Exception e) {
            LOGGER.error("Cannot read presets file! " + e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return presets;
    }

    private static void readPresetListByModality(XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
        throws XMLStreamException {
        int eventType = xmler.next();
        String key;
        if (eventType == XMLStreamConstants.START_ELEMENT) {
            key = xmler.getName().getLocalPart();
            if ("preset".equals(key) && xmler.getAttributeCount() >= 4) { //$NON-NLS-1$
                String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                try {
                    String modality = xmler.getAttributeValue(null, "modality");//$NON-NLS-1$
                    double window = Double.parseDouble(xmler.getAttributeValue(null, "window")); //$NON-NLS-1$
                    double level = Double.parseDouble(xmler.getAttributeValue(null, "level")); //$NON-NLS-1$
                    String shape = xmler.getAttributeValue(null, "shape");//$NON-NLS-1$
                    Integer keyCode = TagUtil.getIntegerTagAttribute(xmler, "key", null);//$NON-NLS-1$
                    LutShape lutShape = LutShape.getLutShape(shape);
                    PresetWindowLevel preset =
                        new PresetWindowLevel(name, window, level, lutShape == null ? LutShape.LINEAR : lutShape);
                    if (keyCode != null) {
                        preset.setKeyCode(keyCode);
                    }
                    List<PresetWindowLevel> presetList = presets.get(modality);
                    if (presetList == null) {
                        presetList = new ArrayList<>();
                        presets.put(modality, presetList);
                    }
                    presetList.add(preset);
                } catch (Exception e) {
                    LOGGER.error("Preset {} cannot be read from xml file", name, e); //$NON-NLS-1$
                }
            }
        }
    }
}