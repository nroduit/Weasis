/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.util.StringUtil;

/** Multipart HTTP request body (e.g. {@code multipart/related} for STOW-RS). */
public final class MultipartBody {

  private static final String HEADER_CONTENT_TYPE = "Content-Type";

  private final String boundary;
  private final Map<String, String> headers;
  private final List<BodyPart> parts = new ArrayList<>();

  /**
   * @param boundary MIME boundary separating the parts
   * @param headers request headers; a {@code Content-Type} entry overrides the default {@code
   *     multipart/form-data} type (optional)
   */
  public MultipartBody(String boundary, Map<String, String> headers) {
    this.boundary = Objects.requireNonNull(boundary, "boundary");
    this.headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  public void addBodyPart(BodyPart part) {
    parts.add(Objects.requireNonNull(part, "part"));
  }

  public List<BodyPart> getBodyParts() {
    return List.copyOf(parts);
  }

  public String getBoundary() {
    return boundary;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  /**
   * Honors the body's own {@code Content-Type} (e.g. STOW-RS multipart/related), else form-data.
   */
  public String contentType() {
    for (var entry : headers.entrySet()) {
      if (HEADER_CONTENT_TYPE.equalsIgnoreCase(entry.getKey())
          && StringUtil.hasText(entry.getValue())) {
        return entry.getValue();
      }
    }
    return "multipart/form-data; boundary=" + boundary; // NON-NLS
  }
}
