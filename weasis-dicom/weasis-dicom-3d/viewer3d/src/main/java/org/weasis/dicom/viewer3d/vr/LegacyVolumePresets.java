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

import jakarta.json.JsonObject;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.Material;

/**
 * Reader of the volume preset format used before the color map schema ({@code group} / {@code
 * point} objects with intensity, opacity, rgb and lighting). Kept to migrate a user's old custom
 * preset file into the registry.
 */
public final class LegacyVolumePresets {

  private static final String HU = "HU"; // NON-NLS

  private LegacyVolumePresets() {}

  /** The presets of the file, with ids made unique so none is lost when saved by id. */
  public static List<ColorMap> read(Path file) throws IOException {
    var maps = new ArrayList<ColorMap>();
    var ids = new HashSet<String>();
    for (JsonObject preset : JsonUtil.objects(JsonUtil.readArray(file))) {
      ColorMap map = toColorMap(preset);
      String id = map.id();
      for (int n = 2; !ids.add(id); n++) {
        id = map.id() + "-" + n;
      }
      maps.add(id.equals(map.id()) ? map : map.toBuilder().id(id).build());
    }
    return List.copyOf(maps);
  }

  static ColorMap toColorMap(JsonObject preset) {
    String name = preset.getString("name", "Preset"); // NON-NLS
    Modality modality = Modality.getModality(preset.getString("modality", null)); // NON-NLS
    var stops = new ArrayList<ColorStop>();
    for (JsonObject group : JsonUtil.objects(preset.getJsonArray("group"))) { // NON-NLS
      String label = group.getString("name", null); // NON-NLS
      for (JsonObject point : JsonUtil.objects(group.getJsonArray("point"))) { // NON-NLS
        stops.add(toStop(point, label));
      }
    }
    if (stops.isEmpty()) {
      throw new IllegalArgumentException("Preset without points: " + name);
    }
    double min = stops.stream().mapToDouble(ColorStop::position).min().orElseThrow();
    double max =
        Math.max(stops.stream().mapToDouble(ColorStop::position).max().orElseThrow(), min + 1);
    return ColorMap.builder(name)
        .id(legacyId(name, modality))
        .type(ColorMapType.TRANSFER)
        .category("Volume") // NON-NLS
        .modalities(modality == Modality.DEFAULT ? Set.of() : Set.of(modality.name()))
        .defaultForModality(JsonUtil.getBoolean(preset, "default", false)) // NON-NLS
        .domain(ColorMapDomain.absolute(modality == Modality.CT ? HU : null, min, max))
        .bits(ColorMap.MAX_BITS)
        .stops(stops)
        .lighting(
            new Lighting(
                JsonUtil.getBoolean(preset, "shade", true), // NON-NLS
                JsonUtil.getFloat(
                    preset, "specularPower", Lighting.DEFAULT.specularPower()))) // NON-NLS
        .build();
  }

  // The old editor keyed presets by name and modality; both go into the id.
  private static String legacyId(String name, Modality modality) {
    String scope =
        modality == Modality.DEFAULT ? "" : modality.name().toLowerCase(Locale.ROOT) + ".";
    return "user.vr." + scope + ColorMap.slug(name); // NON-NLS
  }

  private static ColorStop toStop(JsonObject point, String group) {
    Float red = JsonUtil.getFloat(point, "red"); // NON-NLS
    Color color =
        red == null
            ? null
            : new Color(
                clamp(red),
                clamp(JsonUtil.getFloat(point, "green", 0f)), // NON-NLS
                clamp(JsonUtil.getFloat(point, "blue", 0f))); // NON-NLS
    Float ambient = JsonUtil.getFloat(point, "ambient"); // NON-NLS
    Material material =
        ambient == null
            ? null
            : new Material(
                clamp(ambient),
                clamp(JsonUtil.getFloat(point, "diffuse", Material.DEFAULT.diffuse())), // NON-NLS
                clamp(
                    JsonUtil.getFloat(point, "specular", Material.DEFAULT.specular()))); // NON-NLS
    return new ColorStop(
        JsonUtil.getInt(point, "intensity", 0), // NON-NLS
        color,
        clamp(JsonUtil.getFloat(point, "opacity", 1f)), // NON-NLS
        material,
        group);
  }

  private static float clamp(float value) {
    return Math.clamp(value, 0f, 1f);
  }
}
