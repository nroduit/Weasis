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
import java.io.Serial;

/** Signals that a read stayed blocked longer than the configured stall timeout. */
public class StallTimeoutException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  public StallTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
