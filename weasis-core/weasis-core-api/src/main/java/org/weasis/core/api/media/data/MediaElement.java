/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;

import org.weasis.core.api.Messages;

public abstract class MediaElement<E> {

    // Metadata of the media
    protected final HashMap<TagElement, Object> tags;
    // Reader of the media (local or remote)
    protected final MediaReader<E> mediaIO;
    // Key to identify the media (the URI passed to the Reader can contain several media elements)
    protected final Object key;

    protected boolean localFile;
    protected String name = ""; //$NON-NLS-1$
    protected File file;
    private transient boolean loading = false;

    public MediaElement(MediaReader<E> mediaIO, Object key) {
        if (mediaIO == null) {
            throw new IllegalArgumentException("mediaIO is null"); //$NON-NLS-1$
        }
        this.mediaIO = mediaIO;
        this.key = key;
        HashMap<TagElement, Object> t = mediaIO.getMediaFragmentTags(key);
        this.tags = t == null ? new HashMap<TagElement, Object>() : t;
        URI uri = mediaIO.getMediaFragmentURI(key);
        if (uri == null) {
            localFile = false;
        } else {
            localFile = uri.toString().startsWith("file:/") ? true : false; //$NON-NLS-1$
            if (localFile) {
                file = new File(uri);
                name = file.getName();
            }
        }
    }

    public MediaReader<E> getMediaReader() {
        return mediaIO;
    }

    public void setTag(TagElement tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public boolean containTagKey(TagElement tag) {
        return tags.containsKey(tag);
    }

    public Object getTagValue(TagElement tag) {
        return tags.get(tag);
    }

    public TagElement getTagElement(int id) {
        Iterator<TagElement> enumVal = tags.keySet().iterator();
        while (enumVal.hasNext()) {
            TagElement e = enumVal.next();
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    public void clearAllTags() {
        tags.clear();
    }

    public boolean isLocalFile() {
        return localFile;
    }

    public abstract void dispose();

    public URI getMediaURI() {
        return mediaIO.getMediaFragmentURI(key);
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public Object getKey() {
        return key;
    }

    public long getLength() {
        if (localFile) {
            return file.length();
        }
        return 0L;
    }

    public long getLastModified() {
        if (localFile) {
            return file.lastModified();
        }
        return 0L;
    }

    public String getMimeType() {
        return mediaIO.getMediaFragmentMimeType(key);
    }

    protected synchronized final boolean setAsLoading() {
        if (!this.loading) {
            return (this.loading = true);
        }
        return false;
    }

    protected synchronized final void setAsLoaded() {
        this.loading = false;
    }

    public synchronized final boolean isLoading() {
        return loading;
    }

}
