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
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
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

package org.dcm4che2.imageio;

import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.dcm4che2.data.ConfigurationError;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.pref.DicomPrefManager;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 6, 2007
 */
public class ImageReaderFactory extends ImageReaderWriterFactory {
    private static final Logger log = LoggerFactory.getLogger(ImageReaderFactory.class);
    private static final String CONFIG_KEY = "org.dcm4che2.imageio.ImageReaderFactory";

    private static final String DEF_CONFIG = "org/dcm4che2/imageio/ImageReaderFactory.properties";

    private static final ImageReaderFactory instance = new ImageReaderFactory();

    private ImageReaderFactory() {
        super(CONFIG_KEY, DEF_CONFIG);
    }

    public static final ImageReaderFactory getInstance() {
        return instance;
    }

    public ImageReader getReaderForTransferSyntax(String tsuid) {
        String s = config.getProperty(tsuid);
        if (s == null) {
            throw new UnsupportedOperationException("No Image Reader available for Transfer Syntax:" + tsuid);
        }
        int delim = s.indexOf(',');
        if (delim == -1) {
            throw new ConfigurationError("Missing ',' in " + tsuid + "=" + s);
        }
        if ("1.2.840.10008.1.2.4.90".equals(tsuid) || "1.2.840.10008.1.2.4.91".equals(tsuid)) {
            ImageReader r = getJpeg2000Reader(config.getProperty("jpeg2000"));
            if (s == null) {
                throw new UnsupportedOperationException("No JPEG2000 Reader available");
            }
            return r;
        }
        int delim2 = s.indexOf(',', delim + 1);
        final String formatName = s.substring(0, delim);
        final String className = s.substring(delim + 1, delim2 == -1 ? s.length() : delim2);
        for (Iterator it = ImageIO.getImageReadersByFormatName(formatName); it.hasNext();) {
            ImageReader r = (ImageReader) it.next();
            if (className.equals(r.getClass().getName())) {
                log.debug("Found reader " + className + " for " + tsuid);
                return r;
            }
            log.debug("Skipping image reader " + r.getClass().getName());
        }
        // Get default jpeg decoder for platforms where CLibJPEGImageReader is not available
        if ("1.2.840.10008.1.2.4.50".equals(tsuid)) {
            final String stdJpeg = "com.sun.imageio.plugins.jpeg.JPEGImageReader";
            for (Iterator it = ImageIO.getImageReadersByFormatName("jpeg"); it.hasNext();) {
                ImageReader r = (ImageReader) it.next();
                if (stdJpeg.equals(r.getClass().getName())) {
                    log.debug("Found reader " + stdJpeg + " for " + tsuid);
                    return r;
                }
            }
        }
        throw new ConfigurationError("No Image Reader of class " + className + " available for format:" + formatName);
    }

    /**
     * Some image types need an image type specifier in order to figure out the source image information - if that is
     * the case, then this will return true, based on configuration [tsuid].typeSpecifier=true in the config file. The
     * RLE and RAW readers need such specifiers, but the RAW one is hard coded elsewhere.
     * 
     * @return
     */
    public boolean needsImageTypeSpecifier(String tsuid) {
        String typeSpecifier = config.getProperty(tsuid + ".typeSpecifier");
        return "true".equalsIgnoreCase(typeSpecifier);
    }

    public String getProperty(String prop) {
        return config.getProperty(prop, "");
    }

    public ImageReader getJpeg2000Reader(String decoders) {
        String j2kReader = DicomPrefManager.getInstance().getJ2kReader();
        if (j2kReader != null) {
            for (Iterator it = ImageIO.getImageReadersByFormatName("jpeg2000"); it.hasNext();) {
                ImageReader r = (ImageReader) it.next();
                if (j2kReader.equals(r.getClass().getName())) {
                    return r;
                }
            }
        }
        if (decoders != null) {
            HashMap<String, ImageReader> installedDecoders = new HashMap<String, ImageReader>(5);
            for (Iterator it = ImageIO.getImageReadersByFormatName("jpeg2000"); it.hasNext();) {
                ImageReader r = (ImageReader) it.next();
                installedDecoders.put(r.getClass().getName(), r);
            }
            String[] list = decoders.split(",");
            for (String d : list) {
                ImageReader r = installedDecoders.get(d.trim());
                if (r != null) {
                    log.debug("Found jpeg2000 reader : {}", r.getClass().getName());
                    return r;
                }
            }
        }
        return null;
    }

    public void adjustDatasetForTransferSyntax(DicomObject ds, String tsuid) {
        if (ds.getInt(Tag.SamplesPerPixel, 1) == 1) {
            return;
        }
        String s = config.getProperty(tsuid);
        if (s == null) {
            return;
        }
        int delim = s.indexOf(',');
        if (delim == -1) {
            return;
        }
        int delim2 = s.indexOf(',', delim + 1);
        if (delim2 == -1) {
            return;
        }
        int delim3 = s.indexOf(',', delim2 + 1);
        final String planarConfig = s.substring(delim2 + 1, delim3 == -1 ? s.length() : delim3);
        if (planarConfig.length() != 0) {
            try {
                ds.putInt(Tag.PlanarConfiguration, VR.US, Integer.parseInt(planarConfig));
            } catch (NumberFormatException e) {
                throw new ConfigurationError("Illegal value for Planar Configuration: " + s);
            }
        }
        if (delim3 == -1) {
            return;
        }
        final String pmi = s.substring(delim3 + 1);
        if (pmi.length() != 0) {
            ds.putString(Tag.PhotometricInterpretation, VR.CS, pmi);
        }
    }
}
