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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Arrays;
import org.weasis.core.api.util.JsonUtil;

public class PresetGroup {
  private String name;
  PresetPoint[] points;

  public PresetGroup(String name, PresetPoint[] points) {
    this.name = name;
    this.points = points;
  }

  public String getName() {
    return name;
  }

  public PresetPoint[] getPoints() {
    return points;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setPoints(PresetPoint[] points) {
    this.points = points;
  }

  public PresetGroup copy() {
    PresetPoint[] copiedPoints =
        Arrays.stream(points).map(PresetPoint::copy).toArray(PresetPoint[]::new);
    return new PresetGroup(name, copiedPoints);
  }

  public JsonObject toJson() {
    JsonObjectBuilder builder = Json.createObjectBuilder();
    JsonUtil.addIfPresent(builder, "name", name); // NON-NLS
    JsonArrayBuilder array = Json.createArrayBuilder();
    Arrays.stream(points).map(PresetPoint::toJson).forEach(array::add);
    builder.add("point", array); // NON-NLS
    return builder.build();
  }

  public static PresetGroup fromJson(JsonObject json) {
    PresetPoint[] points =
        JsonUtil.objects(json.getJsonArray("point")).stream() // NON-NLS
            .map(PresetPoint::fromJson)
            .toArray(PresetPoint[]::new);
    return new PresetGroup(json.getString("name", null), points); // NON-NLS
  }
}
