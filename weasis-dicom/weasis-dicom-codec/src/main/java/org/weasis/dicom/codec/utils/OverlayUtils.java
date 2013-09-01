/*
 * ***** BEGIN LICENSE BLOCK ***** Version: MPL 1.1/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the License for the specific language governing rights and limitations under the License.
 * 
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in Java(TM), hosted at
 * http://sourceforge.net/projects/dcm4che.
 * 
 * The Initial Developer of the Original Code is Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005 the Initial Developer. All Rights Reserved.
 * 
 * Contributor(s): See listed authors below.
 * 
 * Alternatively, the contents of this file may be used under the terms of either the GNU General Public License Version
 * 2 or later (the "GPL"), or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in which case the
 * provisions of the GPL or the LGPL are applicable instead of those above. If you wish to allow use of your version of
 * this file only under the terms of either the GPL or the LGPL, and not to allow others to use your version of this
 * file under the terms of the MPL, indicate your decision by deleting the provisions above and replace them with the
 * notice and other provisions required by the GPL or the LGPL. If you do not delete the provisions above, a recipient
 * may use your version of this file under the terms of any one of the MPL, the GPL or the LGPL.
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.weasis.dicom.codec.utils;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.image.Overlays;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;

public class OverlayUtils {

    private static final byte[] icmColorValues = new byte[] { (byte) 0xFF, (byte) 0x00 };

    /**
     * Merge the overlays into the buffered image.
     * 
     */
    public static RenderedImage getOverlays(ImageOperation imageOperation, DicomMediaIO reader, int frameIndex,
        int width, int height) throws IOException {
        Attributes ds = reader.getDicomObject();

        int grayscaleValue = 0xFFFF;
        int outBits = 1;
        IndexColorModel icm =
            new IndexColorModel(outBits, icmColorValues.length, icmColorValues, icmColorValues, icmColorValues, 0);
        BufferedImage overBi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);
        WritableRaster raster = overBi.getRaster();

        byte[][] data = (byte[][]) imageOperation.getImage().getTagValue(TagW.OverlayBurninData);
        int[] overlayGroupOffsets = Overlays.getActiveOverlayGroupOffsets(ds, 0xffff);

        for (int i = 0; i < overlayGroupOffsets.length; i++) {
            byte[] ovlyData = null;
            if (ds.getInt(Tag.OverlayBitsAllocated | overlayGroupOffsets[i], 1) != 1) {
                if (data.length > i) {
                    ovlyData = data[i];
                }
            }

            Attributes ovlyAttrs = ds;

            // if (param instanceof DicomImageReadParam) {
            // DicomImageReadParam dParam = (DicomImageReadParam) param;
            // Attributes psAttrs = dParam.getPresentationState();
            // if (psAttrs != null) {
            // if (psAttrs.containsValue(Tag.OverlayData | gg0000)) {
            // ovlyAttrs = psAttrs;
            // }
            // grayscaleValue = Overlays.getRecommendedDisplayGrayscaleValue(psAttrs, gg0000);
            // } else {
            // grayscaleValue = dParam.getOverlayGrayscaleValue();
            // }
            // }
            Overlays.applyOverlay(ovlyData != null ? 0 : frameIndex, raster, ovlyAttrs, overlayGroupOffsets[i],
                grayscaleValue >>> (16 - outBits), ovlyData);
        }

        Object pr = imageOperation.getActionValue(ActionW.PR_STATE.cmd());
        if (pr instanceof PRSpecialElement) {
            Attributes ovlyAttrs = ((PRSpecialElement) pr).getMediaReader().getDicomObject();
            overlayGroupOffsets = Overlays.getActiveOverlayGroupOffsets(ovlyAttrs, 0xffff);

            for (int i = 0; i < overlayGroupOffsets.length; i++) {
                Overlays.applyOverlay(frameIndex, raster, ovlyAttrs, overlayGroupOffsets[i],
                    grayscaleValue >>> (16 - outBits), null);
            }
        }
        return overBi;
    }

    public static byte[] extractOverlay(int gg0000, Raster raster, Attributes attrs) {
        if (attrs.getInt(Tag.OverlayBitsAllocated | gg0000, 1) == 1) {
            return null;
        }

        int ovlyRows = attrs.getInt(Tag.OverlayRows | gg0000, 0);
        int ovlyColumns = attrs.getInt(Tag.OverlayColumns | gg0000, 0);
        int bitPosition = attrs.getInt(Tag.OverlayBitPosition | gg0000, 0);

        int mask = 1 << bitPosition;
        int length = ovlyRows * ovlyColumns;

        byte[] ovlyData = new byte[(((length + 7) >>> 3) + 1) & (~1)];
        Overlays.extractFromPixeldata(raster, mask, ovlyData, 0, length);
        return ovlyData;
    }

}
