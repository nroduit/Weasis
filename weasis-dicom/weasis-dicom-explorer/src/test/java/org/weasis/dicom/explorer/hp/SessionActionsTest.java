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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

class SessionActionsTest {

  @BeforeEach
  void installSettings(@TempDir Path dir) throws IOException {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
    LayeredDocument document = new LayeredDocument("hp.sessions.json", "sessions"); // NON-NLS
    document.configure(Optional.empty(), dir.resolve("hp.sessions.json")); // NON-NLS
    SessionStore store = new SessionStore(document, () -> 100, () -> 90);
    store.keep(SessionStoreTest.session("s1", Instant.parse("2026-10-17T10:00:00Z"), false));
    SessionActions.setStore(() -> store);
  }

  @AfterEach
  void restoreStore() {
    SessionActions.setStore(SessionStore::new);
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
  }

  private static MediaSeriesGroup study(String uid) {
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn(uid);
    return study;
  }

  @Test
  void theOfferFollowsTheSettingAndTheStudy() {
    MediaSeriesGroup kept = study("1.2.3.s1"); // NON-NLS
    assertAll(
        () -> assertEquals("s1", SessionActions.offerFor(kept).orElseThrow().id()),
        () -> assertTrue(SessionActions.offerFor(study("1.2.3.other")).isEmpty()),
        () -> assertTrue(SessionActions.offerFor(null).isEmpty()),
        () ->
            assertEquals(
                "reading s1", SessionActions.label(SessionActions.offerFor(kept).orElseThrow())));
    Settings.set(HpSettings.RESTORE, HpSettings.Restore.NEVER);
    assertTrue(SessionActions.offerFor(kept).isEmpty(), "never offered");

    Settings.set(HpSettings.RESTORE, HpSettings.Restore.OFFER);
    DicomModel model = mock(DicomModel.class);
    assertFalse(SessionActions.restoreAutomatically(model, kept), "offer is not automatic");
    Settings.set(HpSettings.RESTORE, HpSettings.Restore.AUTO);
    assertFalse(
        SessionActions.restoreAutomatically(model, kept), "its study is not loaded in the model");
  }

  @Test
  void keepOnCloseIsOffByDefaultAndDoesNothingThen() {
    assertFalse(Settings.get(HpSettings.KEEP_ON_CLOSE));
    SessionActions.keepOnClose();
    assertEquals(1, SessionActions.store().all().size(), "nothing added");
  }
}
