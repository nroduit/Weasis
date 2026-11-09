/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.audit.internal;

import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.audit.AuditConfig;
import org.weasis.dicom.audit.AuditRecords;
import org.weasis.dicom.audit.AuditTrail;
import org.weasis.dicom.audit.JsonLinesSink;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.plugin.WeasisPlugin;

/**
 * Starts the audit trail when the site document {@code audit.json} enables it; inactive otherwise,
 * so the plugin costs nothing where unused. The integrity failures of the start are the first
 * security alerts it records.
 */
public class Activator extends WeasisPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  private AuditTrail trail;

  public Activator(org.weasis.plugin.PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    SiteDocuments.onReload(AuditConfig.DOCUMENT, this::restart);
    restart();
  }

  private synchronized void restart() {
    if (trail != null) {
      trail.close();
      trail = null;
    }
    AuditConfig config = AuditConfig.load();
    if (!config.enabled()) {
      LOGGER.info("Audit trail off (no audit.json enabling it)");
      return;
    }
    JsonLinesSink file =
        config.file() || !config.sendsSyslog()
            ? new JsonLinesSink(
                AppProperties.WEASIS_LOG_PATH.resolve("audit"), config.retentionDays()) // NON-NLS
            : null;
    SSLContext ssl = null;
    try {
      ssl = SSLContext.getDefault();
    } catch (Exception e) {
      LOGGER.warn("No default TLS context for the audit transport", e);
    }
    trail = AuditTrail.of(config, file, ssl).subscribe(EventBus.global());
    LOGGER.info(
        "Audit trail on: {} {}:{}, local file {}",
        config.transport(),
        config.host(),
        config.port(),
        file == null ? "off" : file.folder());
    Integrity.Report integrity = Integrity.report();
    integrity
        .failures()
        .forEach(
            failure ->
                EventBus.global()
                    .publish(
                        Event.of(AuditRecords.SECURITY_ALERT)
                            .put("kind", "integrity") // NON-NLS
                            .put("reason", failure))); // NON-NLS
  }

  @Override
  public synchronized void stop() {
    if (trail != null) {
      trail.close();
      trail = null;
    }
  }
}
