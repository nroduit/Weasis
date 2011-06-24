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
package org.weasis.dicom.viewer2d.internal;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.felix.service.command.CommandProcessor;
import org.noos.xing.mydoggy.Content;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;

public class Activator implements BundleActivator {

    public final static BundlePreferences PREFERENCES = new BundlePreferences();

    @Override
    public void start(final BundleContext context) throws Exception {
        PREFERENCES.init(context);

        // GuiExecutor.instance().execute(new Runnable() {
        //
        // @Override
        // public void run() {
        // Dictionary<String, Object> dict = new Hashtable<String, Object>();
        //                dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); //$NON-NLS-1$
        // dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions);
        // context.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);
        // }
        // });
    }

    @Override
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
