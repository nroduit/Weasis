/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.SeriesThumbnail.Badge;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;
import org.weasis.dicom.explorer.items.KoFixtures;

@DisplayNameGeneration(ReplaceUnderscores.class)
class InterestBadgeTest {

  @Test
  void badge_counts_the_images_and_names_the_sources() {
    Interest interest =
        new Interest(
            Map.of(
                InterestSource.KEY_OBJECT, Set.of("1.1", "1.2", "1.3"),
                InterestSource.GRAPHICS, Set.of("1.2", "1.9")));
    Badge badge = InterestBadge.of(interest, null);
    Badge derived = InterestBadge.of(interest, "SUB");
    assertAll(
        () -> assertEquals("★4", badge.text()),
        () -> assertEquals("4 images of interest (KO 3, Drawn graphics 2)", badge.tooltip()),
        () -> assertEquals("SUB ★4", derived.text()),
        () -> assertTrue(derived.tooltip().startsWith("Series derived by Weasis (SUB)")));
  }

  @Test
  void a_derived_series_shows_its_label_without_interest() {
    Badge badge = InterestBadge.of(Interest.NONE, "MIP");
    assertAll(
        () -> assertEquals("MIP", badge.text()),
        () -> assertEquals("Series derived by Weasis (MIP)", badge.tooltip()));
  }

  @Test
  void no_badge_without_interest() {
    assertAll(
        () -> assertNull(InterestBadge.of(Interest.NONE, null)),
        () -> assertNull(InterestBadge.of(null, "")));
  }

  @Test
  void supplier_derives_the_badge_from_the_key_objects_of_the_series() throws Exception {
    String studyUID = UIDUtils.createUID();
    String ctUID = UIDUtils.createUID();
    DicomSeries ct = new DicomSeries(ctUID);
    ct.setTag(TagD.get(Tag.SeriesInstanceUID), ctUID);
    Attributes attrs = KoFixtures.koAttributes(studyUID, ctUID, 2);
    KOSpecialElement ko = new KOSpecialElement(new DicomMediaIO(attrs));
    for (int tag :
        new int[] {Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.StudyInstanceUID, Tag.Modality}) {
      ko.setTag(TagD.get(tag), attrs.getString(tag));
    }
    String koSeries = attrs.getString(Tag.SeriesInstanceUID);
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    manager.series2Elements.computeIfAbsent(koSeries, _ -> new CopyOnWriteArraySet<>()).add(ko);
    manager.reference2Series.computeIfAbsent(ctUID, _ -> new CopyOnWriteArraySet<>()).add(koSeries);

    InterestBadge supplier = new InterestBadge(ct);
    Badge first = supplier.get();
    assertAll(
        () -> assertEquals("\u26052", first.text()),
        () -> assertEquals(first, supplier.get()),
        () -> assertNull(new InterestBadge(new DicomSeries(UIDUtils.createUID())).get()));
  }
}
