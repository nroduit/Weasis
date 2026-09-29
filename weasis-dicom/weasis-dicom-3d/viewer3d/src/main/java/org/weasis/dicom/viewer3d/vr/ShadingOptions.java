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
import org.joml.Vector3f;
import org.weasis.core.api.service.WProperties;

/**
 * Per-view lighting parameters: the Blinn-Phong material weights and, for the cinematic mode, the
 * key light direction, the shadow and occlusion strengths and the exposure.
 */
public class ShadingOptions {
  public static final String P_SHADOW_STRENGTH = "volume.cinematic.shadow";
  public static final String P_AO_STRENGTH = "volume.cinematic.occlusion";
  public static final String P_EXPOSURE = "volume.cinematic.exposure";
  public static final String P_LIGHT_AZIMUTH = "volume.cinematic.light.azimuth";
  public static final String P_LIGHT_ELEVATION = "volume.cinematic.light.elevation";
  public static final String P_ENVIRONMENT = "volume.cinematic.environment";
  public static final String P_ENVIRONMENT_STRENGTH = "volume.cinematic.environment.strength";

  public static final float DEFAULT_SHADOW_STRENGTH = 0.7f;
  public static final float DEFAULT_AO_STRENGTH = 0.6f;
  public static final float DEFAULT_EXPOSURE = 1.2f;

  /** Degrees around the vertical axis: negative is from the viewer's left. */
  public static final float DEFAULT_LIGHT_AZIMUTH = -30f;

  /** Degrees above the view direction. */
  public static final float DEFAULT_LIGHT_ELEVATION = 35f;

  public static final EnvironmentMap DEFAULT_ENVIRONMENT = EnvironmentMap.STUDIO;
  public static final float DEFAULT_ENVIRONMENT_STRENGTH = 1f;

  private final RenderingLayer<?> renderingLayer;
  private float ambient;
  private float diffuse;
  private float specular;
  private float specularPower;
  private float shadowStrength = DEFAULT_SHADOW_STRENGTH;
  private float aoStrength = DEFAULT_AO_STRENGTH;
  private float exposure = DEFAULT_EXPOSURE;
  private float lightAzimuth = DEFAULT_LIGHT_AZIMUTH;
  private float lightElevation = DEFAULT_LIGHT_ELEVATION;
  private EnvironmentMap environment = DEFAULT_ENVIRONMENT;
  private float environmentStrength = DEFAULT_ENVIRONMENT_STRENGTH;

  public ShadingOptions(RenderingLayer<?> renderingLayer) {
    this(renderingLayer, 0.2f, 0.9f, 0.2f, 1f);
  }

  public ShadingOptions(
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

  public float getShadowStrength() {
    return shadowStrength;
  }

  public void setShadowStrength(float shadowStrength) {
    if (this.shadowStrength != shadowStrength) {
      this.shadowStrength = shadowStrength;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getAoStrength() {
    return aoStrength;
  }

  public void setAoStrength(float aoStrength) {
    if (this.aoStrength != aoStrength) {
      this.aoStrength = aoStrength;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getExposure() {
    return exposure;
  }

  public void setExposure(float exposure) {
    if (this.exposure != exposure) {
      this.exposure = exposure;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getLightAzimuth() {
    return lightAzimuth;
  }

  public void setLightAzimuth(float lightAzimuth) {
    if (this.lightAzimuth != lightAzimuth) {
      this.lightAzimuth = lightAzimuth;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getLightElevation() {
    return lightElevation;
  }

  public void setLightElevation(float lightElevation) {
    if (this.lightElevation != lightElevation) {
      this.lightElevation = lightElevation;
      renderingLayer.fireLayerChanged();
    }
  }

  public EnvironmentMap getEnvironment() {
    return environment;
  }

  public void setEnvironment(EnvironmentMap environment) {
    Objects.requireNonNull(environment);
    if (this.environment != environment) {
      this.environment = environment;
      renderingLayer.fireLayerChanged();
    }
  }

  public float getEnvironmentStrength() {
    return environmentStrength;
  }

  public void setEnvironmentStrength(float environmentStrength) {
    if (this.environmentStrength != environmentStrength) {
      this.environmentStrength = environmentStrength;
      renderingLayer.fireLayerChanged();
    }
  }

  /**
   * Unit vector towards the key light in view space, where +z points at the viewer: azimuth and
   * elevation of zero give a headlight.
   */
  public Vector3f getKeyLightDirection() {
    double az = Math.toRadians(lightAzimuth);
    double el = Math.toRadians(lightElevation);
    return new Vector3f(
        (float) (Math.cos(el) * Math.sin(az)),
        (float) Math.sin(el),
        (float) (Math.cos(el) * Math.cos(az)));
  }

  /**
   * Loads the cinematic parameters saved by {@link #persistCinematic}, keeping the defaults
   * otherwise.
   */
  public void loadCinematic(WProperties prefs) {
    renderingLayer.setEnableRepaint(false);
    setShadowStrength(prefs.getFloatProperty(P_SHADOW_STRENGTH, DEFAULT_SHADOW_STRENGTH));
    setAoStrength(prefs.getFloatProperty(P_AO_STRENGTH, DEFAULT_AO_STRENGTH));
    setExposure(prefs.getFloatProperty(P_EXPOSURE, DEFAULT_EXPOSURE));
    setLightAzimuth(prefs.getFloatProperty(P_LIGHT_AZIMUTH, DEFAULT_LIGHT_AZIMUTH));
    setLightElevation(prefs.getFloatProperty(P_LIGHT_ELEVATION, DEFAULT_LIGHT_ELEVATION));
    setEnvironment(EnvironmentMap.fromName(prefs.getProperty(P_ENVIRONMENT)));
    setEnvironmentStrength(
        prefs.getFloatProperty(P_ENVIRONMENT_STRENGTH, DEFAULT_ENVIRONMENT_STRENGTH));
    renderingLayer.setEnableRepaint(true);
  }

  public void persistCinematic(WProperties prefs) {
    prefs.putFloatProperty(P_SHADOW_STRENGTH, shadowStrength);
    prefs.putFloatProperty(P_AO_STRENGTH, aoStrength);
    prefs.putFloatProperty(P_EXPOSURE, exposure);
    prefs.putFloatProperty(P_LIGHT_AZIMUTH, lightAzimuth);
    prefs.putFloatProperty(P_LIGHT_ELEVATION, lightElevation);
    prefs.setProperty(P_ENVIRONMENT, environment.name());
    prefs.putFloatProperty(P_ENVIRONMENT_STRENGTH, environmentStrength);
  }
}
