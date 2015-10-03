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
package org.weasis.core.ui.editor;

import java.io.File;
import java.net.URI;
import java.util.HashMap;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;

public class DefaultMimeIO<F extends File> implements MediaReader<F> {

    protected URI uri;
    protected final String mimeType;
    private MediaElement<F> mediaElement = null;

    public DefaultMimeIO(URI media, String mimeType) {
        if (media == null) {
            throw new IllegalArgumentException("mediaElement uri is null"); //$NON-NLS-1$
        }
        this.uri = media;
        this.mimeType = mimeType == null ? MimeInspector.UNKNOWN_MIME_TYPE : mimeType;
    }

    @Override
    public F getMediaFragment(MediaElement<F> media) throws Exception {
        if (media != null) {
            return (F) media.getFile();
        }
        return null;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void reset() {

    }

    @Override
    public MediaElement<F> getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement<?>[] getMediaElement() {
        MediaElement<?> element = getSingleImage();
        if (element != null) {
            return new MediaElement[] { element };
        }
        return null;
    }

    @Override
    public MediaSeries<MediaElement<F>> getMediaSeries() {
        MediaSeries<MediaElement<F>> mediaSeries =
            new Series<MediaElement<F>>(TagW.FilePath, this.toString(), TagW.FileName, 1) {

                @Override
                public String getMimeType() {
                    synchronized (this) {
                        for (MediaElement<?> m : medias) {
                            return m.getMimeType();
                        }
                    }
                    return null;
                }

                @Override
                public <T extends MediaElement<?>> void addMedia(T media) {
                    if (media != null) {
                        this.add((MediaElement<F>) media);
                        DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                        if (model != null) {
                            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                                new SeriesEvent(SeriesEvent.Action.AddImage, this, media)));
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
        // TODO Auto-generated method stub
        return 0;
    }

    private MediaElement<F> getSingleImage() {
        if (mediaElement == null) {
            mediaElement = new MediaElement<F>(this, null) {
                @Override
                public void dispose() {
                    // TODO Auto-generated method stub
                }
            };
        }
        return mediaElement;
    }

    @Override
    public String getMediaFragmentMimeType(Object key) {
        return mimeType;
    }

    @Override
    public HashMap<TagW, Object> getMediaFragmentTags(Object key) {
        return new HashMap<TagW, Object>();
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void replaceURI(URI uri) {
        // TODO Auto-generated method stub

    }
}
