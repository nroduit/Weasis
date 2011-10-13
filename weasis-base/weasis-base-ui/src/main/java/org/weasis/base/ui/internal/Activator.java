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
package org.weasis.base.ui.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.base.ui.WeasisApp;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;

public class Activator implements BundleActivator, ServiceListener {

    private static final String dataExplorerViewFilter = String.format(
        "(%s=%s)", Constants.OBJECTCLASS, DataExplorerView.class.getName()); //$NON-NLS-1$
    private BundleContext context = null;

    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        // WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                final WeasisWin app = WeasisWin.getInstance();
                try {
                    app.createMainPanel();
                    app.showWindow();

                } catch (Exception ex) {
                    // Nimbus bug, hangs GUI: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6785663
                    // It is better to exit than to let run a zombie process
                    System.err.println("Could not start GUI: " + ex); //$NON-NLS-1$
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        });

        // Register "weasis" command
        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "weasis"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, WeasisApp.functions);
        context.registerService(WeasisApp.class.getName(), WeasisApp.getInstance(), dict);

        // Explorer (with non immediate instance)
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                ServiceTracker m_tracker = new ServiceTracker(context, DataExplorerView.class.getName(), null);
                // Must keep the tracker open, because calling close() will unget service. This is a problem because the
                // desactivate method is called although the service stay alive in UI.
                m_tracker.open();
                final Object[] services = m_tracker.getServices();
                for (int i = 0; (services != null) && (i < services.length); i++) {
                    synchronized (UIManager.EXPLORER_PLUGINS) {
                        if (!UIManager.EXPLORER_PLUGINS.contains(services[i])
                            && services[i] instanceof DataExplorerView) {
                            final DataExplorerView explorer = (DataExplorerView) services[i];
                            UIManager.EXPLORER_PLUGINS.add(explorer);

                            if (explorer.getDataExplorerModel() != null) {
                                explorer.getDataExplorerModel().addPropertyChangeListener(WeasisWin.getInstance());
                            }

                            if (explorer instanceof DockableTool) {
                                final DockableTool dockable = (DockableTool) explorer;
                                dockable.registerToolAsDockable();
                                dockable.showDockable();
                            }
                        }
                    }
                }
                // Add all the service listeners
                try {
                    context.addServiceListener(Activator.this, dataExplorerViewFilter);
                } catch (InvalidSyntaxException e) {
                    e.printStackTrace();
                }
            }

        });
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        this.context = null;
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Explorer (with non immediate instance) and WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {

                final ServiceReference m_ref = event.getServiceReference();
                Object service = context.getService(m_ref);
                if (service == null) {
                    return;
                }
                if (service instanceof DataExplorerView) {
                    final DataExplorerView explorer = (DataExplorerView) service;
                    synchronized (UIManager.EXPLORER_PLUGINS) {
                        if (event.getType() == ServiceEvent.REGISTERED) {
                            if (!UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                                //                                if ("Media Explorer".equals(explorer.getUIName())) { //$NON-NLS-1$
                                // // in this case, if there are several Explorers, the Media Explorer is selected by
                                // // default
                                // UIManager.EXPLORER_PLUGINS.add(0, explorer);
                                // } else {
                                UIManager.EXPLORER_PLUGINS.add(explorer);
                                // }
                                if (explorer.getDataExplorerModel() != null) {
                                    explorer.getDataExplorerModel().addPropertyChangeListener(WeasisWin.getInstance());
                                }
                                if (explorer instanceof DockableTool) {
                                    final DockableTool dockable = (DockableTool) explorer;
                                    dockable.registerToolAsDockable();
                                    dockable.showDockable();
                                }

                                // BundleTools.logger.log(LogService.LOG_INFO, "Register data explorer Plug-in: " +
                                // m_ref.toString());
                            }
                        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                            GuiExecutor.instance().execute(new Runnable() {

                                @Override
                                public void run() {
                                    if (UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                                        if (explorer.getDataExplorerModel() != null) {
                                            explorer.getDataExplorerModel().removePropertyChangeListener(
                                                WeasisWin.getInstance());
                                        }
                                        UIManager.EXPLORER_PLUGINS.remove(explorer);
                                        explorer.dispose();
                                        // TODO unregister property change of the model
                                        // BundleTools.logger.log(LogService.LOG_INFO,
                                        // "Unregister data explorer Plug-in: " +
                                        // m_ref.toString());
                                        // Unget service object and null references.
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
