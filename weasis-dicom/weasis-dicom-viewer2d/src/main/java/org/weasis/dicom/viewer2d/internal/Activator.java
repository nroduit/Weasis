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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.ToolBarFactory;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;

public class Activator implements BundleActivator, ServiceListener {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();
    private static final String TOOLBAR_FILTER = String.format(
        "(%s=%s)", Constants.OBJECTCLASS, ToolBarFactory.class.getName()); //$NON-NLS-1$
    private static final String TOOL_FILTER = String.format(
        "(%s=%s)", Constants.OBJECTCLASS, DockableTool.class.getName()); //$NON-NLS-1$

    private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        PREFERENCES.init(context);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                Dictionary<String, Object> dict = new Hashtable<String, Object>();
                dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); //$NON-NLS-1$
                dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions);
                context.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);

                try {
                    ServiceReference[] scrServiceRef =
                        context.getServiceReferences(ToolBarFactory.class.getName(), null);
                    for (int i = 0; (scrServiceRef != null) && (i < scrServiceRef.length); i++) {
                        synchronized (View2dContainer.TOOLBARS) {
                            // The container should referenced as a property in the provided service
                            if (Boolean.valueOf((String) scrServiceRef[i].getProperty(View2dContainer.class.getName()))) {
                                Object service = context.getService(scrServiceRef[i]);
                                if (service instanceof ToolBarFactory) {
                                    final Toolbar bar = ((ToolBarFactory) service).createToolbar(null);
                                    if (bar != null && !View2dContainer.TOOLBARS.contains(bar)) {
                                        View2dContainer.TOOLBARS.add(bar);
                                    }
                                }
                            }
                        }
                    }
                } catch (InvalidSyntaxException e1) {
                    e1.printStackTrace();
                }

                try {
                    ServiceReference[] scrServiceRef = context.getServiceReferences(DockableTool.class.getName(), null);
                    for (int i = 0; (scrServiceRef != null) && (i < scrServiceRef.length); i++) {
                        synchronized (View2dContainer.TOOLS) {
                            // The container should referenced as a property in the provided service
                            if (Boolean.valueOf((String) scrServiceRef[i].getProperty(View2dContainer.class.getName()))) {
                                Object service = context.getService(scrServiceRef[i]);
                                if (service instanceof DockableTool && !View2dContainer.TOOLS.contains(service)) {
                                    View2dContainer.TOOLS.add((DockableTool) service);
                                    ((DockableTool) service).registerToolAsDockable();
                                }
                            }
                        }
                    }
                } catch (InvalidSyntaxException e1) {
                    e1.printStackTrace();
                }

                // Register the service for new event, but look with context.getServiceReferences() for services
                // previously
                // registered
                try {
                    context.addServiceListener(Activator.this, TOOLBAR_FILTER);
                    // context.addServiceListener(Activator.this, TOOL_FILTER);
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
        UIManager.closeSeriesViewerType(View2dContainer.class);
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Instantiate in the EDT (necessary for UI components with Substance)
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                final ServiceReference m_ref = event.getServiceReference();
                Object service = context.getService(m_ref);
                if (service != null) {
                    if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
                        if (service instanceof ToolBarFactory) {
                            final Toolbar bar = ((ToolBarFactory) service).createToolbar(null);
                            if (bar != null) {
                                synchronized (View2dContainer.TOOLBARS) {
                                    if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
                                        if (event.getType() == ServiceEvent.REGISTERED) {
                                            if (!View2dContainer.TOOLBARS.contains(bar)) {
                                                View2dContainer.TOOLBARS.add(bar);
                                                updateToolbarView();
                                            }
                                        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                                            if (View2dContainer.TOOLBARS.contains(bar)) {
                                                View2dContainer.TOOLBARS.remove(bar);
                                                updateToolbarView();
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (service instanceof DockableTool) {
                            final DockableTool tool = (DockableTool) service;
                            synchronized (View2dContainer.TOOLS) {
                                if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
                                    if (event.getType() == ServiceEvent.REGISTERED) {
                                        if (!View2dContainer.TOOLS.contains(tool)) {
                                            View2dContainer.TOOLS.add(tool);
                                            tool.registerToolAsDockable();
                                        }
                                    } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                                        if (View2dContainer.TOOLS.contains(tool)) {
                                            View2dContainer.TOOLS.remove(tool);
                                            tool.closeDockable();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private static void updateToolbarView() {
        ImageViewerPlugin<DicomImageElement> view = EventManager.getInstance().getSelectedView2dContainer();
        if (view instanceof View2dContainer) {
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView.getDataExplorerModel() instanceof DicomModel) {
                DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.UpdateToolbars, view, null,
                    view));
            }
        }
    }
}
