/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor;

import java.awt.Desktop;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.Messages;

public class DefaultMimeAppFactory implements SeriesViewerFactory {

    public static final String NAME = Messages.getString("DefaultMimeAppFactory.sys_app"); //$NON-NLS-1$
    public static final Icon ICON = new ImageIcon(MimeInspector.class.getResource("/icon/16x16/apps-system.png")); //$NON-NLS-1$
    public static final MimeSystemAppViewer MimeSystemViewer = new MimeSystemAppViewer() {

        @Override
        public String getPluginName() {
            return NAME;
        }

        @Override
        public void addSeries(MediaSeries<MediaElement> series) {
            if (series != null) {
                Iterable<MediaElement> list = series.getMedias(null, null);
                synchronized (series) { //NOSONAR lock object is the list for iterating its elements safely
                    for (MediaElement m : list) {
                        // As SUN JRE supports only Gnome and responds "true" for Desktop.isDesktopSupported()
                        // in KDE session, but actually does not support it.
                        // http://bugs.sun.com/view_bug.do?bug_id=6486393
                        if (AppProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                            startAssociatedProgramFromLinux(m.getFile());
                        } else if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
                            // Workaround of the bug with mpg file see http://bugs.sun.com/view_bug.do?bug_id=6599987
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

    private DefaultMimeAppFactory() {
    }

    public static DefaultMimeAppFactory getInstance() {
        return instance;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
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
