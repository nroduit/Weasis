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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.ContextFacts;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.BuiltInProtocols;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Context;

class HangingContextContributorTest {

  /** The built-in CT protocol with a profile for the protocol and another for group 2. */
  private static Document protocol() {
    Document ct = ProtocolJson.fromJson(BuiltInProtocols.entries().getFirst());
    WeasisExtensions weasis = ct.weasis();
    WeasisExtensions edited =
        new WeasisExtensions(
            weasis.imageSets(),
            weasis.displaySets(),
            Map.of(2, "test.compare"), // NON-NLS
            weasis.placement(),
            weasis.maskingProfile(),
            weasis.loading(),
            "test.ct", // NON-NLS
            weasis.extra());
    return new Document(ct.id(), ct.protocol(), edited);
  }

  private static Applied applied(Document document, int step, ViewerPlugin<?> viewer) {
    HangingLayout layout = HangingLayout.builder().grid("1x1").build(); // NON-NLS
    HangingPlan plan =
        new HangingPlan(
            document.id(),
            "CT", // NON-NLS
            "MANUFACTURER", // NON-NLS
            1,
            List.of(),
            List.of(
                new Step(1, "reading", layout, List.of()), // NON-NLS
                new Step(2, "compare", layout, List.of())), // NON-NLS
            List.of());
    Context context =
        new Context(
            null, null, null, List.of(new Candidate(document, Origin.BUILT_IN, true)), List.of());
    return new Applied(
        plan, step, viewer == null ? List.of() : List.of(viewer), List.of(), List.of(), context);
  }

  @Test
  void theProfileOfAStepIsTheOneOfItsGroupElseTheProtocols() {
    Document document = protocol();
    assertAll(
        () ->
            assertEquals(
                Optional.of("test.ct"), ActiveHanging.uiProfileOf(applied(document, 0, null))),
        () ->
            assertEquals(
                Optional.of("test.compare"), ActiveHanging.uiProfileOf(applied(document, 1, null))),
        () ->
            assertTrue(
                ActiveHanging.uiProfileOf(
                        applied(
                            new Document(document.id(), document.protocol(), WeasisExtensions.NONE),
                            0,
                            null))
                    .isEmpty()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void theContributorPublishesTheProtocolTheStepAndTheProfile() {
    ViewerPlugin<MediaElement> viewer = mock(ViewerPlugin.class);
    HangingPlanApplier.replace(applied(protocol(), 1, viewer));
    Map<String, Object> facts = new LinkedHashMap<>();
    new HangingContextContributor().contribute(viewer, null, facts);
    assertAll(
        () -> assertEquals("weasis.ct", facts.get(ContextFacts.HANGING_PROTOCOL)),
        () -> assertEquals(2, facts.get(HangingContextContributor.STEP)),
        () -> assertEquals(2, facts.get(HangingContextContributor.STEPS)),
        () -> assertEquals("test.compare", facts.get(ContextFacts.UI_PROFILE)));
  }
}
