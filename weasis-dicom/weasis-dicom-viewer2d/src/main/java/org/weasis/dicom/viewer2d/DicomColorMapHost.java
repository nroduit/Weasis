/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.lut.ColorMapFormat;
import org.weasis.core.ui.editor.image.lut.ColorMapFormats;
import org.weasis.core.ui.editor.image.lut.ViewerColorMapHost;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomColorPalette;

/** Editor host of the DICOM 2D viewer: knows the modality and reads and writes DICOM palettes. */
public class DicomColorMapHost extends ViewerColorMapHost<DicomImageElement> {

  public DicomColorMapHost(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
  }

  @Override
  public String currentModality() {
    ViewCanvas<DicomImageElement> view = eventManager.getSelectedViewPane();
    DicomImageElement image = view == null ? null : view.getImage();
    return image == null ? null : TagD.getTagValue(image, Tag.Modality, String.class);
  }

  @Override
  public List<ColorMapFormat> formats() {
    return List.of(ColorMapFormats.JSON, DicomColorPalette.FORMAT, ColorMapFormats.TXT);
  }
}
