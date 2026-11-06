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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.Projection;
import org.weasis.core.ui.editor.image.ViewStateAdapter;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.viewer2d.fusion.FusionController;
import org.weasis.dicom.viewer2d.fusion.FusionOp;
import org.weasis.dicom.viewer2d.fusion.FusionState;
import org.weasis.dicom.viewer2d.fusion.FusionWindow;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.opencv.op.lut.ByteLut;
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

  /** A presentation state applied to the image sets its own zoom, orientation and rendering. */
  @Override
  public boolean ownsPresentation(ViewCanvas<DicomImageElement> view) {
    return view.getActionValue(ActionW.PR_STATE.cmd()) instanceof PRSpecialElement;
  }

  @Override
  public Optional<String> sortKey(Comparator<?> comparator) {
    return SortSeriesStack.keyOf(comparator);
  }

  @Override
  public Optional<Comparator<DicomImageElement>> sort(String key) {
    return SortSeriesStack.fromKey(key).map(Comparator.class::cast);
  }

  @Override
  public Optional<ViewState.Blend> blend(ViewCanvas<DicomImageElement> view) {
    FusionState fusion = FusionController.snapshot(view);
    if (fusion == null) {
      return Optional.empty();
    }
    MediaSeries<DicomImageElement> overlay = fusion.series();
    Object id = overlay.getTagValue(overlay.getTagID());
    JsonObjectBuilder params =
        Json.createObjectBuilder()
            .add("baseOpacity", fusion.baseOpacity()) // NON-NLS
            .add("overlayOpacity", fusion.overlayOpacity()); // NON-NLS
    if (fusion.lut() != null) {
      params.add(
          "colorMap", // NON-NLS
          fusion.lut().source() != null ? fusion.lut().source().id() : fusion.lut().name());
    }
    FusionWindow window = fusion.window();
    if (window != null) {
      JsonObjectBuilder w =
          Json.createObjectBuilder()
              .add("min", window.min()) // NON-NLS
              .add("max", window.max()) // NON-NLS
              .add("factor", window.displayFactor()); // NON-NLS
      if (window.displayUnit() != null) {
        w.add("unit", window.displayUnit()); // NON-NLS
      }
      params.add("window", w); // NON-NLS
    }
    return Optional.of(new ViewState.Blend(id == null ? null : id.toString(), params.build()));
  }

  /**
   * Blends the overlay with the stored map, window and opacities; the fusion volume is built again
   * for the pane, as when the user picks the overlay.
   */
  @Override
  public void blend(
      ViewCanvas<DicomImageElement> view, MediaSeries<DicomImageElement> overlay, JsonObject p) {
    ByteLut lut =
        Optional.ofNullable(p.getString("colorMap", null)) // NON-NLS
            .flatMap(ViewState::colorMapOf)
            .orElse(null);
    JsonObject w = p.getJsonObject("window"); // NON-NLS
    FusionWindow window =
        w == null
            ? null
            : new FusionWindow(
                w.getJsonNumber("min").doubleValue(), // NON-NLS
                w.getJsonNumber("max").doubleValue(), // NON-NLS
                w.containsKey("factor") ? w.getJsonNumber("factor").doubleValue() : 1, // NON-NLS
                w.getString("unit", null)); // NON-NLS
    double base =
        p.containsKey("baseOpacity") // NON-NLS
            ? p.getJsonNumber("baseOpacity").doubleValue() // NON-NLS
            : FusionOp.DEFAULT_BASE_OPACITY;
    double over =
        p.containsKey("overlayOpacity") // NON-NLS
            ? p.getJsonNumber("overlayOpacity").doubleValue() // NON-NLS
            : FusionOp.DEFAULT_OVERLAY_OPACITY;
    FusionController.applyState(
        List.of(view), new FusionState(overlay, lut, window, base, over, null));
  }

  /** A MIP view projects: its type and the slab, in images and in mm when the spacing is known. */
  @Override
  public Optional<Projection> projection(ViewCanvas<DicomImageElement> view) {
    if (!(view instanceof MipView mip)
        || !(mip.getActionValue(MipView.MIP.cmd()) instanceof MipView.Type type)
        || type == MipView.Type.NONE
        || !(mip.getActionValue(MipView.MIP_THICKNESS.cmd()) instanceof Integer extent)) {
      return Optional.empty();
    }
    double mm = MipView.slabThickness(mip.getSeries(), extent);
    return Optional.of(new Projection(type.name(), extent, mm > 0 ? mm : null));
  }

  /**
   * Puts a MIP view in the pane when the view is not one yet and builds the slab; a series too
   * short to project, or a pane outside a viewer, keeps the view as it is.
   */
  @Override
  public ViewCanvas<DicomImageElement> project(
      ViewCanvas<DicomImageElement> view, Projection projection) {
    MediaSeries<DicomImageElement> series = view.getSeries();
    MipView.Type type = projectionType(projection.type());
    if (series == null
        || type == MipView.Type.NONE
        || series.size(null) < DefaultView2d.MINIMAL_IMAGES_FOR_3D) {
      return view;
    }
    MipView mip;
    if (view instanceof MipView m) {
      mip = m;
    } else {
      @SuppressWarnings("unchecked")
      ImageViewerPlugin<DicomImageElement> container =
          (ImageViewerPlugin<DicomImageElement>)
              SwingUtilities.getAncestorOfClass(ImageViewerPlugin.class, view.getJComponent());
      if (container == null) {
        return view;
      }
      mip = MipView.swapIn(EventManager.getInstance(), container, view);
      if (mip == null) {
        return view;
      }
    }
    int extent =
        projection.extent() != null
            ? Math.max(1, projection.extent())
            : MipView.slabExtent(
                series, projection.thickness() == null ? 0 : projection.thickness());
    mip.setActionsInView(MipView.MIP.cmd(), type);
    mip.setActionsInView(MipView.MIP_THICKNESS.cmd(), extent);
    MipView.buildMip(mip);
    return mip;
  }

  private static MipView.Type projectionType(String type) {
    try {
      return MipView.Type.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return MipView.Type.MAX;
    }
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
