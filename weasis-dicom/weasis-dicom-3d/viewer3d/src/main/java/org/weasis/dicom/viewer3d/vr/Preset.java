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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.Icon;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.dicom.viewer3d.vr.lut.PresetGroup;
import org.weasis.dicom.viewer3d.vr.lut.PresetPoint;
import org.weasis.dicom.viewer3d.vr.lut.VolumePreset;
import org.weasis.opencv.seg.RegionAttributes;

public class Preset extends TextureData {
  private static final Logger LOGGER = LoggerFactory.getLogger(Preset.class);
  public static final List<Preset> basicPresets = loadPresets();

  public static final Preset originalPreset = getOriginalPreset();
  private final boolean defaultElement;
  private final List<PresetGroup> groups;
  private boolean requiredBuilding;
  private final byte[] colors;
  private byte[] invertColors;
  private final String name;
  private final Modality modality;

  private final boolean shade;
  private final float specularPower;

  private final int colorMin;
  private final int colorMax;
  private final LightingMap lightingMap;

  private int id2;

  public Preset(VolumePreset v) {
    this(
        Objects.requireNonNull(v).getName(),
        v.getModality(),
        v.isDefaultElement(),
        v.isShade(),
        v.getSpecularPower(),
        v.getGroups());
  }

  public Preset(
      String name,
      String modality,
      boolean defaultElement,
      boolean shade,
      float specularPower,
      List<PresetGroup> groups) {
    super(256, PixelFormat.RGBA8);
    this.name = Objects.requireNonNull(name);
    this.modality = Modality.getModality(modality);
    this.defaultElement = defaultElement;
    this.shade = shade;
    this.specularPower = specularPower;

    this.groups = groups;
    if (groups.isEmpty() || groups.stream().anyMatch(g -> g.getPoints().length == 0)) {
      throw new IllegalArgumentException("empty group or point");
    }
    this.colorMin = groups.getFirst().getPoints()[0].getIntensity();
    PresetPoint[] pts = groups.getLast().getPoints();
    this.colorMax = pts[pts.length - 1].getIntensity();
    this.width = colorMax - colorMin;
    this.colors = new byte[width * 4];
    this.lightingMap = new LightingMap(hasIdenticalLightingValues() ? 1 : width);
    initColors(this, false);
  }

  private boolean hasIdenticalLightingValues() {
    float amb = 0.2f;
    float diff = 0.9f;
    float spec = 0.2f;
    boolean init = false;
    for (PresetGroup g : groups) {
      for (PresetPoint p : g.getPoints()) {
        if (!init) {
          if (p.getAmbient() != null) {
            init = true;
            amb = PresetPoint.convertFloat(p.getAmbient(), amb);
            diff = PresetPoint.convertFloat(p.getDiffuse(), diff);
            spec = PresetPoint.convertFloat(p.getSpecular(), spec);
          }
        } else if (p.getAmbient() != null && p.getAmbient() != amb) {
          return false;
        } else if (p.getDiffuse() != null && p.getDiffuse() != diff) {
          return false;
        } else if (p.getSpecular() != null && p.getSpecular() != spec) {
          return false;
        }
      }
    }
    return true;
  }

  private static int getBestIndex(List<PresetPoint> points, int stepX) {
    int pos = 0;
    for (int i = 0; i < points.size(); i++) {
      PresetPoint val = points.get(i);
      if (val.getIntensity() <= stepX) {
        pos = i;
      } else {
        break;
      }
    }

    return pos;
  }

  private static int getBestColorIndex(List<PresetPoint> points, int stepX) {
    int pos = 0;
    for (int i = 0; i < points.size(); i++) {
      PresetPoint val = points.get(i);
      if (val.getIntensity() > stepX) {
        break;
      }
      if (val.getRed() != null) {
        pos = i;
      }
    }
    return pos;
  }

  private static PresetPoint getNextColor(List<PresetPoint> points, int stepX, int index) {
    for (int i = index + 1; i < points.size(); i++) {
      PresetPoint val = points.get(i);
      if (val.getIntensity() > stepX && val.getRed() != null) {
        return val;
      }
    }
    return points.get(index);
  }

