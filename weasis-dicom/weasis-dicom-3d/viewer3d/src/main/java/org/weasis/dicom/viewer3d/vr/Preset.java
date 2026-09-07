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

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.lut.ColorMapJson;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.image.lut.ColorMapRegistry.Origin;
import org.weasis.core.api.image.lut.ColorMapRegistry.Query;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapEdits;
import org.weasis.opencv.op.lut.colormap.ColorMapSampler;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.GradientOpacity;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.Material;
import org.weasis.opencv.op.lut.colormap.Rgba;

/**
 * A volume rendering preset: the GPU form of a transfer {@link ColorMap} with lighting. The color
 * texture and the lighting map are sampled from the map over its intensity range. A map whose
 * positions depend on the volume (relative, percent of the maximum, SUV units) is compiled again
 * per view by {@link #forVolume}.
 */
public class Preset extends TextureData {
  private static final Logger LOGGER = LoggerFactory.getLogger(Preset.class);

  public static final String BUILTIN_RESOURCE = "/volumeColorMaps.json"; // NON-NLS
  public static final String LEGACY_CUSTOM_PRESETS_FILENAME = "customVolumePresets.json"; // NON-NLS
  public static final List<Preset> basicPresets = loadPresets();

  /** Texels of the color texture at most: a wider intensity span is sampled, not enumerated. */
  static final int MAX_TEXTURE_WIDTH = 4096;

  private final ColorMap source;
  private final ColorMap colorMap;
  private final String name;
  private final Modality modality;
  private final boolean defaultElement;
  private final boolean shade;
  private final float specularPower;
  private final boolean custom;
  private final boolean preview;
  private final int colorMin;
  private final int colorMax;
  private final byte[] colors;
  private byte[] invertColors;
  private final LightingMap lightingMap;
  private boolean requiredBuilding;
  private int id2;

  private Preset(ColorMap map, boolean custom) {
    this(map, custom, false);
  }

  private Preset(ColorMap source, boolean custom, boolean preview) {
    this(source, anchored(Objects.requireNonNull(source)), custom, preview);
  }

  private Preset(ColorMap source, ColorMap map, boolean custom, boolean preview) {
    super(256, PixelFormat.RGBA8);
    this.source = source;
    this.colorMap = map;
    this.preview = preview;
    this.name = map.name();
    this.modality =
        map.modalities().isEmpty()
            ? Modality.DEFAULT
            : Modality.getModality(map.modalities().stream().sorted().findFirst().orElseThrow());
    this.defaultElement = map.defaultForModality();
    Lighting lighting = map.lighting() != null ? map.lighting() : Lighting.DEFAULT;
    this.shade = lighting.shade();
    this.specularPower = lighting.specularPower();
    this.custom = custom;
    this.colorMin = (int) Math.floor(map.firstPosition());
    this.colorMax = Math.max((int) Math.ceil(map.lastPosition()), colorMin + 1);
    this.width = Math.min(colorMax - colorMin, MAX_TEXTURE_WIDTH);
    this.colors = new byte[width * 4];
    this.lightingMap = new LightingMap(hasVaryingMaterial(map) ? width : 1);
    initColors(this, false);
  }

  /** Samples of the gradient opacity curve uploaded to the shader. */
  public static final int GRADIENT_OPACITY_SAMPLES = 32;

  /**
   * Bound of the visible range that never excludes a sample, for a range touching a texture end.
   */
  private static final float UNBOUNDED = 1e30f;

  /**
   * The gradient opacity curve as {@value #GRADIENT_OPACITY_SAMPLES} factors, or null when the map
   * has none.
   */
  public float[] getGradientOpacityTable() {
    GradientOpacity gradient =
        colorMap.lighting() == null ? null : colorMap.lighting().gradientOpacity();
    return gradient == null ? null : gradient.table(GRADIENT_OPACITY_SAMPLES);
  }

  /** Tag marking a map whose opaque, well-separated materials suit the cinematic modes. */
  public static final String CINEMATIC_TAG = "cinematic"; // NON-NLS

  public static boolean isCinematic(ColorMap map) {
    return map.tags().contains(CINEMATIC_TAG);
  }

  /** Highest opacity of the map in {@code [0, 1]}; the path tracer's extinction bound. */
  public float getMaxAlpha() {
    int max = 0;
    for (int i = 0; i < width; i++) {
      max = Math.max(max, colors[i * 4 + 3] & 0xFF);
    }
    return max / 255f;
  }

