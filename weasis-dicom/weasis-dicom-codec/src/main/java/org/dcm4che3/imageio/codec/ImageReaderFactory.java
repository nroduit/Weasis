/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che3.imageio.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLS;
import org.dcm4che3.util.ResourceLocator;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.ImageioUtil;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * 
 */
public class ImageReaderFactory implements Serializable {

    private static final long serialVersionUID = -2881173333124498212L;

    public final static Comparator<ImageReaderParam> ORDER_BY_RANK = new Comparator<ImageReaderParam>() {

        @Override
        public int compare(ImageReaderParam r1, ImageReaderParam r2) {
            return (r1.currentRank < r2.currentRank ? -1 : (r1.currentRank == r2.currentRank ? 0 : 1));
        }
    };

    public static class ImageReaderParam implements Serializable {

        private static final long serialVersionUID = 6593724836340684578L;

        private final int defaultRank;
        private final String formatName;
        private final String name;
        private final String className;
        private final PatchJPEGLS patchJPEGLS;
        private int currentRank;

        public ImageReaderParam(String formatName, String className, String patchJPEGLS, String name, int defaultRank) {
            if (formatName == null || name == null)
                throw new IllegalArgumentException();
            this.formatName = formatName;
            this.className = nullify(className);
            this.patchJPEGLS = patchJPEGLS != null && !patchJPEGLS.isEmpty() ? PatchJPEGLS.valueOf(patchJPEGLS) : null;
            this.defaultRank = this.currentRank = defaultRank;
            this.name = name;
        }

        public String getFormatName() {
            return formatName;
        }

        public String getClassName() {
            return className;
        }

        public PatchJPEGLS getPatchJPEGLS() {
            return patchJPEGLS;
        }

        public int getCurrentRank() {
            return currentRank;
        }

        public void setCurrentRank(int currentRank) {
            this.currentRank = currentRank;
        }

        public int getDefaultRank() {
            return defaultRank;
        }

        public String tosString() {
            return name;
        }

    }

    public static class ImageReaderItem {

        private final ImageReader imageReader;
        private final ImageReaderParam imageReaderParam;

        public ImageReaderItem(ImageReader imageReader, ImageReaderParam imageReaderParam) {
            super();
            this.imageReader = imageReader;
            this.imageReaderParam = imageReaderParam;
        }

        public ImageReader getImageReader() {
            return imageReader;
        }

        public ImageReaderParam getImageReaderParam() {
            return imageReaderParam;
        }

    }

    private static String nullify(String s) {
        return s == null || s.isEmpty() || s.equals("*") ? null : s;
    }

    private static ImageReaderFactory defaultFactory;
    private final Map<String, TreeSet<ImageReaderParam>> map = Collections
        .synchronizedMap(new HashMap<String, TreeSet<ImageReaderParam>>());

    public static ImageReaderFactory getDefault() {
        if (defaultFactory == null) {
            defaultFactory = initDefault();
        }

        return defaultFactory;
    }

    public static void resetDefault() {
        defaultFactory = null;
    }

    public static void setDefault(ImageReaderFactory factory) {
        if (factory == null) {
            throw new NullPointerException();
        }

        defaultFactory = factory;
    }

    private static ImageReaderFactory initDefault() {
        ImageReaderFactory factory = new ImageReaderFactory();
        String name =
            System.getProperty(ImageReaderFactory.class.getName(), "org/dcm4che3/imageio/codec/ImageReaderFactory.xml");
        try {
            factory.load(name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Image Reader Factory configuration from: " + name, e);
        }
        return factory;
    }

    public void load(InputStream stream) throws IOException {
        Map<String, List<PresetWindowLevel>> presetListByModality = new TreeMap<String, List<PresetWindowLevel>>();

        XMLStreamReader xmler = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("ImageReaderFactory".equals(key)) {
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("element".equals(key)) {
                                            String tsuid = xmler.getAttributeValue(null, "tsuid");
                                            
                                            boolean state = true;
                                            while (xmler.hasNext() && state) {
                                                eventType = xmler.next();
                                                switch (eventType) {
                                                    case XMLStreamConstants.START_ELEMENT:
                                                        key = xmler.getName().getLocalPart();
                                                        if ("reader".equals(key)) {
                                                            ImageReaderParam param =
                                                                new ImageReaderParam(xmler.getAttributeValue(null,
                                                                    "format"), xmler.getAttributeValue(null, "class"),
                                                                    xmler.getAttributeValue(null, "patchJPEGLS"),
                                                                    xmler.getAttributeValue(null, "name"),
                                                                    FileUtil.getIntegerTagAttribute(xmler, "priority",
                                                                        100));
                                                            put(tsuid, param);
                                                        }
                                                        break;
                                                    case XMLStreamConstants.END_ELEMENT:
                                                        if ("element".equals(xmler.getName().getLocalPart())) {
                                                            state = false;
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

    public void load(String name) throws IOException {
        URL url;
        try {
            url = new URL(name);
        } catch (MalformedURLException e) {
            url = ResourceLocator.getResourceURL(name, this.getClass());
            if (url == null) {
                throw new IOException("No such resource: " + name);
            }
        }
        InputStream in = url.openStream();
        try {
            load(in);
        } finally {
            SafeClose.close(in);
        }
    }

    public TreeSet<ImageReaderParam> get(String tsuid) {
        return map.get(tsuid);
    }

    public boolean contains(String tsuid) {
        return map.containsKey(tsuid);
    }

    public synchronized boolean put(String tsuid, ImageReaderParam param) {
        TreeSet<ImageReaderParam> readerSet = get(tsuid);
        if (readerSet == null) {
            readerSet = new TreeSet<ImageReaderParam>(ORDER_BY_RANK);
            map.put(tsuid, readerSet);
        }
        return readerSet.add(param);
    }

    public TreeSet<ImageReaderParam> remove(String tsuid) {
        return map.remove(tsuid);
    }

    public Set<Entry<String, TreeSet<ImageReaderParam>>> getEntries() {
        return Collections.unmodifiableMap(map).entrySet();
    }

    public void clear() {
        map.clear();
    }

    public static ImageReaderItem getImageReader(String tsuid) {
        TreeSet<ImageReaderParam> set = getDefault().get(tsuid);
        if (set != null) {
            synchronized (set) {
                for (Iterator<ImageReaderParam> it = set.iterator(); it.hasNext();) {
                    ImageReaderParam imageParam = it.next();
                    String cl = imageParam.getClassName();
                    Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName(imageParam.getFormatName());
                    while (iter.hasNext()) {
                        ImageReader reader = iter.next();
                        if (cl == null || reader.getClass().getName().equals(cl)) {
                            return new ImageReaderItem(reader, imageParam);
                        }
                    }
                }
            }
        }
        return null;
    }
}
