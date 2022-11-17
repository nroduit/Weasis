/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.exceptions;

public class InvalidShapeException extends Exception {

  public InvalidShapeException() {
    super();
  }

  public InvalidShapeException(String message) {
    super(message);
  }

  public InvalidShapeException(Throwable cause) {
    super(cause);
  }

  public InvalidShapeException(String message, Throwable cause) {
    super(message, cause);
  }
}