  static void initColors(Preset preset, boolean inverse) {
    int width = preset.getWidth();
    if (inverse && preset.invertColors == null) {
      preset.invertColors = new byte[width * 4];
    }
    List<PresetPoint> points = new ArrayList<>();
    preset.groups.forEach(g -> points.addAll(Arrays.asList(g.getPoints())));

    for (int i = 0; i < width; i++) {
      int stepX = i + preset.colorMin;

      float a;
      int index = getBestIndex(points, stepX);
      PresetPoint vStart = points.get(index);
      int val = vStart.getIntensity();
      if (val == stepX) {
        a = vStart.getOpacity();
      } else if (val < stepX && index + 1 < points.size()) {
        PresetPoint vEnd = points.get(index + 1);
        a = linearOpacityGradient(vStart, vEnd, stepX);
      } else {
        a = 0.0f;
      }

      float r;
      float g;
      float b;
      float amb;
      float diff;
      float spec;

      index = getBestColorIndex(points, stepX);
      PresetPoint v1 = points.get(index);
      val = v1.getIntensity();
      if (val == stepX) {
        r = PresetPoint.convertFloat(v1.getRed(), 0.0f);
        g = PresetPoint.convertFloat(v1.getGreen(), 0.0f);
        b = PresetPoint.convertFloat(v1.getBlue(), 0.0f);
        amb = PresetPoint.convertFloat(v1.getAmbient(), 0.2f);
        diff = PresetPoint.convertFloat(v1.getDiffuse(), 0.9f);
        spec = PresetPoint.convertFloat(v1.getSpecular(), 0.2f);
      } else if (val < stepX && index + 1 < points.size()) {
        PresetPoint vEnd = getNextColor(points, stepX, index);
        Vector4f v = linearColorGradient(v1, vEnd, stepX);
        r = v.x;
        g = v.y;
        b = v.z;
        v = linearLightingGradient(v1, vEnd, stepX);
        amb = v.x;
        diff = v.y;
        spec = v.z;
      } else {
        r = 0.0f;
        g = 0.0f;
        b = 0.0f;
        amb = 0.2f;
        diff = 0.9f;
        spec = 0.2f;
      }

      if (inverse) {
        preset.invertColors[i * 4] = (byte) Math.round((1 - r) * 255);
        preset.invertColors[i * 4 + 1] = (byte) Math.round((1 - g) * 255);
        preset.invertColors[i * 4 + 2] = (byte) Math.round((1 - b) * 255);
        preset.invertColors[i * 4 + 3] = (byte) Math.round(a * 255);
      } else {
        preset.colors[i * 4] = (byte) Math.round(r * 255);
        preset.colors[i * 4 + 1] = (byte) Math.round(g * 255);
        preset.colors[i * 4 + 2] = (byte) Math.round(b * 255);
        preset.colors[i * 4 + 3] = (byte) Math.round(a * 255);
      }

      preset.lightingMap.setAmbient(i, amb);
      preset.lightingMap.setDiffuse(i, diff);
      preset.lightingMap.setSpecular(i, spec);
    }
  }

  public static Vector4f linearColorGradient(PresetPoint vStart, PresetPoint vEnd, int stepX) {
    int min = vStart.getIntensity();
    int n = vEnd.getIntensity() - min;
    float stepR = (vEnd.getRed() - vStart.getRed()) / (n - 1);
    float stepG = (vEnd.getGreen() - vStart.getGreen()) / (n - 1);
    float stepB = (vEnd.getBlue() - vStart.getBlue()) / (n - 1);

    int pos = stepX - min;
    return new Vector4f(
        vStart.getRed() + stepR * pos,
        vStart.getGreen() + stepG * pos,
        vStart.getBlue() + stepB * pos,
        stepX);
  }

  public static Vector4f linearLightingGradient(PresetPoint vStart, PresetPoint vEnd, int stepX) {
    int min = vStart.getIntensity();
    int n = vEnd.getIntensity() - min;
    float stepA = (vEnd.getAmbient() - vStart.getAmbient()) / (n - 1);
    float stepD = (vEnd.getDiffuse() - vStart.getDiffuse()) / (n - 1);
    float stepS = (vEnd.getSpecular() - vStart.getSpecular()) / (n - 1);

    int pos = stepX - min;
    return new Vector4f(
        vStart.getAmbient() + stepA * pos,
        vStart.getDiffuse() + stepD * pos,
        vStart.getSpecular() + stepS * pos,
        stepX);
  }

