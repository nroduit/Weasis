/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.viewer2d.internal;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MPRContainer;

public class Activator implements BundleActivator, ServiceListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(final BundleContext bundleContext) throws Exception {

        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions);
        bundleContext.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);

        try {
            for (ServiceReference<InsertableFactory> serviceReference : bundleContext
                .getServiceReferences(InsertableFactory.class, null)) {
                // The View2dContainer name should be referenced as a property in the provided service
                if (Boolean.valueOf((String) serviceReference.getProperty(View2dContainer.class.getName()))) {
                    registerComponent(bundleContext, bundleContext.getService(serviceReference));
                }
            }
        } catch (InvalidSyntaxException e1) {
            e1.printStackTrace();
        }

        // Add listener for getting new service events
        try {
            bundleContext.addServiceListener(Activator.this, "(" + Constants.OBJECTCLASS + "=" //$NON-NLS-1$ //$NON-NLS-2$
                + InsertableFactory.class.getName() + ")"); //$NON-NLS-1$
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        ImageViewerPlugin<DicomImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
        if (container instanceof MPRContainer) {
            // Remove crosshair tool
            container.setSelected(false);
        }
        // Save preferences
        EventManager.getInstance().savePreferences(bundleContext);
        UIManager.closeSeriesViewerType(MPRContainer.class);
        UIManager.closeSeriesViewerType(View2dContainer.class);
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // TODO add MPRContainer service
        final ServiceReference<?> m_ref = event.getServiceReference();
        // The View2dContainer name should be referenced as a property in the provided service
        if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
            final BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
            Object service = context.getService(m_ref);
            if (service instanceof InsertableFactory) {
                InsertableFactory factory = (InsertableFactory) service;
                if (event.getType() == ServiceEvent.REGISTERED) {
                    registerComponent(context, factory);
                } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                    if (Type.TOOLBAR.equals(factory.getType())) {
                        boolean updateGUI = false;
                        synchronized (View2dContainer.TOOLBARS) {
                            for (int i = View2dContainer.TOOLBARS.size() - 1; i >= 0; i--) {
                                Insertable b = View2dContainer.TOOLBARS.get(i);
                                if (factory.isComponentCreatedByThisFactory(b)) {
                                    Preferences prefs = BundlePreferences.getDefaultPreferences(context);
                                    if (prefs != null) {
                                        List<Insertable> list = Arrays.asList(b);
                                        InsertableUtil.savePreferences(list,
                                            prefs.node(View2dContainer.class.getSimpleName().toLowerCase()),
                                            Type.TOOLBAR);
                                    }

                                    View2dContainer.TOOLBARS.remove(i);
                                    factory.dispose(b);
                                    updateGUI = true;
                                }
                            }
                        }
                        if (updateGUI) {
                            updateViewerUI(ObservableEvent.BasicAction.UPDTATE_TOOLBARS);
                        }
                    } else if (Type.TOOL.equals(factory.getType())) {
                        synchronized (View2dContainer.TOOLS) {
                            for (int i = View2dContainer.TOOLS.size() - 1; i >= 0; i--) {
                                DockableTool t = View2dContainer.TOOLS.get(i);
                                if (factory.isComponentCreatedByThisFactory(t)) {
                                    Preferences prefs = BundlePreferences.getDefaultPreferences(context);
                                    if (prefs != null) {
                                        Preferences containerNode =
                                            prefs.node(View2dContainer.class.getSimpleName().toLowerCase());
                                        InsertableUtil.savePreferences(Arrays.asList(t), containerNode, Type.TOOL);
                                    }

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

    private void registerComponent(final BundleContext bundleContext, final InsertableFactory factory) {
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
                            updateViewerUI(ObservableEvent.BasicAction.UPDTATE_TOOLBARS);
                            LOGGER.debug("Add Toolbar [{}] for {}", bar, View2dContainer.class.getName()); //$NON-NLS-1$
                        }
                    } else if (Type.TOOL.equals(factory.getType())) {
                        Insertable instance = factory.createInstance(null);
                        if (instance instanceof DockableTool && !View2dContainer.TOOLS.contains(factory)) {
                            DockableTool tool = (DockableTool) instance;
                            View2dContainer.TOOLS.add(tool);
                            ImageViewerPlugin<DicomImageElement> view =
                                EventManager.getInstance().getSelectedView2dContainer();
                            if (view instanceof View2dContainer) {
                                tool.showDockable();
                            }
                            LOGGER.debug("Add Tool [{}] for {}", tool, View2dContainer.class.getName()); //$NON-NLS-1$
                        }
                    }
                }
            }
        });
    }

    private static void updateViewerUI(BasicAction action) {
        ImageViewerPlugin<DicomImageElement> view = EventManager.getInstance().getSelectedView2dContainer();
        if (view instanceof View2dContainer) {
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            DataExplorerModel model = dicomView.getDataExplorerModel();
            if (model != null) {
                model.firePropertyChange(new ObservableEvent(action, view, null, view));
            }
        }
    }
}
