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

import java.net.URI;
import java.util.HashMap;

import org.weasis.core.api.explorer.model.DataExplorerModel;

public interface MediaReader<E> {

    public void reset();

    public URI getUri();

    public MediaElement[] getMediaElement();

    public MediaSeries getMediaSeries();

    public boolean delegate(DataExplorerModel explorerModel);

    public MediaElement<E> getPreview();

    public E getMediaFragment(MediaElement<E> media) throws Exception;

    public int getMediaElementNumber();

    public URI getMediaFragmentURI(Object key);

    public String getMediaFragmentMimeType(Object key);

    public HashMap<TagW, Object> getMediaFragmentTags(Object key);

    public void close();

    public Codec getCodec();

    public String[] getReaderDescription();

    public Object getTagValue(TagW tag);
}
