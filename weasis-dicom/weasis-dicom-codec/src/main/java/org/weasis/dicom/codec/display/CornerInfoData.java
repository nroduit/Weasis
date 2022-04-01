/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.util.Arrays;
import org.weasis.core.api.media.data.TagView;

public class CornerInfoData {

  public static final int ELEMENT_NUMBER = 7;
  private final CornerDisplay corner;
  private final TagView[] infos;

  public CornerInfoData(CornerDisplay corner, Modality extendModality) {
    this.corner = corner;
    TagView[] extInfos = null;
    if (extendModality != null) {
      ModalityInfoData modalityInfoData = ModalityView.MODALITY_VIEW_MAP.get(extendModality);
      if (modalityInfoData != null) {
        extInfos = modalityInfoData.getCornerInfo(corner).getInfos();
      }
    }
    this.infos =
        extInfos == null ? new TagView[ELEMENT_NUMBER] : Arrays.copyOf(extInfos, extInfos.length);
  }

  public TagView[] getInfos() {
    return infos;
  }

  public CornerDisplay getCorner() {
    return corner;
  }

  @Override
  public String toString() {
    return corner.toString();
  }
}
