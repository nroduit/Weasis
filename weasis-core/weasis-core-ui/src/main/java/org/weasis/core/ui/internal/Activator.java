/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.internal;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.FileModel;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.util.FileUtil;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator, ServiceListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  @Override
  public void start(final BundleContext bundleContext) throws Exception {
    registerCommands(bundleContext);
    File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
    FileUtil.readProperties(
        new File(dataFolder, "persistence.properties"), BundleTools.LOCAL_UI_PERSISTENCE);
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    AbstractInfoLayer.applyPreferences(prefs);
    MeasureTool.viewSetting.initMonitors();
    MeasureTool.viewSetting.applyPreferences(prefs);

    // Must be instantiated in EDT
    GuiExecutor.instance()
        .execute(
            () -> {
              try {
                for (ServiceReference<SeriesViewerFactory> service :
                    bundleContext.getServiceReferences(SeriesViewerFactory.class, null)) {
                  registerSeriesViewerFactory(bundleContext.getService(service));
                }
              } catch (InvalidSyntaxException e) {
                LOGGER.error("", e);
              }
            });

    bundleContext.addServiceListener(
        this,
        String.format(
            "(%s=%s)", Constants.OBJECTCLASS, SeriesViewerFactory.class.getName())); // NON-NLS
  }

  @Override
  public void stop(BundleContext bundleContext) throws Exception {
    // Save preferences
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    AbstractInfoLayer.savePreferences(prefs);
    MeasureTool.viewSetting.savePreferences(prefs);
    File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
    if (dataFolder != null) {
      File file = new File(dataFolder, "persistence.properties");
      FileUtil.prepareToWriteFile(file);
      FileUtil.storeProperties(file, BundleTools.LOCAL_UI_PERSISTENCE, null);
    }
  }

  @Override
  public synchronized void serviceChanged(final ServiceEvent event) {

    // Must be instantiated in EDT
    GuiExecutor.instance()
        .execute(
            () -> {
              ServiceReference<?> service = event.getServiceReference();
              BundleContext context = AppProperties.getBundleContext(service);
              SeriesViewerFactory viewerFactory = null;
              try {
                viewerFactory = (SeriesViewerFactory) context.getService(service);
              } catch (Exception e) {
                LOGGER.info("Cannot get service of {}", service.getBundle());
              }
              if (viewerFactory != null) {
                if (event.getType() == ServiceEvent.REGISTERED) {
                  registerSeriesViewerFactory(viewerFactory);
                } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                  if (UIManager.SERIES_VIEWER_FACTORIES.contains(viewerFactory)) {
                    LOGGER.info(
                        "Unregister series viewer plug-in: {}", viewerFactory.getDescription());
                    UIManager.SERIES_VIEWER_FACTORIES.remove(viewerFactory);
                  }
                  context.ungetService(service);
                }
              }
            });
  }

  private static void registerSeriesViewerFactory(SeriesViewerFactory factory) {
    if (factory != null && !UIManager.SERIES_VIEWER_FACTORIES.contains(factory)) {
      UIManager.SERIES_VIEWER_FACTORIES.add(factory);
      LOGGER.info("Register series viewer plug-in: {}", factory.getDescription());
    }
  }

  private static void registerCommands(BundleContext context) {
    Dictionary<String, Object> dict = new Hashtable<>();
    dict.put(CommandProcessor.COMMAND_SCOPE, "image");
    dict.put(CommandProcessor.COMMAND_FUNCTION, AbstractFileModel.functions.toArray(new String[0]));
    context.registerService(FileModel.class.getName(), ViewerPluginBuilder.DefaultDataModel, dict);
  }
}
