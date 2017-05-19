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
package org.weasis.base.ui.internal;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;

import javax.swing.LookAndFeel;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.Bundle;
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
import org.weasis.base.ui.WeasisApp;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.base.ui.gui.WeasisWinPropertyChangeListener;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.pref.GeneralSetting;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // Starts core bundles for initialization before calling UI components
        Bundle bundle = FrameworkUtil.getBundle(BundleTools.class);
        if (bundle != null) {
            bundle.start();
        }
        bundle = FrameworkUtil.getBundle(UIManager.class);
        if (bundle != null) {
            bundle.start();
        }
        String className = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.look"); //$NON-NLS-1$
        if (StringUtil.hasText(className)) {
            LookAndFeel lf = javax.swing.UIManager.getLookAndFeel();
            if (lf == null || !className.equals(lf.getClass().getName())) {
                GeneralSetting.setLookAndFeel(className);
            }
        }

        // WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().invokeAndWait(() -> {
            final WeasisWin app = WeasisWin.getInstance();
            try {
                app.createMainPanel();
                app.showWindow();

            } catch (Exception ex) {
                // It is better to exit than to let run a zombie process
                LOGGER.error("Cannot start GUI", ex);//$NON-NLS-1$
                System.exit(-1);
            }
        });

        // Register "weasis" command
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "weasis"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, WeasisApp.functions);
        bundleContext.registerService(WeasisApp.class.getName(), WeasisApp.getInstance(), dict);

        // Explorer (with immediate instance)
        GuiExecutor.instance().execute(() -> {
          registerExistingDataExplorer(bundleContext);
        });
        
        // Add all the service listeners
        try {
            bundleContext.addServiceListener(Activator.this,
                String.format("(%s=%s)", Constants.OBJECTCLASS, DataExplorerViewFactory.class.getName())); //$NON-NLS-1$
        } catch (InvalidSyntaxException e3) {
            LOGGER.error("Add service listener", e3); //$NON-NLS-1$
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // UnRegister default model
        ViewerPluginBuilder.DefaultDataModel
            .removePropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        // Explorer (with non immediate instance) and WeasisWin must be instantiate in the EDT
        GuiExecutor.instance().execute(() -> dataExplorerChanged(event));
    }

    private void dataExplorerChanged(final ServiceEvent event) {
        final ServiceReference<?> mRef = event.getServiceReference();
        final BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
        Object service = context.getService(mRef);
        if (service instanceof DataExplorerViewFactory) {
            final DataExplorerView explorer = ((DataExplorerViewFactory) service).createDataExplorerView(null);
            if (event.getType() == ServiceEvent.REGISTERED) {
                registerDataExplorer(explorer);
            } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                GuiExecutor.instance().execute(() -> {
                    if (UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                        Optional.ofNullable(explorer.getDataExplorerModel()).ifPresent(
                            e -> e.removePropertyChangeListener(WeasisWinPropertyChangeListener.getInstance()));
                        UIManager.EXPLORER_PLUGINS.remove(explorer);
                        explorer.dispose();
                        LOGGER.info("Unregister data explorer Plug-in: {}", explorer.getUIName()); //$NON-NLS-1$
                    }
                    // Unget service object and null references.
                    context.ungetService(mRef);
                });
            }
        }
    }

    private static void registerExistingDataExplorer(BundleContext bundleContext) {
        // Register default model
        ViewerPluginBuilder.DefaultDataModel.addPropertyChangeListener(WeasisWinPropertyChangeListener.getInstance());

        try {
            for (ServiceReference<DataExplorerViewFactory> serviceReference : bundleContext
                .getServiceReferences(DataExplorerViewFactory.class, null)) {
                DataExplorerViewFactory service = bundleContext.getService(serviceReference);
                if (service != null) {
                    String className1 = BundleTools.SYSTEM_PREFERENCES.getProperty(service.getClass().getName());
                    if (StringUtil.hasText(className1) && !Boolean.valueOf(className1)) {
                        continue;
                    }
                    registerDataExplorer(service.createDataExplorerView(null));
                }
            }

        } catch (InvalidSyntaxException e2) {
            LOGGER.error("Register data explorer", e2); //$NON-NLS-1$
        }
    }

    private static void registerDataExplorer(DataExplorerView explorer) {
        if (explorer != null && !UIManager.EXPLORER_PLUGINS.contains(explorer)) {
            UIManager.EXPLORER_PLUGINS.add(explorer);
            Optional.ofNullable(explorer.getDataExplorerModel())
                .ifPresent(e -> e.addPropertyChangeListener(WeasisWinPropertyChangeListener.getInstance()));
            if (explorer instanceof DockableTool) {
                final DockableTool dockable = (DockableTool) explorer;
                dockable.showDockable();
            }
            LOGGER.info("Register data explorer Plug-in: {}", explorer.getUIName()); //$NON-NLS-1$
        }
    }
}
