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
package org.weasis.dicom.codec.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.CompositeCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition.Type;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Or;

public class SplittingRules {
    private static final Logger LOGGER = LoggerFactory.getLogger(SplittingRules.class);

    private final Map<Modality, SplittingModalityRules> rules;

    public SplittingRules() {
        rules = new EnumMap<>(Modality.class);
        initDefault();
        readSplittingRulesFromResources();
    }

    private void initDefault() {
        SplittingModalityRules defRules = new SplittingModalityRules(Modality.DEFAULT);

        defRules.addSingleFrameTags(Tag.ImageType, null);
        defRules.addSingleFrameTags(Tag.SOPClassUID, null);
        defRules.addSingleFrameTags(Tag.ContrastBolusAgent, null);
        defRules.addMultiFrameTags(Tag.ImageType, null);
        defRules.addMultiFrameTags(Tag.SOPInstanceUID, null);
        defRules.addMultiFrameTags(Tag.FrameType, null);
        defRules.addMultiFrameTags(Tag.FrameAcquisitionNumber, null);
        defRules.addMultiFrameTags(Tag.StackID, null);
        rules.put(defRules.getModality(), defRules);

        SplittingModalityRules ctRules = new SplittingModalityRules(Modality.CT, defRules);
        ctRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
        ctRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);
        // Make a condition to exclude projection image type
        And allOf = new And();
        allOf.addChild(
            new DefaultCondition(TagD.get(Tag.ImageType), Condition.Type.notContainsIgnoreCase, "PROJECTION")); //$NON-NLS-1$
        ctRules.addSingleFrameTags(TagW.ImageOrientationPlane, allOf);
        rules.put(ctRules.getModality(), ctRules);

        SplittingModalityRules ptRules = new SplittingModalityRules(Modality.PT, defRules);
        ptRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
        ptRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);
        rules.put(ptRules.getModality(), ptRules);

        SplittingModalityRules mrRules = new SplittingModalityRules(Modality.MR, defRules);
        mrRules.addSingleFrameTags(Tag.ScanningSequence, null);
        mrRules.addSingleFrameTags(Tag.SequenceVariant, null);
        mrRules.addSingleFrameTags(Tag.ScanOptions, null);
        mrRules.addSingleFrameTags(Tag.RepetitionTime, null);
        mrRules.addSingleFrameTags(Tag.EchoTime, null);
        mrRules.addSingleFrameTags(Tag.InversionTime, null);
        mrRules.addSingleFrameTags(Tag.FlipAngle, null);
        // Reuse the condition for ImageOrientationPlane
        mrRules.addSingleFrameTags(TagW.ImageOrientationPlane, allOf);
        rules.put(mrRules.getModality(), mrRules);
    }

    private void readSplittingRulesFromResources() {
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            File file = ResourceUtil.getResource("series-splitting-rules.xml"); //$NON-NLS-1$
            if (!file.canRead()) {
                return;
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
                        String key = xmler.getName().getLocalPart();
                        if ("modalities".equals(key)) { //$NON-NLS-1$
                            readModalities(xmler);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (Exception e) {
            LOGGER.error("Cannot read series-splitting-rules.xml! ", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
    }

    private void readModalities(XMLStreamReader xmler) throws XMLStreamException {
        while (xmler.hasNext()) {
            int eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    String key = xmler.getName().getLocalPart();
                    if ("modality".equals(key) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                        String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                        Modality m = getModdality(name);
                        if (m != null) {
                            try {
                                String extend = xmler.getAttributeValue(null, "extend");//$NON-NLS-1$
                                SplittingModalityRules splitRules =
                                    new SplittingModalityRules(m, getSplittingModalityRules(extend));
                                readModality(splitRules, xmler);
                                rules.put(m, splitRules);
                            } catch (Exception e) {
                                LOGGER.error("Modality {} cannot be read from series-splitting-rules.xml", //$NON-NLS-1$
                                    name, e);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void readModality(SplittingModalityRules data, XMLStreamReader xmler) throws XMLStreamException {
        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    String element = xmler.getName().getLocalPart();
                    if ("splittingTags".equals(element)) { //$NON-NLS-1$
                        readTags(data, xmler, false, element);
                    } else if ("multiframeSplittingTags".equals(element)) { //$NON-NLS-1$
                        readTags(data, xmler, true, element);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if ("modality".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void readTags(SplittingModalityRules data, XMLStreamReader xmler, boolean multiframe,
        String endElement) throws XMLStreamException {
        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    TagW tag = getTag(xmler.getName().getLocalPart());
                    if (tag != null) {
                        Condition condition = readCondition(data, xmler, tag.getKeyword());
                        if (multiframe) {
                            data.addMultiFrameTags(tag, condition);
                        } else {
                            data.addSingleFrameTags(tag, condition);
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (endElement.equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static Condition readCondition(SplittingModalityRules data, XMLStreamReader xmler, String endElement)
        throws XMLStreamException {

        CompositeCondition conditions = null;
        CompositeCondition lastConditions = null;
        TagW tag = null;
        Condition.Type type = null;
        String value = null;

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.CHARACTERS:
                    value = xmler.getText();
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    if ("condition".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        tag = getTag(xmler.getAttributeValue(null, "tag"));//$NON-NLS-1$
                        type = getConditionType(xmler.getAttributeValue(null, "type"));//$NON-NLS-1$
                    } else if ("conditions".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        String t = xmler.getAttributeValue(null, "type");//$NON-NLS-1$
                        lastConditions = "anyOf".equals(t) ? new Or() : new And(); //$NON-NLS-1$
                        if (conditions == null) {
                            // Root condition
                            conditions = lastConditions;
                        } else {
                            conditions.addChild(lastConditions);
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if ("condition".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        if (tag == null || type == null || value == null) {
                            LOGGER.error("Cannot read condition: {} {} {}", tag, type, value); //$NON-NLS-1$
                        }
                        if (lastConditions != null) {
                            lastConditions.addChild(new DefaultCondition(tag, type, value));
                        }
                        tag = null;
                        type = null;
                        value = null;
                    } else if (endElement.equals(xmler.getName().getLocalPart())) { // $NON-NLS-1$
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
        return conditions;
    }

    private SplittingModalityRules getSplittingModalityRules(String extend) {
        if (StringUtil.hasText(extend)) {
            SplittingModalityRules val = rules.get(getModdality(extend));
            if (val == null) {
                LOGGER.error("Modality {} doesn't exist! Cannot ihnerit the rules.", //$NON-NLS-1$
                    extend);
            }
        }
        return null;
    }

    public SplittingModalityRules getSplittingModalityRules(Modality key, Modality defaultKey) {
        SplittingModalityRules val = rules.get(key);
        if (val == null) {
            val = rules.get(defaultKey);
        }
        return val;
    }

    private static Type getConditionType(String type) {
        try {
            return Condition.Type.valueOf(type);
        } catch (Exception e) {
            LOGGER.error("{} is not a valid condition type", type, e); //$NON-NLS-1$
        }
        return null;
    }

    private static Modality getModdality(String name) {
        try {
            return Modality.valueOf(name);
        } catch (Exception e) {
            LOGGER.error("Modality reference of {} is missing", name, e); //$NON-NLS-1$
        }
        return null;
    }

    private static TagW getTag(String tagKey) {
        TagW tag = TagW.get(tagKey);
        if (tag == null) {
            LOGGER.error("Cannot find a tag with the keyword {}", tagKey); //$NON-NLS-1$
        }
        return tag;
    }
}
