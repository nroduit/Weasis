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
package org.weasis.acquire.explorer.media;

import java.util.Objects;

import javax.swing.Icon;

/**
 * Represent either a FileSystemDrive or a MediaDevice
 *
 * @author btja, nirt
 *
 */
public abstract class MediaSource {

    protected final String path;
    protected String displayName;
    protected String description;
    protected Icon icon;

    public MediaSource(String path) {
        this.path = Objects.requireNonNull(path);
    }

    public final String getPath() {
        return path;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : path;
    }

    public String getDescription() {
        return description != null ? description : ""; //$NON-NLS-1$
    }

    public Icon getIcon() {
        return icon;
    }
    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaSource that = (MediaSource) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
