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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Syslog transport of the audit messages, as IHE ATNA profiles it: RFC 5424 messages over UDP, or
 * RFC 5425 (octet counting framing) over TLS with the platform trust. The facility is {@code
 * local4} (20) as dcm4che and the ARRs expect, the severity {@code informational}; the message id
 * is {@code IHE+DICOM} and the body the PS3.15 XML in UTF-8 with the BOM RFC 5424 asks for.
 */
public final class SyslogSender implements Closeable {

  private static final int FACILITY_LOCAL4 = 20;
  private static final int SEVERITY_INFO = 6;
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

  private final String host;
  private final int port;
  private final boolean tls;
  private final String appName;
  private final String hostname;
  private final SSLContext sslContext;
  private DatagramSocket udp;
  private Socket tcp;

  /**
   * @param sslContext the context of a TLS transport (the platform trust, a client identity when
   *     the site requires one); null for UDP
   */
  public SyslogSender(
      String host, int port, boolean tls, String appName, String hostname, SSLContext sslContext) {
    this.host = host;
    this.port = port;
    this.tls = tls;
    this.appName = appName;
    this.hostname = hostname;
    this.sslContext = sslContext;
  }

  /** The RFC 5424 message of an audit record. */
  public static String frame(
      Instant time, String hostname, String appName, String procId, String xml) {
    int priority = FACILITY_LOCAL4 * 8 + SEVERITY_INFO;
    return "<"
        + priority
        + ">1 "
        + TIMESTAMP.format(time)
        + ' '
        + nil(hostname)
        + ' '
        + nil(appName)
        + ' '
        + nil(procId)
        + " IHE+DICOM - ﻿" // NON-NLS
        + xml;
  }

  private static String nil(String value) {
    return value == null || value.isBlank() ? "-" : value.replace(' ', '_');
  }

  /** Sends one message; a TLS connection is kept open between messages. */
  public synchronized void send(String xml) throws IOException {
    String message =
        frame(Instant.now(), hostname, appName, String.valueOf(ProcessHandle.current().pid()), xml);
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    if (tls) {
      sendTls(bytes);
    } else {
      sendUdp(bytes);
    }
  }

  private void sendUdp(byte[] bytes) throws IOException {
    if (udp == null) {
      udp = new DatagramSocket();
    }
    udp.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), port));
  }

  private void sendTls(byte[] bytes) throws IOException {
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (tcp == null || tcp.isClosed()) {
          SSLSocketFactory factory =
              sslContext == null
                  ? (SSLSocketFactory) SSLSocketFactory.getDefault()
                  : sslContext.getSocketFactory();
          SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
          socket.startHandshake();
          tcp = socket;
        }
        OutputStream out = tcp.getOutputStream();
        // RFC 5425 octet counting: the length of the message, a space, the message
        out.write((bytes.length + " ").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
        return;
      } catch (IOException e) {
        close();
        if (attempt == 1) {
          throw e;
        }
      }
    }
  }

  @Override
  public synchronized void close() {
    if (udp != null) {
      udp.close();
      udp = null;
    }
    if (tcp != null) {
      try {
        tcp.close();
      } catch (IOException e) {
        // closing
      }
      tcp = null;
    }
  }
}
