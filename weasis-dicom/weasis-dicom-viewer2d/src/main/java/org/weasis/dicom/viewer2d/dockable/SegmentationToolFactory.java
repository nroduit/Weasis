/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.dockable;

import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.docking.ExtToolFactory;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class SegmentationToolFactory extends ExtToolFactory<DicomImageElement> {

  public SegmentationToolFactory() {
    super(SegmentationTool.BUTTON_NAME);
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof SegmentationTool;
  }

  @Override
  protected ImageViewerEventManager<DicomImageElement> getImageViewerEventManager() {
    return EventManager.getInstance();
  }

  @Override
  protected boolean isCompatible(Map<String, Object> properties) {
    Object val = null;
    if (properties != null) {
      val = properties.get(MediaSeries.class.getName());
    }
    if (val instanceof MediaSeries<?> mediaSeries) {
      String seriesUID = TagD.getTagValue(mediaSeries, Tag.SeriesInstanceUID, String.class);
      if (StringUtil.hasText(seriesUID)) {
        Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
        if (list != null && !list.isEmpty()) {
          return HiddenSeriesManager.hasHiddenElementsFromSeries(
              SegSpecialElement.class, list.toArray(new String[0]));
        }
      }
    }
    return false;
  }

  @Override
  protected Insertable getInstance(Map<String, Object> properties) {
    return new SegmentationTool();
  }
}
