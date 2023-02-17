/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VolumePreset {
  @JsonProperty("name")
  private String name;

  @JsonProperty("modality")
  private String modality;

  @JsonProperty("default")
  private boolean defaultElement;

  @JsonProperty("colorTransfer")
  private Float[] colorTransfer;

  @JsonProperty("scalarOpacity")
  private Float[] scalarOpacity;

  @JsonProperty("shade")
  private boolean shade;

  @JsonProperty("specularPower")
  private float specularPower;

  @JsonProperty("specular")
  private float specular;

  @JsonProperty("ambient")
  private float ambient;

  @JsonProperty("diffuse")
  private float diffuse;

  public String getName() {
    return name;
  }

  public String getModality() {
    return modality;
  }

  public boolean isDefaultElement() {
    return defaultElement;
  }

  public Float[] getColorTransfer() {
    return colorTransfer;
  }

  public Float[] getScalarOpacity() {
    return scalarOpacity;
  }

  public boolean isShade() {
    return shade;
  }

  public float getSpecularPower() {
    return specularPower;
  }

  public float getSpecular() {
    return specular;
  }

  public float getAmbient() {
    return ambient;
  }

  public float getDiffuse() {
    return diffuse;
  }
}