  /**
   * Lowest and highest normalized LUT coordinate with a non-zero alpha: samples outside skip the
   * color and lighting fetches. A range touching a texture end is left open on that side, since
   * clamped coordinates read that end texel.
   */
  public float[] getVisibleRange() {
    int first = -1;
    int last = -1;
    for (int i = 0; i < width; i++) {
      if (colors[i * 4 + 3] != 0) {
        if (first < 0) {
          first = i;
        }
        last = i;
      }
    }
    if (first < 0) {
      return new float[] {UNBOUNDED, -UNBOUNDED};
    }
    float min = first == 0 ? -UNBOUNDED : (float) first / width;
    float max = last == width - 1 ? UNBOUNDED : (float) (last + 1) / width;
    return new float[] {min, max};
  }

  // Without a volume, a map without physical values spans the usual 12-bit range of its modality.
  private static ColorMap anchored(ColorMap map) {
    boolean ct = map.modalities().contains(Modality.CT.name());
    return ct
        ? ColorMapEdits.anchored(map, "HU", -1024, 3071) // NON-NLS
        : ColorMapEdits.anchored(map, null, 0, 4095);
  }

  /**
   * This preset compiled for the loaded volume when its map depends on it, else this preset. A
   * derived instance is equal to this one, belongs to one view and is retired like a preview.
   */
  public Preset forVolume(DicomVolTexture texture) {
    if (texture == null) {
      return this;
    }
    ColorMap compiled = anchoredTo(source, texture);
    return compiled.equals(colorMap) ? this : new Preset(source, compiled, custom, true);
  }

  /**
   * The map with its positions in the value unit of the volume: a relative map spans the volume
   * range, a percent map is a fraction of the volume maximum (of the range for the window
   * reference), SUV positions are divided by the SUV factor of the series and read as a fraction of
   * the maximum when there is none. Other maps are returned as they are.
   */
  public static ColorMap anchoredTo(ColorMap map, DicomVolTexture texture) {
    ColorMapDomain domain = map.domain();
    double min = texture.getLevelMin();
    double max = texture.getLevelMax();
    if (!(max > min)) {
      return anchored(map);
    }
    String unit = texture.getPixelValueUnit();
    return switch (domain.kind()) {
      case RELATIVE -> ColorMapEdits.anchored(map, unit, min, max);
      case PERCENT ->
          ColorMapDomain.REFERENCE_WINDOW.equals(domain.reference()) || max <= 0
              ? ColorMapEdits.anchored(map, unit, min, max)
              : scaled(map, unit, max / 100.0);
      case ABSOLUTE, FIXED -> isSuvUnit(domain.unit()) ? suvScaled(map, texture, unit) : map;
    };
  }

  private static ColorMap suvScaled(ColorMap map, DicomVolTexture texture, String unit) {
    Double factor = texture.getSuvFactor();
    if (factor != null) {
      return scaled(map, unit, 1.0 / factor);
    }
    double max = texture.getLevelMax();
    double top = map.domain().max();
    return max > 0 && top > 0 ? scaled(map, unit, max / top) : map;
  }

  // The positions multiplied by {@code factor}, in the unit of the volume.
  private static ColorMap scaled(ColorMap map, String unit, double factor) {
    ColorMapDomain d = map.domain();
    ColorMap absolute =
        map.toBuilder().domain(ColorMapDomain.absolute(unit, d.min(), d.max())).build();
    return ColorMapEdits.rescaled(absolute, d.min() * factor, d.max() * factor);
  }

  private static boolean isSuvUnit(String unit) {
    return unit != null && unit.toUpperCase(Locale.ROOT).startsWith("SUV"); // NON-NLS
  }

  /** A preset compiled from a declarative map. */
  public static Preset of(ColorMap map, boolean custom) {
    return new Preset(map, custom);
  }

  /**
   * A throw-away preset shown while editing: not in any menu, so the view owning it releases its
   * GPU textures once it is replaced.
   */
  public static Preset preview(ColorMap map) {
    return new Preset(map, true, true);
  }

  /**
   * The declared map, as the registry and the editor know it: the positions of a relative, percent
   * or SUV map are not those of the compiled texture.
   */
  public ColorMap toColorMap() {
    return source;
  }

  private static boolean hasVaryingMaterial(ColorMap map) {
    return map.stops().stream().map(ColorStop::material).filter(Objects::nonNull).distinct().count()
        > 1;
  }

  /** Intensity of texel {@code i}: the span is sampled evenly when it exceeds the texture width. */
  private double intensityAt(int i) {
    return colorMin + (double) i * (colorMax - colorMin) / width;
  }

