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
package org.weasis.core.api.media.data;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import org.weasis.core.api.util.FileUtil;

public class FileCache {

    private final MediaReader reader;
    private File originalTempFile;
    private File transformedFile;
    private boolean requireTransformation;

    public FileCache(MediaReader reader) {
        this.reader = Objects.requireNonNull(reader);
        this.requireTransformation = false;
    }

    public boolean isLocalFile() {
        return reader.getUri().getScheme().startsWith("file"); //$NON-NLS-1$
    }

    public boolean isElementInMemory() {
        return reader.getUri().getScheme().startsWith("data"); //$NON-NLS-1$
    }

    public Optional<File> getOriginalFile() {
        File originalFile = null;
        if (originalTempFile != null) {
            originalFile = originalTempFile;
        } else if (isLocalFile()) {
            originalFile = Paths.get(reader.getUri()).toFile();
        }
        return Optional.ofNullable(originalFile);
    }

    public File getFinalFile() {
        if (transformedFile != null) {
            return transformedFile;
        }
        return getOriginalFile().orElse(null);
    }

    public synchronized File getOriginalTempFile() {
        return originalTempFile;
    }

    public synchronized void setOriginalTempFile(File downloadedFile) {
        this.originalTempFile = downloadedFile;
    }

    public synchronized File getTransformedFile() {
        return transformedFile;
    }

    public synchronized void setTransformedFile(File transformedFile) {
        this.transformedFile = transformedFile;
    }

    public synchronized boolean isRequireTransformation() {
        return requireTransformation;
    }

    public synchronized void setRequireTransformation(boolean requireTransformation) {
        this.requireTransformation = requireTransformation;
    }

    public long getLength() {
        Optional<File> f = getOriginalFile();
        if (f.isPresent()) {
            return f.get().length();
        }
        return 0L;
    }

    public long getLastModified() {
        Optional<File> f = getOriginalFile();
        if (f.isPresent()) {
            return f.get().lastModified();
        }
        return 0L;
    }

    public void dispose() {
        FileUtil.delete(originalTempFile);
        FileUtil.delete(transformedFile);
    }

}
