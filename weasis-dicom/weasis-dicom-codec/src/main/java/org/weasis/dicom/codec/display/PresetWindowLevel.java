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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.Messages;

public class PresetWindowLevel {

    public final static PresetWindowLevel CUSTOM =
        new PresetWindowLevel(
            Messages.getString("PresetWindowLevel.custom"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    public final static PresetWindowLevel DEFAULT =
        new PresetWindowLevel(
            Messages.getString("PresetWindowLevel.default"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    public final static PresetWindowLevel AUTO =
        new PresetWindowLevel(
            Messages.getString("PresetWindowLevel.full"), Messages.getString("PresetWindowLevel.all"), 0f, 0f); //$NON-NLS-1$ //$NON-NLS-2$
    private final static ArrayList<PresetWindowLevel> presets = getPresetCollection();
    private final String name;
    private final String modality;
    private final float window;
    private final float level;

    public PresetWindowLevel(String name, String modality, float window, float level) {
        this.name = name;
        this.modality = modality;
        this.window = window;
        this.level = level;
    }

    @Override
    public String toString() {
        return name;
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

    public static PresetWindowLevel[] getPresetCollection(String modality) {
        ArrayList<PresetWindowLevel> mpresets = new ArrayList<PresetWindowLevel>();
        mpresets.add(CUSTOM);
        mpresets.add(DEFAULT);
        mpresets.add(AUTO);
        for (int i = 3; i < presets.size(); i++) {
            if (presets.get(i).modality.equals(modality)) {
                mpresets.add(presets.get(i));
            }
        }
        return mpresets.toArray(new PresetWindowLevel[mpresets.size()]);
    }

    private static ArrayList<PresetWindowLevel> getPresetCollection() {
        ArrayList<PresetWindowLevel> presets = new ArrayList<PresetWindowLevel>();
        presets.add(CUSTOM);
        presets.add(DEFAULT);
        presets.add(AUTO);
        readPresets(presets);
        return presets;
    }

    private static void readPresets(ArrayList<PresetWindowLevel> presets) {
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
                                        if ("preset".equals(key)) { //$NON-NLS-1$
                                            if (xmler.getAttributeCount() == 4) {
                                                presets.add(new PresetWindowLevel(
                                                    xmler.getAttributeValue(null, "name"), xmler.getAttributeValue( //$NON-NLS-1$
                                                        null, "modality"), Float.parseFloat(xmler.getAttributeValue( //$NON-NLS-1$
                                                        null, "window")), Float.parseFloat(xmler.getAttributeValue( //$NON-NLS-1$
                                                        null, "level")))); //$NON-NLS-1$
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
    }

}
