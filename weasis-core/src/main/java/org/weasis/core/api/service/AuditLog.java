/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.util.StringUtil;

/** The Class AuditLog allows writing specific traces for the application usage. */
public class AuditLog {
  // Share this logger
  public static final Logger LOGGER = LoggerFactory.getLogger(AuditLog.class);

  public static final String NAME_CONSOLE = "CONSOLE";
  public static final String NAME_ROLLING_FILES = "ROLLING_FILES"; // NON-NLS
  public static final String NAME_AUDIT = "AUDIT";

  public static final String LOG_LEVEL = "org.apache.sling.commons.log.level";
  public static final String LOG_STACKTRACE_LIMIT = "org.apache.sling.commons.log.stack.limit";
  public static final String LOG_FILE_ACTIVATION = "org.apache.sling.commons.log.file.activate";
  public static final String LOG_FILE = "org.apache.sling.commons.log.file";
  public static final String LOG_FILE_NUMBER = "org.apache.sling.commons.log.file.number";
  public static final String LOG_FILE_SIZE = "org.apache.sling.commons.log.file.size";
  public static final String LOG_PATTERN = "org.apache.sling.commons.log.pattern";
  public static final String LOG_LOGGERS = "org.apache.sling.commons.log.names";

  public static final String MARKER_PERF = "*PERF*"; // NON-NLS
  public static final String LOG_CLASSES = "org.apache.sling.commons.log.classes";
  public static final String LOG_FILE_PATTERN = "org.apache.sling.commons.log.file.pattern";

  public static final String LOG_FOLDER_PATH =
      STR."\{AppProperties.WEASIS_PATH}\{File.separator}log\{File.separator}";

  public enum LEVEL {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    public static LEVEL getLevel(String level) {
      try {
        return LEVEL.valueOf(level);
      } catch (Exception ignore) {
        // Do nothing
      }
      return INFO;
    }
  }

  public static WProperties getAuditProperties() {
    WProperties p = new WProperties();
    p.putBooleanProperty(AuditLog.LOG_FILE_ACTIVATION, true);
    p.setProperty(
        AuditLog.LOG_FILE, STR."\{AuditLog.LOG_FOLDER_PATH}audit-\{AppProperties.WEASIS_USER}.log");
    p.setProperty(AuditLog.LOG_FILE_NUMBER, "10");
    p.setProperty(AuditLog.LOG_FILE_SIZE, "20MB"); // NON-NLS
    p.setProperty(AuditLog.LOG_CLASSES, "org.weasis.core.api.service.AuditLog");
    p.setProperty(AuditLog.LOG_FILE_PATTERN, STR."audit-\{AppProperties.WEASIS_USER}.%i.log.zip");
    return p;
  }

  public static void applyConfig(WProperties prefs, LoggerContext loggerContext) {
    String pattern = prefs.getProperty(AuditLog.LOG_PATTERN);
    String limit = prefs.getProperty(AuditLog.LOG_STACKTRACE_LIMIT);
    PatternLayoutEncoder encoder = AuditLog.getPatternLayoutEncoder(loggerContext, pattern, limit);

    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    AuditLog.createOrUpdateConsoleAppender(loggerContext, logger, encoder);

    if (StringUtil.hasText(prefs.getProperty(AuditLog.LOG_FILE))) {
      prefs.setProperty(AuditLog.LOG_FILE_PATTERN, "default.%i.log.zip"); // NON-NLS
      RollingFileAppender<ILoggingEvent> rollingFileAppender =
          AuditLog.getRollingFilesAppender(logger, AuditLog.NAME_ROLLING_FILES);
      AuditLog.updateRollingFilesAppender(rollingFileAppender, loggerContext, prefs, encoder);
    } else {
      logger.detachAppender(AuditLog.NAME_ROLLING_FILES);
    }
  }

  public static void createOrUpdateConsoleAppender(
      LoggerContext loggerContext,
      ch.qos.logback.classic.Logger logger,
      PatternLayoutEncoder encoder) {
    if (logger.getAppender(NAME_CONSOLE)
        instanceof ConsoleAppender<ILoggingEvent> consoleAppender) {
      consoleAppender.setEncoder(encoder);
    } else {
      ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
      appender.setContext(loggerContext);
      appender.setName(NAME_CONSOLE);
      appender.setEncoder(encoder);
      appender.start();
      logger.addAppender(appender);
    }
  }

  public static RollingFileAppender<ILoggingEvent> getRollingFilesAppender(
      ch.qos.logback.classic.Logger logger, String name) {
    if (logger.getAppender(name) instanceof RollingFileAppender<ILoggingEvent> rollingAppender) {
      rollingAppender.stop();
      return rollingAppender;
    } else {
      RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
      appender.setName(name);
      return appender;
    }
  }

  public static void updateRollingFilesAppender(
      RollingFileAppender<ILoggingEvent> appender,
      LoggerContext loggerContext,
      WProperties prefs,
      PatternLayoutEncoder encoder) {
    String logActivation = prefs.getProperty(AuditLog.LOG_FILE);
    if (StringUtil.hasText(logActivation)) {
      Level level = Level.toLevel(prefs.getProperty(AuditLog.LOG_LEVEL), Level.INFO);
      int nbFiles = prefs.getIntProperty(AuditLog.LOG_FILE_NUMBER, 10);
      FileSize fileSize = FileSize.valueOf(prefs.getProperty(AuditLog.LOG_FILE_SIZE, "10MB"));
      String classes = prefs.getProperty(AuditLog.LOG_CLASSES, org.slf4j.Logger.ROOT_LOGGER_NAME);
      String filePattern =
          AuditLog.LOG_FOLDER_PATH
              + prefs.getProperty(AuditLog.LOG_FILE_PATTERN, "default.%i.log.zip");

      appender.setContext(loggerContext);
      appender.setEncoder(encoder);
      appender.setFile(logActivation);

      FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
      rollingPolicy.setContext(loggerContext);
      rollingPolicy.setParent(appender);
      rollingPolicy.setFileNamePattern(filePattern);
      rollingPolicy.setMinIndex(1);
      rollingPolicy.setMaxIndex(nbFiles);
      rollingPolicy.start();

      SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
      triggeringPolicy.setMaxFileSize(fileSize);
      triggeringPolicy.start();

      appender.setRollingPolicy(rollingPolicy);
      appender.setTriggeringPolicy(triggeringPolicy);
      appender.start();

      ch.qos.logback.classic.Logger logger =
          (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(classes);
      logger.setLevel(level);
      logger.addAppender(appender);
    }
  }

  public static PatternLayoutEncoder getPatternLayoutEncoder(
      LoggerContext loggerContext, String pattern, String stackLimit) {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(loggerContext);

    String limit = StringUtil.hasText(stackLimit) && !"-1".equals(stackLimit) ? stackLimit : "full";
    String str = pattern.replaceAll("ex\\{\\d+}", STR."ex{\{limit}}"); // NON-NLS
    encoder.setPattern(str);
    encoder.start();
    return encoder;
  }

  public static PatternLayoutEncoder getPatternLayoutEncoder(
      LoggerContext loggerContext, String pattern) {
    return getPatternLayoutEncoder(loggerContext, pattern, StringUtil.EMPTY_STRING);
  }

  public static void logError(Logger log, Throwable t, String message) {
    if (log.isDebugEnabled()) {
      log.error(message, t);
    } else {
      log.error(t.getMessage());
    }
  }
}
