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

import com.fasterxml.jackson.annotation.JsonProperty;

public class PresetPoint {
  @JsonProperty(value = "intensity", required = true)
  private int intensity;

  @JsonProperty(value = "opacity", required = true)
  private float opacity;

  @JsonProperty("red")
  private Float red;

  @JsonProperty("green")
  private Float green;

  @JsonProperty("blue")
  private Float blue;

  @JsonProperty("specular")
  private Float specular;

  @JsonProperty("ambient")
  private Float ambient;

  @JsonProperty("diffuse")
  private Float diffuse;

  public PresetPoint() {
    // Used by Jackson
  }

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

  public static float convertFloat(Float val, float defaultValue) {
    return val == null ? defaultValue : val;
  }
}
