/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The name the capture dialogs propose: the kind of capture and the time it was taken, so files
 * saved one after the other do not overwrite each other and sort in order. No patient data goes
 * into it, the name leaves with the file whatever masking was applied to the pixels.
 */
public final class CaptureFileName {

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"); // NON-NLS

  private CaptureFileName() {}

  /** {@code base} followed by the current date and time, without an extension. */
  public static String of(String base) {
    return of(base, LocalDateTime.now(ZoneId.systemDefault()));
  }

  static String of(String base, LocalDateTime time) {
    return base + "_" + STAMP.format(time);
  }
}
