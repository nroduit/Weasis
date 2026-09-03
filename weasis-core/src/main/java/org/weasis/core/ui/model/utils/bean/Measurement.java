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

/**
 * A value a graphic can compute. The {@code key} is the stable identity used by preferences,
 * profiles and commands; the {@code name} is the localized label; the {@code id} only survives for
 * preferences written before keys existed.
 */
public class Measurement {

  private final String key;
  private final String name;
  private final int id;
  private final boolean quickComputing;
  private boolean computed;
  private boolean graphicLabel;
  private final boolean defaultGraphicLabel;
  private final QuantityKind kind;

  public Measurement(String key, String name, int id, boolean quickComputing) {
    this(key, name, id, quickComputing, true, true);
  }

  public Measurement(
      String key,
      String name,
      int id,
      boolean quickComputing,
      boolean computed,
      boolean graphicLabel) {
    this(key, name, id, quickComputing, computed, graphicLabel, QuantityKind.ofKey(key));
  }

  /**
   * @param kind what the value is, for its display precision; the other constructors derive it from
   *     the key
   */
  public Measurement(
      String key,
      String name,
      int id,
      boolean quickComputing,
      boolean computed,
      boolean graphicLabel,
      QuantityKind kind) {
    this.key = Objects.requireNonNull(key, "Key cannot be null!");
    this.name = Objects.requireNonNull(name, "Name cannot be null!");
    this.id = id;
    this.quickComputing = quickComputing;
    this.computed = computed;
    this.graphicLabel = graphicLabel;
    this.defaultGraphicLabel = graphicLabel;
    this.kind = kind == null ? QuantityKind.OTHER : kind;
  }

  /**
   * @deprecated use the constructor taking a stable key; this one derives the key from the id.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public Measurement(String name, Integer id, Boolean quickComputing) {
    this(String.valueOf(id), name, id, quickComputing);
  }

  /**
   * @deprecated use the constructor taking a stable key; this one derives the key from the id.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public Measurement(
      String name, Integer id, Boolean quickComputing, Boolean computed, Boolean graphicLabel) {
    this(String.valueOf(id), name, id, quickComputing, computed, graphicLabel);
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public QuantityKind getKind() {
    return kind;
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

  /** True when the given preference token names this measurement, by key or by legacy id. */
  public boolean matches(String token) {
    return key.equals(token) || String.valueOf(id).equals(token);
  }
}
