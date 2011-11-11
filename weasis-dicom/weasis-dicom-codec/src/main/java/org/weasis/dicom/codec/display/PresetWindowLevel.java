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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.Messages;

public class PresetWindowLevel {

    // public static final PresetWindowLevel CUSTOM = new PresetWindowLevel(
    //        Messages.getString("PresetWindowLevel.custom"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    // public static final PresetWindowLevel DEFAULT = new PresetWindowLevel(
    //        Messages.getString("PresetWindowLevel.default"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    // public static final PresetWindowLevel AUTO = new PresetWindowLevel(
    //        Messages.getString("PresetWindowLevel.full"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    public static final PresetWindowLevel AUTO = new PresetWindowLevel(
        Messages.getString("PresetWindowLevel.full"), null, 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$

    // private static final ArrayList<PresetWindowLevel> presetList = getPresetCollection();
    private static final Map<String, List<PresetWindowLevel>> presetListByModality = getPresetListByModality();

    private final String name;
    private final String modality;
    private final float window;
    private final float level;
    private final LutShape shape;

    public PresetWindowLevel(String name, String modality, float window, float level) {
        this(name, modality, window, level, LutShape.LINEAR_SHAPE);
    }

    public PresetWindowLevel(String name, String modality, float window, float level, LutShape shape) {
        this.name = name;
        this.modality = modality;
        this.window = window;
        this.level = level;
        this.shape = shape;
    }

    public String getName() {
        return name;
    }

    public String getModality() {
        return modality;
    }

    public float getWindow() {
        return window;
    }

    public float getLevel() {
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

    public static PresetWindowLevel[] getPresetCollection(ImageElement image) {

        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();

        String modality = (String) image.getTagValue(TagW.Modality);

        float[] windowCenterDefaultTagArray = (float[]) image.getTagValue(TagW.WindowCenter);
        float[] windowWidthDefaultTagArray = (float[]) image.getTagValue(TagW.WindowWidth);
        String[] windowCenterWidthExplanationTagArray = (String[]) image.getTagValue(TagW.WindowCenterWidthExplanation);
        String lutFunction = (String) image.getTagValue(TagW.VOILutFunction);

        int windowLevelDefaultCount = windowCenterDefaultTagArray != null ? windowCenterDefaultTagArray.length : 0;

        if (windowWidthDefaultTagArray != null) {
            if (windowWidthDefaultTagArray.length == windowLevelDefaultCount) {
                for (int i = 0; i < windowLevelDefaultCount; i++) {
                    String name = (i < windowCenterWidthExplanationTagArray.length) ? //
                        windowCenterWidthExplanationTagArray[i] : "Defaut " + i;

                    presetList.add(new PresetWindowLevel(name, modality, windowCenterDefaultTagArray[i],
                        windowWidthDefaultTagArray[i]));
                }
            } else {
                System.err.println("VOI LUT MACRO Attribute Error");
            }
        }

        Object voiLutSequence = image.getTagValue(TagW.VOILUTSequence);
        // if (voiLutSequence != null) {
        // String lutDescriptor = (String) image.getTagValue(TagW.LutDescriptor);
        // String lutExplanation = (String) image.getTagValue(TagW.LutExplanation);
        // Float[] lutData = (Float[]) image.getTagValue(TagW.LutData);
        // }

        // presetList.add(CUSTOM);
        presetList.add(AUTO);

        if (presetListByModality.containsKey(modality)) {
            presetList.addAll(presetListByModality.get(modality));
        }

        return presetList.toArray(new PresetWindowLevel[presetList.size()]);
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // @Deprecated
    // public static PresetWindowLevel[] getPresetCollection(String modality) {
    //
    // ArrayList<PresetWindowLevel> modalityPresetList = new ArrayList<PresetWindowLevel>();
    // modalityPresetList.add(CUSTOM);
    // modalityPresetList.add(DEFAULT);
    // modalityPresetList.add(AUTO);
    // for (int i = 3; i < presetList.size(); i++) {
    // if (presetList.get(i).modality.equals(modality)) {
    // modalityPresetList.add(presetList.get(i));
    // }
    // }
    // return modalityPresetList.toArray(new PresetWindowLevel[modalityPresetList.size()]);
    // }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static ArrayList<PresetWindowLevel> getDefaultPresetList() {
        ArrayList<PresetWindowLevel> presetList = new ArrayList<PresetWindowLevel>();
        // presetList.add(CUSTOM);
        // presetList.add(DEFAULT);
        presetList.add(AUTO);
        // readPresetConfig(presetList);
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
                                        if ("preset".equals(key) && xmler.getAttributeCount() == 4) { //$NON-NLS-1$

                                            String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                                            String modality = xmler.getAttributeValue(null, "modality");//$NON-NLS-1$
                                            float window = Float.parseFloat(xmler.getAttributeValue(null, "window"));//$NON-NLS-1$
                                            float level = Float.parseFloat(xmler.getAttributeValue(null, "level")); //$NON-NLS-1$;

                                            PresetWindowLevel preset =
                                                new PresetWindowLevel(name, modality, window, level);

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
