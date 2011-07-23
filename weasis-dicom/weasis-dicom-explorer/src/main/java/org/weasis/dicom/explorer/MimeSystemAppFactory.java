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
package org.weasis.dicom.explorer;

import java.awt.Desktop;
import java.io.File;
import java.util.Hashtable;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.MimeSystemAppViewer;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomEncapDocSeries;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.FileExtractor;

public class MimeSystemAppFactory implements SeriesViewerFactory {

    public static final String NAME = "default system application"; //$NON-NLS-1$
    public static final Icon ICON = new ImageIcon(MimeInspector.class.getResource("/icon/16x16/apps-system.png")); //$NON-NLS-1$
    public static final MimeSystemAppViewer mimeSystemViewer = new MimeSystemAppViewer() {
        @Override
        public String getPluginName() {
            return Messages.getString("MimeSystemAppViewer.app"); //$NON-NLS-1$
        }

        @Override
        public void addSeries(MediaSeries series) {
            if (series instanceof DicomVideoSeries || series instanceof DicomEncapDocSeries) {
                // As SUN JRE supports only Gnome and responds "true" for Desktop.isDesktopSupported()
                // in KDE session, but actually does not support it.
                // http://bugs.sun.com/view_bug.do?bug_id=6486393
                if (AbstractProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                    FileExtractor extractor = (FileExtractor) series;
                    startAssociatedProgramFromLinux(extractor.getExtractFile());

                } else if (AbstractProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
                    // Workaround of the bug with mpg file see http://bugs.sun.com/view_bug.do?bug_id=6599987
                    FileExtractor extractor = (FileExtractor) series;
                    File file = extractor.getExtractFile();
                    startAssociatedProgramFromWinCMD(file.getAbsolutePath());
                } else if (Desktop.isDesktopSupported()) {
                    final Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        FileExtractor extractor = (FileExtractor) series;
                        startAssociatedProgramFromDesktop(desktop, extractor.getExtractFile());
                    }
                }
            }
        }
    };

    public MimeSystemAppFactory() {
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
        return DicomMediaIO.SERIES_VIDEO_MIMETYPE.equals(mimeType)
            || DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        return false;
    }

    @Override
    public SeriesViewer createSeriesViewer(Hashtable<String, Object> properties) {
        return mimeSystemViewer;
    }

    @Override
    public int getLevel() {
        return 100;
    }
}
