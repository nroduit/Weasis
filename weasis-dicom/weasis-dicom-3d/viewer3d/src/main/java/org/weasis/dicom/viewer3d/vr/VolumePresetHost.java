/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.ui.editor.image.lut.ColorMapEditorHost;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * Editor host of a volume rendering view: previews a map as a preset, histogram from the volume.
 */
public class VolumePresetHost implements ColorMapEditorHost {

  private final View3d view3d;

  public VolumePresetHost(View3d view3d) {
    this.view3d = Objects.requireNonNull(view3d);
  }

  @Override
  public void preview(ByteLut lut) {
    ColorMap map = lut == null ? null : lut.source();
    if (map == null || map.equals(view3d.getVolumePreset().toColorMap())) {
      return;
    }
    Preset listed = listedPreset(map);
    if (listed == null) {
      view3d.setVolumePreset(Preset.preview(map));
      return;
    }
    Optional<ComboItemListener<Preset>> action = presetAction();
    if (action.isPresent() && action.get().getSelectedItem() != listed) {
      action.get().setSelectedItem(listed);
    } else {
      // The combo already holds it (previews bypass the combo): apply it to the view directly
      view3d.setVolumePreset(listed);
    }
  }

  // The catalog entry compiled from that map, if any: keeps the menu selection in sync.
  private static Preset listedPreset(ColorMap map) {
    return presetAction()
        .map(action -> action.getAllItem())
        .flatMap(
            items ->
                Arrays.stream(items)
                    .filter(item -> item instanceof Preset p && p.toColorMap().equals(map))
                    .map(Preset.class::cast)
                    .findFirst())
        .orElse(null);
  }

  @Override
  public void mapsChanged() {
    EventManager.getInstance()
        .getAction(ActionVol.VOL_PRESET)
        .ifPresent(
            action -> {
              Object selected = action.getSelectedItem();
              Object[] existing = action.getAllItem();
              Preset[] presets =
                  Preset.getAllPresets().stream()
                      .map(preset -> sameAs(existing, preset))
                      .toArray(Preset[]::new);
              action.setDataListWithoutTriggerAction(presets);
              if (selected != null && Arrays.asList(presets).contains(selected)) {
                action.setSelectedItemWithoutTriggerAction(selected);
              } else {
                // The shown preset was deleted: back to the modality default
                DicomVolTexture texture = view3d.getVolTexture();
                action.setSelectedItem(
                    Preset.getDefaultPreset(texture == null ? null : texture.getModality()));
              }
            });
  }

  // Keeps the listed instance, and its GPU textures, when the map behind it did not change.
  private static Preset sameAs(Object[] existing, Preset preset) {
    return Arrays.stream(existing)
        .filter(item -> item instanceof Preset p && p.toColorMap().equals(preset.toColorMap()))
        .map(Preset.class::cast)
        .findFirst()
        .orElse(preset);
  }

  @Override
  public String currentModality() {
    DicomVolTexture texture = view3d.getVolTexture();
    return texture == null ? null : texture.getModality().name();
  }

  @Override
  public ByteLut currentLut() {
    return ColorMapCompiler.toByteLut(view3d.getVolumePreset().toColorMap());
  }

  @Override
  public Optional<double[]> valueRange() {
    DicomVolTexture texture = view3d.getVolTexture();
    return texture == null
        ? Optional.empty()
        : Optional.of(new double[] {texture.getLevelMin(), texture.getLevelMax()});
  }

  @Override
  public double[] histogram(double min, double max, int bins) {
    DicomVolTexture texture = view3d.getVolTexture();
    Volume<?, ?> volume = texture == null ? null : texture.getVolume();
    if (volume == null || bins < 1 || !(max > min)) {
      return null;
    }
    // Re-binned from the histogram cached per volume: the editor asks on every range change.
    return VolumeHistogram.fullRange(volume).rebin(min, max, bins).counts();
  }

  /** Cost of a map on the loaded volume, or null without a volume. */
  public static PresetCost cost(View3d view3d, ColorMap map) {
    DicomVolTexture texture = view3d.getVolTexture();
    Volume<?, ?> volume = texture == null ? null : texture.getVolume();
    return volume == null ? null : PresetCost.estimate(map, VolumeHistogram.fullRange(volume));
  }

  private static Optional<ComboItemListener<Preset>> presetAction() {
    return EventManager.getInstance().getAction(ActionVol.VOL_PRESET);
  }
}
