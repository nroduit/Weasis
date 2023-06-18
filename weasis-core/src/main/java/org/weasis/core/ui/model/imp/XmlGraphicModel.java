/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.imp;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;

@XmlRootElement(name = "presentation")
public class XmlGraphicModel extends AbstractGraphicModel {

  public XmlGraphicModel() {
    super();
  }

  public XmlGraphicModel(ImageElement img) {
    super(buildReferences(img));
  }

  private static List<ReferencedSeries> buildReferences(ImageElement img) {
    String seriesUUID = (String) img.getTagValue(TagW.get("SeriesInstanceUID"));
    if (seriesUUID == null) {
      seriesUUID = java.util.UUID.randomUUID().toString();
      img.setTag(TagW.get("SeriesInstanceUID"), seriesUUID);
    }

    String uid = (String) img.getTagValue(TagW.get("SOPInstanceUID"));
    if (uid == null) {
      uid = java.util.UUID.randomUUID().toString();
      img.setTag(TagW.get("SOPInstanceUID"), uid);
    }

    List<Integer> frameList = new ArrayList<>(1);
    int frames = img.getMediaReader().getMediaElementNumber();
    if (frames > 1 && img.getKey() instanceof Integer intVal) {
      frameList.add(intVal);
    }

    return Collections.singletonList(
        new ReferencedSeries(
            seriesUUID, Collections.singletonList(new ReferencedImage(uid, frameList))));
  }
}
