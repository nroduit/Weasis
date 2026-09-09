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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.weasis.core.api.util.JsonUtil;

public class PresetPoint {
  private int intensity;
  private float opacity;
  private Float red;
  private Float green;
  private Float blue;
  private Float specular;
  private Float ambient;
  private Float diffuse;

  public PresetPoint(
      int intensity,
      float opacity,
      Float red,
      Float green,
      Float blue,
      Float specular,
      Float ambient,
      Float diffuse) {
    this.intensity = intensity;
    this.opacity = opacity;
    this.red = red;
    this.green = green;
    this.blue = blue;
    this.specular = specular;
    this.ambient = ambient;
    this.diffuse = diffuse;
  }

  public int getIntensity() {
    return intensity;
  }

  public float getOpacity() {
    return opacity;
  }

  public Float getRed() {
    return red;
  }

  public Float getGreen() {
    return green;
  }

  public Float getBlue() {
    return blue;
  }

  public Float getSpecular() {
    return specular;
  }

  public Float getAmbient() {
    return ambient;
  }

  public Float getDiffuse() {
    return diffuse;
  }

  public void setIntensity(int intensity) {
    this.intensity = intensity;
  }

  public void setOpacity(float opacity) {
    this.opacity = opacity;
  }

  public void setRed(Float red) {
    this.red = red;
  }

  public void setGreen(Float green) {
    this.green = green;
  }

  public void setBlue(Float blue) {
    this.blue = blue;
  }

  public void setSpecular(Float specular) {
    this.specular = specular;
  }

  public void setAmbient(Float ambient) {
    this.ambient = ambient;
  }

  public void setDiffuse(Float diffuse) {
    this.diffuse = diffuse;
  }

  public PresetPoint copy() {
    return new PresetPoint(intensity, opacity, red, green, blue, specular, ambient, diffuse);
  }

  public JsonObject toJson() {
    JsonObjectBuilder builder =
        Json.createObjectBuilder()
            .add("intensity", intensity) // NON-NLS
            .add("opacity", JsonUtil.decimal(opacity)); // NON-NLS
    JsonUtil.addIfPresent(builder, "red", red); // NON-NLS
    JsonUtil.addIfPresent(builder, "green", green); // NON-NLS
    JsonUtil.addIfPresent(builder, "blue", blue); // NON-NLS
    JsonUtil.addIfPresent(builder, "specular", specular); // NON-NLS
    JsonUtil.addIfPresent(builder, "ambient", ambient); // NON-NLS
    JsonUtil.addIfPresent(builder, "diffuse", diffuse); // NON-NLS
    return builder.build();
  }

  public static PresetPoint fromJson(JsonObject json) {
    return new PresetPoint(
        JsonUtil.getInt(json, "intensity", 0), // NON-NLS
        JsonUtil.getFloat(json, "opacity", 0f), // NON-NLS
        JsonUtil.getFloat(json, "red"), // NON-NLS
        JsonUtil.getFloat(json, "green"), // NON-NLS
        JsonUtil.getFloat(json, "blue"), // NON-NLS
        JsonUtil.getFloat(json, "specular"), // NON-NLS
        JsonUtil.getFloat(json, "ambient"), // NON-NLS
        JsonUtil.getFloat(json, "diffuse")); // NON-NLS
  }

  public static float convertFloat(Float val, float defaultValue) {
    return val == null ? defaultValue : val;
  }
}
