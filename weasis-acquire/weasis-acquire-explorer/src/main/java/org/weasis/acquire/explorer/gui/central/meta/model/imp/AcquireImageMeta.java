/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.model.imp;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;

public class AcquireImageMeta extends AcquireMetadataTableModel {

  private static final TagW[] TAGS_TO_DISPLAY =
      getTags(
          "weasis.acquire.meta.image.display", "ImageComments,ContentDate,ContentTime"); // NON-NLS
  private static final TagW[] TAGS_EDITABLE =
      getTags("weasis.acquire.meta.image.edit", "ImageComments,ContentDate,ContentTime"); // NON-NLS
  private static final TagW[] TAGS_TO_PUBLISH =
      getTags("weasis.acquire.meta.image.required", "ContentDate"); // NON-NLS

  public AcquireImageMeta(AcquireImageInfo imageInfo) {
    super(
        (imageInfo == null) ? null : imageInfo.getImage(),
        TAGS_TO_DISPLAY,
        TAGS_EDITABLE,
        TAGS_TO_PUBLISH);
  }

  public static boolean isPublishable(TagReadable tagMaps) {
    return hasNonNullValues(TAGS_TO_PUBLISH, tagMaps);
  }
}
