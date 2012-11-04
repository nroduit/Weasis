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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class DefaultMimeIO implements MediaReader<File> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMimeIO.class);

    protected URI uri;
    protected final String mimeType;
    private MediaElement media = null;

    public DefaultMimeIO(URI media, String mimeType) {
        if (media == null) {
            throw new IllegalArgumentException("media uri is null"); //$NON-NLS-1$
        }
        this.uri = media;
        this.mimeType = mimeType == null ? MimeInspector.UNKNOWN_MIME_TYPE : mimeType;
    }

    @Override
    public File getMediaFragment(MediaElement media) throws Exception {
        if (media != null) {
            return media.getFile();
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
    public MediaElement<File> getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
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
            new Series<MediaElement>(TagW.FilePath, this.toString(), TagW.FileName, 1) {

                @Override
                public void addMedia(MediaElement media) {
                    if (media instanceof MediaElement) {
                        this.add(media);
                        DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                        if (model != null) {
                            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                                new SeriesEvent(SeriesEvent.Action.AddImage, this, media)));
                        }
                    }
                }

                @Override
                public String getMimeType() {
                    synchronized (this) {
                        for (MediaElement m : medias) {
                            return m.getMimeType();
                        }
                    }
                    return null;
                }
            };
        mediaSeries.add(getSingleImage());
        mediaSeries.setTag(TagW.FileName, media.getName());
        return mediaSeries;
    }

    @Override
    public int getMediaElementNumber() {
        // TODO Auto-generated method stub
        return 0;
    }

    private MediaElement<File> getSingleImage() {
        if (media == null) {
            media = new MediaElement<File>(this, null) {
                @Override
                public void dispose() {
                    // TODO Auto-generated method stub
                }
            };
        }
        return media;
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
