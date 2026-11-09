/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.audit;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import org.dcm4che3.audit.AuditMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventFilter;
import org.weasis.core.api.event.Subscription;

/**
 * The subscriber of the event bus that turns the events of the audit table into PS3.15 messages and
 * hands them to the transports: syslog to the repository when the site configured one, the local
 * file always when the site keeps it, and the local file as the fallback of a failed send.
 */
public final class AuditTrail implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditTrail.class);

  private final AuditConfig config;
  private final AuditRecords records;
  private final SyslogSender syslog;
  private final JsonLinesSink file;
  private final AtomicLong recorded = new AtomicLong();
  private final AtomicLong failedSends = new AtomicLong();
  private Subscription subscription;

  public AuditTrail(
      AuditConfig config, AuditRecords records, SyslogSender syslog, JsonLinesSink file) {
    this.config = config;
    this.records = records;
    this.syslog = syslog;
    this.file = file;
  }

  /** The trail of the configuration with the platform TLS context, syslog when the site asked. */
  public static AuditTrail of(AuditConfig config, JsonLinesSink file, SSLContext sslContext) {
    AuditRecords records = new AuditRecords();
    SyslogSender syslog =
        config.sendsSyslog()
            ? new SyslogSender(
                config.host(),
                config.port(),
                config.isTls(),
                "weasis", // NON-NLS
                hostName(),
                config.isTls() ? sslContext : null)
            : null;
    return new AuditTrail(config, records, syslog, file);
  }

  private static String hostName() {
    try {
      return java.net.InetAddress.getLocalHost().getHostName();
    } catch (IOException e) {
      return "localhost"; // NON-NLS
    }
  }

  /** Subscribes to the bus: the events of the table, delivered off the event thread. */
  public AuditTrail subscribe(EventBus bus) {
    subscription =
        bus.subscribe(
            new EventFilter(AuditConfig.eventNames(), java.util.Map.of()),
            Subscription.Delivery.ASYNC,
            "audit", // NON-NLS
            this::record);
    return this;
  }

  /** Records one event; what went wrong is logged, never thrown at the bus. */
  public void record(Event event) {
    Optional<String> eventClass = AuditConfig.eventClassOf(event.name());
    if (eventClass.isEmpty() || !config.records(eventClass.get())) {
      return;
    }
    Optional<AuditMessage> message = records.of(event);
    if (message.isEmpty()) {
      return;
    }
    try {
      String xml = AuditRecords.toXml(message.get());
      boolean sent = false;
      if (syslog != null) {
        try {
          syslog.send(xml);
          sent = true;
        } catch (IOException e) {
          failedSends.incrementAndGet();
          LOGGER.warn("Audit message not sent to {}: {}", config.host(), e.getMessage());
        }
      }
      if (file != null && (config.file() || !sent)) {
        file.write(JsonLinesSink.line(event, message.get(), xml, sent));
      }
      recorded.incrementAndGet();
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Audit record of {} lost", event.name(), e);
    }
  }

  public long recorded() {
    return recorded.get();
  }

  public long failedSends() {
    return failedSends.get();
  }

  @Override
  public void close() {
    if (subscription != null) {
      subscription.close();
      subscription = null;
    }
    if (syslog != null) {
      syslog.close();
    }
  }
}
