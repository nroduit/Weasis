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
        return description != null ? description : "";
    }

    public Icon getIcon() {
        return icon;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof MediaSource) {
            return getID().equals(((MediaSource) obj).getID());
        }
        return super.equals(obj);
    }

    @Override
    public final int hashCode() {
        return getID().hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
