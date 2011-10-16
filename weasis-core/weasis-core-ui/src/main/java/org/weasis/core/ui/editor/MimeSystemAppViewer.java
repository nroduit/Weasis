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
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

public abstract class MimeSystemAppViewer implements SeriesViewer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MimeSystemAppViewer.class);

    @Override
    public void close() {
    }

    @Override
    public List<MediaSeries> getOpenSeries() {
        return null;
    }

    public static void startAssociatedProgramFromLinux(File file) {
        if (file != null) {
            try {
                String cmd = String.format("xdg-open %s", file.getAbsolutePath()); //$NON-NLS-1$
                Runtime.getRuntime().exec(cmd);
            } catch (IOException e1) {
                LOGGER.error("Cannot open {} with the default system application", file.getName()); //$NON-NLS-1$
            }
        }
    }

    public static void startAssociatedProgramFromWinCMD(String file) {
        try {
            Runtime.getRuntime().exec("cmd /c \"" + file + '"'); //$NON-NLS-1$
        } catch (IOException e) {
            LOGGER.error("Cannot open {} with the default system application", file); //$NON-NLS-1$
            e.printStackTrace();
        }
    }

    public static void startAssociatedProgramFromDesktop(final Desktop desktop, File file) {
        if (file != null) {
            try {
                desktop.open(file);
            } catch (IOException e1) {
                LOGGER.error("Cannot open {} with the default system application", file.getName()); //$NON-NLS-1$
            }
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
    public List<Toolbar> getToolBar() {
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
