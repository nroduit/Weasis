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

package org.dcm4che2.imageioimpl.plugins.rle;

import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;

/**
 * @author gunter zeilinger(gunterze@gmail.com)
 * @version $Revision: 13898 $ $Date: 2010-08-19 13:50:09 +0200 (Thu, 19 Aug 2010) $
 * @since May 11, 2006
 * 
 */
public class RLEImageReaderSpi extends ImageReaderSpi {

    private static String[] formatNames = { "rle", "RLE" };
    private static String[] entensions = { "" };
    private static String[] MIMETypes = { "" };
    private static String vendor;
    private static String version;
    static {
        Package p = RLEImageReaderSpi.class.getPackage();
        vendor = maskNull(p.getImplementationVendor(), "");
        version = maskNull(p.getImplementationVersion(), "");
    }

    private static String maskNull(String s, String def) {
        return s != null ? s : def;
    }

    public RLEImageReaderSpi() {
        super(vendor, version, formatNames, entensions, MIMETypes,
            "org.dcm4che2.imageioimpl.plugins.rle.RLEImageReader", STANDARD_INPUT_TYPE, null, false, null, null, null,
            null, false, null, null, null, null);
    }

    @Override
    public String getDescription(Locale locale) {
        return "RLE Image Reader";
    }

    @Override
    public boolean canDecodeInput(Object source) {
        // never auto-selected by ImageIO
        return false;
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new RLEImageReader(this);
    }
}
