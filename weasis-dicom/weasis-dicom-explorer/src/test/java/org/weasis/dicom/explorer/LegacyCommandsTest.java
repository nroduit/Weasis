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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;

/** The 4.x command lines documented on the site must still parse: the syntax is the public API. */
class LegacyCommandsTest {

  private static final List<String> LINES =
      List.of(
          "dicom:get -l /tmp/dicom",
          "dicom:get --local=/tmp/a -l /tmp/b",
          "dicom:get -r http://host/file.dcm -w http://host/manifest.xml",
          "dicom:get -z http://host/study.zip",
          "dicom:get -p",
          "dicom:get -i H4sIAAAAAAAA",
          "dicom:close -a",
          "dicom:close -y 1.2.3 -s 1.2.4",
          "dicom:close --patient=P1",
          "dicom:rs -u http://pacs/dicomweb -r studyUID=1.2.3",
          "dicom:rs --url=http://pacs -r a=1 -r b=2 -H \"Authorization: Bearer x\" --query-header q --retrieve-header r --query-ext qe --retrieve-ext re --accept-ext ae --auth-uid u --oidc-iss i --oidc-login l --show-whole-study",
          // the 5.0 spellings of the same lines
          "dicom:load --path /tmp/dicom --wait first-series",
          "dicom:load-dicomweb --url http://pacs/dicomweb --request studyUID=1.2.3",
          "dicom:unload --all",
          "dicom:unload --patient P1 --study 1.2.3 --series 1.2.4",
          "dicom:open --series 1.2.3 --viewer-type mpr",
          "dicom:open --series 1.2.3 --interest",
          "dicom:tag --series 1.2.3 --tag SliceLocation",
          "dicom:objects",
          "dicom:objects --study 1.2.3 --type ko",
          "dicom:objects --series 1.2.4 --type seg",
          "dicom:interest",
          "dicom:interest --study 1.2.3 --images",
          "dicom:interest --series 1.2.4");

  @Test
  void documentedCommandLinesStillParse() {
    Object model = new DicomModelCommands(new DicomModel());
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(model, line), "legacy: " + line))
            .toList());
  }
}
