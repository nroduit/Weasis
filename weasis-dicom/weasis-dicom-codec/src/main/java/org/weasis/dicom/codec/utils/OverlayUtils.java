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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

import javax.media.jai.PlanarImage;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.Overlays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.display.OverlayOp;

public class OverlayUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayUtils.class);

    private static final byte[] icmColorValues = new byte[] { (byte) 0xFF, (byte) 0x00 };

    /**
     * Merge the overlays into the buffered image. This method apply only white pixel overlays.
     *
     * @param params
     *
     */
    public static RenderedImage getBinaryOverlays(ImageElement image, Attributes attributes, int frameIndex, int width,
        int height, HashMap<String, Object> params) throws IOException {

        // Default grayscale value for overlay
        int grayscaleValue = 0xFFFF;
        int outBits = 1;
        IndexColorModel icm =
            new IndexColorModel(outBits, icmColorValues.length, icmColorValues, icmColorValues, icmColorValues, 0);
        BufferedImage overBi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);
        WritableRaster raster = overBi.getRaster();

        // Get serialized overlay (from pixel data)
        byte[][] data = null;
        String filePath = (String) image.getTagValue(TagW.OverlayBurninData);
        if (filePath != null) {
            FileInputStream fileIn = null;
            ObjectInputStream objIn = null;
            try {
                fileIn = new FileInputStream(filePath);
                objIn = new ObjectInputStream(fileIn);
                Object o = objIn.readObject();
                if (o instanceof byte[][]) {
                    data = (byte[][]) o;
                }
            } catch (Exception e) {
                LOGGER.error("Cannot read serialized overlay: {}", e.getMessage()); //$NON-NLS-1$
            } finally {
                FileUtil.safeClose(objIn);
                FileUtil.safeClose(fileIn);
            }
        }

        int[] overlayGroupOffsets = Overlays.getActiveOverlayGroupOffsets(attributes, 0xffff);

        for (int i = 0; i < overlayGroupOffsets.length; i++) {
            byte[] ovlyData = null;
            // Get bitmap overlay from pixel data
            if (data != null && attributes.getInt(Tag.OverlayBitsAllocated | overlayGroupOffsets[i], 1) != 1) {
                if (data.length > i) {
                    ovlyData = data[i];
                }
            }
            // If onlyData is null, get bitmap overlay from dicom attributes
            Overlays.applyOverlay(ovlyData != null ? 0 : frameIndex, raster, attributes, overlayGroupOffsets[i],
                grayscaleValue >>> (16 - outBits), ovlyData);
        }
        
        Object pr = params.get(OverlayOp.P_PR_ELEMENT);
        if (pr instanceof PRSpecialElement) {
            Attributes ovlyAttrs = ((PRSpecialElement) pr).getMediaReader().getDicomObject();
            overlayGroupOffsets = Overlays.getActiveOverlayGroupOffsets(ovlyAttrs, 0xffff);
            Integer shuttOverlayGroup =
                DicomMediaUtils.getIntegerFromDicomElement(ovlyAttrs, Tag.ShutterOverlayGroup, Integer.MIN_VALUE);      

            // grayscaleValue = Overlays.getRecommendedDisplayGrayscaleValue(psAttrs, gg0000);
            for (int i = 0; i < overlayGroupOffsets.length; i++) {
                if (shuttOverlayGroup != overlayGroupOffsets[i]) {
                Overlays.applyOverlay(frameIndex, raster, ovlyAttrs, overlayGroupOffsets[i],
                    grayscaleValue >>> (16 - outBits), null);
                }
            }
        }
        
        return overBi;
    }
    
    public static RenderedImage getShutterOverlay(Attributes attributes, int frameIndex, int width, int height,
        int shuttOverlayGroup) throws IOException {
        IndexColorModel icm =
            new IndexColorModel(1, icmColorValues.length, icmColorValues, icmColorValues, icmColorValues, 0);
        BufferedImage overBi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);

        Overlays.applyOverlay(frameIndex, overBi.getRaster(), attributes, shuttOverlayGroup - 0x6000, 1, null);

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

        // Binary size = ((imageSize + 7) / 8 ) + 1 & (-2) = 32769 & (-2) = 1000000000000001 & 1111111111111111110
        byte[] ovlyData = new byte[(((length + 7) >>> 3) + 1) & (~1)];
        extractFromPixeldata(raster, mask, ovlyData, 0, length);
        return ovlyData;
    }

    public static void extractFromPixeldata(Raster raster, int mask, byte[] ovlyData, int off, int length) {
        ComponentSampleModel sm = (ComponentSampleModel) raster.getSampleModel();
        int rows = raster.getHeight();
        int columns = raster.getWidth();
        int stride = sm.getScanlineStride();
        DataBuffer db = raster.getDataBuffer();
        switch (db.getDataType()) {
            case DataBuffer.TYPE_BYTE:
                extractFromPixeldata(((DataBufferByte) db).getData(), rows, columns, stride, mask, ovlyData, off,
                    length);
                break;
            case DataBuffer.TYPE_USHORT:
                extractFromPixeldata(((DataBufferUShort) db).getData(), rows, columns, stride, mask, ovlyData, off,
                    length);
                break;
            case DataBuffer.TYPE_SHORT:
                extractFromPixeldata(((DataBufferShort) db).getData(), rows, columns, stride, mask, ovlyData, off,
                    length);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported DataBuffer type: " + db.getDataType());
        }
    }

    private static void extractFromPixeldata(byte[] pixeldata, int rows, int columns, int stride, int mask,
        byte[] ovlyData, int off, int length) {
        for (int y = 0, i = off, imax = off + length; y < columns && i < imax; y++) {
            for (int j = y * stride; j < imax && i < imax; j++, i++) {
                if ((pixeldata[j] & mask) != 0) {
                    ovlyData[i >>> 3] |= 1 << (i & 7);
                }
            }
        }
    }

    private static void extractFromPixeldata(short[] pixeldata, int rows, int columns, int stride, int mask,
        byte[] ovlyData, int off, int length) {
        for (int y = 0, i = off, imax = off + length; y < columns && i < imax; y++) {
            for (int j = y * stride; j < imax && i < imax; j++, i++) {
                if ((pixeldata[j] & mask) != 0) {
                    ovlyData[i >>> 3] |= 1 << (i & 7);
                }
            }
        }
    }

}
