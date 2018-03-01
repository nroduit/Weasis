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
 * @author btja
 *
 */
public abstract class MediaSource {

    protected final String id; // assume to be unique, like a DeviceID or a FilePath/URL
    protected String displayName;
    protected String description;
    protected Icon icon;

    public MediaSource(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public final String getID() {
        return id;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    public String getDescription() {
        return description != null ? description : ""; //$NON-NLS-1$
    }

    public Icon getIcon() {
        return icon;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MediaSource other = (MediaSource) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
