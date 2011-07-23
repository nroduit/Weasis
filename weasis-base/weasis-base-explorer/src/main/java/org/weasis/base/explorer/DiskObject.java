package org.weasis.base.explorer;

import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Comparator;

import javax.swing.filechooser.FileSystemView;

public final class DiskObject implements Comparator<DiskObject>, Serializable {

    /**
	 *
	 */
    private static final long serialVersionUID = 5247091367179832780L;

    private static final String osName = System.getProperty("os.name").toLowerCase();

    private static final Format formatter = new SimpleDateFormat("yyyy/MMM/dd");

    public static final String MIMETYPE_JPEG = "image/jpeg";
    public static final String MIMETYPE_GIF = "image/gif";
    public static final String MIMETYPE_PNG = "image/png";
    public static final String MIMETYPE_TIFF = "image/tiff";

    private String name;
    private String type;
    private String path;
    private String uid;

    // private File file = null;
    private String suffix = null;

    private int width = -1;
    private int height = -1;
    private int orientation = -1;

    private long length = 0;
    private long lastModified = 0;

    protected boolean validated = false;

    boolean loading = false;

    protected URI uri;

    protected boolean isDir;
    protected boolean exists;

    protected String absolutePath;
    protected String displayName;

    public void update(final File file) {
        uri = file.toURI();
        length = file.length();
        lastModified = file.lastModified();
        isDir = file.isDirectory();
        exists = file.exists();
        absolutePath = file.getAbsolutePath();
        this.path = (absolutePath != null ? JIUtility.portablePath(absolutePath) : null);

        try {
            if (!file.getName().equals("hiberfil.sys") && file.exists()) {
                // type = FileSystemView.getFileSystemView().getSystemTypeDescription(file);
                displayName = FileSystemView.getFileSystemView().getSystemDisplayName(file);
            }
        } catch (final Exception e) {
            // e.printStackTrace();
            type = "";
            displayName = "";
        }

        name = file.getName().length() > 0 ? file.getName() : file.toURI().getPath();
        if (osName.startsWith("win") && (name.indexOf('/') == 0)) {
            name = name.substring(1);
        }
    }

    public String getMimeType() {
        if (getSuffix().equals("jpg") || getSuffix().equals("jpeg"))
            return MIMETYPE_JPEG;
        if (getSuffix().equals("gif"))
            return MIMETYPE_GIF;
        if (getSuffix().equals("png"))
            return MIMETYPE_PNG;
        if (getSuffix().equals("tiff") || getSuffix().equals("tif"))
            return MIMETYPE_TIFF;
        return null;
    }

    public String getShortDate() {
        final Format formatter = new SimpleDateFormat("dd/MM/yy hh:mm a");
        return formatter.format(new java.util.Date(this.lastModified));
    }

    public String getDim() {
        if ((this.width > 0) && (this.height > 0))
            return this.width + " x " + this.height;
        return "";
    }

    public Dimension getDimension() {
        return new Dimension(this.width, this.height);
    }

    public String getSize() {
        return JIUtility.length2KB(this.length);
    }

    @Override
    public String toString() {
        return this.name;
    }

    public File getFile() {
        if (absolutePath != null)
            return new File(absolutePath);
        else
            return new File(path);

    }

    @Override
    public int compare(final DiskObject a, final DiskObject b) {
        return a.getFile().compareTo(b.getFile());
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof DiskObject)
            return (compare(this, (DiskObject) obj) == 0) ? true : false;
        return false;
    }

    public String[] getDateCategories() {
        return formatter.format(new java.util.Date(this.lastModified)).split("/");
    }

    /**
     * @return the suffix
     */
    public synchronized String getSuffix() {
        if (this.suffix == null) {
            this.suffix = JIUtility.suffix(this.name);
        }

        return this.suffix;
    }

    public boolean validate() throws FileNotFoundException, IOException {
        if (!this.validated) {
            if (!getFile().exists())
                throw new FileNotFoundException(getFile().getAbsolutePath());

            if (!getFile().canRead())
                throw new IOException("Cannot read " + getFile().getAbsolutePath());
        }
        return (this.validated = true);
    }

    public synchronized int getOrientationValue() {
        return this.orientation;
    }

    public synchronized void setOrientation(final int orientation) {
        this.orientation = orientation;
    }

    /**
     * @return the uid
     */
    public synchronized String getUid() {
        if ((this.uid == null) || (this.uid.trim().length() < 2)) {
            // this.uid = JIThumbnailService.getInstance().getImageID(this);
        }
        return this.uid;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(final int height) {
        this.height = height;
    }

    public long getLastModified() {
        return this.lastModified;
    }

    public void setLastModified(final long lastModified) {
        this.lastModified = lastModified;
    }

    public long getLength() {
        return this.length;
    }

    public void setLength(final long length) {
        this.length = length;
    }

    public String getName() {
        return (displayName == null ? name : displayName);
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public boolean isValidated() {
        return this.validated;
    }

    public void setValidated(final boolean validated) {
        this.validated = validated;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public void setSuffix(final String suffix) {
        this.suffix = suffix;
    }

    public void setUid(final String uid) {
        this.uid = uid;
    }
}
