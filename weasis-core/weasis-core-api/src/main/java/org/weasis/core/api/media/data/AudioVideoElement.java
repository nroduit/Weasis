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

public class AudioVideoElement extends MediaElement<URI> {

    public AudioVideoElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
    }

    @Override
    public void dispose() {
        if (mediaIO != null) {
            mediaIO.close();
        }
    }

}
