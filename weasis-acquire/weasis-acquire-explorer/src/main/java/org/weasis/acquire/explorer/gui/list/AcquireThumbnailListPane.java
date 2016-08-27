/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.list;

import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireThumbnailListPane<E extends MediaElement> extends AThumbnailListPane<E> {

    private final ImageGroupPane centralPane;

    public AcquireThumbnailListPane(ImageGroupPane centralPane) {
        super(new AcquireThumbnailList<E>());
        this.centralPane = centralPane;
        ((AcquireThumbnailList<E>) getThumbnailList()).setMainPanel(this);
    }

    public ImageGroupPane getCentralPane() {
        return centralPane;
    }
}
