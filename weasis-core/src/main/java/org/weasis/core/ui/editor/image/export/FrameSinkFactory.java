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

import java.io.IOException;
import java.nio.file.Path;
import org.weasis.core.api.media.data.MaskingProfile;

/** One output format offered by the export dialog. */
public interface FrameSinkFactory {

  String title();

  String extension();

  /** One line on what the format is good and bad at, shown next to the selection. */
  String advice();

  /**
   * Whether every frame stays in memory until the file is written. Buffering sinks are the ones the
   * memory projection warns about; a streaming sink has no length limit.
   */
  boolean buffersRawFrames();

  /**
   * @param profile the masking profile the frames are captured under, or {@code null}; a sink that
   *     writes identity of its own must follow it
   */
  FrameSink create(Path file, MaskingProfile profile) throws IOException;
}
