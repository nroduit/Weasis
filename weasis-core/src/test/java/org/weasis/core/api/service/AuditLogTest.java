/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class AuditLogTest {

  private static LoggerContext apply(String level) {
    WProperties prefs = new WProperties();
    if (level != null) {
      prefs.setProperty(AuditLog.LOG_LEVEL, level);
    }
    LoggerContext context = new LoggerContext();
    AuditLog.applyConfig(prefs, context);
    return context;
  }

  @Test
  void applyConfig_appliesConfiguredLevelToRootLogger() {
    LoggerContext context = apply("DEBUG");
    assertEquals(Level.DEBUG, context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
    assertNotNull(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(AuditLog.NAME_CONSOLE));
  }

  @Test
  void applyConfig_defaultsToInfoWhenLevelIsMissingOrInvalid() {
    assertEquals(Level.INFO, apply(null).getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
    assertEquals(Level.INFO, apply("VERBOSE").getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
  }

  @Test
  void applyConfig_capsVerboseThirdPartyLoggersAtInfo() {
    assertEquals(Level.INFO, apply("TRACE").getLogger("oshi").getLevel());
    assertEquals(Level.INFO, apply("DEBUG").getLogger("oshi").getLevel());
    assertEquals(Level.INFO, apply("INFO").getLogger("oshi").getLevel());
  }

  @Test
  void applyConfig_keepsThirdPartyLoggersQuieterWhenRootIsStricter() {
    assertEquals(Level.WARN, apply("WARN").getLogger("oshi").getLevel());
    assertEquals(Level.ERROR, apply("ERROR").getLogger("oshi").getLevel());
  }
}
