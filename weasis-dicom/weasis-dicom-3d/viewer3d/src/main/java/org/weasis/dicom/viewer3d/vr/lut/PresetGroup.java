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

import com.fasterxml.jackson.annotation.JsonProperty;

public class PresetGroup {
  @JsonProperty("name")
  private String name;

  @JsonProperty("point")
  PresetPoint[] points;

  public PresetGroup() {
    // Used by Jackson
  }

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
}
