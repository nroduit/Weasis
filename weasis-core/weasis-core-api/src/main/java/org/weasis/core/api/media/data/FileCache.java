/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
    private volatile File originalTempFile;
    private volatile File transformedFile;
    private volatile boolean requireTransformation;
    
    private volatile long[] segmentPositions;
    private volatile long[] segmentLengths;

    public FileCache(MediaReader reader) {
        this.reader = Objects.requireNonNull(reader);
        this.requireTransformation = false;
    }

    public long[] getSegmentPositions() {
        return segmentPositions;
    }

    public void setSegmentPositions(long[] segmentPositions) {
        this.segmentPositions = segmentPositions;
    }

    public long[] getSegmentLengths() {
        return segmentLengths;
    }

    public void setSegmentLengths(long[] segmentLengths) {
        this.segmentLengths = segmentLengths;
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
        return getOriginalFile().get();
    }

    public File getOriginalTempFile() {
        return originalTempFile;
    }

    public void setOriginalTempFile(File downloadedFile) {
        this.originalTempFile = downloadedFile;
    }

    public File getTransformedFile() {
        return transformedFile;
    }

    public void setTransformedFile(File transformedFile) {
        this.transformedFile = transformedFile;
    }

    public boolean isRequireTransformation() {
        return requireTransformation;
    }

    public void setRequireTransformation(boolean requireTransformation) {
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
