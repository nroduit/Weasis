/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import org.apache.felix.prefs.BackingStore;
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
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
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
  public void start(BundleContext bundleContext) throws Exception {
    WProperties properties = GuiUtils.getUICore().getSystemPreferences();
    bundleContext.registerService(
        BackingStore.class.getName(),
        new StreamBackingStoreImpl(bundleContext, properties.getProperty("weasis.pref.dir")),
        null);

    for (ServiceReference<Codec> service : bundleContext.getServiceReferences(Codec.class, null)) {
      registerCodecPlugins(bundleContext.getService(service));
    }

    bundleContext.addServiceListener(this, BundleTools.createServiceFilter(Codec.class));

    initLoggerAndAudit(properties);

    // FIXME do not use system property
    File file = ResourceUtil.getResource("presets.xml");
    if (file.canRead()) {
      System.setProperty("dicom.presets.path", file.getPath());
    }

    registerCommands(bundleContext);
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    AbstractInfoLayer.applyPreferences(prefs);
    MeasureTool.viewSetting.initMonitors();
    MeasureTool.viewSetting.applyPreferences(prefs);

    // Must be instantiated in EDT
    GuiExecutor.execute(
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
        this, BundleTools.createServiceFilter(Codec.class, SeriesViewerFactory.class));
  }

  @Override
  public void stop(BundleContext bundleContext) throws Exception {
    GuiUtils.getUICore().saveSystemPreferences();

    // Save preferences
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    AbstractInfoLayer.savePreferences(prefs);
    MeasureTool.viewSetting.savePreferences(prefs);
    prefs.sync(); // Force to save as PreferencesManager (as specific bundle managing preferences)

    File dataFolder = AppProperties.getBundleDataFolder(bundleContext);
    if (dataFolder != null) {
      File file = new File(dataFolder, "persistence.properties");
      FileUtil.prepareToWriteFile(file);
      FileUtil.storeProperties(file, GuiUtils.getUICore().getLocalPersistence(), null);
    }
  }

  @Override
  public synchronized void serviceChanged(ServiceEvent event) {

    ServiceReference<?> sRef = event.getServiceReference();
    BundleContext context = AppProperties.getBundleContext(sRef);
    Object service = null;
    try {
      service = context.getService(sRef);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot get service of {}", sRef.getBundle(), e);
    }
    if (service == null) {
      return;
    }

    if (service instanceof Codec<?> codec) {
      // TODO manage when several identical MimeType, register the default one
      if (event.getType() == ServiceEvent.REGISTERED) {
        registerCodecPlugins(codec);
      } else if (event.getType() == ServiceEvent.UNREGISTERING) {
        List<Codec<MediaElement>> codecs = GuiUtils.getUICore().getCodecPlugins();
        if (codecs.contains(codec)) {
          LOGGER.info("Unregister Image Codec Plug-in: {}", codec.getCodecName());
          codecs.remove(codec);
        }
        // Unget service object and null references.
        context.ungetService(sRef);
      }
    } else if (service instanceof SeriesViewerFactory viewerFactory) {
      // Must be instantiated in EDT
      GuiExecutor.execute(
          () -> {
            if (event.getType() == ServiceEvent.REGISTERED) {
              registerSeriesViewerFactory(viewerFactory);
            } else if (event.getType() == ServiceEvent.UNREGISTERING) {
              List<SeriesViewerFactory> viewerFactories =
                  GuiUtils.getUICore().getSeriesViewerFactories();
              if (viewerFactories.contains(viewerFactory)) {
                LOGGER.info("Unregister series viewer plug-in: {}", viewerFactory.getDescription());
                viewerFactories.remove(viewerFactory);
              }
              context.ungetService(sRef);
            }
          });
    }
  }

  private static void registerCodecPlugins(Codec<?> codec) {
    List<Codec<MediaElement>> codecs = GuiUtils.getUICore().getCodecPlugins();
    if (codec != null && !codecs.contains(codec)) {
      codecs.add((Codec<MediaElement>) codec);
      LOGGER.info("Register Image Codec Plug-in: {}", codec.getCodecName());
    }
  }

  private static void registerSeriesViewerFactory(SeriesViewerFactory factory) {
    List<SeriesViewerFactory> viewerFactories = GuiUtils.getUICore().getSeriesViewerFactories();
    if (factory != null && !viewerFactories.contains(factory)) {
      viewerFactories.add(factory);
      LOGGER.info("Register series viewer plug-in: {}", factory.getDescription());
    }
  }

  private static void registerCommands(BundleContext context) {
    Dictionary<String, Object> dict = new Hashtable<>();
    dict.put(CommandProcessor.COMMAND_SCOPE, "image");
    dict.put(CommandProcessor.COMMAND_FUNCTION, AbstractFileModel.functions.toArray(new String[0]));
    context.registerService(FileModel.class.getName(), ViewerPluginBuilder.DefaultDataModel, dict);
  }

  private static void initLoggerAndAudit(WProperties properties) {
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();

    AuditLog.applyConfig(prefs, loggerContext);

    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // Audit log for giving statistics about usage of Weasis
    String loggerKey = "audit.log";
    if (properties.getBooleanProperty(loggerKey, false)) {
      String pattern =
          prefs.getProperty(
              AuditLog.LOG_PATTERN,
              "%d{dd.MM.yyyy HH:mm:ss.SSS} *%-5level* %msg" + "%ex{0}%nopex%n");

      PatternLayoutEncoder encoder = AuditLog.getPatternLayoutEncoder(loggerContext, pattern);

      RollingFileAppender<ILoggingEvent> rollingFileAppender =
          AuditLog.getRollingFilesAppender(logger, AuditLog.NAME_AUDIT);
      WProperties p = AuditLog.getAuditProperties();

      AuditLog.updateRollingFilesAppender(rollingFileAppender, loggerContext, p, encoder);
      AuditLog.LOGGER.info("Start audit log session");
    } else {
      logger.detachAppender(AuditLog.NAME_AUDIT);
      if (AuditLog.LOGGER instanceof ch.qos.logback.classic.Logger auditLogger) {
        auditLogger.detachAndStopAllAppenders();
        auditLogger.setLevel(Level.OFF);
      }
    }
  }
}
