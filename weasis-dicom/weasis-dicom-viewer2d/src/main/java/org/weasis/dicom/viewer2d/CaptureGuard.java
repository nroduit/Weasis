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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.export.CaptureScope;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PixelAttestation;
import org.weasis.dicom.codec.PixelPrivacy;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.TagD;

/**
 * The rule of the pixels for a remote capture: an image that may carry burned-in identification
 * leaves the workstation only redacted or reviewed. A view capture checks its image, a layout the
 * panes of the view, a window every pane of every view, since all of them end up in the file.
 */
public final class CaptureGuard {
  private CaptureGuard() {}

  /** Refuses a remote capture of a pane whose image needs a review and has none. */
  public static void requireCapturable(ViewCanvas<?> view) {
    requireCapturable(List.of(view));
  }

  /** The panes a scope shows, for the rule above. */
  public static List<ViewCanvas<?>> panesOf(
      CaptureScope scope, ViewCanvas<?> view, ImageViewerPlugin<?> container) {
    List<ViewCanvas<?>> panes = new ArrayList<>();
    switch (scope) {
      case VIEW -> {
        if (view != null) {
          panes.add(view);
        }
      }
      case CONTAINER -> {
        if (container != null) {
          panes.addAll(container.getImagePanels());
        }
      }
      case APPLICATION_WINDOW -> {
        for (ViewerPlugin<?> plugin : GuiUtils.getUICore().getViewerPlugins()) {
          if (plugin instanceof ImageViewerPlugin<?> viewer) {
            panes.addAll(viewer.getImagePanels());
          }
        }
      }
    }
    return panes;
  }

  /**
   * Refuses a remote capture when any of the panes shows an unreviewed image with burned-in text.
   */
  public static void requireCapturable(Collection<? extends ViewCanvas<?>> panes) {
    if (!CommandContext.current().isRemote()) {
      return;
    }
    for (ViewCanvas<?> pane : panes) {
      ImageElement image = pane.getImage();
      if (!(image instanceof DicomImageElement dicom)) {
        continue;
      }
      String modality = TagD.getTagValue(dicom, Tag.Modality, String.class);
      if (PixelPrivacy.requiresReview(dicom, modality)
          && !Redaction.isRedacted(dicom, pane.getSeries())
          && !PixelAttestation.covers(dicom, pane.getSeries())) {
        throw new IllegalStateException(
            "An image of the capture may carry burned-in identification; apply a redaction, or"
                + " have a person review it (redact:attest), before capturing it remotely");
      }
    }
  }
}
