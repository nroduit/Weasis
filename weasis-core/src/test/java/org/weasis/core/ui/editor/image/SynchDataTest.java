/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchData.SyncState;

/**
 * Tests {@link SynchData} — the domain-level synchronization state shared by every view of a viewer
 * container. The propagation rules and the auto/manual sync state pinned here are what every
 * downstream view inspects via {@link SynchData#isActionEnable(String)} and {@link
 * SynchData#isSynchActivated()}.
 *
 * <p>A regression that lets a stale {@code actions} map leak between two {@code SynchData}
 * instances (no defensive copy in the copy constructor) would silently re-enable a propagation rule
 * the user had explicitly turned off; this is verified explicitly below.
 */
class SynchDataTest {

  private static final String PAN = "pan";
  private static final String ZOOM = "zoom";
  private static final String SCROLL = "scrollSeries";

  private static Map<String, Boolean> sampleActions() {
    Map<String, Boolean> m = new HashMap<>();
    m.put(PAN, true);
    m.put(ZOOM, false);
    m.put(SCROLL, true);
    return m;
  }

  @Test
  void constructor_storesActionsAndMode() {
    SynchData data = new SynchData(Mode.STACK, sampleActions(), false);
    assertEquals(Mode.STACK, data.getMode());
    assertEquals(3, data.getActions().size());
  }

  @Test
  void constructor_nullActionsThrowsIae() {
    assertThrows(IllegalArgumentException.class, () -> new SynchData(Mode.TILE, null, false));
  }

  @Test
  void constructor_synchTrueSetsAutoSyncOn() {
    SynchData data = new SynchData(Mode.TILE, sampleActions(), true);
    assertEquals(SyncState.ON, data.getAutoSyncState());
    assertTrue(data.isAutoSynchActivated());
  }

  @Test
  void constructor_synchFalseSetsAutoSyncOff() {
    SynchData data = new SynchData(Mode.STACK, sampleActions(), false);
    assertEquals(SyncState.OFF, data.getAutoSyncState());
    assertFalse(data.isAutoSynchActivated());
  }

  @Test
  void constructor_manualSyncIsAlwaysOffInitially() {
    assertEquals(
        SyncState.OFF, new SynchData(Mode.STACK, sampleActions(), true).getManualSyncState());
    assertEquals(
        SyncState.OFF, new SynchData(Mode.TILE, sampleActions(), false).getManualSyncState());
  }

  @Test
  void constructor_isOriginalTrueInitially() {
    assertTrue(new SynchData(Mode.STACK, sampleActions(), false).isOriginal());
  }

  @Test
  void copyConstructor_deepCopiesActionsMap() {
    Map<String, Boolean> source = sampleActions();
    SynchData original = new SynchData(Mode.STACK, source, true);
    SynchData copy = new SynchData(original);

    // Mutating the copy's actions must not bleed into the original (regression: shared HashMap
    // would silently re-enable / disable rules across views).
    copy.getActions().put(PAN, false);
    assertTrue(original.isActionEnable(PAN));
    assertFalse(copy.isActionEnable(PAN));
    assertNotSame(original.getActions(), copy.getActions());
  }

  @Test
  void copyConstructor_preservesAllFields() {
    SynchData original = new SynchData(Mode.TILE, sampleActions(), true);
    original.setManualSyncState(SyncState.ON);
    original.setOriginal(false);

    SynchData copy = new SynchData(original);
    assertEquals(Mode.TILE, copy.getMode());
    assertEquals(SyncState.ON, copy.getAutoSyncState());
    assertEquals(SyncState.ON, copy.getManualSyncState());
    assertFalse(copy.isOriginal());
  }

  @Test
  void copyConstructor_nullThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new SynchData(null));
  }

  @Test
  void copy_returnsNewSynchDataInstance() {
    SynchData original = new SynchData(Mode.STACK, sampleActions(), true);
    SynchData copy = original.copy();
    assertNotSame(original, copy);
    assertEquals(original.getMode(), copy.getMode());
  }

  @Test
  void isActionEnable_trueWhenActionMappedToTrue() {
    assertTrue(new SynchData(Mode.STACK, sampleActions(), true).isActionEnable(PAN));
  }

  @Test
  void isActionEnable_falseWhenActionMappedToFalse() {
    assertFalse(new SynchData(Mode.STACK, sampleActions(), true).isActionEnable(ZOOM));
  }

  @Test
  void isActionEnable_falseWhenActionMissing() {
    assertFalse(new SynchData(Mode.STACK, sampleActions(), true).isActionEnable("unknown-cmd"));
  }

  @Test
  void isActionEnable_falseWhenMappedToNull() {
    Map<String, Boolean> actions = new HashMap<>();
    actions.put(PAN, null);
    assertFalse(new SynchData(Mode.STACK, actions, true).isActionEnable(PAN));
  }

  @Test
  void setAutoSyncState_updatesStateAndIsSynchActivated() {
    SynchData data = new SynchData(Mode.STACK, sampleActions(), false);
    assertFalse(data.isSynchActivated());
    data.setAutoSyncState(SyncState.ON);
    assertTrue(data.isSynchActivated());
    assertTrue(data.isAutoSynchActivated());
    assertFalse(data.isManualSynchActivated());
  }

  @Test
  void setManualSyncState_updatesStateAndIsSynchActivated() {
    SynchData data = new SynchData(Mode.TILE, sampleActions(), false);
    data.setManualSyncState(SyncState.ON);
    assertTrue(data.isSynchActivated());
    assertTrue(data.isManualSynchActivated());
    assertFalse(data.isAutoSynchActivated());
  }

  @Test
  void isSynchActivated_trueWhenEitherAutoOrManualOn() {
    SynchData onlyAuto = new SynchData(Mode.TILE, sampleActions(), true);
    SynchData onlyManual = new SynchData(Mode.TILE, sampleActions(), false);
    onlyManual.setManualSyncState(SyncState.ON);
    SynchData both = new SynchData(Mode.TILE, sampleActions(), true);
    both.setManualSyncState(SyncState.ON);

    assertTrue(onlyAuto.isSynchActivated());
    assertTrue(onlyManual.isSynchActivated());
    assertTrue(both.isSynchActivated());
  }

  @Test
  void isSynchActivated_falseWhenBothOff() {
    SynchData data = new SynchData(Mode.STACK, sampleActions(), false);
    assertFalse(data.isSynchActivated());
  }

  @Test
  void setOriginal_togglesFlag() {
    SynchData data = new SynchData(Mode.STACK, sampleActions(), false);
    data.setOriginal(false);
    assertFalse(data.isOriginal());
    data.setOriginal(true);
    assertTrue(data.isOriginal());
  }

  @Test
  void mode_enumExposesStackAndTile() {
    assertSame(Mode.STACK, Mode.valueOf("STACK"));
    assertSame(Mode.TILE, Mode.valueOf("TILE"));
    assertEquals(2, Mode.values().length);
  }

  @Test
  void syncState_enumExposesOnAndOff() {
    assertSame(SyncState.ON, SyncState.valueOf("ON"));
    assertSame(SyncState.OFF, SyncState.valueOf("OFF"));
    assertEquals(2, SyncState.values().length);
  }
}