  // Colors and lighting are sampled from the map; "inverse" is a color negative.
  static void initColors(Preset preset, boolean inverse) {
    int width = preset.getWidth();
    if (inverse && preset.invertColors == null) {
      preset.invertColors = new byte[width * 4];
    }
    ColorMapSampler sampler = preset.colorMap.sampler();
    byte[] target = inverse ? preset.invertColors : preset.colors;
    for (int i = 0; i < width; i++) {
      double intensity = preset.intensityAt(i);
      Rgba c = sampler.sample(intensity);
      int o = i * 4;
      target[o] = (byte) (inverse ? 255 - c.red8() : c.red8());
      target[o + 1] = (byte) (inverse ? 255 - c.green8() : c.green8());
      target[o + 2] = (byte) (inverse ? 255 - c.blue8() : c.blue8());
      target[o + 3] = (byte) c.alpha8();
      Material m = sampler.material(intensity);
      preset.lightingMap.setAmbient(i, m.ambient());
      preset.lightingMap.setDiffuse(i, m.diffuse());
      preset.lightingMap.setSpecular(i, m.specular());
    }
  }

  @Override
  public String toString() {
    return modality == Modality.DEFAULT ? name : modality.name() + " - " + name;
  }

  public String getName() {
    return name;
  }

  public Modality getModality() {
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

  public boolean isRequiredBuilding() {
    return requiredBuilding;
  }

  public void setRequiredBuilding(boolean requiredBuilding) {
    this.requiredBuilding = requiredBuilding;
  }

  public boolean isDefaultForAll() {
    return defaultElement && modality == Modality.DEFAULT;
  }

  public boolean isCustom() {
    return custom;
  }

  public boolean isPreview() {
    return preview;
  }

  public int getColorMin() {
    return colorMin;
  }

  public int getColorMax() {
    return colorMax;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof Preset other
            && custom == other.custom
            && source.id().equals(other.source.id()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(source.id(), custom);
  }

  // ── catalog ──

  private static final Query CUSTOM_VOLUME_MAPS =
      new Query(null, true, EnumSet.of(Origin.USER, Origin.IMPORTED), null, null, false);

  /**
   * Built-in presets and the user's volume maps of the registry, one per id (a user map saved under
   * a built-in id replaces it), sorted by modality.
   */
  public static List<Preset> getAllPresets() {
    Map<String, Preset> byId = new LinkedHashMap<>();
    basicPresets.forEach(p -> byId.put(p.source.id(), p));
    ColorMapRegistry.getInstance()
        .query(CUSTOM_VOLUME_MAPS)
        .forEach(map -> byId.put(map.id(), of(map, true)));
    List<Preset> all = new ArrayList<>(byId.values());
    all.sort(Comparator.comparing(p -> String.format("%03d", p.modality.ordinal()) + p.name));
    return all;
  }

  /** The maps of the built-in presets, for the registry. */
  public static List<ColorMap> builtInMaps() {
    return basicPresets.stream().map(Preset::toColorMap).toList();
  }

  /**
   * The preset flagged as default for the modality, user maps of the registry first, else the first
   * built-in preset without modality.
   */
  public static Preset getDefaultPreset(Modality modality) {
    if (modality != null) {
      Optional<ColorMap> registered =
          ColorMapRegistry.getInstance().defaultVolumeFor(modality.name());
      if (registered.isPresent()) {
        return presetOf(registered.get());
      }
    }
    Preset defPreset = null;
    for (Preset p : basicPresets) {
      if (defPreset == null && p.getModality() == Modality.DEFAULT) {
        defPreset = p;
      }
      if (p.getModality() == modality && p.isDefaultElement()) {
        defPreset = p;
        break;
      }
    }
    return defPreset;
  }

  // The built-in preset compiled from that map, else a custom one.
  private static Preset presetOf(ColorMap map) {
    return basicPresets.stream()
        .filter(p -> p.source.equals(map))
        .findFirst()
        .orElseGet(() -> of(map, true));
  }

  static List<Preset> loadPresets() {
    try (InputStream in = Preset.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      List<Preset> presets = new ArrayList<>();
      for (ColorMap map : ColorMapJson.readAll(in)) {
        presets.add(of(map, false));
      }
      presets.sort(Comparator.comparing(p -> String.format("%03d", p.modality.ordinal()) + p.name));
      return presets;
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the volume presets", e);
      return List.of();
    }
  }

  /**
   * Moves a custom preset file of the former format into the registry as user maps, then renames it
   * so this runs once.
   */
  public static void migrateLegacyCustomPresets() {
    String prefDir =
        UICore.getInstance().getSystemPreferences().getProperty("weasis.pref.dir"); // NON-NLS
    if (!StringUtil.hasText(prefDir)) {
      return;
    }
    Path legacy = Path.of(prefDir).resolve(LEGACY_CUSTOM_PRESETS_FILENAME);
    if (!Files.isRegularFile(legacy)) {
      return;
    }
    try {
      ColorMapRegistry registry = ColorMapRegistry.getInstance();
      for (ColorMap map : LegacyVolumePresets.read(legacy)) {
        registry.saveUserMap(map);
      }
      Files.move(
          legacy, legacy.resolveSibling(LEGACY_CUSTOM_PRESETS_FILENAME + ".migrated")); // NON-NLS
      LOGGER.info("Migrated the custom volume presets into the color map registry");
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot migrate the custom volume presets", e);
    }
  }

  // ── GPU ──

  @Override
  public void init(GL2ES2 gl) {
    init(gl, false);
  }

  public void init(GL2ES2 gl, boolean inverse) {
    super.init(gl);
    if (inverse && id2 <= 0) {
      IntBuffer intBuffer = IntBuffer.allocate(1);
      gl.glGenTextures(1, intBuffer);
      id2 = intBuffer.get(0);
    }
    initColors(this, inverse);
    gl.glActiveTexture(GL.GL_TEXTURE1);
    gl.glBindTexture(GL.GL_TEXTURE_2D, inverse ? id2 : getId());
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        internalFormat,
        width,
        height,
        0,
        format,
        type,
        Buffers.newDirectByteBuffer(inverse ? invertColors : colors).rewind());
    lightingMap.init(gl);
  }

  @Override
  public void render(GL2ES2 gl) {
    render(gl, false);
  }

  void render(GL2ES2 gl, boolean inverse) {
    if (gl != null) {
      if (requiredBuilding) {
        this.requiredBuilding = false;
        if (getId() <= 0 || (inverse && (invertColors == null || id2 <= 0))) {
          init(gl, inverse);
        }
      }
      // Bound on every frame: the texture outlives the GL context of a closed view.
      gl.glActiveTexture(GL.GL_TEXTURE1);
      gl.glBindTexture(GL.GL_TEXTURE_2D, inverse ? id2 : getId());
      gl.glTexImage2D(
          GL.GL_TEXTURE_2D,
          0,
          internalFormat,
          width,
          height,
          0,
          format,
          type,
          Buffers.newDirectByteBuffer(inverse ? invertColors : colors).rewind());
      lightingMap.update(gl);
    }
  }

  @Override
  public void destroy(GL2ES2 gl) {
    super.destroy(gl);
    if (id2 != 0) {
      gl.glDeleteTextures(1, new int[] {id2}, 0);
      id2 = 0;
    }
  }

  // ── icons ──

  public void drawLutIcon(Graphics2D g2d, Icon icon, int x, int y, int border) {
    int iconWidth = icon.getIconWidth();
    int iconHeight = icon.getIconHeight() - 2 * border;
    boolean inverse =
        EventManager.getInstance().getAction(ActionW.INVERT_LUT).orElseThrow().isSelected();
    ColorMapSampler sampler = colorMap.sampler();
    int sx = x + border;
    int sy = y + border;
    for (int i = 0; i < iconWidth; i++) {
      double intensity = colorMin + (double) i * (colorMax - colorMin) / iconWidth;
      Rgba c = sampler.sample(intensity);
      g2d.setColor(
          inverse
              ? new Color(1f - c.red(), 1f - c.green(), 1f - c.blue())
              : new Color(c.red(), c.green(), c.blue()));
      g2d.drawLine(sx + i, sy, sx + i, sy + iconHeight);
    }
  }

  public Icon getLUTIcon(int height) {
    int border = 2;
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (g instanceof Graphics2D g2d) {
          g2d.setStroke(new BasicStroke(1.2f));
          drawLutIcon(g2d, this, x, y, border);
        }
      }

      @Override
      public int getIconWidth() {
        return 256 + 2 * border;
      }

      @Override
      public int getIconHeight() {
        return height;
      }
    };
  }

  public static Map<String, List<SegRegion<?>>> getRegionMap() {
    if (EventManager.getInstance().getSelectedView2dContainer()
        instanceof View3DContainer container) {
      return container.getRegionMap();
    }
    return null;
  }
}
