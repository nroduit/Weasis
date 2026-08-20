/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import jakarta.json.JsonObject;
import java.time.Instant;
import org.weasis.core.api.util.JsonUtil;

/** Latest release published by <a href="https://nroduit.github.io/en/api/release">the site</a>. */
public record Release(Instant date, String version, String url) {

  static Release fromJson(JsonObject json) {
    return new Release(
        JsonUtil.getInstant(json, "date"), // NON-NLS
        json.getString("version", null), // NON-NLS
        json.getString("url", null)); // NON-NLS
  }
}
