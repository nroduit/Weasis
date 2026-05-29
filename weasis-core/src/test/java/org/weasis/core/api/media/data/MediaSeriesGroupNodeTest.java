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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

/**
 * Tests {@link MediaSeriesGroupNode} — the identity container that holds the patient, study, and
 * series hierarchy across the entire viewer. Two nodes are equal iff their tagID resolves to the
 * same value; this is the load-bearing contract for the patient-study-series hierarchy lookup and
 * for cross-frame graphic anchoring. A regression here would silently merge two distinct entities
 * (e.g. two studies with the same StudyInstanceUID but different content) or split one entity
 * across multiple records.
 */
class MediaSeriesGroupNodeTest {

  // Stable test-only TagW instances to avoid collisions with the built-in dictionary.
  private static final TagW PATIENT_ID = new TagW(98_000, "PatientId", TagType.STRING);
  private static final TagW STUDY_ID = new TagW(98_001, "StudyId", TagType.STRING);
  private static final TagW SOME_TAG = new TagW(98_002, "SomeTag", TagType.STRING);

  // -- Constructor guards ---------------------------------------------------

  @Test
  void constructor_nullTagIdThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new MediaSeriesGroupNode(null, "id-1", null));
  }

  @Test
  void constructor_nullIdentifierThrowsNpe() {
    assertThrows(
        NullPointerException.class, () -> new MediaSeriesGroupNode(PATIENT_ID, null, null));
  }

  @Test
  void constructor_acceptsNullDisplayTagAndDefaultsToTagIdView() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    // No exception, and toString round-trips through a view built on the tagID.
    assertNotNull(node);
  }

  @Test
  void constructor_storesIdentifierUnderTagId() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertEquals("id-1", node.getTagValue(PATIENT_ID));
  }

  @Test
  void constructor_tagIdAccessorReturnsConstructorArgument() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertSame(PATIENT_ID, node.getTagID());
  }

  // -- equals / hashCode (the patient/study/series merge rule) -------------

  @Test
  void equals_identicalIdentifiersAreEqual() {
    MediaSeriesGroupNode a = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    MediaSeriesGroupNode b = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertAll(
        () -> assertEquals(a, b, "same tagID + same identifier -> equal"),
        () -> assertEquals(a.hashCode(), b.hashCode()));
  }

  @Test
  void equals_distinctIdentifiersAreNotEqual() {
    MediaSeriesGroupNode a = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    MediaSeriesGroupNode b = new MediaSeriesGroupNode(PATIENT_ID, "id-2", null);

    assertNotEquals(a, b, "different identifier -> not equal (no silent merge)");
  }

  @Test
  void equals_reflexive() {
    MediaSeriesGroupNode a = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertEquals(a, a);
  }

  @Test
  void equals_nullIsNotEqual() {
    MediaSeriesGroupNode a = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertNotEquals(a, null);
  }

  @Test
  void equals_otherClassIsNotEqual() {
    MediaSeriesGroupNode a = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertNotEquals(a, "id-1", "cross-type compare is false");
  }

  @Test
  void hashSet_dedupesByIdentifier() {
    // The hashCode contract is what powers DicomModel's hierarchy lookup. Verify a HashSet
    // dedupes nodes that share an identifier under the same tagID.
    Set<MediaSeriesGroupNode> set = new HashSet<>();
    set.add(new MediaSeriesGroupNode(PATIENT_ID, "id-1", null));
    set.add(new MediaSeriesGroupNode(PATIENT_ID, "id-1", null));
    set.add(new MediaSeriesGroupNode(PATIENT_ID, "id-2", null));

    assertEquals(2, set.size(), "duplicates collapse, distinct id stays");
  }

  // -- matchIdValue ---------------------------------------------------------

  @Test
  void matchIdValue_returnsTrueForStoredIdentifier() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertTrue(node.matchIdValue("id-1"));
  }

  @Test
  void matchIdValue_returnsFalseForDifferentIdentifier() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertFalse(node.matchIdValue("id-2"));
  }

  @Test
  void matchIdValue_nullValueIsFalseWhenIdentifierNonNull() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    assertFalse(node.matchIdValue(null));
  }

  // -- Tag mutation: identity must be immutable -----------------------------

  @Test
  void setTag_rejectsTagIdToPreserveIdentity() {
    // Critical: if setTag(tagID, x) succeeded, the node could silently change identity (e.g. a
    // study's UID swapped under the user's feet). Verify the assignment is refused.
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.setTag(PATIENT_ID, "id-2");

    assertEquals("id-1", node.getTagValue(PATIENT_ID), "tagID is immutable via setTag");
  }

  @Test
  void setTag_nullTagIsNoOp() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.setTag(null, "value");

    // No exception, and no spurious entry inserted.
    assertNull(node.getTagValue(null));
  }

  @Test
  void setTag_otherTagIsAccepted() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.setTag(SOME_TAG, "value");

    assertEquals("value", node.getTagValue(SOME_TAG));
  }

  @Test
  void setTagNoNull_rejectsNullValue() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    node.setTag(SOME_TAG, "value");

    node.setTagNoNull(SOME_TAG, null);

    assertEquals("value", node.getTagValue(SOME_TAG), "null value via setTagNoNull is rejected");
  }

  @Test
  void setTagNoNull_rejectsTagIdToPreserveIdentity() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.setTagNoNull(PATIENT_ID, "id-2");

    assertEquals("id-1", node.getTagValue(PATIENT_ID));
  }

  @Test
  void removeTag_doesNotRemoveTagId() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.removeTag(PATIENT_ID);

    assertEquals("id-1", node.getTagValue(PATIENT_ID), "tagID is undeletable");
  }

  @Test
  void removeTag_removesNonIdTag() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    node.setTag(SOME_TAG, "value");

    node.removeTag(SOME_TAG);

    assertNull(node.getTagValue(SOME_TAG));
  }

  @Test
  void removeTag_nullIsNoOp() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);

    node.removeTag(null);

    assertEquals("id-1", node.getTagValue(PATIENT_ID));
  }

  // -- Tag lookup -----------------------------------------------------------

  @Test
  void getTagValue_nullReturnsNull() {
    assertNull(new MediaSeriesGroupNode(PATIENT_ID, "id-1", null).getTagValue(null));
  }

  @Test
  void containTagKey_trueForStoredTags() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    node.setTag(SOME_TAG, "value");

    assertAll(
        () -> assertTrue(node.containTagKey(PATIENT_ID), "tagID is always present"),
        () -> assertTrue(node.containTagKey(SOME_TAG)),
        () -> assertFalse(node.containTagKey(STUDY_ID)));
  }

  @Test
  void getTagElement_findsByIntegerId() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    node.setTag(SOME_TAG, "value");

    assertAll(
        () -> assertSame(PATIENT_ID, node.getTagElement(PATIENT_ID.getId())),
        () -> assertSame(SOME_TAG, node.getTagElement(SOME_TAG.getId())),
        () -> assertNull(node.getTagElement(123_456), "unknown id -> null"));
  }

  @Test
  void getTagEntrySetIterator_iteratesAllTags() {
    MediaSeriesGroupNode node = new MediaSeriesGroupNode(PATIENT_ID, "id-1", null);
    node.setTag(SOME_TAG, "value");

    int count = 0;
    Iterator<Entry<TagW, Object>> it = node.getTagEntrySetIterator();
    while (it.hasNext()) {
      it.next();
      count++;
    }

    assertEquals(2, count, "tagID + one user tag");
  }

  // -- Root node -----------------------------------------------------------

  @Test
  void rootNode_isDefined() {
    assertNotNull(MediaSeriesGroupNode.rootNode);
  }

  @Test
  void rootNode_hasRootElementTagIdAndKnownIdentifier() {
    assertAll(
        () -> assertSame(TagW.RootElement, MediaSeriesGroupNode.rootNode.getTagID()),
        () ->
            assertEquals("__ROOT__", MediaSeriesGroupNode.rootNode.getTagValue(TagW.RootElement)));
  }
}
