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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

@DisplayName("TagView under an IdentityMask")
class TagViewIdentityMaskTest {

  private final TagW name =
      new TagW(0x00100010, "PatientName", "Patient Name", TagType.DICOM_PERSON_NAME, 1, 1, null);
  private final TagW id =
      new TagW(0x00100020, "PatientID", "Patient ID", TagType.STRING, 1, 1, null);

  private final Map<TagW, Object> values = new HashMap<>();

  private IdentityMask mask(Map<TagW, AnonymizationAction> actions) {
    return new IdentityMask(
        tag -> actions.getOrDefault(tag, AnonymizationAction.KEEP),
        IdentityMask.saltedPseudonymizer(new byte[] {7}),
        0);
  }

  private String resolve(TagView view) {
    return view.resolveText(values::get, null);
  }

  @Test
  @DisplayName("without a mask the real value is returned")
  void resolveText_unmasked() {
    values.put(name, "DOE^JOHN");
    assertEquals("DOE^JOHN", resolve(new TagView(name)));
  }

  @Test
  @DisplayName("PSEUDONYMIZE substitutes in place instead of blanking")
  void resolveText_pseudonymize() {
    values.put(name, "DOE^JOHN");
    TagView view = new TagView(name);

    String masked =
        mask(Map.of(name, AnonymizationAction.PSEUDONYMIZE)).callMasked(() -> resolve(view));

    assertAll(
        () -> assertNotEquals("DOE^JOHN", masked),
        () -> assertTrue(masked.startsWith("ANONYMOUS^"), masked));
  }

  @Test
  @DisplayName("REMOVE falls through to the next candidate tag")
  void resolveText_removeFallsThrough() {
    values.put(name, "DOE^JOHN");
    values.put(id, "PID-42");
    TagView view = new TagView(name, id);

    String masked = mask(Map.of(name, AnonymizationAction.REMOVE)).callMasked(() -> resolve(view));

    assertEquals("PID-42", masked);
  }

  @Test
  @DisplayName("CLEAR empties the field without falling through")
  void resolveText_clearStops() {
    values.put(name, "DOE^JOHN");
    values.put(id, "PID-42");
    TagView view = new TagView(name, id);

    String masked = mask(Map.of(name, AnonymizationAction.CLEAR)).callMasked(() -> resolve(view));

    assertEquals("", masked);
  }

  @Test
  @DisplayName("REMOVE on every candidate yields an empty result")
  void resolveText_allRemoved() {
    values.put(name, "DOE^JOHN");
    values.put(id, "PID-42");
    TagView view = new TagView(name, id);
    Map<TagW, AnonymizationAction> actions =
        Map.of(name, AnonymizationAction.REMOVE, id, AnonymizationAction.REMOVE);

    assertEquals("", mask(actions).callMasked(() -> resolve(view)));
  }

  @Test
  @DisplayName("the same patient resolves to the same pseudonym across views")
  void resolveText_coherentAcrossViews() {
    values.put(name, "DOE^JOHN");
    TagView first = new TagView(name);
    TagView second = new TagView(name);
    IdentityMask mask = mask(Map.of(name, AnonymizationAction.PSEUDONYMIZE));

    assertEquals(mask.callMasked(() -> resolve(first)), mask.callMasked(() -> resolve(second)));
  }
}
