/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.net.Status;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.Type;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.op.Echo;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.TlsOptions;

/**
 * C-ECHO to a configured node, the *Test connection* of the node dialog and the {@code dicom:echo}
 * verb: one association with the TLS of the node, the result named for what it is.
 */
public final class DicomEcho {

  /** The calling AE title when nothing is configured. */
  public static final String DEFAULT_CALLING_AET = "WEASIS_AE"; // NON-NLS

  /**
   * What an echo gave back.
   *
   * @param success whether the peer answered the C-ECHO with success
   * @param status the DICOM status, or dcm4che's unable-to-process on a connection failure
   * @param millis the round trip, connection included
   * @param message the failure named for what it is, null on success
   * @param tlsKind the kind of TLS failure, null when the failure is not about TLS
   */
  public record Result(
      boolean success, int status, long millis, String message, TlsErrors.Kind tlsKind) {}

  private DicomEcho() {}

  /**
   * The calling AE title: the administrator's {@code weasis.aet}, else the first calling node of
   * the workstation, else {@link #DEFAULT_CALLING_AET}.
   */
  public static String defaultCallingAet() {
    String aet = null;
    try {
      aet = GuiUtils.getUICore().getSystemPreferences().getProperty("weasis.aet"); // NON-NLS
    } catch (RuntimeException e) {
      // no UI core in a headless test
    }
    if (StringUtil.hasText(aet)) {
      return aet.trim();
    }
    try {
      for (AbstractDicomNode node :
          AbstractDicomNode.loadDicomNodes(Type.DICOM_CALLING, UsageType.BOTH)) {
        if (node instanceof DefaultDicomNode calling && StringUtil.hasText(calling.getAeTitle())) {
          return calling.getAeTitle();
        }
      }
    } catch (RuntimeException e) {
      // no documents in a headless test
    }
    return DEFAULT_CALLING_AET;
  }

  /** A configured DICOM node or printer by id, then description, then AE title. */
  public static Optional<DefaultDicomNode> findNode(String key) {
    if (!StringUtil.hasText(key)) {
      return Optional.empty();
    }
    List<AbstractDicomNode> nodes = new java.util.ArrayList<>();
    nodes.addAll(AbstractDicomNode.loadDicomNodes(Type.DICOM, UsageType.BOTH));
    nodes.addAll(AbstractDicomNode.loadDicomNodes(Type.PRINTER, UsageType.BOTH));
    String wanted = key.trim();
    for (AbstractDicomNode node : nodes) {
      if (node instanceof DefaultDicomNode d && wanted.equals(d.getId())) {
        return Optional.of(d);
      }
    }
    for (AbstractDicomNode node : nodes) {
      if (node instanceof DefaultDicomNode d && wanted.equalsIgnoreCase(d.getDescription())) {
        return Optional.of(d);
      }
    }
    for (AbstractDicomNode node : nodes) {
      if (node instanceof DefaultDicomNode d && wanted.equalsIgnoreCase(d.getAeTitle())) {
        return Optional.of(d);
      }
    }
    return Optional.empty();
  }

  /**
   * One C-ECHO to the node.
   *
   * @param callingAet the calling AE title, null for {@link #defaultCallingAet()}
   * @param tls the TLS options, null for an association in clear
   * @param timeoutSeconds connect and accept time-out
   */
  public static Result echo(
      DefaultDicomNode node, String callingAet, TlsOptions tls, int timeoutSeconds) {
    AdvancedParams params = new AdvancedParams();
    ConnectOptions connect = new ConnectOptions();
    connect.setConnectTimeout(Math.max(1, timeoutSeconds) * 1000);
    connect.setAcceptTimeout(Math.max(1, timeoutSeconds) * 1000);
    params.setConnectOptions(connect);
    params.setTlsOptions(tls);
    String calling = StringUtil.hasText(callingAet) ? callingAet.trim() : defaultCallingAet();
    long start = System.nanoTime();
    DicomState state = Echo.process(params, new DicomNode(calling), node.getDicomNode());
    long millis = (System.nanoTime() - start) / 1_000_000;
    boolean ok = state.getStatus() == Status.Success;
    if (ok) {
      return new Result(true, state.getStatus(), millis, null, null);
    }
    String raw = state.getMessage() == null ? state.getErrorMessage() : state.getMessage();
    TlsErrors.Kind kind = tls == null ? null : TlsErrors.kindOf(raw);
    String message = tls == null ? raw : TlsErrors.describe(node.getHostname(), raw);
    return new Result(
        false, state.getStatus(), millis, message, kind == TlsErrors.Kind.OTHER ? null : kind);
  }

  /** The echo with the TLS the node document says. */
  public static Result echo(DefaultDicomNode node, String callingAet, int timeoutSeconds)
      throws IOException {
    return echo(node, callingAet, node.getTlsOptions(), timeoutSeconds);
  }
}
