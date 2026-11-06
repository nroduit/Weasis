/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.Color;
import java.awt.print.PrinterException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import org.weasis.core.ui.util.PrintOptions;

/**
 * Where a page can be sent: a system printer, a DICOM print node, a file. A destination knows its
 * papers and their printable area, its resolution when it has one, and prints a layout through the
 * {@link PageRenderer}.
 */
public interface PrintDestination {

  /** A stable key, under which the settings last used for this destination are remembered. */
  String id();

  /** The name shown in the destination list. */
  String title();

  /** The papers the destination accepts, the default one first. */
  List<Paper> papers();

  Paper defaultPaper();

  /** White for paper, black for film. */
  Color defaultBackground();

  /** Whether the destination prints in color; otherwise the page is rendered in 8-bit gray. */
  default boolean isColor() {
    return true;
  }

  /** The orientation the destination is configured for, used when none is remembered. */
  default Optional<PageSpec.Orientation> preferredOrientation() {
    return Optional.empty();
  }

  /**
   * The settings of the destination itself, shown under the common ones of the dialog; {@code
   * onChange} is run when one of them changes.
   */
  default Optional<JComponent> optionsComponent(Runnable onChange) {
    return Optional.empty();
  }

  /** The page the destination prints on this paper: its own printable area as margins. */
  PageSpec pageSpec(Paper paper, PageSpec.Orientation orientation, Color background, Color border);

  /** The resolution the destination prints at, when it is known before the job starts. */
  OptionalInt resolutionDpi();

  /** Prints the pages, {@code copies} times, on the calling thread. */
  void print(PageSource pages, PageSpec spec, PrintOptions options, int copies)
      throws PrinterException;
}
