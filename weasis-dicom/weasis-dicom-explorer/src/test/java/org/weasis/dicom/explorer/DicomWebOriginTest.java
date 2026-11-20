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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.mf.WadoParameters;

/**
 * The origin a study keeps: normalized once, recorded once, recoverable from the WADO-RS parameters
 * of a series, and matched back to a configured node by id or by URL prefix.
 */
class DicomWebOriginTest {

  private static final TagW STUDY_UID = new TagW(98_100, "StudyUid", TagType.STRING);
  private static final TagW SERIES_UID = new TagW(98_101, "SeriesUid", TagType.STRING);

  private static MediaSeriesGroup study() {
    return new MediaSeriesGroupNode(STUDY_UID, "1.2.3", null);
  }

  private static DicomWebNode node(String id, String url) throws Exception {
    DicomWebNode node =
        new DicomWebNode("n", WebType.DICOMWEB, new URL(url), UsageType.RETRIEVE); // NOSONAR
    node.setId(id);
    return node;
  }

  @Test
  void theBaseUrlIsNormalizedAndTheNoAuthIdMeansNoAuth() {
    DicomWebOrigin origin =
        new DicomWebOrigin(" https://pacs/dicomweb// ", OAuth2ServiceFactory.NO_AUTH_ID, "");

    assertEquals("https://pacs/dicomweb", origin.baseUrl());
    assertNull(origin.authMethodUid());
    assertNull(origin.nodeId());
    assertTrue(origin.authMethod().isEmpty());
  }

  @Test
  void anEmptyBaseUrlIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new DicomWebOrigin(" / ", null, null));
    assertThrows(IllegalArgumentException.class, () -> new DicomWebOrigin(null, null, null));
  }

  @Test
  void onlyWadoRsParametersWithAUrlDescribeAnOrigin() {
    assertNull(DicomWebOrigin.of((WadoParameters) null));
    assertNull(DicomWebOrigin.of(new WadoParameters("http://pacs/wado", true)));
    assertNull(DicomWebOrigin.of(new WadoParameters("", true, true)));

    DicomWebOrigin origin =
        DicomWebOrigin.of(new WadoParameters("http://pacs/dicomweb/", true, true));
    assertEquals("http://pacs/dicomweb", origin.baseUrl());
  }

  @Test
  void theFirstOriginOfAStudyIsKept() {
    MediaSeriesGroup study = study();
    DicomWebOrigin first = new DicomWebOrigin("https://a/dicomweb", "auth-1", "node-a");
    DicomWebOrigin second = new DicomWebOrigin("https://b/dicomweb", null, null);

    assertNull(DicomWebOrigin.of(study));
    assertTrue(DicomWebOrigin.attach(study, first));
    assertFalse(DicomWebOrigin.attach(study, second));
    assertFalse(DicomWebOrigin.attach(study, null));
    assertFalse(DicomWebOrigin.attach(null, first));
    assertSame(first, DicomWebOrigin.of(study));
  }

  @Test
  void anOriginIsRecoveredFromTheWadoRsParametersOfASeries() {
    MediaSeriesGroup wadoUri = new MediaSeriesGroupNode(SERIES_UID, "s1", null);
    wadoUri.setTag(TagW.WadoParameters, new WadoParameters("http://pacs/wado", true));
    MediaSeriesGroup wadoRs = new MediaSeriesGroupNode(SERIES_UID, "s2", null);
    wadoRs.setTag(TagW.WadoParameters, new WadoParameters("http://pacs/dicomweb", true, true));

    assertNull(DicomWebOrigin.fromSeries(List.of()));
    assertNull(DicomWebOrigin.fromSeries(List.of(wadoUri)));
    assertEquals(
        "http://pacs/dicomweb", DicomWebOrigin.fromSeries(List.of(wadoUri, wadoRs)).baseUrl());
  }

  @Test
  void theNodeIsMatchedByIdFirstThenByTheLongestUrlPrefix() throws Exception {
    DicomWebNode root = node("root", "https://pacs/");
    DicomWebNode dicomweb = node("web", "https://pacs/dicomweb");
    DicomWebNode other = node("other", "https://other/dicomweb");
    List<DicomWebNode> nodes = List.of(other, root, dicomweb);

    DicomWebOrigin byId = new DicomWebOrigin("https://elsewhere/rs", null, "other");
    assertSame(other, byId.match(nodes).orElseThrow());

    DicomWebOrigin byPrefix = new DicomWebOrigin("https://pacs/dicomweb/", null, "unknown");
    assertSame(dicomweb, byPrefix.match(nodes).orElseThrow());

    DicomWebOrigin deeper = new DicomWebOrigin("https://pacs/dicomweb/aets/A/rs", null, null);
    assertSame(dicomweb, deeper.match(nodes).orElseThrow());

    DicomWebOrigin sibling = new DicomWebOrigin("https://pacs/dicomwebx", null, null);
    assertSame(root, sibling.match(nodes).orElseThrow());

    assertEquals(Optional.empty(), new DicomWebOrigin("https://nowhere", null, null).match(nodes));
  }

  @Test
  void theRetrieveUrlsFollowWadoRs() {
    DicomWebOrigin origin = new DicomWebOrigin("https://pacs/dicomweb", null, null);

    assertEquals("https://pacs/dicomweb/studies/1.2", origin.studyUrl("1.2"));
    assertEquals("https://pacs/dicomweb/studies/1.2/series/3.4", origin.seriesUrl("1.2", "3.4"));
    assertEquals(
        "https://pacs/dicomweb/studies/1.2/series/3.4/instances/5.6",
        origin.instanceUrl("1.2", "3.4", "5.6"));
    assertEquals(URI.create(origin.instanceUrl("1.2", "3.4", "5.6")).getHost(), "pacs");
  }
}
