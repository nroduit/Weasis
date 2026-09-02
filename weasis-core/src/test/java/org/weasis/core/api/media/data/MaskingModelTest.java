/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.api.media.data.TagW.TagType;

@DisplayName("MaskingModel")
class MaskingModelTest {

  private static MaskingModel read(String json) {
    return MaskingModel.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("tag keys are normalized from keywords, hex and internal names")
  void normalizeKey() {
    assertAll(
        () -> assertEquals("PatientName", MaskingModel.normalizeKey(" PatientName ", null)),
        () -> assertEquals("00101040", MaskingModel.normalizeKey("(0010,1040)", null)),
        () -> assertEquals("00101040", MaskingModel.normalizeKey("00101040", null)),
        () -> assertEquals("0009ABCD@ACME", MaskingModel.normalizeKey("(0009,abcd)", "ACME")),
        () -> assertEquals("00091010@ACME", MaskingModel.normalizeKey("(0009,1010)@ACME", null)),
        () -> assertEquals("weasis:Foo", MaskingModel.normalizeKey("weasis:Foo", null)),
        () -> assertNull(MaskingModel.normalizeKey("(0009,1010)", null), "private needs creator"),
        () -> assertNull(MaskingModel.normalizeKey(" ", null)));
  }

  @Test
  @DisplayName("a rule exposes its tag number, creator or keyword")
  void tagRuleAccessors() {
    TagRule privateRule = new TagRule("00091010@ACME", TagCategory.DIRECT_ID);
    TagRule keywordRule = new TagRule("PatientName", TagCategory.DIRECT_ID);
    TagRule internalRule = new TagRule("weasis:PatientPseudoUID", TagCategory.DIRECT_ID);
    assertAll(
        () -> assertEquals(0x00091010, privateRule.tagId().orElseThrow()),
        () -> assertEquals("ACME", privateRule.privateCreator()),
        () -> assertNull(privateRule.keyword()),
        () -> assertTrue(keywordRule.tagId().isEmpty()),
        () -> assertEquals("PatientName", keywordRule.keyword()),
        () -> assertTrue(internalRule.isInternal()),
        () -> assertEquals("PatientPseudoUID", internalRule.keyword()),
        () -> assertFalse(keywordRule.isInternal()));
  }

  @Test
  @DisplayName("a document is read and invalid entries are skipped")
  void readsDocument() {
    MaskingModel model =
        read(
            """
            {
              "schema": 1,
              "locked": true,
              "tags": [
                { "tag": "OperatorsName", "category": "DIRECT_ID" },
                { "tag": "(0009,1010)", "privateCreator": "ACME", "category": "direct_id" },
                { "tag": "(0009,1011)", "category": "DIRECT_ID" },
                { "tag": "StationName", "category": "NOT_A_CATEGORY" }
              ],
              "profiles": [
                {
                  "id": "demo",
                  "name": "Demo",
                  "label": { "fr": "Démo" },
                  "offerInExport": true,
                  "actions": { "DIRECT_ID": "PSEUDONYMIZE", "DATE": "BOGUS" },
                  "tagActions": { "weasis:Secret": "REMOVE" }
                },
                { "name": "no id" }
              ],
              "sessionProfile": "demo",
              "unknown": 42
            }
            """);
    MaskingProfile demo = model.profiles().getFirst();
    assertAll(
        () ->
            assertEquals(
                List.of(
                    new TagRule("OperatorsName", TagCategory.DIRECT_ID),
                    new TagRule("00091010@ACME", TagCategory.DIRECT_ID)),
                model.tags()),
        () -> assertEquals(1, model.profiles().size()),
        () -> assertEquals("demo", model.sessionProfile()),
        () -> assertNull(model.aiProfile()),
        () -> assertTrue(model.locked()),
        () -> assertTrue(demo.offerInExport()),
        () -> assertEquals(AnonymizationAction.KEEP, demo.actionFor(TagCategory.DATE)),
        () -> assertEquals("Démo", demo.displayName(Locale.FRENCH)),
        () -> assertEquals("Demo", demo.displayName(Locale.GERMAN)));
  }

  @Test
  @DisplayName("a tag action overrides the category of that tag")
  void tagActionOverridesCategory() {
    MaskingModel model =
        read(
            """
            { "profiles": [ { "id": "p",
                "actions": { "DIRECT_ID": "PSEUDONYMIZE" },
                "tagActions": { "weasis:Secret": "REMOVE" } } ] }
            """);
    MaskingProfile profile = model.profiles().getFirst();
    TagW secret = new TagW("Secret", TagType.STRING);
    secret.setCategory(TagCategory.DIRECT_ID);
    TagW other = new TagW("Other", TagType.STRING);
    other.setCategory(TagCategory.DIRECT_ID);
    assertAll(
        () -> assertEquals(AnonymizationAction.REMOVE, profile.actionFor(secret)),
        () -> assertEquals(AnonymizationAction.PSEUDONYMIZE, profile.actionFor(other)));
  }

  @Test
  @DisplayName("a newer schema is refused")
  void newerSchemaRefused() {
    assertThrows(IllegalArgumentException.class, () -> read("{ \"schema\": 2 }"));
  }
}
