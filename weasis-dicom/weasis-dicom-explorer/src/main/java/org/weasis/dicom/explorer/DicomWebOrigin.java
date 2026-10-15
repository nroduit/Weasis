/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.Type;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.mf.WadoParameters;

/**
 * The DICOMweb server a study was loaded from, kept on the study group under {@link
 * TagW#DicomWebOrigin} so a plugin that has to ask that server again (UPS-RS workitems, FHIR
 * resources, a later retrieve) knows where to go and with which credentials, without guessing from
 * a series URL. Written by the {@code $dicom:rs} path, the DICOMweb manifests and the DICOMweb
 * retrieve of the Q/R dialog; the first origin of a study wins.
 *
 * @param baseUrl the DICOMweb root without trailing slash, e.g. {@code https://pacs/dicomweb}
 * @param authMethodUid the uid of the Weasis authentication method, null for an open origin
 * @param nodeId the id of the {@link DicomWebNode} entry when the load came from one, else null
 */
public record DicomWebOrigin(String baseUrl, String authMethodUid, String nodeId) {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomWebOrigin.class);

  public DicomWebOrigin {
    baseUrl = normalize(baseUrl);
    if (!StringUtil.hasText(baseUrl)) {
      throw new IllegalArgumentException("The DICOMweb base URL cannot be empty");
    }
    authMethodUid =
        StringUtil.hasText(authMethodUid) && !OAuth2ServiceFactory.NO_AUTH_ID.equals(authMethodUid)
            ? authMethodUid
            : null;
    nodeId = StringUtil.hasText(nodeId) ? nodeId : null;
  }

  /** Trims the URL and removes its trailing slashes; null stays null. */
  public static String normalize(String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  /**
   * The origin described by WADO-RS parameters, null for WADO-URI ones or when the base URL is
   * empty (the {@code $dicom:rs} path keeps its URL in the query parameters, not here).
   */
  public static DicomWebOrigin of(WadoParameters wado) {
    if (wado == null || !wado.isWadoRS() || !StringUtil.hasText(normalize(wado.getBaseURL()))) {
      return null;
    }
    return new DicomWebOrigin(wado.getBaseURL(), null, null);
  }

  /** The origin recorded on the study, null when the study was not loaded through DICOMweb. */
  public static DicomWebOrigin of(MediaSeriesGroup study) {
    return study != null && study.getTagValue(TagW.DicomWebOrigin) instanceof DicomWebOrigin o
        ? o
        : null;
  }

  /**
   * The origin of the study, recorded on it or recovered from the WADO-RS parameters of one of its
   * series (a study built before this tag existed); recovered origins are recorded for the next
   * call.
   */
  public static DicomWebOrigin of(DicomModel model, MediaSeriesGroup study) {
    DicomWebOrigin origin = of(study);
    if (origin == null && model != null && study != null) {
      origin = fromSeries(model.getChildren(study));
      attach(study, origin);
    }
    return origin;
  }

  static DicomWebOrigin fromSeries(Collection<? extends MediaSeriesGroup> seriesOfStudy) {
    for (MediaSeriesGroup series : seriesOfStudy) {
      if (series.getTagValue(TagW.WadoParameters) instanceof WadoParameters wado) {
        DicomWebOrigin origin = of(wado);
        if (origin != null) {
          return origin;
        }
      }
    }
    return null;
  }

  /**
   * Records the origin on the study when it has none yet.
   *
   * @return true when the origin was recorded, false when either argument is null or the study
   *     already carries one (a different one is logged, the first is kept)
   */
  public static boolean attach(MediaSeriesGroup study, DicomWebOrigin origin) {
    if (study == null || origin == null) {
      return false;
    }
    DicomWebOrigin current = of(study);
    if (current == null) {
      study.setTag(TagW.DicomWebOrigin, origin);
      return true;
    }
    if (!current.equals(origin)) {
      LOGGER.debug("Study {} keeps its origin {}, ignoring {}", study, current, origin);
    }
    return false;
  }

  /** The configured DICOMweb node this origin belongs to, by id first, else by URL prefix. */
  public Optional<DicomWebNode> findNode() {
    return match(AbstractDicomNode.loadDicomNodes(Type.WEB, UsageType.BOTH));
  }

  /**
   * The node of {@code nodes} matching this origin: the one with {@link #nodeId()} when known, else
   * the DICOMweb node whose URL is the longest prefix of {@link #baseUrl()}.
   */
  public Optional<DicomWebNode> match(Collection<? extends AbstractDicomNode> nodes) {
    DicomWebNode best = null;
    int bestLength = -1;
    for (AbstractDicomNode node : nodes) {
      if (!(node instanceof DicomWebNode web) || web.getUrl() == null) {
        continue;
      }
      if (nodeId != null && nodeId.equals(web.getId())) {
        return Optional.of(web);
      }
      String url = normalize(web.getUrl().toString());
      if (url.length() > bestLength && isPrefix(url, baseUrl)) {
        best = web;
        bestLength = url.length();
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean isPrefix(String url, String base) {
    return base.equals(url) || base.startsWith(url + "/");
  }

  /** The authentication method of the origin, empty for an open one or an unknown uid. */
  public Optional<AuthMethod> authMethod() {
    return authMethodUid == null
        ? Optional.empty()
        : Optional.ofNullable(AuthenticationPersistence.getAuthMethod(authMethodUid));
  }

  public String studyUrl(String studyUid) {
    return baseUrl + "/studies/" + Objects.requireNonNull(studyUid); // NON-NLS
  }

  public String seriesUrl(String studyUid, String seriesUid) {
    return studyUrl(studyUid) + "/series/" + Objects.requireNonNull(seriesUid); // NON-NLS
  }

  /** The WADO-RS Retrieve URL of an instance, as a UPS Input Information Sequence names it. */
  public String instanceUrl(String studyUid, String seriesUid, String sopUid) {
    return seriesUrl(studyUid, seriesUid)
        + "/instances/"
        + Objects.requireNonNull(sopUid); // NON-NLS
  }
}
