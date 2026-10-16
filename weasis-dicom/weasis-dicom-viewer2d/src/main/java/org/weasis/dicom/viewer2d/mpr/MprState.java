/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * What a session keeps of an MPR viewer beyond the 2D presentation of its planes: the source
 * series, the grid, the crosshair and the rotation of the volume, the MIP mode and, per plane, the
 * slab thickness and the presentation (window, zoom, pan, color map). The images shown follow the
 * crosshair, so no plane carries a stack position.
 *
 * @param series id of the source series (the volume is built again from it)
 * @param grid id of the grid
 * @param crosshair center of the axes in slice space, null when unknown
 * @param rotation global rotation of the volume as {@code x, y, z, w}, null for none
 * @param mip MIP mode, lower case name of {@link MipView.Type}
 * @param planes per plane, the slab thickness and the presentation
 */
public record MprState(
    String series,
    String grid,
    double[] crosshair,
    double[] rotation,
    String mip,
    Map<Plane, PlaneState> planes) {

  /** The state of one plane: its slab thickness extension and its presentation. */
  public record PlaneState(int thickness, ViewPresentation view) {}

  public MprState {
    planes = planes == null ? Map.of() : Map.copyOf(planes);
  }

  /** The id of the series the volume of the container was built from. */
  public static Optional<String> sourceSeriesId(MprContainer container) {
    Volume<?, ?> volume = container.getMprController().getVolume();
    MediaSeries<DicomImageElement> source =
        volume == null || volume.getStack() == null ? null : volume.getStack().getSeries();
    if (source == null) {
      return Optional.empty();
    }
    Object id = source.getTagValue(source.getTagID());
    return Optional.ofNullable(id).map(Object::toString);
  }

  public static MprState capture(MprContainer container) {
    MprController controller = container.getMprController();
    AxesControl axes = controller.getAxesControl();
    Quaterniond rotation = axes.getGlobalRotation();
    Object mip = controller.getMipTypeOption().getSelectedItem();
    Map<Plane, PlaneState> planes = new EnumMap<>(Plane.class);
    for (Plane plane : Plane.values()) {
      MprView view = container.getMprView(plane);
      MprAxis axis = controller.getMprAxis(plane);
      if (view != null && axis != null) {
        planes.put(
            plane, new PlaneState(axis.getThicknessExtension(), ViewState.capture(view).view()));
      }
    }
    Vector3d center = axes.getCenter();
    return new MprState(
        sourceSeriesId(container).orElse(null),
        container.getLayoutModel().getId(),
        controller.getVolume() == null ? null : new double[] {center.x, center.y, center.z},
        rotation == null ? null : new double[] {rotation.x, rotation.y, rotation.z, rotation.w},
        mip instanceof MipView.Type type ? type.name().toLowerCase(Locale.ROOT) : null,
        planes);
  }

  /** Drives the container to this state; the volume must be built. */
  public void applyTo(MprContainer container) {
    MprController controller = container.getMprController();
    AxesControl axes = controller.getAxesControl();
    if (crosshair != null && crosshair.length == 3) {
      axes.setCenter(new Vector3d(crosshair[0], crosshair[1], crosshair[2]));
    }
    if (rotation != null && rotation.length == 4) {
      axes.setGlobalRotation(new Quaterniond(rotation[0], rotation[1], rotation[2], rotation[3]));
    }
    if (mip != null) {
      ComboItemListener<MipView.Type> option = controller.getMipTypeOption();
      for (Object item : option.getAllItem()) {
        if (item instanceof MipView.Type type && type.name().equalsIgnoreCase(mip)) {
          option.setSelectedItem(type);
        }
      }
    }
    planes.forEach(
        (plane, state) -> {
          MprAxis axis = controller.getMprAxis(plane);
          if (axis != null) {
            axis.setThicknessExtension(state.thickness());
          }
        });
    controller.updateAllViews();
    controller.fireCrossHairChanged();
    planes.forEach(
        (plane, state) -> {
          MprView view = container.getMprView(plane);
          if (view != null && view.getSeries() != null) {
            state.view().applyTo(view, null, false);
          }
        });
  }

  /** Writes this state into {@code json}, replacing the series list with the source series. */
  public JsonObjectBuilder writeTo(JsonObjectBuilder json) {
    if (series != null) {
      json.add("series", Json.createArrayBuilder().add(series)); // NON-NLS
    }
    if (grid != null) {
      json.add("grid", grid); // NON-NLS
    }
    JsonObjectBuilder mprJson = Json.createObjectBuilder();
    if (crosshair != null) {
      mprJson.add("crosshair", numbers(crosshair)); // NON-NLS
    }
    if (rotation != null) {
      mprJson.add("rotation", numbers(rotation)); // NON-NLS
    }
    if (mip != null) {
      mprJson.add("mip", mip); // NON-NLS
    }
    JsonObjectBuilder planesJson = Json.createObjectBuilder();
    planes.forEach(
        (plane, state) ->
            planesJson.add(
                plane.name().toLowerCase(Locale.ROOT),
                Json.createObjectBuilder()
                    .add("thickness", state.thickness()) // NON-NLS
                    .add("view", state.view().toJson()))); // NON-NLS
    return json.add("mpr", mprJson.add("planes", planesJson)); // NON-NLS
  }

  public JsonObject toJson() {
    return writeTo(Json.createObjectBuilder()).build();
  }

  public static MprState fromJson(JsonObject json) {
    JsonArray series = json.getJsonArray("series"); // NON-NLS
    JsonObject mpr = json.getJsonObject("mpr"); // NON-NLS
    Map<Plane, PlaneState> planes = new EnumMap<>(Plane.class);
    if (mpr != null && mpr.containsKey("planes")) { // NON-NLS
      JsonObject planesJson = mpr.getJsonObject("planes"); // NON-NLS
      for (Plane plane : Plane.values()) {
        JsonObject p = planesJson.getJsonObject(plane.name().toLowerCase(Locale.ROOT));
        if (p != null) {
          JsonObject view = p.getJsonObject("view"); // NON-NLS
          planes.put(
              plane,
              new PlaneState(
                  p.getInt("thickness", 0), // NON-NLS
                  view == null ? ViewPresentation.DEFAULT : ViewPresentation.fromJson(view)));
        }
      }
    }
    return new MprState(
        series == null || series.isEmpty() ? null : series.getString(0),
        json.getString("grid", null), // NON-NLS
        mpr == null ? null : doubles(mpr.getJsonArray("crosshair")), // NON-NLS
        mpr == null ? null : doubles(mpr.getJsonArray("rotation")), // NON-NLS
        mpr == null ? null : mpr.getString("mip", null), // NON-NLS
        planes);
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
      return null;
    }
    return array.getValuesAs(JsonNumber.class).stream()
        .mapToDouble(JsonNumber::doubleValue)
        .toArray();
  }
}
