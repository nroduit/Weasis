/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor;

import java.awt.Desktop;
import java.util.Hashtable;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.docking.UIManager;

public class DefaultMimeAppFactory implements SeriesViewerFactory {

    public static final String NAME = "Default System Application";
    public static final Icon ICON = new ImageIcon(MimeInspector.class.getResource("/icon/16x16/apps-system.png")); //$NON-NLS-1$
    public static final MimeSystemAppViewer MimeSystemViewer = new MimeSystemAppViewer() {
        private final String dockableUID = "" + UIManager.dockableUIGenerator.getAndIncrement(); //$NON-NLS-1$;

        @Override
        public String getPluginName() {
            return NAME;
        }

        @Override
        public void addSeries(MediaSeries<MediaElement> series) {
            if (series != null) {
                Iterable<MediaElement> list = series.getMedias(null, null);
                synchronized (list) {
                    for (MediaElement m : list) {
                        // As SUN JRE supports only Gnome and responds "true" for Desktop.isDesktopSupported()
                        // in KDE session, but actually does not support it.
                        // http://bugs.sun.com/view_bug.do?bug_id=6486393
                        if (AbstractProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                            startAssociatedProgramFromLinux(m.getFile());
                        } else if (AbstractProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
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
            return dockableUID;
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
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        return false;
    }

    @Override
    public SeriesViewer createSeriesViewer(Hashtable<String, Object> properties) {
        return MimeSystemViewer;
    }

    @Override
    public int getLevel() {
        return 100;
    }
}
