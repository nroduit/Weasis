/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import java.util.Objects;

public class Measurement {

  private final String name;
  private final int id;
  private final boolean quickComputing;
  private boolean computed;
  private boolean graphicLabel;
  private final boolean defaultGraphicLabel;

  public Measurement(String name, Integer id, Boolean quickComputing) {
    this(name, id, quickComputing, Boolean.TRUE, Boolean.TRUE);
  }

  public Measurement(
      String name, Integer id, Boolean quickComputing, Boolean computed, Boolean graphicLabel) {
    this.name = Objects.requireNonNull(name, "Name cannot be null!");
    this.id = id;
    this.quickComputing = quickComputing;
    this.computed = computed;
    this.graphicLabel = graphicLabel;
    this.defaultGraphicLabel = graphicLabel;
  }

  public String getName() {
    return name;
  }

  public Integer getId() {
    return id;
  }

  public Boolean getComputed() {
    return computed;
  }

  public void setComputed(Boolean computed) {
    this.computed = computed;
  }

  public Boolean getGraphicLabel() {
    return graphicLabel;
  }

  public void setGraphicLabel(Boolean graphicLabel) {
    this.graphicLabel = graphicLabel;
  }

  public void resetToGraphicLabelValue() {
    graphicLabel = defaultGraphicLabel;
  }

  public Boolean getQuickComputing() {
    return quickComputing;
  }
}