  public static float linearOpacityGradient(PresetPoint vStart, PresetPoint vEnd, int stepX) {
    int min = vStart.getIntensity();
    int n = vEnd.getIntensity() - min;
    float step = (vEnd.getOpacity() - vStart.getOpacity()) / (n - 1);

    return vStart.getOpacity() + step * (stepX - min);
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
    return modality == Modality.DEFAULT && defaultElement;
  }

  @Override
  public void init(GL4 gl4) {
    init(gl4, false);
  }

  public void init(GL4 gl4, boolean inverse) {
    super.init(gl4);
    if (inverse && id2 <= 0) {
      IntBuffer intBuffer = IntBuffer.allocate(1);
      gl4.glGenTextures(1, intBuffer);
      id2 = intBuffer.get(0);
    }
    initColors(this, inverse);
    gl4.glActiveTexture(GL.GL_TEXTURE1);
    gl4.glBindTexture(GL.GL_TEXTURE_2D, inverse ? id2 : getId());
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        internalFormat,
        width,
        height,
        0,
        format,
        type,
        Buffers.newDirectByteBuffer(inverse ? invertColors : colors).rewind());

    lightingMap.init(gl4);
  }

  @Override
  public void render(GL4 gl4) {
    render(gl4, false, false);
  }

  void render(GL4 gl4, boolean inverse, boolean originalLut) {
    if (gl4 != null) {
      Preset p = originalLut ? originalPreset : this;
      if (requiredBuilding) {
        this.requiredBuilding = false;
        if (p.getId() <= 0 || (inverse && (p.invertColors == null || p.id2 <= 0))) {
          p.init(gl4, inverse);
        }
      }

      gl4.glActiveTexture(GL.GL_TEXTURE1);
      gl4.glTexImage2D(
          GL.GL_TEXTURE_2D,
          0,
          p.internalFormat,
          p.width,
          p.height,
          0,
          p.format,
          p.type,
          Buffers.newDirectByteBuffer(inverse ? p.invertColors : p.colors).rewind());

      p.lightingMap.update(gl4);
    }
  }

  @Override
  public void destroy(GL4 gl4) {
    super.destroy(gl4);
    if (id2 != 0) {
      gl4.glDeleteTextures(1, new int[] {id2}, 0);
      id2 = 0;
    }
  }

  public void drawLutIcon(Graphics2D g2d, Icon icon, int x, int y, int border, boolean markers) {
    int iconWidth = icon.getIconWidth();
    int iconHeight = icon.getIconHeight() - 2 * border;
    boolean inverse =
        EventManager.getInstance().getAction(ActionW.INVERT_LUT).orElseThrow().isSelected();
    List<PresetPoint> points = new ArrayList<>();
    groups.forEach(g -> points.addAll(Arrays.asList(g.getPoints())));

    int sx = x + border;
    int sy = y + border;

    for (int i = 0; i < iconWidth; i++) {
      float r;
      float g;
      float b;
      int stepX = (width * i / iconWidth) + colorMin;

      int index = getBestColorIndex(points, stepX);
      PresetPoint v1 = points.get(index);
      int val = v1.getIntensity();
      if (val == stepX) {
        r = PresetPoint.convertFloat(v1.getRed(), 0.0f);
        g = PresetPoint.convertFloat(v1.getGreen(), 0.0f);
        b = PresetPoint.convertFloat(v1.getBlue(), 0.0f);
      } else if (val < stepX) {
        PresetPoint vEnd = getNextColor(points, stepX, index);
        Vector4f v = linearColorGradient(v1, vEnd, stepX);
        r = v.x;
        g = v.y;
        b = v.z;
      } else {
        r = 0.0f;
        g = 0.0f;
        b = 0.0f;
      }

      if (inverse) {
        r = 1 - r;
        g = 1 - g;
        b = 1 - b;
      }
      g2d.setColor(new Color(r, g, b));
      g2d.drawLine(sx + i, sy, sx + i, sy + iconHeight);
    }

    if (markers) {
      for (PresetPoint p : points) {
        if (p.getRed() != null) {
          int index = (p.getIntensity() - colorMin) * iconWidth / width;
          g2d.setColor(Color.BLACK);
          g2d.draw3DRect(sx + index - 1, sy + iconHeight / 2 - 1, 3, 3, true);
        }
      }
    }
  }

  public Icon getLUTIcon(int height) {
    int border = 2;
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (g instanceof Graphics2D g2d) {
          g2d.setStroke(new BasicStroke(1.2f));
          drawLutIcon(g2d, this, x, y, border, false);
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

  public int getColorMin() {
    return colorMin;
  }

  public int getColorMax() {
    return colorMax;
  }

  public static Preset getDefaultPreset(Modality modality) {
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

  static List<Preset> loadPresets() {
    List<Preset> presets = new ArrayList<>();
    try {
      List<VolumePreset> list = loadFile("/volumePresets.json"); // NON-NLS
      list.forEach(
          p -> {
            Preset preset = buildPreset(p);
            if (preset != null) {
              presets.add(preset);
            }
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    presets.sort(
        Comparator.comparing(
            o -> (String.format("%03d", o.getModality().ordinal()) + o.getName()))); // NON-NLS
    return presets;
  }

  static Preset getOriginalPreset() {
    try {
      List<VolumePreset> original = loadFile("/originalLut.json"); // NON-NLS
      return buildPreset(original.getFirst());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static List<VolumePreset> loadFile(String file) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(Preset.class.getResourceAsStream(file), new TypeReference<>() {});
  }

  static Preset buildPreset(VolumePreset p) {
    try {
      return new Preset(p);
    } catch (Exception e) {
      LOGGER.error("Cannot read the preset {}", p.getName(), e);
    }
    return null;
  }

  public static Map<String, List<SegRegion<?>>> getRegionMap() {
    if (EventManager.getInstance().getSelectedView2dContainer()
        instanceof View3DContainer container) {
      return container.getRegionMap();
    }
    return null;
  }

  private static List<SegRegion<?>> getOrderRegionAttributes(Map<String, List<SegRegion<?>>> map) {
    List<SegRegion<?>> list = new ArrayList<>();
    for (Entry<String, List<SegRegion<?>>> entry : map.entrySet()) {
      list.addAll(entry.getValue());
    }
    list.sort(Comparator.comparingInt(RegionAttributes::getId));
    return list;
  }

  public static Preset getSegmentationLut() {
    Map<String, List<SegRegion<?>>> map = getRegionMap();

    if (map != null && !map.isEmpty()) {
      List<PresetGroup> groups = new ArrayList<>();
      groups.add(new PresetGroup("StartEmpty", new PresetPoint[] {getTransparentPoint(0)}));

      List<PresetPoint> presetPoints = new ArrayList<>();
      int max = 1;

      for (RegionAttributes a : getOrderRegionAttributes(map)) {
        float opacity = a.getInteriorOpacity();
        int density = a.getId();
        max = Math.max(max, density);
        if (a.isVisible()) {
          Color c = a.getColor();
          presetPoints.add(
              new PresetPoint(
                  density,
                  opacity,
                  c.getRed() / 255.0f,
                  c.getGreen() / 255.0f,
                  c.getBlue() / 255.0f,
                  1.0f,
                  0.2f,
                  1.0f));
        } else {
          presetPoints.add(getTransparentPoint(density));
        }
      }
      //   presetPoints.add(presetPoints.getLast());

      groups.add(new PresetGroup("segments", presetPoints.toArray(new PresetPoint[0]))); // NON-NLS
      // groups.add(new PresetGroup("EndEmpty", new PresetPoint[] {getTransparentPoint(max + 1)}));
      return new Preset("Segmentation", "SEG", false, true, 1.0f, groups); // NON-NLS
    }
    return null;
  }

  private static PresetPoint getTransparentPoint(int intensity) {
    return new PresetPoint(intensity, 0, 0f, 0f, 0f, 0.2f, 0.1f, 0.9f);
  }
}
