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
package org.weasis.dicom.codec;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.dcm4che2.data.VR;
import org.weasis.core.api.media.data.TagW.TagType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class TagDictionary {

    private final HashMap<Integer, DicomTag> table;

    public TagDictionary() {
        this.table = new HashMap<Integer, DicomTag>(40);
    }

    public static TagType getTagType(VR vr, String vm) {
        boolean array = false;
        if (!"1".equals(vm.trim())) { //$NON-NLS-1$
            array = true;
        }
        if (vr == VR.AE || vr == VR.AS || vr == VR.AT || vr == VR.CS || vr == VR.LO || vr == VR.LT || vr == VR.PN
            || vr == VR.SH || vr == VR.ST || vr == VR.UI || vr == VR.UN || vr == VR.UN_SIEMENS) {
            return TagType.String;
        } else if (vr == VR.DA) {
            return TagType.Date;
        } else if (vr == VR.DT) {
            return TagType.DateTime;
        } else if (vr == VR.TM) {
            return TagType.Time;
        } else if (vr == VR.IS || vr == VR.SS || vr == VR.US || vr == VR.SL || vr == VR.UL) {
            return array ? TagType.IntegerArray : TagType.Integer;
        } else if (vr == VR.DS || vr == VR.FL) {
            return array ? TagType.FloatArray : TagType.Float;
        } else if (vr == VR.FD) {
            return array ? TagType.DoubleArray : TagType.Double;
        } else if (vr == VR.OB || vr == VR.OW || vr == VR.OF || vr == VR.UT) {
            return TagType.Text;
        } else if (vr == VR.SQ) {
            return TagType.Sequence;
        }
        return null;

    }

    public HashMap<Integer, DicomTag> loadXML(InputStream stream) throws IOException, SAXException {
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(stream, new SAXAdapter());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (FactoryConfigurationError e) {
            throw new RuntimeException(e);
        }
        return table;
    }

    private final class SAXAdapter extends DefaultHandler {

        int tag = -1;
        StringBuffer name = new StringBuffer(80);
        String vr = null;
        String vm = null;
        String format = null;
        boolean retired = false;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (tag != -1) {
                name.append(ch, start, length);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("element".equals(qName)) { //$NON-NLS-1$
                tag = (int) Long.parseLong(attributes.getValue("tag").replace('x', '0'), 16); //$NON-NLS-1$
                vr = attributes.getValue("vr"); //$NON-NLS-1$
                vm = attributes.getValue("vm"); //$NON-NLS-1$
                format = attributes.getValue("format"); //$NON-NLS-1$
                retired = attributes.getValue("ret").equals("RET"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("element".equals(qName)) { //$NON-NLS-1$
                VR vrVal = null;
                try {
                    Field f = VR.class.getField(vr);
                    vrVal = (VR) f.get(vr);
                } catch (Exception e) {
                }
                if (format != null && format.trim().equals("")) { //$NON-NLS-1$
                    format = null;
                }
                table.put(tag, new DicomTag(tag, name.toString(), vrVal, vm, format, retired));
                name.setLength(0);
                tag = -1;
            }
        }
    }

    public HashMap<Integer, DicomTag> getTable() {
        return table;
    }
}
