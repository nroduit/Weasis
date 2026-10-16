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

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewStateAdapter;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * Ids of the DICOM viewer for {@link org.weasis.core.ui.editor.image.ViewState}: configured
 * window/level presets by id (a preset the image itself carries by a slug of its name, as the
 * {@code view2d:preset} verb names it) and the stack orders of {@link SortSeriesStack}.
 */
final class DicomViewStateAdapter implements ViewStateAdapter<DicomImageElement> {

  static final DicomViewStateAdapter INSTANCE = new DicomViewStateAdapter();

  private DicomViewStateAdapter() {}

  @Override
  public Optional<String> presetId(Object preset) {
    return preset instanceof PresetWindowLevel p ? Optional.of(idOf(p)) : Optional.empty();
  }

  @Override
  public Optional<Object> preset(ViewCanvas<DicomImageElement> view, String id) {
    DicomImageElement image = view.getImage();
    if (image == null || id == null) {
      return Optional.empty();
    }
    WlPresentation wlp =
        view.getDisplayOpManager().getNode(WindowOp.OP_NAME).stream()
            .filter(WindowOp.class::isInstance)
            .map(node -> ((WindowOp) node).getWlPresentation())
            .findFirst()
            .orElse(null);
    return image.getPresetList(wlp).stream()
        .filter(p -> id.equalsIgnoreCase(idOf(p)) || id.equalsIgnoreCase(p.getName()))
        .findFirst()
        .map(Object.class::cast);
  }

  @Override
  public Optional<String> sortKey(Comparator<?> comparator) {
    return SortSeriesStack.keyOf(comparator);
  }

  @Override
  public Optional<Comparator<DicomImageElement>> sort(String key) {
    return SortSeriesStack.fromKey(key).map(Comparator.class::cast);
  }

  /** The configured id, or one derived from the name for a preset the image itself carries. */
  static String idOf(PresetWindowLevel preset) {
    if (StringUtil.hasText(preset.getId())) {
      return preset.getId();
    }
    String slug = preset.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    return slug.replaceAll("^-+|-+$", "");
  }
}
