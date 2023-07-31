/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.pref;

import java.util.Objects;

public class Preference {
  private final String code;
  private String value;

  private String defaultValue;
  private String description;

  private final String type;

  private final String category;

  public Preference(
      String code,
      String value,
      String defaultValue,
      String description,
      String type,
      String category) {
    this.code = code;
    this.value = value;
    this.defaultValue = defaultValue;
    this.description = description;
    this.type = type;
    this.category = category;
  }

  public String getCode() {
    return code;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public String getCategory() {
    return category;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Preference that = (Preference) o;
    return Objects.equals(code, that.code)
        && Objects.equals(value, that.value)
        && Objects.equals(defaultValue, that.defaultValue)
        && Objects.equals(description, that.description)
        && Objects.equals(type, that.type)
        && Objects.equals(category, that.category);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, value, defaultValue, description, type, category);
  }
}
