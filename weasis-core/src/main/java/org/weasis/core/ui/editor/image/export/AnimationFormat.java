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

import org.weasis.core.Messages;

/**
 * The two animated image containers OpenCV writes. Neither is a fallback for the other: APNG is
 * lossless and stores only what changes between frames, GIF has a 256-color palette and stores
 * every frame in full but is the one that journals and presentation software accept.
 */
public enum AnimationFormat {
  APNG("APNG", "apng", "animation.apng.advice"), // NON-NLS
  GIF("GIF", "gif", "animation.gif.advice"); // NON-NLS

  private final String title;
  private final String extension;
  private final String adviceKey;

  AnimationFormat(String title, String extension, String adviceKey) {
    this.title = title;
    this.extension = extension;
    this.adviceKey = adviceKey;
  }

  public String getTitle() {
    return title;
  }

  public String getExtension() {
    return extension;
  }

  public String getAdvice() {
    return Messages.getString(adviceKey);
  }

  @Override
  public String toString() {
    return title;
  }
}
