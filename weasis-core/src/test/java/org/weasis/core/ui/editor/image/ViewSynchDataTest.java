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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.ViewSynchData.ManualSyncData;

/**
 * Tests {@link ViewSynchData} — the per-view extension of {@link SynchData} carrying the
 * FrameOfReferenceUID grouping, the orphan/auto-sync/manual-sync flags, and the per-view
 * propagation overrides.
 *
 * <p>This suite is the **automated regression coverage for WEA-004 v3 R19** ("Synchronisation of
 * views between series of distinct FrameOfReferenceUID"). The two scenarios that matter the most:
 *
 * <ol>
 *   <li>A view whose series carries a different FoR is marked {@code orphan = true} and must not be
 *       auto-synced (otherwise images of unrelated anatomy can be compared side by side).
 *   <li>Per-view {@code userActionOverrides} entries always win over the shared {@code actions}
 *       template — this is the contract the fix in {@code bb082d6a6} ("honor per-view sync options
 *       in auto-sync mode") relies on.
 * </ol>
 */
class ViewSynchDataTest {

  private static final String PAN = "pan";
  private static final String ZOOM = "zoom";
  private static final String SCROLL = "scrollSeries";

  private static Map<String, Boolean> templateActions(boolean panOn) {
    Map<String, Boolean> m = new HashMap<>();
    m.put(PAN, panOn);
    m.put(ZOOM, false);
    m.put(SCROLL, true);
    return m;
  }

