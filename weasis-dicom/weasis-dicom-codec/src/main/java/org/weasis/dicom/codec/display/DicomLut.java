/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.image.VOIUtils;
import org.weasis.dicom.codec.Messages;

public class DicomLut {

    public static final String LINEAR = "LINEAR"; //$NON-NLS-1$
    public static final String SIGMOID = "SIGMOID"; //$NON-NLS-1$

    private boolean autoWindowing = true;
    private float center;
    private float width;
    private String vlutFct;
    private DicomObject voiLut;
    private DicomObject prState;
    private short[] pval2gray;
    String overlayRGB = null;

    public void createLut(DicomObject img, int frame) {
        DicomObject mlutObj = VOIUtils.selectModalityLUTObject(img, null, frame);
        DicomObject voiObj = VOIUtils.selectVoiObject(img, null, frame);

        boolean inverse = isInverse(img);
        int stored = img.getInt(Tag.BitsStored, img.getInt(Tag.BitsAllocated, 8));
        boolean signed = img.getInt(Tag.PixelRepresentation) != 0;
        float slope = mlutObj.getFloat(Tag.RescaleSlope, 1.f);
        float intercept = mlutObj.getFloat(Tag.RescaleIntercept, 0.f);

        DicomObject mLut = mlutObj.getNestedDicomObject(Tag.ModalityLUTSequence);
        DicomObject voiLut = voiObj != null ? voiObj.getNestedDicomObject(Tag.VOILUTSequence) : null;

        if (voiLut == null && voiObj != null) {
            vlutFct = voiObj.getString(Tag.VOILUTFunction);
            center = voiObj.getFloat(Tag.WindowCenter, 0f);
            width = voiObj.getFloat(Tag.WindowWidth, 0f);
        }

    }

    private static boolean isInverse(DicomObject img) {
        String shape = img.getString(Tag.PresentationLUTShape);
        return shape != null ? "INVERSE".equals(shape) : "MONOCHROME1".equals(img //$NON-NLS-1$ //$NON-NLS-2$
            .getString(Tag.PhotometricInterpretation));
    }

    private static byte[][] createSigmoidLut(int inBits, float slope, float intercept, float center, float width,
        boolean inverse) {
        int size = 256;
        int outMax = size - 1;
        float ic = (center - intercept) / slope;
        float k = -4 * slope / width;
        byte[][] data = new byte[1][size];
        for (int i = 0; i < size; i++) {
            int tmp = (int) (size / (1 + Math.exp((i - ic) * k)));
            if (inverse) {
                tmp = outMax - tmp;
            }
            data[0][i] = (byte) tmp;
        }
        return data;
    }

}
