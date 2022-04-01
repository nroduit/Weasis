/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer;

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.weasis.core.ui.model.utils.UUIDable;

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface Layer extends Comparable<Layer>, UUIDable {

  void setVisible(Boolean visible);

  Boolean getVisible();

  void setLevel(Integer level);

  Integer getLevel();

  LayerType getType();

  void setType(LayerType type);

  /**
   * Set a name to the layer. The default value is null and toString() gets the layer type name.
   *
   * @param layerName the layer name
   */
  void setName(String layerName);

  String getName();

  @Override
  default int compareTo(Layer obj) {
    if (obj == null) {
      return 1;
    }
    int thisVal = this.getLevel();
    int anotherVal = obj.getLevel();
    return Integer.compare(thisVal, anotherVal);
  }
}
