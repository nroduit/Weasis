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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

@DisplayName("IdentityMask")
class IdentityMaskTest {

  private static final byte[] SALT = {1, 2, 3, 4};

  private static TagW personTag() {
    return new TagW(
        0x00100010, "PatientName", "Patient Name", TagType.DICOM_PERSON_NAME, 1, 1, null);
  }

  private static TagW stringTag() {
    return new TagW(0x00080050, "AccessionNumber", "Accession", TagType.STRING, 1, 1, null);
  }

  private static TagW dateTag() {
    return new TagW(0x00080020, "StudyDate", "Study Date", TagType.DICOM_DATE, 1, 1, null);
  }

  private static IdentityMask maskOf(Map<TagW, AnonymizationAction> actions, int shiftDays) {
    return new IdentityMask(
        tag -> actions.getOrDefault(tag, AnonymizationAction.KEEP),
        IdentityMask.saltedPseudonymizer(SALT),
        shiftDays);
  }

  @Test
  @DisplayName("no mask is bound by default")
  void active_unboundByDefault() {
    assertTrue(IdentityMask.active().isEmpty());
  }

  @Test
  @DisplayName("binding lasts only for the dynamic extent")
  void runMasked_bindsThenUnbinds() {
    IdentityMask mask = IdentityMask.uniform(AnonymizationAction.REMOVE);
    mask.runMasked(() -> assertEquals(mask, IdentityMask.active().orElseThrow()));
    assertTrue(IdentityMask.active().isEmpty());
  }

  @Test
  @DisplayName("binding unwinds when the masked task throws")
  void runMasked_unbindsOnException() {
    IdentityMask mask = IdentityMask.uniform(AnonymizationAction.REMOVE);
    assertThrows(
        IllegalStateException.class,
        () ->
            mask.runMasked(
                () -> {
                  throw new IllegalStateException("boom");
                }));
    assertTrue(IdentityMask.active().isEmpty());
  }

  @Test
  @DisplayName("a mask on one thread does not leak to another")
  void runMasked_isThreadScoped() throws Exception {
    IdentityMask mask = IdentityMask.uniform(AnonymizationAction.REMOVE);
    AtomicReference<Boolean> boundElsewhere = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);

    mask.runMasked(
        () -> {
          Thread other =
              new Thread(
                  () -> {
                    boundElsewhere.set(IdentityMask.active().isPresent());
                    done.countDown();
                  });
          other.start();
          try {
            assertTrue(done.await(5, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    assertAll(
        () -> assertFalse(boundElsewhere.get(), "mask leaked to another thread"),
        () -> assertTrue(IdentityMask.active().isEmpty()));
  }

  @Test
  @DisplayName("pseudonyms are stable for the same value and differ across values")
  void pseudonymize_isDeterministic() {
    TagW tag = personTag();
    IdentityMask mask = maskOf(Map.of(tag, AnonymizationAction.PSEUDONYMIZE), 0);

    Object first = mask.apply(tag, "DOE^JOHN");
    Object again = mask.apply(tag, "DOE^JOHN");
    Object other = mask.apply(tag, "ROE^JANE");

    assertAll(
        () -> assertEquals(first, again),
        () -> assertNotEquals(first, other),
        () -> assertNotEquals("DOE^JOHN", first));
  }

  @Test
  @DisplayName("a person name keeps the DICOM PN structure")
  void pseudonymize_keepsPersonNameFormat() {
    TagW tag = personTag();
    IdentityMask mask = maskOf(Map.of(tag, AnonymizationAction.PSEUDONYMIZE), 0);
    String value = (String) mask.apply(tag, "DOE^JOHN");
    assertTrue(value.startsWith("ANONYMOUS^"), value);
  }

  @Test
  @DisplayName("a different salt yields a different pseudonym for the same value")
  void pseudonymize_dependsOnSalt() {
    TagW tag = stringTag();
    Map<TagW, AnonymizationAction> actions = Map.of(tag, AnonymizationAction.PSEUDONYMIZE);
    IdentityMask a = maskOf(actions, 0);
    IdentityMask b =
        new IdentityMask(
            t -> actions.getOrDefault(t, AnonymizationAction.KEEP),
            IdentityMask.saltedPseudonymizer(new byte[] {9, 9}),
            0);

    assertNotEquals(a.apply(tag, "ACC-1"), b.apply(tag, "ACC-1"));
  }

  @Test
  @DisplayName("shifting preserves the interval between two dates")
  void shift_preservesIntervals() {
    TagW tag = dateTag();
    IdentityMask mask = maskOf(Map.of(tag, AnonymizationAction.SHIFT), 37);

    LocalDate first = (LocalDate) mask.apply(tag, LocalDate.of(2026, 3, 1));
    LocalDate second = (LocalDate) mask.apply(tag, LocalDate.of(2026, 3, 15));

    assertAll(
        () -> assertEquals(LocalDate.of(2026, 4, 7), first),
        () -> assertEquals(14, second.toEpochDay() - first.toEpochDay()));
  }

  @Test
  @DisplayName("KEEP leaves the value untouched")
  void apply_keepIsIdentity() {
    TagW tag = stringTag();
    IdentityMask mask = maskOf(Map.of(tag, AnonymizationAction.KEEP), 30);
    assertEquals("ACC-1", mask.apply(tag, "ACC-1"));
  }

  @Test
  @DisplayName("maskText substitutes, empties or passes through per action")
  void maskText_perAction() {
    TagW person = personTag();
    TagW accession = stringTag();
    IdentityMask mask =
        maskOf(
            Map.of(person, AnonymizationAction.PSEUDONYMIZE, accession, AnonymizationAction.REMOVE),
            0);

    assertAll(
        () -> assertEquals("DOE^JOHN", IdentityMask.maskText(person, "DOE^JOHN")),
        () ->
            assertTrue(
                mask.callMasked(() -> IdentityMask.maskText(person, "DOE^JOHN"))
                    .startsWith("ANONYMOUS^")),
        () -> assertEquals("", mask.callMasked(() -> IdentityMask.maskText(accession, "ACC-1"))),
        () -> assertNull(mask.callMasked(() -> IdentityMask.maskText(person, null))));
  }
}
