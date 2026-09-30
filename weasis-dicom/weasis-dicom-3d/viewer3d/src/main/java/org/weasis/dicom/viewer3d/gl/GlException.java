/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

/** A context could not be created or a GL thread task failed; 3D is unavailable. */
public class GlException extends RuntimeException {

  public GlException(String message) {
    super(message);
  }

  public GlException(String message, Throwable cause) {
    super(message, cause);
  }
}
