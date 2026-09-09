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

import java.io.IOException;

/**
 * Signals an unsuccessful HTTP response, keeping the status code available to the caller so that a
 * service capability can be deduced from it.
 */
public class HttpStatusException extends IOException {

  private final int statusCode;

  public HttpStatusException(int statusCode) {
    super("HTTP request failed with status code: " + statusCode);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
