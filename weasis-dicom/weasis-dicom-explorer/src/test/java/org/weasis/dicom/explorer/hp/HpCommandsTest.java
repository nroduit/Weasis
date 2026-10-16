/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.WeasisExtensions;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.hp.HangingProtocol;

class HpCommandsTest {

  private static final List<String> LINES =
      List.of(
          "hp:list",
          "hp:list --json",
          "hp:match",
          "hp:match --study 1.2.3 --protocol ct-chest",
          "hp:match --protocols /tmp/draft.json",
          "hp:apply",
          "hp:apply --study 1.2.3 --protocol ct-chest --step 2 --new-tab",
          "hp:apply --protocols /tmp/draft.json --json",
          "hp:features",
          "hp:features --study 1.2.3",
          "hp:features --corpus /tmp/corpus --json",
          "hp:sets",
          "hp:sets --view v1 --json",
          "hp:group --next",
          "hp:group --view v1 --index 2",
          "hp:group --protocol weasis.ct",
          "hp:explain --cell 2",
          "hp:show --id weasis.ct",
          "hp:validate --file /tmp/draft.json",
          "hp:validate --corpus /tmp/corpus",
          "hp:validate --corpus /tmp/corpus --protocols /tmp/draft.json --protocol ct-chest --json",
          "hp:validate --corpus /tmp/corpus --expected /tmp/corpus/hp-expected.json");

  @Test
  void documentedCommandLinesParse() {
    Object model = new HpCommands();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () -> assertDoesNotThrow(() -> CommandModel.parse(model, line), line))
            .toList());
  }

  @Test
  void theNamespaceFollowsTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("hp", new HpCommands())); // NON-NLS
  }

  @Test
  void validateNeedsItsCorpusOrAFile() {
    CommandResult result =
        CommandModel.execute(new HpCommands(), "validate", new String[0]); // NON-NLS
    assertTrue(result.status() != CommandResult.OK, String.valueOf(result.status()));
  }

  @Test
  void theStudyComesFromTheOptionTheSelectionOrTheOnlyLoadedOne() {
    DicomModel model = mock(DicomModel.class);
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    MediaSeriesGroup other = mock(MediaSeriesGroup.class);
    DicomSeries selected = mock(DicomSeries.class);
    when(model.getStudyNode("1.2.3")).thenReturn(study); // NON-NLS
    when(model.getChildren(MediaSeriesGroupNode.rootNode)).thenReturn(List.of(patient));
    when(model.getParent(selected, DicomModel.study)).thenReturn(other);

    HpCommands none = new HpCommands().with(() -> model, List::of, List::of);
    HpCommands withSelection =
        new HpCommands().with(() -> model, List::of, () -> List.of(selected));

    when(model.getChildren(patient)).thenReturn(List.of(study));
    MediaSeriesGroup only = none.resolveStudy(model, null);
    when(model.getChildren(patient)).thenReturn(List.of(study, other));
    assertAll(
        () -> assertSame(study, none.resolveStudy(model, new StudyUid("1.2.3"))), // NON-NLS
        () -> assertSame(only, study),
        () -> assertSame(other, withSelection.resolveStudy(model, null)),
        () ->
            assertTrue(
                assertThrows(IllegalArgumentException.class, () -> none.resolveStudy(model, null))
                    .getMessage()
                    .contains("--study")), // NON-NLS
        () ->
            assertThrows(
                CommandFailure.class,
                () -> none.resolveStudy(model, new StudyUid("9.9")))); // NON-NLS
  }

  @Test
  void aProtocolFileHoldsAnArrayOrAProtocolsObject(@TempDir Path dir) throws IOException {
    HangingProtocol protocol = new HangingProtocol();
    protocol.setHangingProtocolName("Draft"); // NON-NLS
    String entry =
        ProtocolJson.toJson("draft", protocol, WeasisExtensions.NONE).toString(); // NON-NLS
    Path array = dir.resolve("array.json"); // NON-NLS
    Path object = dir.resolve("object.json"); // NON-NLS
    Files.writeString(array, "[" + entry + "]"); // NON-NLS
    Files.writeString(object, "{\"protocols\":[" + entry + "]}"); // NON-NLS
    List<Candidate> fromArray = HpCommands.Validate.readProtocols(array);
    List<Candidate> fromObject = HpCommands.Validate.readProtocols(object);
    assertAll(
        () -> assertEquals(1, fromArray.size()),
        () -> assertEquals("draft", fromArray.getFirst().document().id()), // NON-NLS
        () ->
            assertEquals(
                "Draft",
                fromObject.getFirst().document().protocol().getHangingProtocolName()), // NON-NLS
        () -> assertTrue(fromArray.getFirst().enabled()));
  }
}
