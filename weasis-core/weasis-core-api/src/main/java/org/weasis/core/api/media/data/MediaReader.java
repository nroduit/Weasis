/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.weasis.core.api.explorer.model.DataExplorerModel;

public interface MediaReader<E> extends Tagable {

    void reset();

    URI getUri();

    FileCache getFileCache();

    MediaElement<?>[] getMediaElement();

    MediaSeries<? extends MediaElement<E>> getMediaSeries();

    boolean delegate(DataExplorerModel explorerModel);

    MediaElement<E> getPreview();

    E getMediaFragment(MediaElement<E> media) throws Exception;

    int getMediaElementNumber();

    String getMediaFragmentMimeType(Object key);

    Map<TagW, Object> getMediaFragmentTags(Object key);

    void close();

    Codec getCodec();

    String[] getReaderDescription();

    @Override
    Object getTagValue(TagW tag);

    void replaceURI(URI uri);

    boolean buildFile(File ouptut);
}
