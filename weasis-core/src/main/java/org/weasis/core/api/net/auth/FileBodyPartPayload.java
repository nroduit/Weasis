/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** File body part payload for multipart HTTP requests. */
public class FileBodyPartPayload extends BodyPartPayload {

  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String FORM_DATA = "form-data";
  private final BodySupplier<InputStream> bodySupplier;

  public FileBodyPartPayload(BodySupplier<InputStream> bodySupplier) {
    this(bodySupplier, null);
  }

  public FileBodyPartPayload(BodySupplier<InputStream> bodySupplier, String filename) {
    this(null, bodySupplier, filename);
  }

  /**
   * @param contentType content type header (optional)
   * @param bodySupplier input stream supplier for file content
   * @param filename filename for {@code Content-Disposition} header (optional)
   */
  public FileBodyPartPayload(
      String contentType, BodySupplier<InputStream> bodySupplier, String filename) {
    super(buildHeaders(contentType, filename));
    this.bodySupplier = bodySupplier;
  }

  public BodySupplier<InputStream> getPayload() {
    return bodySupplier;
  }

  private static Map<String, String> buildHeaders(String contentType, String filename) {
    var headers = new LinkedHashMap<String, String>(2);
    if (contentType != null) {
      headers.put(HttpClient.CONTENT_TYPE, contentType);
    }
    headers.put(CONTENT_DISPOSITION, disposition(filename));
    return Map.copyOf(headers);
  }

  private static String disposition(String filename) {
    return filename == null ? FORM_DATA : FORM_DATA + "; filename=\"" + filename + "\"";
  }
}
