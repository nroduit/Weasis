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
import java.io.IOException;
import java.util.List;

import javax.swing.JMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomEncapDocSeries;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.FileExtractor;

public class MimeSystemAppViewer implements SeriesViewer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MimeSystemAppViewer.class);

    @Override
    public String getPluginName() {
        return Messages.getString("MimeSystemAppViewer.app"); //$NON-NLS-1$
    }

    @Override
    public void close() {
    }

    @Override
    public List<MediaSeries> getOpenSeries() {
        return null;
    }

    @Override
    public void addSeries(MediaSeries series) {
        if (series instanceof DicomVideoSeries || series instanceof DicomEncapDocSeries) {
            // As SUN JRE supports only Gnome and responds "true" for Desktop.isDesktopSupported()
            // in KDE session, but actually does not support it.
            // http://bugs.sun.com/view_bug.do?bug_id=6486393
            if (AbstractProperties.OPERATING_SYSTEM.startsWith("linux")) { //$NON-NLS-1$
                FileExtractor extractor = (FileExtractor) series;
                File file = extractor.getExtractFile();
                if (file != null) {
                    try {
                        String cmd = String.format("xdg-open %s", file.getAbsolutePath()); //$NON-NLS-1$
                        Runtime.getRuntime().exec(cmd);
                    } catch (IOException e1) {
                        LOGGER.error("Cannot open {} with the default system application", file.getName()); //$NON-NLS-1$
                    }
                }

            } else if (AbstractProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
                // Workaround of the bug with mpg file see http://bugs.sun.com/view_bug.do?bug_id=6599987
                FileExtractor extractor = (FileExtractor) series;
                File file = extractor.getExtractFile();
                startAssociatedProgram(file.getAbsolutePath());
            } else if (Desktop.isDesktopSupported()) {
                final Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {

                    FileExtractor extractor = (FileExtractor) series;
                    File file = extractor.getExtractFile();
                    if (file != null) {
                        try {
                            desktop.open(file);
                        } catch (IOException e1) {
                            LOGGER.error("Cannot open {} with the default system application", file.getName()); //$NON-NLS-1$
                        }
                    }
                }
            }
        }
    }

    public static void startAssociatedProgram(String file) {
        try {
            Runtime.getRuntime().exec("cmd /c \"" + file + '"'); //$NON-NLS-1$
        } catch (IOException e) {
            LOGGER.error("Cannot open {} with the default system application", file); //$NON-NLS-1$
            e.printStackTrace();
        }
    }

    @Override
    public void removeSeries(MediaSeries sequence) {

    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menu) {
        return null;
    }

    @Override
    public WtoolBar[] getToolBar() {
        return null;
    }

    @Override
    public WtoolBar getStatusBar() {
        return null;
    }

    @Override
    public PluginTool[] getToolPanel() {
        return null;
    }

    @Override
    public void setSelected(boolean selected) {
    }

    @Override
    public MediaSeriesGroup getGroupID() {
        return null;
    }

}
