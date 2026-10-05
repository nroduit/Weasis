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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.ui.util.PrintOptions.DotPerInches;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.Documents;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.Type;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;
import org.weasis.dicom.explorer.print.DicomPrintOptions;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomNodeDocumentsTest {

  private static final String PACS_ID = "site.node.pacs-ae-pacs-example-org-11112";
  private static final String TEACH_ID = "site.node.teach-teach-example-org-4242";
  private static final String DICOMWEB_URL = "https://dicomweb.example.org/dicomweb";

  @TempDir Path temp;

  @BeforeEach
  void secretStoreInMemory() {
    SecretStore.useInstance(new SecretStore(temp.resolve("auth-secrets.json")));
  }

  private static Path fixture(String name) {
    URL url = DicomNodeDocumentsTest.class.getResource("/config/" + name);
    return Path.of(URI.create(url.toString()));
  }

  /** A site shipping only the legacy XML of that type, and the user JSON in the temp folder. */
  private Documents legacySite(Type type) {
    return new Documents(
        null, fixture(type.getFilename()), temp.resolve(type.getJsonFilename()), null);
  }

  private static List<String> ids(List<? extends AbstractDicomNode> nodes) {
    return nodes.stream().map(AbstractDicomNode::getId).toList();
  }

  private static List<String> descriptions(List<? extends AbstractDicomNode> nodes) {
    return nodes.stream().map(AbstractDicomNode::getDescription).toList();
  }

  private static DefaultDicomNode siteNode(String description) {
    DefaultDicomNode node = new DefaultDicomNode(description, "S", "s", 104, UsageType.BOTH);
    node.setLocal(false);
    return node;
  }

  private static JsonObject entry(String id, String json) {
    return JsonUtil.readObject("{\"id\": \"" + id + "\", " + json + "}");
  }

  // ---- XML to JSON conversion -------------------------------------------------------------

  @Test
  void converting_the_legacy_xml_gives_stable_site_ids() throws IOException {
    List<JsonObject> first =
        AbstractDicomNode.convertLegacy(
            fixture("dicomNodes.xml"), Type.DICOM, EntryIds.SITE_PREFIX, false);
    List<JsonObject> second =
        AbstractDicomNode.convertLegacy(
            fixture("dicomNodes.xml"), Type.DICOM, EntryIds.SITE_PREFIX, false);
    List<JsonObject> calling =
        AbstractDicomNode.convertLegacy(
            fixture("dicomCallingNodes.xml"), Type.DICOM_CALLING, EntryIds.SITE_PREFIX, false);

    assertAll(
        () ->
            assertEquals(
                List.of(PACS_ID, TEACH_ID, PACS_ID + "#2"),
                first.stream().map(o -> o.getString("id")).toList(),
                "the key is aeTitle@hostname:port, a duplicate gets a suffix"),
        () -> assertEquals(first, second, "the same document always converts the same way"),
        () ->
            assertEquals(
                List.of(
                    "site.node.weasis-ae-localhost-11113", "site.node.weasis-qr-localhost-11113"),
                calling.stream().map(o -> o.getString("id")).toList()));
  }

  @Test
  void the_registered_conversions_cover_the_four_documents() throws IOException {
    AbstractDicomNode.registerLegacyConversions();
    for (Type type : Type.values()) {
      Optional<LegacyConverters.Conversion> conversion = LegacyConverters.of(type.getFilename());
      assertTrue(conversion.isPresent(), type.name());
      assertEquals(type.getJsonFilename(), conversion.get().jsonName());
      assertEquals("nodes", conversion.get().entries());
      List<JsonObject> entries = conversion.get().converter().convert(fixture(type.getFilename()));
      assertFalse(entries.isEmpty(), type.name());
      assertTrue(entries.getFirst().getString("id").startsWith(EntryIds.SITE_PREFIX));
    }
  }

  @Test
  void converting_a_dicom_node_keeps_every_attribute() throws IOException {
    List<JsonObject> entries =
        AbstractDicomNode.convertLegacy(
            fixture("dicomNodes.xml"), Type.DICOM, EntryIds.SITE_PREFIX, false);
    JsonObject pacs = entries.getFirst();
    JsonObject teach = entries.get(1);

    assertAll(
        () -> assertEquals("DICOM", teach.getString("type")),
        () -> assertEquals("Teaching archive", teach.getString("description")),
        () -> assertEquals("RETRIEVE", teach.getString("usageType")),
        () -> assertEquals("EXPLICIT_VR_LE", teach.getString("tsuid")),
        () -> assertFalse(pacs.containsKey("usageType"), "BOTH is the default, left out"),
        () -> assertFalse(pacs.containsKey("tsuid"), "NONE is the default, left out"),
        () -> assertEquals("TEACH", teach.getString("aeTitle")),
        () -> assertEquals("teach.example.org", teach.getString("hostname")),
        () -> assertEquals(4242, teach.getInt("port")),
        () -> assertFalse(teach.containsKey("hidden")),
        () -> assertFalse(teach.containsKey("locked")));
  }

  @Test
  void converting_a_printer_keeps_every_print_option() throws IOException {
    JsonObject printer =
        AbstractDicomNode.convertLegacy(
                fixture("dicomPrinterNodes.xml"), Type.PRINTER, EntryIds.SITE_PREFIX, false)
            .getFirst();
    JsonObject print = printer.getJsonObject("print");

    assertAll(
        () -> assertEquals("site.node.print-scp-printer-example-org-104", printer.getString("id")),
        () -> assertEquals("PRINTER", printer.getString("type")),
        () -> assertFalse(printer.containsKey("usageType"), "a printer is always STORAGE"),
        () -> assertEquals("PRINT_SCP", printer.getString("aeTitle")),
        () -> assertEquals("printer.example.org", printer.getString("hostname")),
        () -> assertEquals(104, printer.getInt("port")),
        () -> assertEquals("CLEAR FILM", print.getString("mediumType")),
        () -> assertEquals("HIGH", print.getString("priority")),
        () -> assertEquals("PROCESSOR", print.getString("filmDestination")),
        () -> assertEquals(2, print.getInt("numberOfCopies")),
        () -> assertTrue(print.getBoolean("colorPrint")),
        () -> assertEquals("LANDSCAPE", print.getString("filmOrientation")),
        () -> assertEquals("IN14X17", print.getString("filmSizeId")),
        () -> assertEquals("STANDARD\\2,2", print.getString("imageDisplayFormat")),
        () -> assertEquals("BILINEAR", print.getString("magnificationType")),
        () -> assertEquals("SHARP", print.getString("smoothingType")),
        () -> assertEquals("BLACK", print.getString("borderDensity")),
        () -> assertEquals("YES", print.getString("trim")),
        () -> assertEquals("WHITE", print.getString("emptyDensity")),
        () -> assertFalse(print.getBoolean("showingAnnotations")),
        () -> assertTrue(print.getBoolean("printOnlySelectedView")),
        () -> assertEquals("DPI_300", print.getString("dpi")));

    // And the JSON reads back into the same options
    DicomPrintNode node = (DicomPrintNode) AbstractDicomNode.fromJson(printer, Type.PRINTER);
    DicomPrintOptions options = node.getPrintOptions();
    assertAll(
        () -> assertEquals("CLEAR FILM", options.getMediumType()),
        () -> assertEquals(2, options.getNumOfCopies()),
        () -> assertTrue(options.isColorPrint()),
        () -> assertEquals(FilmSize.IN14X17, options.getFilmSizeId()),
        () -> assertFalse(options.isShowingAnnotations()),
        () -> assertTrue(options.isPrintOnlySelectedView()),
        () -> assertEquals(DotPerInches.DPI_300, options.getDpi()));
  }

  @Test
  void converting_a_site_web_node_keeps_the_header_values() throws IOException {
    List<JsonObject> entries =
        AbstractDicomNode.convertLegacy(
            fixture("dicomWebNodes.xml"), Type.WEB, EntryIds.SITE_PREFIX, false);
    JsonObject web = entries.getFirst();
    JsonObject wado = entries.get(1);

    assertAll(
        () -> assertEquals("site.node.https-dicomweb-example-org-dicomweb", web.getString("id")),
        () -> assertEquals("WEB", web.getString("type")),
        () -> assertEquals("Hospital DICOMweb", web.getString("description")),
        () -> assertFalse(web.containsKey("usageType"), "BOTH is the default, left out"),
        () -> assertFalse(web.containsKey("tsuid"), "NONE is the default, left out"),
        () -> assertEquals(DICOMWEB_URL, web.getString("url")),
        () -> assertEquals("DICOMWEB", web.getString("webType")),
        () -> assertEquals("NO_AUTH", web.getString("authMethod")),
        () -> assertFalse(web.containsKey("auth"), "only the new member is written"),
        () ->
            assertEquals(
                Map.of("Authorization", "Bearer abc", "X-Api-Key", "k1"),
                JsonUtil.getStringMap(web, "headers"),
                "a site document carries the header values as is"),
        () -> assertEquals("WADO", wado.getString("webType")),
        () -> assertFalse(wado.containsKey("usageType"), "RETRIEVE is what WADO defaults to"),
        () -> assertTrue(JsonUtil.getStringMap(wado, "headers").isEmpty()));
  }

  // ---- Layers -------------------------------------------------------------------------------

  @Test
  void a_user_document_overrides_and_hides_site_nodes_by_id() throws IOException {
    Documents documents = legacySite(Type.DICOM);
    ListDocument.write(
        documents.userJson(),
        "nodes",
        List.of(
            entry(
                PACS_ID,
                """
                "type": "DICOM", "description": "My PACS", "aeTitle": "PACS_AE",
                "hostname": "10.0.0.1", "port": 11112, "usageType": "BOTH"
                """),
            entry(TEACH_ID, "\"hidden\": true"),
            entry(
                "user.node.ext", "\"extends\": \"" + PACS_ID + "\", \"description\": \"Extended\""),
            entry(
                "user.node.mine",
                """
                "type": "DICOM", "description": "Mine", "aeTitle": "MINE",
                "hostname": "mine.example.org", "port": 104, "usageType": "STORAGE"
                """)));

    List<AbstractDicomNode> all =
        AbstractDicomNode.loadDicomNodes(documents, Type.DICOM, UsageType.BOTH, null);
    List<AbstractDicomNode> retrieve =
        AbstractDicomNode.loadDicomNodes(documents, Type.DICOM, UsageType.RETRIEVE, null);
    Merged<AbstractDicomNode> merged = AbstractDicomNode.loadMerged(documents, Type.DICOM);

    DefaultDicomNode pacs = (DefaultDicomNode) all.getFirst();
    DefaultDicomNode extended = (DefaultDicomNode) all.get(2);
    assertAll(
        () ->
            assertEquals(
                List.of("My PACS", "Main PACS (copy)", "Extended", "Mine"),
                descriptions(all),
                "the override keeps the site position, the hidden one is left out"),
        () -> assertEquals(PACS_ID, pacs.getId()),
        () -> assertTrue(pacs.isLocal(), "the user definition wins"),
        () -> assertEquals("10.0.0.1", pacs.getHostname()),
        () -> assertFalse(all.get(1).isLocal(), "a site node the user did not touch"),
        () ->
            assertEquals(
                "PACS_AE", extended.getAeTitle(), "inherited from the override of the site node"),
        () ->
            assertEquals(
                "10.0.0.1", extended.getHostname(), "the same document wins over the site"),
        () -> assertTrue(extended.isLocal()),
        () ->
            assertEquals(
                List.of(PACS_ID, TEACH_ID, PACS_ID + "#2", "user.node.ext", "user.node.mine"),
                ids(merged.entries()),
                "the merge still holds the hidden node, so that it replaces the site one"),
        () -> assertTrue(merged.entries().get(1).isHidden()),
        () ->
            assertEquals(
                List.of("My PACS", "Extended"),
                descriptions(retrieve),
                "BOTH matches, STORAGE does not"));
  }

  @Test
  void a_locked_site_node_ignores_the_user_one() throws IOException {
    Path siteJson = temp.resolve("site").resolve("dicomNodes.json");
    ListDocument.write(
        siteJson,
        "nodes",
        List.of(
            entry(
                "site.node.a",
                """
                "type": "DICOM", "description": "Locked", "aeTitle": "A", "hostname": "a",
                "port": 104, "usageType": "BOTH", "locked": true
                """),
            entry(
                "site.node.b",
                """
                "type": "DICOM", "description": "Free", "aeTitle": "B", "hostname": "b",
                "port": 104, "usageType": "BOTH"
                """)));
    Path userJson = temp.resolve("user").resolve("dicomNodes.json");
    ListDocument.write(
        userJson,
        "nodes",
        List.of(
            entry(
                "site.node.a",
                """
                "type": "DICOM", "description": "Hijacked", "aeTitle": "A", "hostname": "evil",
                "port": 104, "usageType": "BOTH"
                """),
            entry("site.node.b", "\"hidden\": true")));
    Documents documents = new Documents(siteJson, null, userJson, null);

    Merged<AbstractDicomNode> merged = AbstractDicomNode.loadMerged(documents, Type.DICOM);
    List<AbstractDicomNode> listed =
        AbstractDicomNode.loadDicomNodes(documents, Type.DICOM, UsageType.BOTH, null);
    DefaultDicomNode a = (DefaultDicomNode) merged.entries().getFirst();

    assertAll(
        () -> assertEquals(List.of("site.node.a", "site.node.b"), ids(merged.entries())),
        () -> assertEquals("Locked", a.getDescription()),
        () -> assertEquals("a", a.getHostname()),
        () -> assertFalse(a.isLocal()),
        () -> assertTrue(a.isLocked()),
        () -> assertTrue(merged.isLocked("site.node.a")),
        () -> assertTrue(merged.entries().get(1).isHidden(), "the unlocked one can be hidden"),
        () -> assertEquals(List.of("Locked"), descriptions(listed)),
        () -> assertEquals("Locked (site, locked)", DicomNodeListView.displayName(a)),
        () ->
            assertEquals(
                "Free",
                DicomNodeListView.displayName(merged.entries().get(1)),
                "hidden by the user, so a user entry"),
        () -> assertEquals("Free (site)", DicomNodeListView.displayName(siteNode("Free"))));
  }

  @Test
  void web_nodes_are_filtered_by_service() {
    Documents documents = legacySite(Type.WEB);

    List<AbstractDicomNode> wado =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.RETRIEVE, WebType.WADO);
    List<AbstractDicomNode> qido =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.RETRIEVE, WebType.QIDORS);
    List<AbstractDicomNode> storage =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.STORAGE, null);

    assertAll(
        () -> assertEquals(List.of("Legacy WADO"), descriptions(wado)),
        () ->
            assertEquals(List.of("Hospital DICOMweb"), descriptions(qido), "DICOMWEB serves QIDO"),
        () -> assertEquals(List.of("Hospital DICOMweb"), descriptions(storage)));
  }

  // ---- Migration and secrets ----------------------------------------------------------------

  @Test
  void a_legacy_user_xml_is_migrated_once_and_kept() throws IOException {
    Path userXml = temp.resolve("dicomWebNodes.xml");
    Path userJson = temp.resolve("dicomWebNodes.json");
    Files.copy(fixture("dicomWebNodes.xml"), userXml);
    Documents documents = new Documents(null, null, userJson, userXml);

    List<AbstractDicomNode> first =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.BOTH, null);
    ListDocument.Content written = ListDocument.read(userJson, "nodes");
    JsonObject web = written.entries().getFirst();
    DicomWebNode node = (DicomWebNode) first.getFirst();

    assertAll(
        () -> assertTrue(Files.isRegularFile(userXml), "the XML is kept for an older version"),
        () -> assertTrue(Files.isRegularFile(userJson), "the JSON is written"),
        () -> assertEquals(ListDocument.SCHEMA_VERSION, written.schema()),
        () ->
            assertEquals(
                List.of(
                    "user.node.https-dicomweb-example-org-dicomweb",
                    "user.node.https-wado-example-org-wado"),
                ids(first),
                "user ids for the user document"),
        () -> assertTrue(node.isLocal()),
        () ->
            assertEquals(
                Map.of("Authorization", "", "X-Api-Key", ""),
                JsonUtil.getStringMap(web, "headers"),
                "the user document never holds the header values"),
        () ->
            assertEquals(
                Optional.of("Bearer abc"),
                SecretStore.getInstance().get(node.getId(), "Authorization"),
                "they went to the secret store"),
        () ->
            assertEquals(
                Map.of("Authorization", "Bearer abc", "X-Api-Key", "k1"),
                node.getHeaders(),
                "and the loaded node has them back"));

    // The second load reads the JSON: the XML no longer matters
    Files.delete(userXml);
    List<AbstractDicomNode> second =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.BOTH, null);
    assertEquals(ids(first), ids(second));
    assertEquals(
        "Bearer abc", ((DicomWebNode) second.getFirst()).getHeaders().get("Authorization"));
  }

  @Test
  void web_header_values_round_trip_through_the_secret_store() throws IOException {
    Path userJson = temp.resolve("dicomWebNodes.json");
    DicomWebNode node =
        new DicomWebNode(
            "Mine", WebType.DICOMWEB, URI.create(DICOMWEB_URL).toURL(), UsageType.BOTH);
    node.addHeader("Authorization", "Bearer xyz");
    node.setTsuid(TransferSyntax.EXPLICIT_VR_LE);
    DefaultDicomNode other = new DefaultDicomNode("Not a web node", "AE", "h", 104, UsageType.BOTH);
    DicomWebNode site =
        new DicomWebNode("Site", WebType.WADO, URI.create("https://site/wado").toURL(), null);
    site.setLocal(false);

    AbstractDicomNode.saveDicomNodes(List.of(node, other, site), Type.WEB, userJson);
    JsonObject written = ListDocument.read(userJson, "nodes").entries().getFirst();
    Documents documents = new Documents(null, null, userJson, null);
    List<AbstractDicomNode> loaded =
        AbstractDicomNode.loadDicomNodes(documents, Type.WEB, UsageType.BOTH, null);
    DicomWebNode back = (DicomWebNode) loaded.getFirst();

    assertAll(
        () -> assertEquals(1, ListDocument.read(userJson, "nodes").entries().size()),
        () -> assertEquals("user.node.https-dicomweb-example-org-dicomweb", node.getId()),
        () -> assertEquals(node.getId(), written.getString("id")),
        () -> assertEquals("", written.getJsonObject("headers").getString("Authorization")),
        () ->
            assertEquals(
                Optional.of("Bearer xyz"),
                SecretStore.getInstance().get(node.getId(), "Authorization")),
        () -> assertEquals(1, loaded.size()),
        () -> assertEquals("Bearer xyz", back.getHeaders().get("Authorization")),
        () -> assertEquals(DICOMWEB_URL, back.getUrl().toString()),
        () -> assertEquals(TransferSyntax.EXPLICIT_VR_LE, back.getTsuid()),
        () -> assertEquals(WebType.DICOMWEB, back.getWebType()),
        () -> assertTrue(back.isLocal()));
  }

  @Test
  void a_document_spelling_out_the_defaults_and_the_old_auth_member_reads_the_same()
      throws IOException {
    Path userJson = temp.resolve("dicomWebNodes.json");
    ListDocument.write(
        userJson,
        "nodes",
        List.of(
            entry(
                "user.node.old",
                """
                "type": "WEB", "description": "Old", "url": "https://old.example.org/dicomweb",
                "webType": "DICOMWEB", "usageType": "BOTH", "tsuid": "NONE", "auth": "old-uid",
                "headers": {}
                """),
            entry(
                "user.node.new",
                """
                "type": "WEB", "description": "New", "url": "https://new.example.org/stow",
                "webType": "STOWRS", "authMethod": "new-uid", "headers": {}
                """),
            entry(
                "user.node.both",
                """
                "type": "WEB", "description": "Both", "url": "https://both.example.org/wado",
                "webType": "WADO", "usageType": "BOTH", "headers": {}
                """)));

    List<AbstractDicomNode> loaded =
        AbstractDicomNode.loadDicomNodes(
            new Documents(null, null, userJson, null), Type.WEB, UsageType.BOTH, null);
    DicomWebNode old = (DicomWebNode) loaded.getFirst();
    DicomWebNode fresh = (DicomWebNode) loaded.get(1);
    DicomWebNode both = (DicomWebNode) loaded.get(2);
    JsonObject rewritten = old.toJson(true);
    JsonObject rewrittenBoth = both.toJson(true);

    assertAll(
        () -> assertEquals(UsageType.BOTH, old.getUsageType()),
        () -> assertEquals(TransferSyntax.NONE, old.getTsuid()),
        () -> assertEquals("old-uid", old.getAuthMethodUid(), "auth is read as an alias"),
        () -> assertEquals("new-uid", fresh.getAuthMethodUid()),
        () -> assertEquals(UsageType.STORAGE, fresh.getUsageType(), "derived from the service"),
        () -> assertEquals(TransferSyntax.NONE, fresh.getTsuid()),
        () -> assertEquals("old-uid", rewritten.getString("authMethod")),
        () -> assertFalse(rewritten.containsKey("auth")),
        () -> assertFalse(rewritten.containsKey("usageType")),
        () -> assertFalse(rewritten.containsKey("tsuid")),
        () -> assertEquals(UsageType.BOTH, both.getUsageType()),
        () ->
            assertEquals(
                "BOTH",
                rewrittenBoth.getString("usageType"),
                "not the usage of a WADO service, so it must stay in the file"),
        () ->
            assertEquals(
                UsageType.BOTH,
                ((DicomWebNode) AbstractDicomNode.fromJson(rewrittenBoth, Type.WEB)).getUsageType(),
                "and reads back the same"));
  }

  @Test
  void a_new_node_gets_a_unique_user_id_at_save() throws IOException {
    Path userJson = temp.resolve("dicomNodes.json");
    DefaultDicomNode first = new DefaultDicomNode("First", "AE", "host", 104, UsageType.BOTH);
    DefaultDicomNode second = new DefaultDicomNode("Second", "AE", "host", 104, UsageType.BOTH);

    AbstractDicomNode.saveDicomNodes(List.of(first, second), Type.DICOM, userJson);
    List<AbstractDicomNode> loaded =
        AbstractDicomNode.loadDicomNodes(
            new Documents(null, null, userJson, null), Type.DICOM, UsageType.BOTH, null);

    assertAll(
        () -> assertEquals("user.node.ae-host-104", first.getId()),
        () -> assertEquals("user.node.ae-host-104#2", second.getId()),
        () ->
            assertEquals(List.of("user.node.ae-host-104", "user.node.ae-host-104#2"), ids(loaded)),
        () -> assertInstanceOf(DefaultDicomNode.class, loaded.getFirst()));
  }

  // ---- Selection ----------------------------------------------------------------------------

  @Test
  void the_selection_is_restored_by_id_then_by_description() {
    DefaultDicomNode a = new DefaultDicomNode("Archive", "A", "a", 104, UsageType.BOTH);
    a.setId("site.node.a");
    DefaultDicomNode b = new DefaultDicomNode("Backup", "B", "b", 104, UsageType.BOTH);
    b.setId("user.node.b");
    DefaultComboBoxModel<AbstractDicomNode> model = new DefaultComboBoxModel<>();
    model.addElement(a);
    model.addElement(b);
    model.setSelectedItem(b);
    WProperties prefs = new WProperties();

    AbstractDicomNode.nodeSelectionPersistence(prefs, b, "last");
    assertEquals("user.node.b", prefs.getProperty("last"), "the id is remembered");

    model.setSelectedItem(a);
    AbstractDicomNode.restoreNodeSelection(prefs, model, "last");
    assertSame(b, model.getSelectedItem(), "restored by id");

    prefs.setProperty("last", "Archive");
    AbstractDicomNode.restoreNodeSelection(prefs, model, "last");
    assertSame(a, model.getSelectedItem(), "an older preference holds the description");

    prefs.setProperty("last", "Unknown");
    AbstractDicomNode.restoreNodeSelection(prefs, model, "last");
    assertSame(a, model.getSelectedItem(), "no match leaves the selection alone");

    assertNull(new DefaultDicomNode("New", "N", "n", 104, UsageType.BOTH).getId());
  }
}
