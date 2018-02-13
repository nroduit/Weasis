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
package org.weasis.jpeg.internal;

class SOFSegment {
    private final int marker;
    private final int samplePrecision;
    private final int lines; // height
    private final int samplesPerLine; // width
    private final int components;

    SOFSegment(int marker, int samplePrecision, int lines, int samplesPerLine, int components) {
        this.marker = marker;
        this.samplePrecision = samplePrecision;
        this.lines = lines;
        this.samplesPerLine = samplesPerLine;
        this.components = components;
    }

    public int getMarker() {
        return marker;
    }

    public int getSamplePrecision() {
        return samplePrecision;
    }

    public int getLines() {
        return lines;
    }

    public int getSamplesPerLine() {
        return samplesPerLine;
    }

    public int getComponents() {
        return components;
    }

    @Override
    public String toString() {
        return String.format("SOF%d[%04x, precision: %d, lines: %d, samples/line: %d]", marker & 0xff - 0xc0, marker,
            samplePrecision, lines, samplesPerLine);
    }
}
