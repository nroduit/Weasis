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
package org.weasis.dicom.codec;

import java.io.File;

import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.AudioVideoElement;
import org.weasis.core.api.media.data.TagW;

public class DicomVideoElement extends AudioVideoElement {

    public static final String MPEG_MIMETYPE = "video/mpeg"; //$NON-NLS-1$

    private double pixelSizeX = 1.0;
    private double pixelSizeY = 1.0;
    private File videoFile = null;
    private Unit pixelSpacingUnit;

    public DicomVideoElement(DicomMediaIO mediaIO, Object key) {
        super(mediaIO, key);
        // Physical distance in mm between the center of each pixel (ratio in mm)
        double[] val = (double[]) mediaIO.getTagValue(TagW.PixelSpacing);
        if (val == null) {
            val = (double[]) mediaIO.getTagValue(TagW.ImagerPixelSpacing);
        }
        if (val != null) {
            pixelSizeX = val[0];
            pixelSizeY = val[1];
            pixelSpacingUnit = Unit.MILLIMETER;
        }
    }

    public double getPixelSizeX() {
        return pixelSizeX;
    }

    public double getPixelSizeY() {
        return pixelSizeY;
    }

    public Unit getPixelSpacingUnit() {
        return pixelSpacingUnit;
    }

    public File getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(File videoFile) {
        this.videoFile = videoFile;
    }
}
