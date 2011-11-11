package org.weasis.dicom.codec.utils;

import java.awt.image.ByteLookupTable;
import java.awt.image.DataBuffer;
import java.awt.image.LookupTable;
import java.awt.image.ShortLookupTable;

public class DicomImageUtils {

    public static LookupTable createRampLut(int dataType, Float intercept, Float slope, int minValue, int maxValue) {

        LookupTable lookup = null;

        int numEntries = maxValue - minValue + 1;
        if (numEntries <= 0)
            return null;

        slope = (slope == null) ? 1.0f : slope;
        intercept = (intercept == null) ? 0.0f : intercept;

        // double low = minValue * slope + intercept;
        // double high = maxValue * slope + intercept;
        // double outRange = high - low;
        // outRange = (outRange < 1.0) ? 1.0 : outRange;

        if (dataType == DataBuffer.TYPE_BYTE) {
            byte[] lut = new byte[numEntries];

            for (int i = 0; i < numEntries; i++) {
                lut[i] = (byte) ((i + minValue) * slope + intercept);
            }

            lookup = new ByteLookupTable(minValue, lut);

        } else if (dataType <= DataBuffer.TYPE_SHORT) {
            short[] lut = new short[numEntries];

            for (int i = 0; i < numEntries; i++) {
                lut[i] = (short) ((i + minValue) * slope + intercept);
            }
            lookup = new ShortLookupTable(minValue, lut);
        }

        return lookup;
    }
}
