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
package org.weasis.core.api.media.data;

import java.io.File;
import java.net.URI;
import java.util.Map;

import javax.media.jai.PlanarImage;

import org.weasis.core.api.explorer.model.DataExplorerModel;

public interface MediaReader extends Tagable {

    void reset();

    URI getUri();

    FileCache getFileCache();

    MediaElement[] getMediaElement();

    MediaSeries<MediaElement> getMediaSeries();

    boolean delegate(DataExplorerModel explorerModel);

    MediaElement getPreview();

    PlanarImage getImageFragment(MediaElement media) throws Exception;

    int getMediaElementNumber();

    String getMediaFragmentMimeType();

    Map<TagW, Object> getMediaFragmentTags(Object key);

    void close();

    Codec getCodec();

    String[] getReaderDescription();

    @Override
    Object getTagValue(TagW tag);

    void replaceURI(URI uri);

    boolean buildFile(File ouptut);
}
