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
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.DockableToolFactory;
import org.weasis.core.ui.util.ToolBarFactory;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MPRContainer;
import org.weasis.dicom.viewer2d.sr.SRContainer;

public class Activator implements BundleActivator, ServiceListener {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {

        GuiExecutor.instance().execute(new Runnable() {

            /*
             * (non-Javadoc)
             * 
             * @see java.lang.Runnable#run()
             */
            @Override
            public void run() {
                Dictionary<String, Object> dict = new Hashtable<String, Object>();
                dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); //$NON-NLS-1$
                dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions);
                bundleContext.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);

                try {
                    for (ServiceReference<ToolBarFactory> serviceReference : bundleContext.getServiceReferences(
                        ToolBarFactory.class, null)) {
                        // The container should referenced as a property in the provided service
                        if (Boolean.valueOf((String) serviceReference.getProperty(View2dContainer.class.getName()))) {
                            ToolBarFactory factory = bundleContext.getService(serviceReference);
                            if (factory != null) {
                                final Toolbar bar = factory.createToolbar(null);
                                if (bar != null && !View2dContainer.TOOLBARS.contains(bar)) {
                                    View2dContainer.TOOLBARS.add(bar);
                                }
                            }
                        }
                    }
                } catch (InvalidSyntaxException e1) {
                    e1.printStackTrace();
                }

                try {
                    for (ServiceReference<DockableToolFactory> serviceReference : bundleContext.getServiceReferences(
                        DockableToolFactory.class, null)) {
                        // The container should referenced as a property in the provided service
                        if (Boolean.valueOf((String) serviceReference.getProperty(View2dContainer.class.getName()))) {
                            DockableToolFactory factory = bundleContext.getService(serviceReference);
                            if (factory != null) {
                                final DockableTool tool = factory.createTool(null);
                                if (tool != null && !View2dContainer.TOOLS.contains(factory)) {
                                    View2dContainer.TOOLS.add(tool);
                                }
                            }
                        }
                    }
                } catch (InvalidSyntaxException e1) {
                    e1.printStackTrace();
                }

                /*
                 * Register services for new events after getting those previously registered from
                 * context.getServiceReferences()
                 */
                try {
                    bundleContext.addServiceListener(Activator.this,
                        String.format("(%s=%s)", Constants.OBJECTCLASS, ToolBarFactory.class.getName())); //$NON-NLS-1$
                    bundleContext.addServiceListener(Activator.this,
                        String.format("(%s=%s)", Constants.OBJECTCLASS, DockableToolFactory.class.getName())); //$NON-NLS-1$
                } catch (InvalidSyntaxException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        EventManager.getInstance().savePreferences(bundleContext);
        UIManager.closeSeriesViewerType(MPRContainer.class);
        UIManager.closeSeriesViewerType(View2dContainer.class);
        UIManager.closeSeriesViewerType(SRContainer.class);
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Instantiate in the EDT (necessary for UI components with Substance)
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                final ServiceReference<?> m_ref = event.getServiceReference();
                final BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
                if (Boolean.valueOf((String) m_ref.getProperty(View2dContainer.class.getName()))) {
                    Object service = context.getService(m_ref);
                    if (service instanceof ToolBarFactory) {
                        final Toolbar bar = ((ToolBarFactory) service).createToolbar(null);

                        if (event.getType() == ServiceEvent.REGISTERED) {
                            if (bar != null && !View2dContainer.TOOLBARS.contains(bar)) {
                                View2dContainer.TOOLBARS.add(bar);
                                updateToolbarView();
                            }
                        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                            if (View2dContainer.TOOLBARS.contains(bar)) {
                                View2dContainer.TOOLBARS.remove(bar);
                                updateToolbarView();
                            }
                        }

                    } else if (service instanceof DockableToolFactory) {
                        final DockableTool tool = ((DockableToolFactory) service).createTool(null);
                        if (event.getType() == ServiceEvent.REGISTERED) {
                            if (!View2dContainer.TOOLS.contains(tool)) {
                                View2dContainer.TOOLS.add(tool);
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
