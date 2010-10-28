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
 * Gunter Zeilinger <gunterze@gmail.com>
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

package org.dcm4che2.imageioimpl.plugins.dcm;

import java.io.EOFException;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * @author gunter zeilinger(gunterze@gmail.com)
 * @version $Revision: 1.2 $ $Date: 2006/05/15 20:44:30 $
 * @since May 11, 2006
 * 
 */
public class DicomImageReaderSpi extends ImageReaderSpi {

    private static final String[] formatNames = { "dicom", "DICOM" };
    private static final String[] suffixes = { "dcm", "dic", "dicm", "dicom" };
    private static final String[] MIMETypes = { "application/dicom" };
    private static String vendor;
    private static String version;
    static {
        Package p = DicomImageReaderSpi.class.getPackage();
        vendor = maskNull(p.getImplementationVendor(), "");
        version = maskNull(p.getImplementationVersion(), "");
    }

    private static String maskNull(String s, String def) {
        return s != null ? s : def;
    }

    public DicomImageReaderSpi() {
        this(vendor, version, MIMETypes, "org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader", STANDARD_INPUT_TYPE,
            null, false, false);
    }

    /**
     * A constructor added to easier extend this class.
     */
    protected DicomImageReaderSpi(String vendorName, String version, String[] MIMETypes, String readerClassName,
        Class[] inputTypes, String[] writerSpiNames, boolean supportsStandardStreamMetadataFormat,
        boolean supportsStandardImageMetadataFormat) {

        super(vendorName, version, formatNames, suffixes, MIMETypes, readerClassName, inputTypes, writerSpiNames,
            supportsStandardStreamMetadataFormat, null, null, null, null, supportsStandardImageMetadataFormat, null,
            null, null, null);
    }

    @Override
    public String getDescription(Locale locale) {
        return "DICOM Image Reader";
    }

    @Override
    public boolean canDecodeInput(Object input) throws IOException {
        if (!(input instanceof ImageInputStream)) {
            return false;
        }

        ImageInputStream stream = (ImageInputStream) input;
        byte[] b = new byte[132];
        stream.mark();
        try {
            stream.readFully(b);
        } catch (EOFException e) {
            return false;
        } finally {
            stream.reset();
        }
        if (b[128] == 0x44 // D
            && b[129] == 0x49 // I
            && b[130] == 0x43 // C
            && b[131] == 0x4D) { // M
            return true;
        }
        try {
            if (b[0] == 0) { // big endian
                if (b[1] == 0) {
                    return false;
                }
                int len = ((b[6] & 0xff) << 8) | (b[7] & 0xff);
                return (b[1] == b[len + 9]);
            }

            // little endian
            if (b[1] != 0) { // expect group tag <= 00FF
                return false;
            }
            int len = (b[6] & 0xff) | ((b[7] & 0xff) << 8);
            if (b[0] == b[len + 8]) {
                return true;
            }
            len = (b[4] & 0xff) | ((b[5] & 0xff) << 8);
            return (b[0] == b[len + 8]);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new DicomImageReader(this);
    }
}
