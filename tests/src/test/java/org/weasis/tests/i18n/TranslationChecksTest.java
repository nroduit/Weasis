/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.tests.i18n;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationChecksTest {

  @Test
  void theSourceKeepsItsNotesLimitsAndContinuationLines() {
    MessageSource s =
        MessageSource.parse(
            Path.of("messages.properties"),
            List.of(
                "# Button of the dialog",
                "# max:24",
                "Dialog.ok=OK",
                "",
                "# {0} = the number of series, {1} = the study",
                "Opener.skipped={0} series not hung in {1}",
                "Long.text=first \\",
                "    second",
                "Unicode.x=a \\u00D7 b"));
    assertAll(
        () -> assertEquals(List.of("Button of the dialog", "max:24"), s.get("Dialog.ok").note()),
        () -> assertEquals(24, s.get("Dialog.ok").max().orElseThrow()),
        () -> assertEquals(List.of("0", "1"), s.get("Opener.skipped").arguments()),
        () -> assertTrue(s.get("Opener.skipped").note().get(0).contains("{0}")),
        () -> assertEquals("first second", s.get("Long.text").value()),
        () -> assertTrue(s.get("Long.text").note().isEmpty()),
        () -> assertEquals("a × b", s.get("Unicode.x").value()));
  }

  @Test
  void aSourceFailsOnPrintfPlaceholdersLoneApostrophesAndTextsOverTheirLimit() {
    MessageSource s =
        MessageSource.parse(
            Path.of("messages.properties"),
            List.of(
                "Old.printf=%d files",
                "Bad.apostrophe=L'image {0}",
                "Good.apostrophe=L''image {0}",
                "Plain.apostrophe=Don't worry",
                "# max:4",
                "Too.long=Cancel",
                "Html.ok=<html><b>{0}</b> and {1,number,0.00}</html>"));
    List<String> findings = TranslationChecks.checkSource(s).stream().map(f -> f.key()).toList();
    assertEquals(List.of("Old.printf", "Bad.apostrophe", "Too.long"), findings);
  }

  @Test
  void aTranslationMustKeepTheArgumentsAndRespectTheLimit() {
    MessageSource en =
        MessageSource.parse(
            Path.of("messages.properties"),
            List.of(
                "# {0} = count, {1} = study",
                "Opener.skipped={0} series not hung in {1}",
                "# max:6",
                "Dialog.ok=OK",
                "Dialog.cancel=Cancel",
                "Plain=Text"));
    MessageSource fr =
        MessageSource.parse(
            Path.of("messages_fr.properties"),
            List.of(
                "Opener.skipped={0} séries non accrochées",
                "Dialog.ok=Valider", "Dialog.cancel=Annuler", "Gone.key=Perdu", "Plain=L'texte"));
    List<String> findings =
        TranslationChecks.checkTranslation(en, fr).stream()
            .map(f -> f.key() + ": " + f.message())
            .toList();
    assertAll(
        () -> assertEquals(3, findings.size(), findings.toString()),
        () -> assertTrue(findings.get(0).startsWith("Opener.skipped: arguments [0] differ")),
        () ->
            assertTrue(
                findings.get(1).startsWith("Dialog.ok: 7 characters, the slot allows max:6")),
        () -> assertEquals("Gone.key: key absent from the English source", findings.get(2)));
  }

  @Test
  void aDirectoryIsCheckedWithItsSiblingTranslations(@TempDir Path dir) throws IOException {
    Path pkg = dir.resolve("org/weasis/x");
    Files.createDirectories(pkg);
    Files.writeString(pkg.resolve("messages.properties"), "A.b={0} ok\n", StandardCharsets.UTF_8);
    Files.writeString(
        pkg.resolve("messages_de.properties"), "A.b={1} nein\n", StandardCharsets.UTF_8);
    Files.writeString(
        pkg.resolve("messages_fr.properties"), "A.b={0} oui\n", StandardCharsets.UTF_8);
    Path noSource = dir.resolve("org/weasis/y");
    Files.createDirectories(noSource);
    Files.writeString(
        noSource.resolve("messages_fr.properties"), "B.c=L'image {0}\n", StandardCharsets.UTF_8);
    List<TranslationChecks.Finding> findings = TranslationChecks.checkDirectories(List.of(dir));
    assertAll(
        () -> assertEquals(2, findings.size(), findings.toString()),
        () ->
            assertEquals("messages_de.properties", findings.get(0).file().getFileName().toString()),
        () ->
            assertEquals("B.c", findings.get(1).key(), "the pattern is checked without a source"));
  }
}
