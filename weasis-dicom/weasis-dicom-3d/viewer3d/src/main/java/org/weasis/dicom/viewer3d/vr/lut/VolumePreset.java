/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr.lut;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Arrays;
import java.util.List;
import org.weasis.core.api.util.JsonUtil;

public class VolumePreset {
  private String name;
  private String modality;
  private boolean defaultElement;
  public PresetGroup[] groups = new PresetGroup[0];
  private boolean shade;
  private float specularPower;

  public String getName() {
    return name;
  }

  public String getModality() {
    return modality;
  }

  public boolean isDefaultElement() {
    return defaultElement;
  }

  public boolean isShade() {
    return shade;
  }

  public float getSpecularPower() {
    return specularPower;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setModality(String modality) {
    this.modality = modality;
  }

  public void setDefaultElement(boolean defaultElement) {
    this.defaultElement = defaultElement;
  }

  public void setShade(boolean shade) {
    this.shade = shade;
  }

  public void setSpecularPower(float specularPower) {
    this.specularPower = specularPower;
  }

  public void setGroups(PresetGroup[] groups) {
    this.groups = groups;
  }

  public JsonObject toJson() {
    JsonObjectBuilder builder = Json.createObjectBuilder();
    JsonUtil.addIfPresent(builder, "name", name); // NON-NLS
    JsonUtil.addIfPresent(builder, "modality", modality); // NON-NLS
    builder.add("default", defaultElement); // NON-NLS
    builder.add("shade", shade); // NON-NLS
    builder.add("specularPower", JsonUtil.decimal(specularPower)); // NON-NLS
    JsonArrayBuilder array = Json.createArrayBuilder();
    Arrays.stream(groups).map(PresetGroup::toJson).forEach(array::add);
    builder.add("group", array); // NON-NLS
    return builder.build();
  }

  /** Numbers and booleans may be quoted, as they are in the bundled presets. */
  public static VolumePreset fromJson(JsonObject json) {
    VolumePreset preset = new VolumePreset();
    preset.name = json.getString("name", null); // NON-NLS
    preset.modality = json.getString("modality", null); // NON-NLS
    preset.defaultElement = JsonUtil.getBoolean(json, "default", false); // NON-NLS
    preset.shade = JsonUtil.getBoolean(json, "shade", false); // NON-NLS
    preset.specularPower = JsonUtil.getFloat(json, "specularPower", 0f); // NON-NLS
    preset.groups =
        JsonUtil.objects(json.getJsonArray("group")).stream() // NON-NLS
            .map(PresetGroup::fromJson)
            .toArray(PresetGroup[]::new);
    return preset;
  }

  public List<PresetGroup> getGroups() {
    if (groups.length > 0) {
      PresetGroup lastGroup = groups[groups.length - 1];
      if (lastGroup.points.length > 0) {
        PresetPoint lastPt = lastGroup.points[lastGroup.points.length - 1];
        if ("CT".equals(modality) && lastPt.getIntensity() < 3071) {
          PresetPoint pt =
              new PresetPoint(
                  3071,
                  lastPt.getOpacity(),
                  lastPt.getRed(),
                  lastPt.getGreen(),
                  lastPt.getBlue(),
                  lastPt.getSpecular(),
                  lastPt.getAmbient(),
                  lastPt.getDiffuse());
          lastGroup.points = Arrays.copyOf(lastGroup.points, lastGroup.points.length + 1);
          lastGroup.points[lastGroup.points.length - 1] = pt;
        }
      }
    }
    return Arrays.asList(groups);
  }
}
