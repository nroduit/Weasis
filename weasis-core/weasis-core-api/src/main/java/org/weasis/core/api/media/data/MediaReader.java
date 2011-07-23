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

import java.net.URI;
import java.util.HashMap;

import org.weasis.core.api.explorer.model.DataExplorerModel;

public interface MediaReader<E> {

    void reset();

    URI getUri();

    MediaElement[] getMediaElement();

    MediaSeries getMediaSeries();

    boolean delegate(DataExplorerModel explorerModel);

    MediaElement<E> getPreview();

    E getMediaFragment(MediaElement<E> media) throws Exception;

    int getMediaElementNumber();

    URI getMediaFragmentURI(Object key);

    String getMediaFragmentMimeType(Object key);

    HashMap<TagW, Object> getMediaFragmentTags(Object key);

    void close();

    Codec getCodec();

    String[] getReaderDescription();

    Object getTagValue(TagW tag);

    void replaceURI(URI uri);
}