  @Test
  void constructor_initializesFrameOfReferenceUidNull() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    assertNull(data.getFrameOfReferenceUID());
  }

  @Test
  void constructor_canBeAutoSyncedDefaultsTrue() {
    assertTrue(new ViewSynchData(Mode.TILE, templateActions(true), true).canBeAutoSynced());
  }

  @Test
  void constructor_canBeManuallySyncedDefaultsFalse() {
    assertFalse(new ViewSynchData(Mode.TILE, templateActions(true), true).canBeManuallySynced());
  }

  @Test
  void constructor_orphanDefaultsFalse() {
    assertFalse(new ViewSynchData(Mode.STACK, templateActions(true), false).isOrphan());
  }

  @Test
  void setFrameOfReferenceUID_storesValue() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    data.setFrameOfReferenceUID("1.2.840.10008.1.4");
    assertEquals("1.2.840.10008.1.4", data.getFrameOfReferenceUID());
  }

  @Test
  void setOrphan_marksViewAsOutsideAutoSyncGroup() {
    // R19: a view whose series carries a different FrameOfReferenceUID is flagged orphan
    // so the sync manager skips it in auto-sync mode.
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    data.setOrphan(true);
    assertTrue(data.isOrphan());
    data.setOrphan(false);
    assertFalse(data.isOrphan());
  }

  @Test
  void setCanBeAutoSynced_togglesFlag() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    data.setCanBeAutoSynced(false);
    assertFalse(data.canBeAutoSynced());
    data.setCanBeAutoSynced(true);
    assertTrue(data.canBeAutoSynced());
  }

  @Test
  void setCanBeManuallySynced_togglesFlag() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    data.setCanBeManuallySynced(true);
    assertTrue(data.canBeManuallySynced());
  }

  // ---------------------------------------------------------------------------
  // Regression coverage for bb082d6a6 "Fix selection logic to honor per-view
  // sync options in auto-sync mode."
  //
  // The contract is: an entry in userActionOverrides ALWAYS wins over the
  // shared actions template. A bug here would either re-enable a propagation
  // rule the user explicitly disabled on this view, or hide a rule the user
  // re-enabled on this view.
  // ---------------------------------------------------------------------------

  @Test
  void isActionEnable_overrideTrueWinsOverTemplateFalse() {
    // Template says PAN=false; user override says PAN=true for this view only.
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(false), true);
    data.setUserActionOverride(PAN, true);
    assertTrue(data.isActionEnable(PAN));
  }

  @Test
  void isActionEnable_overrideFalseWinsOverTemplateTrue() {
    // The actual scenario from the bb082d6a6 fix: template default says
    // SCROLL=true, but the user has turned scroll off for this specific view.
    // The auto-sync manager must NOT propagate scroll to this view.
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    data.setUserActionOverride(SCROLL, false);
    assertFalse(data.isActionEnable(SCROLL));
  }

  @Test
  void isActionEnable_fallsBackToTemplateWhenNoOverride() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    assertTrue(data.isActionEnable(PAN)); // template PAN=true
    assertFalse(data.isActionEnable(ZOOM)); // template ZOOM=false
  }

  @Test
  void setUserActionOverride_addsToOverrideMap() {
    ViewSynchData data = new ViewSynchData(Mode.TILE, templateActions(true), false);
    data.setUserActionOverride(PAN, false);
    assertEquals(Boolean.FALSE, data.getUserActionOverrides().get(PAN));
  }

  @Test
  void setUserActionOverride_nullCmdIsNoOp() {
    ViewSynchData data = new ViewSynchData(Mode.TILE, templateActions(true), false);
    data.setUserActionOverride(null, true);
    assertTrue(data.getUserActionOverrides().isEmpty());
  }

  @Test
  void clearUserActionOverrides_dropsAllOverridesAndRestoresTemplate() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), true);
    data.setUserActionOverride(PAN, false);
    data.setUserActionOverride(SCROLL, false);
    data.clearUserActionOverrides();
    assertTrue(data.getUserActionOverrides().isEmpty());
    // After clear, behaviour reverts to the shared template.
    assertTrue(data.isActionEnable(PAN));
    assertTrue(data.isActionEnable(SCROLL));
  }

  // ---------------------------------------------------------------------------
  // Copy semantics
  // ---------------------------------------------------------------------------

  @Test
  void copy_returnsViewSynchDataInstance() {
    ViewSynchData original = new ViewSynchData(Mode.STACK, templateActions(true), true);
    ViewSynchData copy = original.copy();
    assertNotSame(original, copy);
  }

  @Test
  void copy_preservesFoRUidAndFlags() {
    ViewSynchData original = new ViewSynchData(Mode.STACK, templateActions(true), false);
    original.setFrameOfReferenceUID("1.2.3");
    original.setOrphan(true);
    original.setCanBeAutoSynced(false);
    original.setCanBeManuallySynced(true);

    ViewSynchData copy = original.copy();
    assertEquals("1.2.3", copy.getFrameOfReferenceUID());
    assertTrue(copy.isOrphan());
    assertFalse(copy.canBeAutoSynced());
    assertTrue(copy.canBeManuallySynced());
  }

  @Test
  void copy_preservesUserActionOverridesSoLayoutChangesDoNotLoseUserChoices() {
    // The override map is per-view and survives view selection/layout changes — see the
    // userActionOverrides Javadoc and bb082d6a6.
    ViewSynchData original = new ViewSynchData(Mode.STACK, templateActions(true), true);
    original.setUserActionOverride(SCROLL, false);

    ViewSynchData copy = original.copy();
    assertFalse(copy.isActionEnable(SCROLL));
    assertEquals(Boolean.FALSE, copy.getUserActionOverrides().get(SCROLL));

    // Mutating the copy's override map must not bleed back to the original.
    copy.setUserActionOverride(PAN, false);
    assertFalse(original.getUserActionOverrides().containsKey(PAN));
  }

  // ---------------------------------------------------------------------------
  // ManualSyncData set operations
  // ---------------------------------------------------------------------------

  @Test
  void addManualSyncData_addsEntryToSet() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    ViewCanvas<?> pane = Mockito.mock(ViewCanvas.class);
    data.addManualSyncData(1.0, 2.5, pane);
    assertEquals(1, data.getManualSyncDataSet().size());
  }

  @Test
  void clearManualSyncData_emptiesSet() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    data.addManualSyncData(1.0, 2.0, Mockito.mock(ViewCanvas.class));
    data.addManualSyncData(3.0, 4.0, Mockito.mock(ViewCanvas.class));
    data.clearManualSyncData();
    assertTrue(data.getManualSyncDataSet().isEmpty());
  }

  @Test
  void removeManualSyncData_removesMatchingEntry() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    ViewCanvas<?> pane = Mockito.mock(ViewCanvas.class);
    data.addManualSyncData(1.0, 2.0, pane);
    ManualSyncData entry = data.getManualSyncDataSet().iterator().next();
    data.removeManualSyncData(entry);
    assertTrue(data.getManualSyncDataSet().isEmpty());
  }

  @Test
  void getManualSyncDataByPane_returnsMatchByIdentity() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    ViewCanvas<?> paneA = Mockito.mock(ViewCanvas.class);
    ViewCanvas<?> paneB = Mockito.mock(ViewCanvas.class);
    data.addManualSyncData(1.0, 2.0, paneA);
    data.addManualSyncData(3.0, 4.0, paneB);

    ManualSyncData match = data.getManualSyncDataByPane(paneB);
    assertSame(paneB, match.getTargetPane());
    assertEquals(3.0, match.getSourceLocation());
    assertEquals(4.0, match.getTargetLocation());
  }

  @Test
  void getManualSyncDataByPane_returnsNullWhenNoMatch() {
    ViewSynchData data = new ViewSynchData(Mode.STACK, templateActions(true), false);
    data.addManualSyncData(1.0, 2.0, Mockito.mock(ViewCanvas.class));
    assertNull(data.getManualSyncDataByPane(Mockito.mock(ViewCanvas.class)));
  }

  @Test
  void manualSyncData_constructorAndAccessors() {
    ViewCanvas<?> pane = Mockito.mock(ViewCanvas.class);
    ManualSyncData entry = new ManualSyncData(1.5, 7.25, pane);
    assertEquals(1.5, entry.getSourceLocation());
    assertEquals(7.25, entry.getTargetLocation());
    assertSame(pane, entry.getTargetPane());
  }

  @Test
  void manualSyncData_settersUpdateFields() {
    ViewCanvas<?> initial = Mockito.mock(ViewCanvas.class);
    ViewCanvas<?> replaced = Mockito.mock(ViewCanvas.class);
    ManualSyncData entry = new ManualSyncData(0.0, 0.0, initial);
    entry.setSourceLocation(11.0);
    entry.setTargetLocation(22.0);
    entry.setTargetPane(replaced);
    assertEquals(11.0, entry.getSourceLocation());
    assertEquals(22.0, entry.getTargetLocation());
    assertSame(replaced, entry.getTargetPane());
  }
}
