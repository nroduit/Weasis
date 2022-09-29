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

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Desktop;
import java.util.Map;
import javax.swing.Icon;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;

public class DefaultMimeAppFactory implements SeriesViewerFactory {

  public static final String NAME = Messages.getString("DefaultMimeAppFactory.sys_app");

  public static final MimeSystemAppViewer MimeSystemViewer =
      new MimeSystemAppViewer() {

        @Override
        public String getPluginName() {
          return NAME;
        }

        @Override
        public void addSeries(MediaSeries<MediaElement> series) {
          if (series != null) {
            Iterable<MediaElement> list = series.getMedias(null, null);
            synchronized (series) { // NOSONAR lock object is safe
              for (MediaElement m : list) {
                // As SUN JRE supports only Gnome and responds "true" for
                // Desktop.isDesktopSupported()
                // in KDE session, but actually does not support it.
                // http://bugs.sun.com/view_bug.do?bug_id=6486393
                if (SystemInfo.isLinux) {
                  startAssociatedProgramFromLinux(m.getFile());
                } else if (SystemInfo.isWindows) {
                  // Workaround of the bug with mpg file see
                  // http://bugs.sun.com/view_bug.do?bug_id=6599987
                  startAssociatedProgramFromWinCMD(m.getFile());
                } else if (Desktop.isDesktopSupported()) {
                  final Desktop desktop = Desktop.getDesktop();
                  if (desktop.isSupported(Desktop.Action.OPEN)) {
                    startAssociatedProgramFromDesktop(desktop, m.getFile());
                  }
                }
              }
            }
          }
        }

        @Override
        public String getDockableUID() {
          return null;
        }
      };
  private static final DefaultMimeAppFactory instance = new DefaultMimeAppFactory();

  private DefaultMimeAppFactory() {}

  public static DefaultMimeAppFactory getInstance() {
    return instance;
  }

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
    return "";
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return true;
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return false;
  }

  @Override
  public SeriesViewer<MediaElement> createSeriesViewer(Map<String, Object> properties) {
    return MimeSystemViewer;
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
}
