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