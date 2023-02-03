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
import com.jogamp.opengl.GL2;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Icon;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.weasis.dicom.codec.display.Modality;

public class Preset extends TextureData {

  public static final List<Preset> basicPresets = loadPresets();
  private boolean requiredBuilding;
  final byte[] colors;
  private final String name;
  private final Modality modality;

  private final boolean shade;

  private final float specularPower;
  private final float specular;
  private final float ambient;
  private final float diffuse;
  private final int colorMin;
  private final int colorMax;

  private final List<Vector4f> colorTransfer;

  private final List<Vector2f> scalarOpacity;

  private final List<Vector2f> gradientOpacity;
  private View3d renderer;

  public Preset(VolumePreset v) {
    super(v.getColorTransfer().length / 4, PixelFormat.RGBA8);
    this.name = v.getName();
    this.modality = Modality.getModality(v.getModality());
    this.shade = v.isShade();
    this.specularPower = v.getSpecularPower();
    this.specular = v.getSpecular();
    this.ambient = v.getAmbient();
    this.diffuse = v.getDiffuse();

    this.colorTransfer = new ArrayList<>();
    Float[] vColorTransfer = v.getColorTransfer();
    if (vColorTransfer.length >= 4) {
      int start = vColorTransfer.length % 2 == 0 ? 0 : 1;
      for (int i = start; i < vColorTransfer.length; i = i + 4) {
        colorTransfer.add(
            new Vector4f(
                vColorTransfer[i + 1],
                vColorTransfer[i + 2],
                vColorTransfer[i + 3],
                vColorTransfer[i]));
      }
    }

    this.gradientOpacity = new ArrayList<>();
    Float[] vGradientOpacity = v.getGradientOpacity();
    if (vGradientOpacity.length >= 4) {
      int start = vGradientOpacity.length % 2 == 0 ? 0 : 1;
      for (int i = start; i < vGradientOpacity.length; i = i + 2) {
        gradientOpacity.add(new Vector2f(vGradientOpacity[i], vGradientOpacity[i + 1]));
      }
    }

    this.scalarOpacity = new ArrayList<>();
    Float[] vScalarOpacity = v.getScalarOpacity();
    if (vScalarOpacity.length >= 4) {
      int start = vScalarOpacity.length % 2 == 0 ? 0 : 1;
      for (int i = start; i < vScalarOpacity.length; i = i + 2) {
        scalarOpacity.add(new Vector2f(vScalarOpacity[i], vScalarOpacity[i + 1]));
      }
    }

    this.colorMin = Math.round(scalarOpacity.get(0).x);
    this.colorMax = Math.round(scalarOpacity.get(scalarOpacity.size() - 1).x);
    this.colors = new byte[(colorMax - colorMin) * 4];
    initColors(this);
  }

  private static int getBestIndex(List<Vector2f> list, int stepX) {
    int pos = 0;
    for (int i = 0; i < list.size(); i++) {
      Vector2f val = list.get(i);
      if (Math.round(val.x) <= stepX) {
        pos = i;
      } else {
        break;
      }
    }

    return pos;
  }

  private static int getBestColorIndex(List<Vector4f> list, int stepX) {
    int pos = 0;
    for (int i = 0; i < list.size(); i++) {
      Vector4f val = list.get(i);
      if (Math.round(val.w) <= stepX) {
        pos = i;
      } else {
        break;
      }
    }

    return pos;
  }

  static void initColors(Preset preset) {
    int width = preset.colorMax - preset.colorMin;
    int maxRange = width - 1;

    for (int i = 0; i < width; i++) {
      int stepX = i + preset.colorMin;
      float px = ((float) i) / maxRange;

      float a = 0.0f;
      int index = getBestIndex(preset.scalarOpacity, stepX);
      Vector2f vStart = preset.scalarOpacity.get(index);
      int val = Math.round(vStart.x);
      if (val == stepX) {
        a = vStart.y;
      }
      if (val < stepX) {
        Vector2f vEnd =
            index + 1 < preset.scalarOpacity.size()
                ? preset.scalarOpacity.get(index + 1)
                : new Vector2f(stepX + 1, 1.0f);
        Vector2f v = linearGradient(vStart, vEnd, stepX);
        a = v.y;
      }

      float r = 0.0f;
      float g = 0.0f;
      float b = 0.0f;

      index = getBestColorIndex(preset.colorTransfer, stepX);
      Vector4f v1 = preset.colorTransfer.get(index);
      val = Math.round(v1.w);
      if (val == stepX) {
        r = v1.x;
        g = v1.y;
        b = v1.z;
      }
      if (val < stepX) {
        Vector4f vEnd =
            index + 1 < preset.colorTransfer.size()
                ? preset.colorTransfer.get(index + 1)
                : new Vector4f(1.0f, 1.0f, 1.0f, stepX + 1);
        Vector4f v = linearGradient(v1, vEnd, stepX);
        r = v.x;
        g = v.y;
        b = v.z;
      }

      preset.colors[i * 4] = (byte) Math.round(r * 255);
      preset.colors[i * 4 + 1] = (byte) Math.round(g * 255);
      preset.colors[i * 4 + 2] = (byte) Math.round(b * 255);
      preset.colors[i * 4 + 3] = (byte) Math.round(a * 255);
    }
  }

