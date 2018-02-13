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
package org.weasis.acquire.explorer.gui.list;

import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireThumbnailListPane<E extends MediaElement> extends AThumbnailListPane<E> {

    public AcquireThumbnailListPane(JIThumbnailCache thumbCache) {
        super(new AcquireThumbnailList<E>(thumbCache));
        ((AcquireThumbnailList<E>) getThumbnailList()).setMainPanel(this);

    }

}
