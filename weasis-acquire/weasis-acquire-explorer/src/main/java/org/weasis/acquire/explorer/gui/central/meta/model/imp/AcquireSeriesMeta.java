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

import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;

public class AcquireSeriesMeta extends AcquireMetadataTableModel {

  private static final TagW[] TAGS_TO_DISPLAY =
      getTags(
          "weasis.acquire.meta.series.display",
          "Modality,OperatorsName,ReferringPhysicianName,BodyPartExamined,SeriesDescription"); // NON-NLS
  private static final TagW[] TAGS_EDITABLE =
      getTags(
          "weasis.acquire.meta.series.edit",
          "ReferringPhysicianName,BodyPartExamined,SeriesDescription"); // NON-NLS
  private static final TagW[] TAGS_TO_PUBLISH =
      getTags("weasis.acquire.meta.series.required", "Modality,SeriesDescription"); // NON-NLS

  public AcquireSeriesMeta(SeriesGroup seriesGroup) {
    super(seriesGroup, TAGS_TO_DISPLAY, TAGS_EDITABLE, TAGS_TO_PUBLISH);
  }

  public static boolean isPublishable(TagReadable tagMaps) {
    return hasNonNullValues(TAGS_TO_PUBLISH, tagMaps);
  }
}
