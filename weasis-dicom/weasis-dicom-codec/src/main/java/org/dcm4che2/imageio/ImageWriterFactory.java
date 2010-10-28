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

import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.dcm4che2.data.ConfigurationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 6, 2007
 */
public class ImageWriterFactory extends ImageReaderWriterFactory {
    private static final Logger log = LoggerFactory.getLogger(ImageWriterFactory.class);

    private static final String CONFIG_KEY = "org.dcm4che2.imageio.ImageWriterFactory";

    private static final String DEF_CONFIG = "org/dcm4che2/imageio/ImageWriterFactory.properties";

    private static final ImageWriterFactory instance = new ImageWriterFactory();

    private ImageWriterFactory() {
        super(CONFIG_KEY, DEF_CONFIG);
    }

    public static final ImageWriterFactory getInstance() {
        return instance;
    }

    public ImageWriter getWriterForTransferSyntax(String tsuid) {
        String s = config.getProperty(tsuid);
        if (s == null) {
            throw new UnsupportedOperationException("No Image Writer available for Transfer Syntax:" + tsuid);
        }
        int delim = s.indexOf(',');
        if (delim == -1) {
            throw new ConfigurationError("Missing ',' in " + tsuid + "=" + s);
        }
        final String formatName = s.substring(0, delim);
        final String className = s.substring(delim + 1);
        for (Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName(formatName); it.hasNext();) {
            ImageWriter r = it.next();
            if (className.equals(r.getClass().getName())) {
                return r;
            }
        }
        throw new ConfigurationError("No Image Writer of class " + className + " available for format:" + formatName);
    }

    /**
     * This is used to create a param for writing the sub-image. Ideally, this shouldn't depend on the implementation
     * details, but right now, I can't easily figure out how to avoid that.
     * 
     * @param tsuid
     * @param writer
     * @return
     */
    public ImageWriteParam createWriteParam(String tsuid, ImageWriter writer) {
        ImageWriteParam param = writer.getDefaultWriteParam();
        String type = config.getProperty(tsuid + ".type");
        if (type != null) {
            log.debug("Setting compression to type " + type);
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(type);
        }
        return param;
    }
}
