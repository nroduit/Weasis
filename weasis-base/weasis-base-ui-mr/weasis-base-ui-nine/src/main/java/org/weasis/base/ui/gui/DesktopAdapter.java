/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.ui.gui;

import java.awt.Desktop;

import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;

public class DesktopAdapter {

    private DesktopAdapter() {
    }

    static void buildDesktopMenu(WeasisWin win) {

        // Works from Java 9 on specific Mac Menu
        Desktop app = Desktop.getDesktop();
        if (app.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            app.setQuitHandler((e, response) -> {
                if (win.closeWindow()) {
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }

        if (app.isSupported(Desktop.Action.APP_PREFERENCES)) {
            app.setPreferencesHandler(e -> {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win.getRootPaneContainer());
                PreferenceDialog dialog = new PreferenceDialog(win.getFrame());
                ColorLayerUI.showCenterScreen(dialog, layer);
            });
        }

        if (app.isSupported(Desktop.Action.APP_ABOUT)) {
            app.setAboutHandler(e -> {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win.getRootPaneContainer());
                WeasisAboutBox about = new WeasisAboutBox(win.getFrame());
                ColorLayerUI.showCenterScreen(about, layer);
            });
        }
        if (app.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            app.setOpenFileHandler(e -> win.dropFiles(e.getFiles(), null));
        }
    }
}
