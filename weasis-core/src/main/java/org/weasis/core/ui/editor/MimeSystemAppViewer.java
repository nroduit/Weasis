/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;

public abstract class MimeSystemAppViewer implements SeriesViewer<MediaElement> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MimeSystemAppViewer.class);

  private static final String ERROR_MSG =
      "Cannot open {} with the default system application"; // NON-NLS

  @Override
  public void close() {}

  @Override
  public List<MediaSeries<MediaElement>> getOpenSeries() {
    return Collections.emptyList();
  }

  public static void startAssociatedProgramFromLinux(Path path) {
    if (path != null && Files.isReadable(path)) {
      try {
        Process process =
            new ProcessBuilder("xdg-open", path.toString()) // NON-NLS
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() != 0) {
          LOGGER.error("'xdg-open' failed (exit {}) for {}", process.exitValue(), path); // NON-NLS
        }
      } catch (IOException e) {
        LOGGER.error(ERROR_MSG, path, e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static void startAssociatedProgramFromDesktop(final Desktop desktop, Path path) {
    if (path != null && Files.isReadable(path)) {
      try {
        desktop.open(path.toFile());
      } catch (IOException e) {
        LOGGER.error(ERROR_MSG, path, e);
      }
    }
  }

  @Override
  public void removeSeries(MediaSeries<MediaElement> sequence) {}

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menu) {
    return null;
  }

  @Override
  public void setSelected(boolean selected) {}

  @Override
  public MediaSeriesGroup getGroupID() {
    return null;
  }
}
