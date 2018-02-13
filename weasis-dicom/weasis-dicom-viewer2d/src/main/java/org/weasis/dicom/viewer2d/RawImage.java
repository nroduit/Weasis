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
package org.weasis.dicom.viewer2d;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.weasis.core.api.util.FileUtil;

public class RawImage {
    private File file;
    private FileOutputStream outputStream;

    public RawImage(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null"); //$NON-NLS-1$
        }
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public FileOutputStream getOutputStream() throws FileNotFoundException {
        if (outputStream == null) {
            outputStream = new FileOutputStream(file);
        }
        return outputStream;
    }

    public void disposeOutputStream() {
        if (outputStream != null) {
            FileUtil.safeClose(outputStream);
            outputStream = null;
        }
    }
}