/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.Hashtable;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.docking.ExtToolFactory;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.dockable.DisplayTool;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.dicom.viewer2d.View2dContainer=true"})
public class RtDisplayToolFactory extends ExtToolFactory<DicomImageElement> {

  public RtDisplayToolFactory() {
    super(DisplayTool.BUTTON_NAME);
  }

  @Override
  protected ImageViewerEventManager<DicomImageElement> getImageViewerEventManager() {
    return EventManager.getInstance();
  }

  @Override
  protected boolean isCompatible(Hashtable<String, Object> properties) {
    Object val = null;
    if (properties != null) {
      val = properties.get(MediaSeries.class.getName());
    }
    if (val instanceof MediaSeries<?> mediaSeries) {
      return RtDisplayTool.isCtLinkedRT(mediaSeries);
    }
    return false;
  }

  @Override
  protected Insertable getInstance(Hashtable<String, Object> properties) {
    return new RtDisplayTool();
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof RtDisplayTool;
  }
}
