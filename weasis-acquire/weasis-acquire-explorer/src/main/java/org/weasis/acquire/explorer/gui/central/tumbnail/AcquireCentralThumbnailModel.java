/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.nio.file.Path;

import javax.swing.JList;

import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireCentralThumbnailModel<E extends MediaElement> extends AThumbnailModel<E> {

    public AcquireCentralThumbnailModel(JList<E> list, JIThumbnailCache thumbCache) {
        super(list, thumbCache);
    }

    @Override
    public void setData(Path path) {
        // Only get images from model
    }
}
