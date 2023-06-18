/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.list.impl;

import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

public class JIThumbnailListPane<E extends MediaElement> extends AThumbnailListPane<E>
    implements IThumbnailListPane<E> {

  public JIThumbnailListPane(JIThumbnailCache thumbCache) {
    super(new DefaultThumbnailList<>(thumbCache));
  }
}
