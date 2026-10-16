/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Locale;
import java.util.Optional;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import org.weasis.dicom.viewer3d.vr.RenderingType;
import org.weasis.dicom.viewer3d.vr.View3d;

/**
 * What a session keeps of a volume rendering viewer: the series, the grid, the preset by id, the
 * camera and the rendering parameters of its view.
 *
 * @param series id of the series the volume is built from
 * @param grid id of the grid
 * @param preset id of the volume preset, null for the default
 * @param camera the camera, null when unknown
 * @param rendering the rendering parameters, null when unknown
 */
public record VolumeState(
    String series, String grid, String preset, CameraState camera, Rendering rendering) {

  /** Position, rotation ({@code x, y, z, w}), zoom and projection of the camera. */
  public record CameraState(
      double[] position, double[] rotation, double zoom, boolean orthographic) {}

  /** Rendering type, shading, quality, opacity, window and inversion of the rendering layer. */
  public record Rendering(
      String type,
      boolean shading,
      boolean cinematic,
      int quality,
      double opacity,
      int window,
      int level,
      boolean invert) {}

  /** The id of the series the volume of the container was built from. */
  public static Optional<String> seriesId(View3DContainer container) {
    View3d view = firstView(container);
    DicomVolTexture texture = view == null ? null : view.getVolTexture();
    MediaSeries<DicomImageElement> series = texture == null ? null : texture.getSeries();
    if (series == null) {
      return Optional.empty();
    }
    Object id = series.getTagValue(series.getTagID());
    return Optional.ofNullable(id).map(Object::toString);
  }

  static View3d firstView(View3DContainer container) {
    return container.getImagePanels().stream()
        .filter(View3d.class::isInstance)
        .map(View3d.class::cast)
        .findFirst()
        .orElse(null);
  }

  public static VolumeState capture(View3DContainer container) {
    View3d view = firstView(container);
    String grid = container.getLayoutModel().getId();
    if (view == null) {
      return new VolumeState(null, grid, null, null, null);
    }
    Camera camera = view.getCamera();
    Vector3d position = camera.getPosition();
    Quaterniond rotation = camera.getRotation();
    RenderingLayer<DicomImageElement> layer = view.getRenderingLayer();
    Preset preset = view.getVolumePreset();
    return new VolumeState(
        seriesId(container).orElse(null),
        grid,
        preset == null ? null : preset.getMapId(),
        new CameraState(
            new double[] {position.x, position.y, position.z},
            new double[] {rotation.x, rotation.y, rotation.z, rotation.w},
            camera.getZoomFactor(),
            camera.isOrthographicProjection()),
        new Rendering(
            layer.getRenderingType().name().toLowerCase(Locale.ROOT),
            layer.isShading(),
            layer.isCinematic(),
            layer.getQuality(),
            layer.getOpacity(),
            layer.getWindowWidth(),
            layer.getWindowCenter(),
            layer.isInvertLut()));
  }

  /** Drives the view of the container to this state; the volume must be loaded. */
  public void applyTo(View3DContainer container) {
    View3d view = firstView(container);
    if (view == null) {
      return;
    }
    if (preset != null) {
      Preset.getAllPresets().stream()
          .filter(p -> preset.equals(p.getMapId()))
          .findFirst()
          .ifPresent(view::setVolumePreset);
    }
    if (rendering != null) {
      RenderingLayer<DicomImageElement> layer = view.getRenderingLayer();
      boolean repaint = layer.isEnableRepaint();
      layer.setEnableRepaint(false);
      try {
        renderingType(rendering.type()).ifPresent(layer::setRenderingType);
        layer.setShading(rendering.shading());
        layer.setCinematic(rendering.cinematic());
        layer.setQuality(rendering.quality());
        layer.setOpacity(rendering.opacity());
        layer.setWindowWidth(rendering.window());
        layer.setWindowCenter(rendering.level());
        layer.setInvertLut(rendering.invert());
      } finally {
        layer.setEnableRepaint(repaint);
      }
    }
    if (camera != null && camera.position().length == 3 && camera.rotation().length == 4) {
      Camera cam = view.getCamera();
      cam.setOrthographicProjection(camera.orthographic());
      double[] p = camera.position();
      double[] r = camera.rotation();
      cam.set(
          new Vector3d(p[0], p[1], p[2]), new Quaterniond(r[0], r[1], r[2], r[3]), camera.zoom());
    }
    view.display();
  }

  private static Optional<RenderingType> renderingType(String name) {
    if (name == null) {
      return Optional.empty();
    }
    for (RenderingType type : RenderingType.values()) {
      if (type.name().equalsIgnoreCase(name)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public JsonObjectBuilder writeTo(JsonObjectBuilder json) {
    if (series != null) {
      json.add("series", Json.createArrayBuilder().add(series)); // NON-NLS
    }
    if (grid != null) {
      json.add("grid", grid); // NON-NLS
    }
    JsonObjectBuilder volume = Json.createObjectBuilder();
    if (preset != null) {
      volume.add("preset", preset); // NON-NLS
    }
    if (camera != null) {
      volume.add(
          "camera", // NON-NLS
          Json.createObjectBuilder()
              .add("position", numbers(camera.position())) // NON-NLS
              .add("rotation", numbers(camera.rotation())) // NON-NLS
              .add("zoom", camera.zoom()) // NON-NLS
              .add("orthographic", camera.orthographic())); // NON-NLS
    }
    if (rendering != null) {
      volume.add(
          "rendering", // NON-NLS
          Json.createObjectBuilder()
              .add("type", rendering.type()) // NON-NLS
              .add("shading", rendering.shading()) // NON-NLS
              .add("cinematic", rendering.cinematic()) // NON-NLS
              .add("quality", rendering.quality()) // NON-NLS
              .add("opacity", rendering.opacity()) // NON-NLS
              .add("window", rendering.window()) // NON-NLS
              .add("level", rendering.level()) // NON-NLS
              .add("invert", rendering.invert())); // NON-NLS
    }
    return json.add("volume", volume); // NON-NLS
  }

  public JsonObject toJson() {
    return writeTo(Json.createObjectBuilder()).build();
  }

  public static VolumeState fromJson(JsonObject json) {
    JsonArray series = json.getJsonArray("series"); // NON-NLS
    JsonObject volume = json.getJsonObject("volume"); // NON-NLS
    JsonObject cam = volume == null ? null : volume.getJsonObject("camera"); // NON-NLS
    JsonObject ren = volume == null ? null : volume.getJsonObject("rendering"); // NON-NLS
    return new VolumeState(
        series == null || series.isEmpty() ? null : series.getString(0),
        json.getString("grid", null), // NON-NLS
        volume == null ? null : volume.getString("preset", null), // NON-NLS
        cam == null
            ? null
            : new CameraState(
                doubles(cam.getJsonArray("position")), // NON-NLS
                doubles(cam.getJsonArray("rotation")), // NON-NLS
                cam.getJsonNumber("zoom").doubleValue(), // NON-NLS
                cam.getBoolean("orthographic", false)), // NON-NLS
        ren == null
            ? null
            : new Rendering(
                ren.getString("type", null), // NON-NLS
                ren.getBoolean("shading", false), // NON-NLS
                ren.getBoolean("cinematic", false), // NON-NLS
                ren.getInt("quality", 0), // NON-NLS
                ren.getJsonNumber("opacity").doubleValue(), // NON-NLS
                ren.getInt("window", 0), // NON-NLS
                ren.getInt("level", 0), // NON-NLS
                ren.getBoolean("invert", false))); // NON-NLS
  }

  private static JsonArrayBuilder numbers(double[] values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    for (double v : values) {
      array.add(v);
    }
    return array;
  }

  private static double[] doubles(JsonArray array) {
    if (array == null) {
      return new double[0];
    }
    return array.getValuesAs(JsonNumber.class).stream()
        .mapToDouble(JsonNumber::doubleValue)
        .toArray();
  }
}
