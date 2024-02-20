/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Desktop;
import java.io.File;
import java.util.Map;
import javax.swing.Icon;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.FilesExtractor;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class MimeSystemAppFactory implements SeriesViewerFactory {

  public static final String NAME = Messages.getString("MimeSystemAppViewer.app");

  public static final MimeSystemAppViewer mimeSystemViewer =
      new MimeSystemAppViewer() {

        @Override
        public String getPluginName() {
          return NAME;
        }

        @Override
        public void addSeries(MediaSeries<MediaElement> series) {
          if (series instanceof FilesExtractor extractor) {
            // Linux KDE session not supported
            // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6486393
            for (File file : extractor.getExtractFiles()) {
              if (SystemInfo.isLinux) {
                startAssociatedProgramFromLinux(file);
              } else if (Desktop.isDesktopSupported()) {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                  startAssociatedProgramFromDesktop(desktop, file);
                }
              }
            }
          }
        }

        @Override
        public SeriesViewerUI getSeriesViewerUI() {
          return null;
        }

        @Override
        public String getDockableUID() {
          return null;
        }
      };

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.OPEN_EXTERNAL);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_VIDEO_MIMETYPE.equals(mimeType)
        || DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return false;
  }

  @Override
  public SeriesViewer<? extends MediaElement> createSeriesViewer(Map<String, Object> properties) {
    return mimeSystemViewer;
  }

  @Override
  public int getLevel() {
    return 100;
  }

  @Override
  public boolean canAddSeries() {
    return false;
  }

  @Override
  public boolean canExternalizeSeries() {
    return false;
  }

  @Override
  public boolean canReadSeries(MediaSeries<?> series) {
    return series instanceof FilesExtractor;
  }
}
