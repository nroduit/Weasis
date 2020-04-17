/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.io.File;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.AudioVideoElement;
import org.weasis.core.util.FileUtil;

public class DicomVideoElement extends AudioVideoElement implements FileExtractor {

    public static final String MPEG_MIMETYPE = "video/mpeg"; //$NON-NLS-1$

    private double pixelSizeX = 1.0;
    private double pixelSizeY = 1.0;
    private File videoFile = null;
    private Unit pixelSpacingUnit;

    public DicomVideoElement(DicomMediaIO mediaIO, Object key) {
        super(mediaIO, key);
        // Physical distance in mm between the center of each pixel (ratio in mm)
        double[] val = TagD.getTagValue(mediaIO, Tag.PixelSpacing, double[].class);
        if (val == null || val.length != 2) {
            val = TagD.getTagValue(mediaIO, Tag.ImagerPixelSpacing, double[].class);
        }
        if (val == null || val.length != 2) {
            val = TagD.getTagValue(mediaIO, Tag.NominalScannedPixelSpacing, double[].class);
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

    public void setVideoFile(File videoFile) {
        FileUtil.delete(this.videoFile);
        this.videoFile = videoFile;
    }

    @Override
    public File getExtractFile() {
        return videoFile;
    }
}
