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
import java.util.Arrays;
import java.util.List;

public class VolumePreset {
  @JsonProperty(value = "name", required = true)
  private String name;

  @JsonProperty(value = "modality", required = true)
  private String modality;

  @JsonProperty("default")
  private boolean defaultElement;

  @JsonProperty(value = "group", required = true)
  public PresetGroup[] groups;

  @JsonProperty("shade")
  private boolean shade;

  @JsonProperty(value = "specularPower", required = true)
  private float specularPower;

  public String getName() {
    return name;
  }

  public String getModality() {
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

  public List<PresetGroup> getGroups() {
    if (groups.length > 0) {
      PresetGroup lastGroup = groups[groups.length - 1];
      if (lastGroup.points.length > 0) {
        PresetPoint lastPt = lastGroup.points[lastGroup.points.length - 1];
        if ("CT".equals(modality) && lastPt.getIntensity() < 3071) {
          PresetPoint pt =
              new PresetPoint(
                  3071,
                  lastPt.getOpacity(),
                  lastPt.getRed(),
                  lastPt.getGreen(),
                  lastPt.getBlue(),
                  lastPt.getSpecular(),
                  lastPt.getAmbient(),
                  lastPt.getDiffuse());
          lastGroup.points = Arrays.copyOf(lastGroup.points, lastGroup.points.length + 1);
          lastGroup.points[lastGroup.points.length - 1] = pt;
        }
      }
    }
    return Arrays.asList(groups);
  }
}
