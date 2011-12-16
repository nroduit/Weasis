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
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa HealthCare
 * Portions created by the Initial Developer are Copyright (C) 2009
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
package org.dcm4che2.imageioimpl.plugins.dcm;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.RasterFormatException;
import java.awt.image.SampleModel;

public class PartialComponentSampleModel extends SampleModel {

    protected final int subsampleX, subsampleY;
    int rowLen, rowsWithBR, rowsWithoutBR, size;
    static final int[] sampleSize = new int[] { 8, 8, 8 };

    /**
     * Sub-sample x,y are the rate of sub-sampling. YBR_X_422 corresponds to 2, 1, while YBR_X_420 corresponds to 2,2
     */
    public PartialComponentSampleModel(int w, int h, int subsampleX, int subsampleY) {
        super(DataBuffer.TYPE_BYTE, w, h, 3);
        this.subsampleX = subsampleX;
        this.subsampleY = subsampleY;
        rowLen = (w * (subsampleX + 2)) / subsampleX;
        rowsWithBR = h / subsampleY;
        rowsWithoutBR = h - rowsWithBR;
        size = w * rowsWithoutBR + rowLen * rowsWithBR;
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        return new PartialComponentSampleModel(w, h, this.subsampleX, this.subsampleY);
    }

    @Override
    public DataBuffer createDataBuffer() {
        return new DataBufferByte(size);
    }

    @Override
    public SampleModel createSubsetSampleModel(int[] bands) {
        if (bands.length != 3) {
            throw new RasterFormatException("Accept only 3 bands");
        }
        return this;
    }

    @Override
    public Object getDataElements(int x, int y, Object obj, DataBuffer data) {
        byte[] ret;
        DataBufferByte dbb = (DataBufferByte) data;
        if ((obj instanceof byte[]) && ((byte[]) obj).length == 3) {
            ret = (byte[]) obj;
        } else {
            ret = new byte[3];
        }
        int yWithBR = y / subsampleY;
        int yWithoutBR = y - yWithBR;
        int yStart = yWithoutBR * getWidth() + rowLen * yWithBR;
        byte[] ba = dbb.getData();
        if (subsampleY == 1 || (y % subsampleY == 0)) {
            int xOffset = x % subsampleX;
            int xWithBR = x / subsampleX;
            int xStart = yStart + xWithBR * (2 + subsampleX);
            ret[0] = ba[xStart + xOffset];
            ret[1] = ba[xStart + subsampleX];
            ret[2] = ba[xStart + subsampleX + 1];
        } else {
            int yHavingBR = (y - y % subsampleY);
            ret = (byte[]) getDataElements(x, yHavingBR, ret, data);
            ret[0] = dbb.getData()[yStart + x];
        }
        return ret;
    }

    @Override
    public int getNumDataElements() {
        return 3;
    }

    @Override
    public int getSample(int x, int y, int b, DataBuffer data) {
        return ((byte[]) getDataElements(x, y, null, data))[b];
    }

    @Override
    public int[] getSampleSize() {
        return sampleSize;
    }

    @Override
    public int getSampleSize(int band) {
        return sampleSize[band];
    }

    @Override
    public void setDataElements(int x, int y, Object obj, DataBuffer data) {
        if (getTransferType() != DataBuffer.TYPE_BYTE) {
            throw new UnsupportedOperationException();
        }
        if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
        byte[] barray = (byte[]) obj;

        DataBufferByte dbb = (DataBufferByte) data;
        int yWithBR = y / subsampleY;
        int yWithoutBR = y - yWithBR;
        int yStart = yWithoutBR * getWidth() + rowLen * yWithBR;
        byte[] ba = dbb.getData();
        if (subsampleY == 1 || (y % subsampleY == 0)) {
            int xOffset = x % subsampleX;
            int xWithBR = x / subsampleX;
            int xStart = yStart + xWithBR * (2 + subsampleX);
            ba[xStart + xOffset] = barray[0];
            ba[xStart + subsampleX] = barray[1];
            ba[xStart + subsampleX + 1] = barray[2];
        } else {
            ba[yStart + x] = barray[0];
        }
    }

    @Override
    public void setSample(int x, int y, int b, int s, DataBuffer data) {
        throw new UnsupportedOperationException();
    }

}
