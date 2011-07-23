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
package org.weasis.base.viewer2d.internal;

import java.util.ArrayList;
import java.util.List;

import org.noos.xing.mydoggy.Content;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;

public class Activator implements BundleActivator {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();

    public void start(final BundleContext context) throws Exception {
        PREFERENCES.init(context);
    }

    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        EventManager.getInstance().savePreferences();
        PREFERENCES.close();
        final List<ViewerPlugin> pluginsToRemove = new ArrayList<ViewerPlugin>();
        synchronized (UIManager.VIEWER_PLUGINS) {
            for (final ViewerPlugin plugin : UIManager.VIEWER_PLUGINS) {
                if (plugin instanceof View2dContainer) {
                    // Do not close Series directly, it can produce deadlock.
                    pluginsToRemove.add(plugin);
                }
            }
        }
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (final ViewerPlugin viewerPlugin : pluginsToRemove) {
                    viewerPlugin.close();
                    Content content =
                        UIManager.toolWindowManager.getContentManager().getContent(viewerPlugin.getDockableUID());
                    if (content != null) {
                        UIManager.toolWindowManager.getContentManager().removeContent(content);
                    }
                }
            }
        });
    }

}
