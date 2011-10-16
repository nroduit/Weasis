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
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;

public class Activator implements BundleActivator, ServiceListener {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();
    private static final String TOOLBAR_FILTER = String.format(
        "(%s=%s)", Constants.OBJECTCLASS, Toolbar.class.getName()); //$NON-NLS-1$

    private BundleContext context = null;

    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;
        PREFERENCES.init(context);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                Dictionary<String, Object> dict = new Hashtable<String, Object>();
                dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); //$NON-NLS-1$
                dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions);
                context.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);

                // Add standard toolbars
                ViewerToolBar<DicomImageElement> bar = new ViewerToolBar<DicomImageElement>(EventManager.getInstance());
                View2dContainer.TOOLBARS.add(bar);
                View2dContainer.TOOLBARS.add(bar.getMeasureToolBar());

                ServiceTracker m_tracker = new ServiceTracker(context, Toolbar.class.getName(), null);
                // Must keep the tracker open, because calling close() will unget service. This is a problem because the
                // desactivate method is called although the service stay alive in UI.
                m_tracker.open();
                final Object[] services = m_tracker.getServices();
                for (int i = 0; (services != null) && (i < services.length); i++) {
                    synchronized (View2dContainer.TOOLBARS) {
                        if (services[i] instanceof WtoolBar && !View2dContainer.TOOLBARS.contains(services[i])) {
                            View2dContainer.TOOLBARS.add((WtoolBar) services[i]);
                        }
                    }
                }
                // Add all the service listeners
                try {
                    context.addServiceListener(Activator.this, TOOLBAR_FILTER);
                } catch (InvalidSyntaxException e) {
                    e.printStackTrace();
                }
            }
        });
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

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Instantiate in the EDT
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {

                final ServiceReference m_ref = event.getServiceReference();
                Object service = context.getService(m_ref);
                if (service == null) {
                    return;
                }
                if (service instanceof WtoolBar) {
                    final WtoolBar bar = (WtoolBar) service;
                    synchronized (View2dContainer.TOOLBARS) {
                        if (event.getType() == ServiceEvent.REGISTERED) {
                            if (!View2dContainer.TOOLBARS.contains(bar)) {
                                View2dContainer.TOOLBARS.add(bar);

                                ImageViewerPlugin<DicomImageElement> view =
                                    EventManager.getInstance().getSelectedView2dContainer();
                                if (view instanceof View2dContainer) {
                                    DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
                                    if (dicomView.getDataExplorerModel() instanceof DicomModel) {
                                        DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                                        model.firePropertyChange(new ObservableEvent(
                                            ObservableEvent.BasicAction.UpdateToolbars, view, null, view));
                                    }
                                }
                            }
                        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                            GuiExecutor.instance().execute(new Runnable() {

                                @Override
                                public void run() {
                                    if (View2dContainer.TOOLBARS.contains(bar)) {
                                        View2dContainer.TOOLBARS.remove(bar);
                                        ImageViewerPlugin<DicomImageElement> view =
                                            EventManager.getInstance().getSelectedView2dContainer();
                                        if (view instanceof View2dContainer) {
                                            DataExplorerView dicomView =
                                                UIManager.getExplorerplugin(DicomExplorer.NAME);
                                            if (dicomView.getDataExplorerModel() instanceof DicomModel) {
                                                DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                                                model.firePropertyChange(new ObservableEvent(
                                                    ObservableEvent.BasicAction.UpdateToolbars, view, null, view));
                                            }
                                        }
                                        context.ungetService(m_ref);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });
    }

}
