package org.weasis.acquire.explorer.media;

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
        if (id == null || id.equals("")) {
            throw new IllegalArgumentException();
        }
        this.id = id;
    }

    final public String getID() {
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
    final public boolean equals(Object obj) {
        if (obj instanceof MediaSource) {
            return getID().equals(((MediaSource) obj).getID());
        }
        return super.equals(obj);
    }

    @Override
    final public int hashCode() {
        return getID().hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
