/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.SeriesMenuContributor;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.Messages;

/**
 * The <i>Subtract</i> entry of the series context menu of the explorer: one item per series of the
 * study that can be the mask of the series the menu was opened on.
 */
@Extension
public class SubtractionMenu implements SeriesMenuContributor {

  @Override
  public void contribute(MediaSeries<? extends MediaElement> series, JPopupMenu menu) {
    if (!(series instanceof DicomSeries dicom)) {
      return;
    }
    List<MediaSeries<DicomImageElement>> masks = SubtractionService.compatibleMasks(dicom);
    if (masks.isEmpty()) {
      return;
    }
    JMenu subtract = new JMenu(Messages.getString("SubtractionTool.menu"));
    for (MediaSeries<DicomImageElement> mask : masks) {
      JMenuItem item = new JMenuItem("− " + label(mask));
      item.setToolTipText(label(dicom) + " − " + label(mask));
      item.addActionListener(e -> SubtractionController.subtract(dicom, mask, 1.0));
      subtract.add(item);
    }
    menu.add(subtract);
  }

  static String label(MediaSeries<?> series) {
    String description = TagD.getTagValue(series, Tag.SeriesDescription, String.class);
    Integer number = TagD.getTagValue(series, Tag.SeriesNumber, Integer.class);
    String text = StringUtil.hasText(description) ? description : "";
    if (number != null) {
      text = "[" + number + "] " + text;
    }
    return text.isBlank() ? series.toString() : text.strip();
  }
}
