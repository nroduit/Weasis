/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.weasis.core.api.media.data.AttributeSource;

/**
 * {@link AttributeSource} backed by a JSON object. Scalar members (string, number, boolean) are
 * exposed as attributes; nested objects and arrays are structural and therefore ignored here.
 */
record JsonAttributeSource(JsonObject object) implements AttributeSource {

  @Override
  public String getAttribute(String name) {
    JsonValue value = object.get(name);
    if (value == null) {
      return null;
    }
    return switch (value.getValueType()) {
      case STRING -> ((JsonString) value).getString();
      case NUMBER -> value.toString();
      case TRUE -> Boolean.TRUE.toString();
      case FALSE -> Boolean.FALSE.toString();
      default -> null;
    };
  }
}
