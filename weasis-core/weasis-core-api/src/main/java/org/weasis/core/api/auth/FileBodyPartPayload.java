/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.auth;

import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FileBodyPartPayload extends BodyPartPayload {

  private final BodySupplier<InputStream> bodySupplier;

  public FileBodyPartPayload(BodySupplier<InputStream> bodySupplier) {
    this(bodySupplier, null);
  }

  public FileBodyPartPayload(BodySupplier<InputStream> bodySupplier, String filename) {
    this(null, bodySupplier, filename);
  }

  public FileBodyPartPayload(
      String contentType, BodySupplier<InputStream> bodySupplier, String filename) {
    super(composeHeaders(contentType, filename));
    this.bodySupplier = bodySupplier;
  }

  public BodySupplier<InputStream> getPayload() {
    return bodySupplier;
  }

  private static Map<String, String> composeHeaders(String contentType, String filename) {

    String contentDispositionHeader = "form-data"; // NON-NLS
    if (filename != null) {
      contentDispositionHeader += "; filename=\"" + filename + '"'; // NON-NLS
    }
    if (contentType == null) {
      return Collections.singletonMap("Content-Disposition", contentDispositionHeader); // NON-NLS
    } else {
      final Map<String, String> headers = new HashMap<>();
      headers.put(HttpClient.CONTENT_TYPE, contentType);
      headers.put("Content-Disposition", contentDispositionHeader); // NON-NLS
      return headers;
    }
  }
}
