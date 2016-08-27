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
package org.weasis.base.explorer.list.impl;

import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class JIThumbnailListPane<E extends MediaElement> extends AThumbnailListPane<E>
    implements IThumbnailListPane<E> {

    public JIThumbnailListPane() {
        super(new JIThumbnailList<E>());
    }

}
