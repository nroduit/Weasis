/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.pf4j.ExtensionPoint;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * A format {@code measure:export} writes the graphics of a pane to: the core gives {@code xml} (the
 * presentation model of Weasis) and {@code csv} (the measured values), a DICOM plugin adds {@code
 * sr} (a TID 1500 Measurement Report) and {@code pr} (a Grayscale Softcopy Presentation State). A
 * plugin contributes its formats as a pf4j extension.
 */
public interface GraphicsExporter extends ExtensionPoint {

  /** What the export wrote: the file and what the format wants to say about it. */
  record Written(Path file, int graphics, int skipped, Map<String, String> details) {}

  /** The format id, lower-case: {@code sr}. */
  String format();

  /** One sentence for the help. */
  String description();

  /**
   * Writes {@code graphics} of the pane shown by {@code view} to {@code file}.
   *
   * @throws IllegalStateException when the pane cannot be exported in this format
   */
  Written export(ViewCanvas<?> view, List<Graphic> graphics, Path file) throws IOException;
}
