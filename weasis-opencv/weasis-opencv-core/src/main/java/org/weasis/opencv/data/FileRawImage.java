/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.opencv.data;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.weasis.opencv.op.ImageProcessor;

public class FileRawImage {
    public static final int HEADER_LENGTH = 46;

    private final File file;

    public FileRawImage(File file) {
        this.file = Objects.requireNonNull(file);
    }

    public File getFile() {
        return file;
    }

    public ImageCV read() throws IOException {
        return ImageProcessor.readImageWithCvException(file);
    }

    public boolean write(PlanarImage mat) {
        return ImageProcessor.writeImage(mat.toMat(), file);
    }
}