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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;
import org.weasis.dicom.viewer3d.Messages;

/**
 * Procedural HDR environments lighting the cinematic mode: a sky gradient plus a few soft area
 * lights, described in view space (y up, +z towards the viewer) so they turn with the camera like a
 * studio rig. Each environment is baked once into an equirectangular radiance map whose mip levels
 * are prefiltered for increasing roughness, and into the spherical-harmonic irradiance the diffuse
 * term samples.
 */
public enum EnvironmentMap {
  NONE(Messages.getString("env.none"), null),
  STUDIO(
      Messages.getString("env.studio"),
      new Sky(
          gray(0.55f),
          gray(0.40f),
          gray(0.18f),
          List.of(
              new AreaLight(new Vector3f(-0.4f, 0.6f, 0.7f), gray(1f), 2.5f, 12f),
              new AreaLight(new Vector3f(0.7f, 0.2f, 0.6f), gray(1f), 0.8f, 6f),
              new AreaLight(new Vector3f(0.2f, 0.5f, -0.8f), gray(1f), 1.2f, 20f)))),
  OVERCAST(
      Messages.getString("env.overcast"), new Sky(gray(0.9f), gray(0.7f), gray(0.25f), List.of())),
  WARM(
      Messages.getString("env.warm"),
      new Sky(
          new Vector3f(0.45f, 0.5f, 0.65f),
          new Vector3f(1.0f, 0.7f, 0.45f),
          new Vector3f(0.3f, 0.22f, 0.18f),
          List.of(
              new AreaLight(
                  new Vector3f(0.6f, 0.3f, 0.7f), new Vector3f(1f, 0.8f, 0.6f), 3f, 40f)))),
  COOL(
      Messages.getString("env.cool"),
      new Sky(
          new Vector3f(0.6f, 0.7f, 0.85f),
          new Vector3f(0.5f, 0.55f, 0.65f),
          new Vector3f(0.25f, 0.27f, 0.3f),
          List.of(
              new AreaLight(
                  new Vector3f(-0.5f, 0.8f, 0.3f), new Vector3f(0.85f, 0.92f, 1f), 1.5f, 10f),
              new AreaLight(
                  new Vector3f(0.5f, 0.8f, 0.3f), new Vector3f(0.85f, 0.92f, 1f), 1.5f, 10f))));

  /** Width of the base level; the height is half of it. */
  static final int BASE_WIDTH = 128;

  /**
   * Mip levels, the last one being 4×2; level {@code k} is prefiltered for roughness k/(LEVELS-1).
   */
  static final int LEVELS = 6;

  /** Sources of the prefiltered levels are downsampled to this width to bound the convolution. */
  private static final int SOURCE_WIDTH = 32;

  /** Soft light around a direction: radiance falls off as a cosine power of the angle to it. */
  private record AreaLight(Vector3f direction, Vector3f color, float intensity, float sharpness) {
    private AreaLight {
      direction = new Vector3f(direction).normalize();
    }
  }

  private record Sky(Vector3f zenith, Vector3f horizon, Vector3f ground, List<AreaLight> lights) {
    Vector3f radiance(Vector3f d) {
      float t = (float) Math.pow(Math.abs(d.y), 0.7);
      Vector3f sky = new Vector3f(horizon).lerp(d.y >= 0 ? zenith : ground, t);
      for (AreaLight light : lights) {
        float cos = Math.max(d.dot(light.direction()), 0f);
        if (cos > 0f) {
          float w = light.intensity() * (float) Math.pow(cos, light.sharpness());
          sky.fma(w, light.color());
        }
      }
      return sky;
    }
  }

  /**
   * Baked form of an environment: RGB float texels of each mip level, widest first, and the nine
   * RGB spherical-harmonic coefficients of the irradiance divided by π, so a uniform white sky
   * yields 1.
   */
  public record Baked(List<float[]> levels, float[] irradianceSh) {
    public int width(int level) {
      return BASE_WIDTH >> level;
    }

    public int height(int level) {
      return width(level) / 2;
    }
  }

  private static final Map<EnvironmentMap, Baked> CACHE = new ConcurrentHashMap<>();

  private final String title;
  private final Sky sky;

  EnvironmentMap(String title, Sky sky) {
    this.title = title;
    this.sky = sky;
  }

  private static Vector3f gray(float v) {
    return new Vector3f(v, v, v);
  }

  public static EnvironmentMap fromName(String name) {
    try {
      return name == null ? STUDIO : valueOf(name);
    } catch (IllegalArgumentException e) {
      return STUDIO;
    }
  }

  /** The baked texture and irradiance; {@code null} for {@link #NONE}. Baked once, then cached. */
  public Baked bake() {
    if (sky == null) {
      return null;
    }
    return CACHE.computeIfAbsent(this, m -> bake(m.sky));
  }

  /** Direction of the centre of an equirectangular texel, matching {@code equirectUv} in GLSL. */
  static Vector3f direction(int x, int y, int width, int height) {
    double phi = ((x + 0.5) / width - 0.5) * 2.0 * Math.PI;
    double theta = (y + 0.5) / height * Math.PI;
    double sinTheta = Math.sin(theta);
    return new Vector3f(
        (float) (sinTheta * Math.sin(phi)),
        (float) Math.cos(theta),
        (float) (sinTheta * Math.cos(phi)));
  }

