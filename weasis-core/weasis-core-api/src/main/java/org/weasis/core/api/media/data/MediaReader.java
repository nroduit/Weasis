/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.opencv.data.PlanarImage;

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
