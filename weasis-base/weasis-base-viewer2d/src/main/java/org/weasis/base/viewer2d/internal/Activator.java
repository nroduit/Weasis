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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.Toolbar;

public class Activator implements BundleActivator, ServiceListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        try {
            for (ServiceReference<InsertableFactory> serviceReference : bundleContext.getServiceReferences(
                InsertableFactory.class, null)) {
                // The View2dContainer name should be referenced as a property in the provided service
                if (Boolean.valueOf((String) serviceReference.getProperty(View2dContainer.class.getName()))) {
                    registerComponent(bundleContext, bundleContext.getService(serviceReference), false);
                }
            }
        } catch (InvalidSyntaxException e1) {
            e1.printStackTrace();
        }

        // Add listener for getting new service events
        try {
            bundleContext.addServiceListener(Activator.this, "(" + Constants.OBJECTCLASS + "="
                + InsertableFactory.class.getName() + ")");
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        EventManager.getInstance().savePreferences(bundleContext);
        UIManager.closeSeriesViewerType(View2dContainer.class);
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {

        final ServiceReference<?> m_ref = event.getServiceReference();
        // The View2dContainer name should be referenced as a property in the provided service
        if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
            final BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
            Object service = context.getService(m_ref);
            if (service instanceof InsertableFactory) {
                InsertableFactory factory = (InsertableFactory) service;
                if (event.getType() == ServiceEvent.REGISTERED) {
                    registerComponent(context, factory, true);
                } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                    if (Type.TOOLBAR.equals(factory.getType())) {
                        boolean updateGUI = false;
                        synchronized (View2dContainer.TOOLBARS) {
                            for (int i = View2dContainer.TOOLBARS.size() - 1; i >= 0; i--) {
                                Insertable b = View2dContainer.TOOLBARS.get(i);
                                if (factory.isComponentCreatedByThisFactory(b)) {
                                    View2dContainer.TOOLBARS.remove(i);
                                    factory.dispose(b);
                                    updateGUI = true;
                                }
                            }
                        }
                        if (updateGUI) {
                            updateViewerUI(ObservableEvent.BasicAction.UpdateToolbars);
                        }
                    } else if (Type.TOOL.equals(factory.getType())) {
                        synchronized (View2dContainer.TOOLS) {
                            for (int i = View2dContainer.TOOLS.size() - 1; i >= 0; i--) {
                                DockableTool t = View2dContainer.TOOLS.get(i);
                                if (factory.isComponentCreatedByThisFactory(t)) {
                                    View2dContainer.TOOLS.remove(i);
                                    factory.dispose(t);
                                    t.closeDockable();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void registerComponent(final BundleContext bundleContext, final InsertableFactory factory,
        final boolean updateGUI) {
        // Instantiate UI components in EDT (necessary with Substance Theme)
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (factory != null) {
                    if (Type.TOOLBAR.equals(factory.getType())) {
                        Insertable instance = factory.createInstance(null);
                        if (instance instanceof Toolbar && !View2dContainer.TOOLBARS.contains(instance)) {
                            Toolbar bar = (Toolbar) instance;
                            View2dContainer.TOOLBARS.add(bar);
                            if (updateGUI) {
                                updateViewerUI(ObservableEvent.BasicAction.UpdateToolbars);
                            }
                            LOGGER.debug("Add Toolbar [{}] for {}", bar, View2dContainer.class.getName());
                        }
                    } else if (Type.TOOL.equals(factory.getType())) {
                        Insertable instance = factory.createInstance(null);
                        if (instance instanceof DockableTool && !View2dContainer.TOOLS.contains(factory)) {
                            DockableTool tool = (DockableTool) instance;
                            View2dContainer.TOOLS.add(tool);
                            if (updateGUI) {
                                tool.showDockable();
                            }
                            LOGGER.debug("Add Tool [{}] for {}", tool, View2dContainer.class.getName());
                        }
                    }
                }
            }
        });
    }

    private static void updateViewerUI(BasicAction action) {
        ImageViewerPlugin<ImageElement> view = EventManager.getInstance().getSelectedView2dContainer();
        if (view instanceof View2dContainer) {
            ViewerPluginBuilder.DefaultDataModel.firePropertyChange(new ObservableEvent(action, view, null, view));
        }
    }
}
