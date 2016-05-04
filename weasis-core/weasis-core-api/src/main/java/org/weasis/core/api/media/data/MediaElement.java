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
import java.util.Map;
import java.util.Map.Entry;

import org.weasis.core.api.util.FileUtil;

public abstract class MediaElement<E> implements Tagable {

    // Metadata of the media
    protected final Map<TagW, Object> tags;
    // Reader of the media (local or remote)
    protected final MediaReader<E> mediaIO;
    // Key to identify the media (the URI passed to the Reader can contain several media elements)
    protected final Object key;

    protected boolean localFile;
    protected String name = ""; //$NON-NLS-1$
    protected File file;
    private volatile boolean loading = false;

    public MediaElement(MediaReader<E> mediaIO, Object key) {
        if (mediaIO == null) {
            throw new IllegalArgumentException("mediaIO is null"); //$NON-NLS-1$
        }
        this.mediaIO = mediaIO;
        this.key = key;
        Map<TagW, Object> t = mediaIO.getMediaFragmentTags(key);
        this.tags = t == null ? new HashMap<TagW, Object>() : t;
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

    @Override
    public void setTag(TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

    public TagW getTagElement(int id) {
        Iterator<TagW> enumVal = tags.keySet().iterator();
        while (enumVal.hasNext()) {
            TagW e = enumVal.next();
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (value != null) {
            setTag(tag, value);
        }
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
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

    public boolean saveToFile(File output) {
        return FileUtil.nioCopyFile(file, output);
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

    protected final synchronized boolean setAsLoading() {
        if (!loading) {
            loading = true;
            return loading;
        }
        return false;
    }

    protected final synchronized void setAsLoaded() {
        loading = false;
    }

    public final synchronized boolean isLoading() {
        return loading;
    }

}
