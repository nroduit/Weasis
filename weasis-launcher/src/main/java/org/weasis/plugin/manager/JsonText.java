/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin.manager;

import com.formdev.flatlaf.json.Json;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;

/** The minimal JSON reader of the launcher, on a text. */
final class JsonText {

  private JsonText() {}

  static Object parse(String text) {
    try {
      return Json.parse(new StringReader(text));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
