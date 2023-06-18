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

import java.util.Objects;
import org.weasis.core.api.media.data.ImageElement;

public class ShadingOptions {
  private final RenderingLayer<?> renderingLayer;
  private float ambient;
  private float diffuse;
  private float specular;
  private float specularPower;

  public ShadingOptions(RenderingLayer<?> renderingLayer) {
    this(renderingLayer, 0.2f, 0.9f, 0.2f, 1f);
  }

  public <E extends ImageElement> ShadingOptions(
      RenderingLayer<?> renderingLayer,
      float ambient,
      float diffuse,
      float specular,
      float specularPower) {
    this.renderingLayer = Objects.requireNonNull(renderingLayer);
    this.ambient = ambient;
    this.diffuse = diffuse;
    this.specular = specular;
    this.specularPower = specularPower;
  }

  public float getSpecularPower() {
    return specularPower;
  }

  public void setSpecularPower(float specularPower) {
    if (this.specularPower != specularPower) {
      this.specularPower = specularPower;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getSpecular() {
    return specular;
  }

  public void setSpecular(float specular) {
    if (this.specular != specular) {
      this.specular = specular;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getAmbient() {
    return ambient;
  }

  public void setAmbient(float ambient) {
    if (this.ambient != ambient) {
      this.ambient = ambient;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getDiffuse() {
    return diffuse;
  }

  public void setDiffuse(float diffuse) {
    if (this.diffuse != diffuse) {
      this.diffuse = diffuse;
      renderingLayer.fireLayerChanged();
    }
  }
}
