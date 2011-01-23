/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.base.ui;

import java.awt.AWTException;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.IOException;

import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GuiExecutor;

public class WeasisApp {

    public static final String[] functions = { "info", "ui" }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final WeasisApp instance = new WeasisApp();

    private WeasisApp() {
    }

    public final static WeasisApp getInstance() {
        return instance;
    }

    public void info(String[] argv) throws IOException {
        final String[] usage =
            { "Show information about Weasis", "Usage: weasis:info [Options]", "  -v --version		show version", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "  -? --help		show help" }; //$NON-NLS-1$

        Option opt = Options.compile(usage).parse(argv);

        if (opt.isSet("version")) { //$NON-NLS-1$
            System.out.println(AbstractProperties.WEASIS_VERSION);
        } else {
            opt.usage();
        }
    }

    public void ui(String[] argv) throws IOException {
        final String[] usage = { "Manage user interface", "Usage: weasis:ui [Options]", "  -q --quit		shutdown Weasis", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "  -v --visible		set window on top", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$

        Option opt = Options.compile(usage).parse(argv);
        if (opt.isSet("quit")) { //$NON-NLS-1$
            System.exit(0);
        } else if (opt.isSet("visible")) { //$NON-NLS-1$
            GuiExecutor.instance().execute(new Runnable() {

                public void run() {
                    WeasisWin app = WeasisWin.getInstance();
                    app.setVisible(true);
                    int state = app.getExtendedState();
                    state &= ~Frame.ICONIFIED;
                    app.setExtendedState(state);
                    app.setVisible(true);
                    /*
                     * Sets the window to be "always on top" instead using toFront() method that does not always bring
                     * the window to the front. It depends the platform, Windows XP or Ubuntu has the facility to
                     * prevent windows from stealing focus; instead it flashes the taskbar icon.
                     */
                    if (app.isAlwaysOnTopSupported()) {
                        app.setAlwaysOnTop(true);

                        try {
                            Thread.sleep(500L);
                            Robot robot = new Robot();
                            Point p = app.getLocationOnScreen();
                            robot.mouseMove(p.x + app.getWidth() / 2, p.y + 5);
                            // Simulate a mouse click
                            robot.mousePress(InputEvent.BUTTON1_MASK);
                            robot.mouseRelease(InputEvent.BUTTON1_MASK);
                        } catch (AWTException e1) {
                        } catch (InterruptedException e) {
                        } finally {
                            app.setAlwaysOnTop(false);
                        }

                    } else {
                        app.toFront();
                    }
                }
            });

        } else {
            opt.usage();
        }

    }

}
