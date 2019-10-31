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
package org.weasis.core.api.gui.util;

import java.util.Timer;
import java.util.TimerTask;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

@SuppressWarnings("serial")
public abstract class DynamicMenu extends JMenu {

    public DynamicMenu() {
        super();
    }

    public DynamicMenu(Action a) {
        super(a);
    }

    public DynamicMenu(String s, boolean b) {
        super(s, b);
    }

    public DynamicMenu(String s) {
        super(s);
    }

    public abstract void popupMenuWillBecomeVisible();

	public void popupMenuWillBecomeInvisible() {
	    // Wait the action performed of JMenuItem (Bug on Mac menu bar)
		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				removeAll();
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 250);
	}

    public void popupMenuCanceled() {
    }

    public void addPopupMenuListener() {
        // #WEA-6 - workaround, PopupMenuListener doesn't work on Mac in the top bar with native look and feel
        if (AppProperties.OPERATING_SYSTEM.startsWith("mac") && Boolean.TRUE.toString().equals(System.getProperty("apple.laf.useScreenMenuBar", Boolean.FALSE.toString()))) { //$NON-NLS-1$
            this.addChangeListener(e -> {
                if (DynamicMenu.this.isSelected()) {
                	DynamicMenu.this.removeAll();
                    DynamicMenu.this.popupMenuWillBecomeVisible();
                } else {
                    DynamicMenu.this.popupMenuWillBecomeInvisible();
                }
            });
        } else {
            JPopupMenu menuExport = this.getPopupMenu();
            menuExport.addPopupMenuListener(new PopupMenuListener() {

                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    DynamicMenu.this.popupMenuWillBecomeVisible();
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    DynamicMenu.this.popupMenuWillBecomeInvisible();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                    DynamicMenu.this.popupMenuCanceled();
                }
            });
        }
    }
}