  /**
   * Solid angle of a texel of a {@code width}×{@code height} equirectangular map at row {@code y}.
   */
  static double solidAngle(int y, int width, int height) {
    double theta = (y + 0.5) / height * Math.PI;
    return (2.0 * Math.PI / width) * (Math.PI / height) * Math.sin(theta);
  }

  private static Baked bake(Sky sky) {
    int w = BASE_WIDTH;
    int h = w / 2;
    float[] base = new float[w * h * 3];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        Vector3f c = sky.radiance(direction(x, y, w, h));
        int i = (y * w + x) * 3;
        base[i] = c.x;
        base[i + 1] = c.y;
        base[i + 2] = c.z;
      }
    }
    float[] source = downsample(base, w, h, SOURCE_WIDTH);
    List<float[]> levels = new java.util.ArrayList<>(LEVELS);
    levels.add(base);
    for (int level = 1; level < LEVELS; level++) {
      float roughness = level / (float) (LEVELS - 1);
      levels.add(prefilter(source, SOURCE_WIDTH, w >> level, roughness));
    }
    return new Baked(List.copyOf(levels), irradianceSh(base, w, h));
  }

  // Box-downsamples an RGB equirectangular map to the given width (integer ratio).
  private static float[] downsample(float[] src, int w, int h, int outW) {
    int ratio = w / outW;
    int outH = h / ratio;
    float[] out = new float[outW * outH * 3];
    float norm = 1f / (ratio * ratio);
    for (int y = 0; y < outH; y++) {
      for (int x = 0; x < outW; x++) {
        int o = (y * outW + x) * 3;
        for (int dy = 0; dy < ratio; dy++) {
          for (int dx = 0; dx < ratio; dx++) {
            int i = ((y * ratio + dy) * w + x * ratio + dx) * 3;
            out[o] += src[i] * norm;
            out[o + 1] += src[i + 1] * norm;
            out[o + 2] += src[i + 2] * norm;
          }
        }
      }
    }
    return out;
  }

  // Convolves the source with a Phong lobe whose exponent matches the roughness, weighting each
  // source texel by its solid angle; a roughness of 1 is a plain cosine lobe.
  private static float[] prefilter(float[] src, int srcW, int outW, float roughness) {
    int srcH = srcW / 2;
    int outH = outW / 2;
    float exponent = Math.max(2f / (roughness * roughness) - 2f, 1f);
    Vector3f[] srcDirs = new Vector3f[srcW * srcH];
    double[] srcWeights = new double[srcW * srcH];
    for (int y = 0; y < srcH; y++) {
      for (int x = 0; x < srcW; x++) {
        srcDirs[y * srcW + x] = direction(x, y, srcW, srcH);
        srcWeights[y * srcW + x] = solidAngle(y, srcW, srcH);
      }
    }
    float[] out = new float[outW * outH * 3];
    for (int y = 0; y < outH; y++) {
      for (int x = 0; x < outW; x++) {
        Vector3f d = direction(x, y, outW, outH);
        double r = 0;
        double g = 0;
        double b = 0;
        double sum = 0;
        for (int i = 0; i < srcDirs.length; i++) {
          float cos = d.dot(srcDirs[i]);
          if (cos > 0f) {
            double wgt = Math.pow(cos, exponent) * srcWeights[i];
            r += src[i * 3] * wgt;
            g += src[i * 3 + 1] * wgt;
            b += src[i * 3 + 2] * wgt;
            sum += wgt;
          }
        }
        int o = (y * outW + x) * 3;
        out[o] = (float) (r / sum);
        out[o + 1] = (float) (g / sum);
        out[o + 2] = (float) (b / sum);
      }
    }
    return out;
  }

  // Projects the radiance onto the nine real SH bands (order: L00, L1-1, L10, L11, L2-2, L2-1,
  // L20, L21, L22) and divides by π so the shader's irradiance is 1 under a uniform white sky.
  private static float[] irradianceSh(float[] map, int w, int h) {
    double[] sh = new double[27];
    double[] basis = new double[9];
    for (int y = 0; y < h; y++) {
      double dOmega = solidAngle(y, w, h);
      for (int x = 0; x < w; x++) {
        Vector3f d = direction(x, y, w, h);
        shBasis(d, basis);
        int i = (y * w + x) * 3;
        for (int k = 0; k < 9; k++) {
          double wgt = basis[k] * dOmega / Math.PI;
          sh[k * 3] += map[i] * wgt;
          sh[k * 3 + 1] += map[i + 1] * wgt;
          sh[k * 3 + 2] += map[i + 2] * wgt;
        }
      }
    }
    float[] out = new float[27];
    for (int k = 0; k < 27; k++) {
      out[k] = (float) sh[k];
    }
    return out;
  }

  private static void shBasis(Vector3f d, double[] out) {
    out[0] = 0.282095;
    out[1] = 0.488603 * d.y;
    out[2] = 0.488603 * d.z;
    out[3] = 0.488603 * d.x;
    out[4] = 1.092548 * d.x * d.y;
    out[5] = 1.092548 * d.y * d.z;
    out[6] = 0.315392 * (3.0 * d.z * d.z - 1.0);
    out[7] = 1.092548 * d.x * d.z;
    out[8] = 0.546274 * (d.x * d.x - d.y * d.y);
  }

  @Override
  public String toString() {
    return title;
  }
}
