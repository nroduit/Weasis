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

package org.dcm4che2.imageio.plugins.dcm;

import javax.imageio.ImageReadParam;

import org.dcm4che2.data.DicomObject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Sep 2, 2006
 */
public class DicomImageReadParam extends ImageReadParam {

    public static final String LINEAR = "LINEAR";
    public static final String SIGMOID = "SIGMOID";

    private boolean autoWindowing = true;
    private float center;
    private float width;
    private String vlutFct;
    private DicomObject voiLut;
    private DicomObject prState;
    private short[] pval2gray;
    String overlayRGB = null;

    public final boolean isAutoWindowing() {
        return autoWindowing;
    }

    public final void setAutoWindowing(boolean autoWindowing) {
        this.autoWindowing = autoWindowing;
    }

    public final float getWindowCenter() {
        return center;
    }

    public final void setWindowCenter(float center) {
        this.center = center;
    }

    public final float getWindowWidth() {
        return width;
    }

    public final void setWindowWidth(float width) {
        this.width = width;
    }

    public final void setVoiLutFunction(String vlutFct) {
        this.vlutFct = vlutFct;
    }

    public final String getVoiLutFunction() {
        return vlutFct;
    }

    public final DicomObject getVoiLut() {
        return voiLut;
    }

    public final void setVoiLut(DicomObject voiLut) {
        this.voiLut = voiLut;
    }

    public final DicomObject getPresentationState() {
        return prState;
    }

    public final void setPresentationState(DicomObject prState) {
        this.prState = prState;
    }

    public final short[] getPValue2Gray() {
        return pval2gray;
    }

    public final void setPValue2Gray(short[] pval2gray) {
        this.pval2gray = pval2gray;
    }

    /**
     * Get the 6 digit hex string that specifies the RGB colour to use for the overlay
     */
    public String getOverlayRGB() {
        return overlayRGB;
    }

    /**
     * Sets the 6 digit hex string that specifies the RGB colour to use for the overlay.
     */
    public void setOverlayRGB(String overlayRGB) {
        if (overlayRGB == null) {
            this.overlayRGB = null;
            return;
        }
        overlayRGB = overlayRGB.trim();
        if (overlayRGB.startsWith("#")) {
            overlayRGB = overlayRGB.substring(1, overlayRGB.length());
        }
        if (overlayRGB.length() == 0) {
            this.overlayRGB = null;
            return;
        }
        this.overlayRGB = overlayRGB;
    }
}
