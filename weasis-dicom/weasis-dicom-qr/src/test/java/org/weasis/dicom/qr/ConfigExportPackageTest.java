/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.json.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.ConfigCommands;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.api.util.LegacyConverters.Conversion;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.display.ModalityViewJson;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.codec.utils.SplittingRules;
import org.weasis.dicom.codec.utils.SplittingRulesJson;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;

/**
 * {@code config:export} run end to end on the real resources package of the distribution, with
 * every conversion the bundles register at runtime. Skipped when the module is built outside the
 * repository.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ConfigExportPackageTest {

  private static Path packageRoot() {
    Path root = Path.of("..", "..", "weasis-distributions", "resources").toAbsolutePath();
    return root.normalize();
  }

  @Test
  void the_shipped_package_converts_cleanly(@TempDir Path out) throws Exception {
    Path source = packageRoot();
    assumeTrue(Files.isRegularFile(source.resolve("dicomCallingNodes.xml")), "not in the repo");

    // What the bundle activators register at runtime
    AbstractDicomNode.registerLegacyConversions();
    LegacyConverters.register(AuthenticationPersistence.CONVERSION);
    LegacyConverters.register(SearchParameters.CONVERSION);
    LegacyConverters.register(
        new Conversion(
            DicomResource.ATTRIBUTES_VIEW.getPath(),
            ModalityView.SITE_FILE,
            ModalityView.ENTRIES,
            ModalityViewJson::convert));
    LegacyConverters.register(
        new Conversion(
            DicomResource.SERIES_SPITTING_RULES.getPath(),
            SplittingRules.SITE_FILE,
            SplittingRules.ENTRIES,
            SplittingRulesJson::convert));

    PrintStream console = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    JsonObject body;
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      new ConfigCommands()
          .export(
              new String[] {
                "--source", source.toString(), "--out", out.toString(), "--force" // NON-NLS
              });
    } finally {
      System.setOut(console);
    }
    body = JsonUtil.readObject(captured.toString(StandardCharsets.UTF_8).trim());
    console.println("config:export body: " + body);

    Map<String, Integer> written =
        JsonUtil.objects(body.getJsonArray("written")).stream()
            .collect(Collectors.toMap(o -> o.getString("legacy"), o -> o.getInt("entries")));
    List<String> skipped =
        body.getJsonArray("skipped").getValuesAs(v -> ((jakarta.json.JsonString) v).getString());

    assertAll(
        () -> assertTrue(body.getJsonArray("failed").isEmpty(), "nothing failed"),
        () -> assertEquals(2, written.get("dicomCallingNodes.xml"), "two shipped calling nodes"),
        () -> assertTrue(written.get("attributes-view.xml") >= 1, "overlay modalities"),
        () -> assertEquals(0, written.get("series-splitting-rules.xml"), "all commented out"),
        () ->
            assertTrue(
                skipped.containsAll(
                    List.of(
                        "dicomNodes.xml",
                        "dicomPrinterNodes.xml",
                        "dicomWebNodes.xml",
                        "authenticationNodes.xml",
                        "searchParameters.xml")),
                "absent legacy documents are skipped"));

    // The written documents read back as site documents
    ListDocument.Content nodes = ListDocument.read(out.resolve("dicomCallingNodes.json"), "nodes");
    ListDocument.Content overlay =
        ListDocument.read(out.resolve("attributesView.json"), "modalities");
    console.println(
        "calling node ids: "
            + nodes.entries().stream().map(o -> o.getString("id")).toList()
            + ", overlay modalities: "
            + overlay.entries().stream().map(o -> o.getString("id")).toList());
    assertAll(
        () -> assertEquals(2, nodes.entries().size()),
        () ->
            assertTrue(
                nodes.entries().stream().allMatch(o -> o.getString("id").startsWith("site.node.")),
                "site ids"),
        () -> assertTrue(overlay.entries().stream().allMatch(o -> o.containsKey("id"))),
        () -> assertTrue(Files.isRegularFile(out.resolve("seriesSplittingRules.json"))));
  }
}
