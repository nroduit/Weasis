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
package org.weasis.core.ui.internal;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.FileModel;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        registerCommands(bundleContext);
        File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            FileUtil.readProperties(new File(dataFolder, "persitence.properties"), BundleTools.LOCAL_PERSISTENCE);//$NON-NLS-1$
        }
        MeasureTool.viewSetting.initMonitors();
        MeasureTool.viewSetting.applyPreferences(BundlePreferences.getDefaultPreferences(bundleContext));

        // Must be instantiate in EDT
        GuiExecutor.instance().execute(() -> {
            try {
                for (ServiceReference<SeriesViewerFactory> service : bundleContext
                    .getServiceReferences(SeriesViewerFactory.class, null)) {
                    SeriesViewerFactory factory = bundleContext.getService(service);
                    if (factory != null && !UIManager.SERIES_VIEWER_FACTORIES.contains(factory)) {
                        UIManager.SERIES_VIEWER_FACTORIES.add(factory);
                        LOGGER.info("Register series viewer plug-in: {}", factory.getUIName());
                    }
                }
            } catch (InvalidSyntaxException e) {
                LOGGER.error("", e);
            }
        });

        bundleContext.addServiceListener(this,
            String.format("(%s=%s)", Constants.OBJECTCLASS, SeriesViewerFactory.class.getName()));//$NON-NLS-1$
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        // Save preferences
        MeasureTool.viewSetting.savePreferences(BundlePreferences.getDefaultPreferences(bundleContext));
        File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
        if (dataFolder != null) {
            File file = new File(dataFolder, "persitence.properties"); //$NON-NLS-1$
            FileUtil.prepareToWriteFile(file);
            FileUtil.storeProperties(file, BundleTools.LOCAL_PERSISTENCE, null);
        }
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {

        // Must be instantiate in EDT
        GuiExecutor.instance().execute(() -> {
            ServiceReference<?> service = event.getServiceReference();
            BundleContext context = AppProperties.getBundleContext(Activator.this.getClass());
            SeriesViewerFactory viewerFactory = (SeriesViewerFactory) context.getService(service);
            Objects.requireNonNull(viewerFactory);

            if (event.getType() == ServiceEvent.REGISTERED) {
                if (!UIManager.SERIES_VIEWER_FACTORIES.contains(viewerFactory)) {
                    UIManager.SERIES_VIEWER_FACTORIES.add(viewerFactory);
                    LOGGER.info("Register series viewer plug-in: {}", viewerFactory.getUIName());
                }
            } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                if (UIManager.SERIES_VIEWER_FACTORIES.contains(viewerFactory)) {
                    LOGGER.info("Unregister series viewer plug-in: {}", viewerFactory.getUIName());
                    UIManager.SERIES_VIEWER_FACTORIES.remove(viewerFactory);
                }
                context.ungetService(service);
            }
        });
    }

    private static void registerCommands(BundleContext context) {
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "image"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, FileModel.functions);
        context.registerService(FileModel.class.getName(), ViewerPluginBuilder.DefaultDataModel, dict);
    }

}
