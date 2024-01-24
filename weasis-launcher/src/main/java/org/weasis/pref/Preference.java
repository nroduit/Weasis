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
import org.weasis.launcher.Utils;

public class Preference {
  private final String code;
  private final String type;
  private final String javaType;
  private final String category;
  private String value;
  private String defaultValue;
  private String description;

  public Preference(String code, String type, String javaType, String category) {
    if (!Utils.hasText(code)) {
      throw new IllegalArgumentException("Invalid code");
    }
    this.code = code;
    this.type = type;
    this.javaType = javaType;
    this.category = category;
  }

  public String getCode() {
    return code;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = "null".equals(value) ? null : value; // NON-NLS
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = "null".equals(defaultValue) ? null : defaultValue; // NON-NLS
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

  public String getJavaType() {
    return javaType;
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
    return Objects.equals(code, that.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code);
  }
}