  public static Vector4f linearGradient(Vector4f vStart, Vector4f vEnd, int stepX) {
    if (vStart == null || vEnd == null) {
      return null;
    }

    int min = Math.round(vStart.w);
    int max = Math.round(vEnd.w);
    int n = max - min;
    float stepR = (vEnd.x - vStart.x) / (n - 1);
    float stepG = (vEnd.y - vStart.y) / (n - 1);
    float stepB = (vEnd.z - vStart.z) / (n - 1);

    int pos = stepX - min;
    return new Vector4f(
        vStart.x + stepR * pos, vStart.y + stepG * pos, vStart.z + stepB * pos, stepX);
  }

  public static Vector2f linearGradient(Vector2f vStart, Vector2f vEnd, int stepX) {
    if (vStart == null || vEnd == null) {
      return null;
    }

    int min = Math.round(vStart.x);
    int max = Math.round(vEnd.x);
    int n = max - min;
    float step = (vEnd.y - vStart.y) / (n - 1);

    return new Vector2f(stepX, vStart.y + step * (stepX - min));
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

  public boolean isRequiredBuilding() {
    return requiredBuilding;
  }

  public void setRequiredBuilding(boolean requiredBuilding) {
    this.requiredBuilding = requiredBuilding;
  }

  @Override
  public void init(GL2 gl2) {
    super.init(gl2);
    initColors(renderer.volumePreset);
    gl2.glActiveTexture(GL.GL_TEXTURE1);
    gl2.glBindTexture(GL.GL_TEXTURE_2D, getId());
    gl2.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl2.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl2.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl2.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl2.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        GL.GL_RGBA,
        colors.length / 4,
        1,
        0,
        GL.GL_RGBA,
        GL.GL_UNSIGNED_BYTE,
        Buffers.newDirectByteBuffer(colors).rewind());
  }

  @Override
  public void render(GL2 gl2) {
    update(gl2);
  }

  void update(GL2 gl2) {
    if (renderer != null && (requiredBuilding && renderer.volumePreset != null)) {
      this.requiredBuilding = false;

      if (gl2 != null) {
        if (getId() <= 0) {
          init(gl2);
        }
        gl2.glActiveTexture(GL.GL_TEXTURE1);
        gl2.glTexImage2D(
            GL.GL_TEXTURE_2D,
            0,
            GL.GL_RGBA,
            renderer.volumePreset.colors.length / 4,
            1,
            0,
            GL.GL_RGBA,
            GL.GL_UNSIGNED_BYTE,
            Buffers.newDirectByteBuffer(renderer.volumePreset.colors).rewind());
      }
    }
  }

  public void drawLutIcon(Graphics2D g2d, Icon icon, int x, int y, int border, boolean markers) {
    int iconWidth = icon.getIconWidth();
    int iconHeight = icon.getIconHeight() - 2 * border;
    ;
    int width = colorMax - colorMin;

    int sx = x + border;
    int sy = y + border;

    for (int i = 0; i < iconWidth; i++) {
      float r = 0.0f;
      float g = 0.0f;
      float b = 0.0f;
      int stepX = (width * i / iconWidth) + colorMin;
      int index = getBestColorIndex(colorTransfer, stepX);
      Vector4f v1 = colorTransfer.get(index);
      int val = Math.round(v1.w);
      if (val == stepX) {
        r = v1.x;
        g = v1.y;
        b = v1.z;
      }
      if (val < stepX) {
        Vector4f vEnd =
            index + 1 < colorTransfer.size()
                ? colorTransfer.get(index + 1)
                : new Vector4f(1.0f, 1.0f, 1.0f, stepX + 1);
        Vector4f v = linearGradient(v1, vEnd, stepX);
        r = v.x;
        g = v.y;
        b = v.z;
      }
      g2d.setColor(new Color(r, g, b));
      g2d.drawLine(sx + i, sy, sx + i, sy + iconHeight);
    }

    if (markers) {
      for (Vector4f t : colorTransfer) {
        int index = (Math.round(t.w) - colorMin) * iconWidth / width;
        g2d.setColor(Color.BLACK);
        g2d.draw3DRect(sx + index - 1, sy + iconHeight / 2 - 1, 3, 3, true);
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

  public void setRenderer(View3d renderer) {
    this.renderer = renderer;
  }

  public int getColorMin() {
    return colorMin;
  }

  public int getColorMax() {
    return colorMax;
  }

  public static List<Preset> loadPresets() {
    List<Preset> presets = new ArrayList<>();
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      List<VolumePreset> list =
          objectMapper.readValue(
              Preset.class.getResourceAsStream("/volumePresets.json"), new TypeReference<>() {});

      list.forEach(p -> presets.add(new Preset(p)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    presets.sort(
        Comparator.comparing(
            o -> (String.format("%03d", o.getModality().ordinal()) + o.getName())));
    return presets;
  }
}
