/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

public class DefaultMimeIO implements MediaReader {

    private static final TagView defaultTagView = new TagView(TagW.FileName);

    protected URI uri;
    protected final String mimeType;
    private MediaElement mediaElement = null;
    private final FileCache fileCache;

    public DefaultMimeIO(URI media, String mimeType) {
        this.uri = Objects.requireNonNull(media);
        this.fileCache = new FileCache(this);
        this.mimeType = mimeType == null ? MimeInspector.UNKNOWN_MIME_TYPE : mimeType;
    }

    @Override
    public PlanarImage getImageFragment(MediaElement media) throws Exception {
        return null;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void reset() {
        // Do nothing
    }

    @Override
    public MediaElement getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        MediaElement element = getSingleImage();
        if (element != null) {
            return new MediaElement[] { element };
        }
        return null;
    }

    @Override
    public MediaSeries<MediaElement> getMediaSeries() {

        MediaSeries<MediaElement> mediaSeries =
            new Series<MediaElement>(TagW.FilePath, this.toString(), defaultTagView, 1) {

                @Override
                public String getMimeType() {
                    if (!medias.isEmpty()) {
                        synchronized (this) {
                            MediaElement img = medias.get(0);
                            if (img != null) {
                                return img.getMimeType();
                            }
                        }
                    }
                    return null;
                }

                @Override
                public void addMedia(MediaElement media) {
                    if (media != null) {
                        this.add(media);
                        DataExplorerModel model = (DataExplorerModel) this.getTagValue(TagW.ExplorerModel);
                        if (model != null) {
                            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null,
                                new SeriesEvent(SeriesEvent.Action.ADD_IMAGE, this, media)));
                        }
                    }
                }
            };
        mediaSeries.add(getSingleImage());
        mediaSeries.setTag(TagW.FileName, mediaElement.getName());
        return mediaSeries;
    }

    @Override
    public int getMediaElementNumber() {
        return 1;
    }

    private MediaElement getSingleImage() {
        if (mediaElement == null) {
            mediaElement = new MediaElement(this, null);
        }
        return mediaElement;
    }

    @Override
    public String getMediaFragmentMimeType() {
        return mimeType;
    }

    @Override
    public Map<TagW, Object> getMediaFragmentTags(Object key) {
        return new HashMap<>();
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    public Codec getCodec() {
        return null;
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "Default mime type reader " }; //$NON-NLS-1$
    }

    @Override
    public Object getTagValue(TagW tag) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceURI(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTag(TagW tag, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return false;
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileCache getFileCache() {
        return fileCache;
    }

    @Override
    public boolean buildFile(File ouptut) {
        return false;
    }
}
