/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.image.jni;

import java.io.File;

import javax.imageio.ImageReadParam;

import com.sun.media.imageioimpl.common.ExtendImageParam;

public class NativeImageReadParam extends ImageReadParam implements ExtendImageParam {

    /** Pixel interleaved mode: banded, line interleaved, pixel interleaved */
    public static final int ILV_NONE = 0, ILV_LINE = 1, ILV_SAMPLE = 2;

    private File file;
    private Integer height;
    private Integer width;
    private Integer bitsPerSample;
    private Integer samplesPerPixel;
    private Integer bytesPerLine;
    private Integer pixelInterleaved;
    private Boolean signedData;
    private String ybrColorModel;
    private long[] segmentPositions;
    private long[] segmentLengths;

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public Integer getHeight() {
        return height;
    }

    @Override
    public void setHeight(Integer height) {
        this.height = height;
    }

    @Override
    public Integer getWidth() {
        return width;
    }

    @Override
    public void setWidth(Integer width) {
        this.width = width;
    }

    @Override
    public Integer getBitsPerSample() {
        return bitsPerSample;
    }

    @Override
    public void setBitsPerSample(Integer bitsPerSample) {
        this.bitsPerSample = bitsPerSample;
    }

    @Override
    public Integer getSamplesPerPixel() {
        return samplesPerPixel;
    }

    @Override
    public void setSamplesPerPixel(Integer samplesPerPixel) {
        this.samplesPerPixel = samplesPerPixel;
    }

    @Override
    public Integer getBytesPerLine() {
        return bytesPerLine;
    }

    @Override
    public void setBytesPerLine(Integer bytesPerLine) {
        this.bytesPerLine = bytesPerLine;
    }

    @Override
    public Integer getPixelInterleaved() {
        return pixelInterleaved;
    }

    @Override
    public void setPixelInterleaved(Integer pixelInterleaved) {
        this.pixelInterleaved = pixelInterleaved;
    }

    @Override
    public Boolean getSignedData() {
        return signedData;
    }

    @Override
    public void setSignedData(Boolean signedData) {
        this.signedData = signedData;
    }

    @Override
    public String getYbrColorModel() {
        return ybrColorModel;
    }

    @Override
    public void setYbrColorModel(String ybrColorModel) {
        this.ybrColorModel = ybrColorModel;
    }

    @Override
    public long[] getSegmentPositions() {
        return segmentPositions;
    }

    @Override
    public void setSegmentPositions(long[] segmentPositions) {
        this.segmentPositions = segmentPositions;
    }

    @Override
    public long[] getSegmentLengths() {
        return segmentLengths;
    }

    @Override
    public void setSegmentLengths(long[] segmentLengths) {
        this.segmentLengths = segmentLengths;
    }

}
