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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One part of a {@link MultipartBody}: its headers and lazily supplied content. */
public class BodyPart {

  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String FORM_DATA = "form-data";

  private final Map<String, String> headers;
  private final BodySupplier<InputStream> content;

  public BodyPart(Map<String, String> headers, BodySupplier<InputStream> content) {
    this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    this.content = Objects.requireNonNull(content, "content");
  }

  /**
   * Creates a {@code form-data} part.
   *
   * @param contentType {@code Content-Type} header (optional)
   * @param content content supplier
   * @param filename filename for the {@code Content-Disposition} header (optional)
   */
  public static BodyPart of(
      String contentType, BodySupplier<InputStream> content, String filename) {
    var headers = new LinkedHashMap<String, String>(2);
    if (contentType != null) {
      headers.put("Content-Type", contentType);
    }
    headers.put(CONTENT_DISPOSITION, disposition(filename));
    return new BodyPart(headers, content);
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public BodySupplier<InputStream> getContent() {
    return content;
  }

  private static String disposition(String filename) {
    return filename == null ? FORM_DATA : FORM_DATA + "; filename=\"" + filename + "\"";
  }
}
