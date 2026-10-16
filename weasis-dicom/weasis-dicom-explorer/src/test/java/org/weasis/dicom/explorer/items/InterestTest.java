/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.main.SeriesFilter;

@DisplayNameGeneration(ReplaceUnderscores.class)
class InterestTest {

  @Test
  void a_key_object_marks_the_images_it_references() throws URISyntaxException {
    String seriesUID = UIDUtils.createUID();
    DicomSeries series = series(seriesUID);
    Attributes attrs = KoFixtures.koAttributes(UIDUtils.createUID(), seriesUID, 2);
    KOSpecialElement ko = new KOSpecialElement(new DicomMediaIO(attrs));
    for (int tag : new int[] {Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.Modality}) {
      ko.setTag(TagD.get(tag), attrs.getString(tag));
    }
    String koSeries = attrs.getString(Tag.SeriesInstanceUID);
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    manager.series2Elements.computeIfAbsent(koSeries, _ -> new CopyOnWriteArraySet<>()).add(ko);
    manager
        .reference2Series
        .computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>())
        .add(koSeries);

    Interest interest = Interest.of(series);
    assertAll(
        () -> assertFalse(interest.isEmpty()),
        () -> assertEquals(Set.of(InterestSource.KEY_OBJECT), interest.sources()),
        () -> assertEquals(2, interest.count()),
        () -> assertEquals(2, interest.count(InterestSource.KEY_OBJECT)),
        () -> assertEquals(0, interest.count(InterestSource.GRAPHICS)));

    SeriesFilter filter = new SeriesFilter();
    filter.setInterest(true, List.of());
    assertTrue(filter.test(series, null));
    filter.setInterest(false, List.of(InterestSource.SEGMENTATION));
    assertFalse(filter.test(series, null), "a KO is not a SEG");
    filter.setInterest(false, List.of(InterestSource.KEY_OBJECT));
    assertTrue(filter.test(series, null));
    assertFalse(filter.test(series(UIDUtils.createUID()), null), "another series: nothing");
  }

  @Test
  void drawn_graphics_mark_their_image_but_dicom_layers_do_not() {
    DicomImageElement drawn = image(graphicModel(LayerType.MEASURE));
    DicomImageElement prLayer = image(graphicModel(LayerType.DICOM_PR));
    DicomImageElement bare = image(null);
    String uid = UIDUtils.createUID();
    DicomSeries series =
        new DicomSeries(uid, new java.util.ArrayList<>(List.of(drawn, prLayer, bare)), null);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);

    Interest interest = Interest.of(series);
    assertAll(
        () -> assertEquals(Set.of(InterestSource.GRAPHICS), interest.sources()),
        () -> assertEquals(1, interest.count()),
        () ->
            assertEquals(
                Set.of(TagD.getTagValue(drawn, Tag.SOPInstanceUID, String.class)),
                interest.images()));
    assertTrue(Interest.of(series(UIDUtils.createUID())).isEmpty());
    assertTrue(Interest.of(null).isEmpty());

    var filter = org.weasis.dicom.explorer.main.InterestOpener.filterOf(interest);
    assertAll(
        () -> assertTrue(filter.passes(drawn)),
        () -> assertFalse(filter.passes(prLayer)),
        () -> assertFalse(filter.passes(null)),
        () -> assertEquals(1, series.size(filter)));
    assertEquals(null, org.weasis.dicom.explorer.main.InterestOpener.filterOf(Interest.NONE));
  }

  private static DicomSeries series(String uid) {
    DicomSeries series = new DicomSeries(uid);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    series.setTag(TagD.get(Tag.Modality), "CT");
    return series;
  }

  private static DicomImageElement image(GraphicModel model) {
    DicomImageElement image = mock(DicomImageElement.class);
    String uid = UIDUtils.createUID();
    when(image.getTagValue(TagD.get(Tag.SOPInstanceUID))).thenReturn(uid);
    when(image.getTagValue(TagW.PresentationModel)).thenReturn(model);
    return image;
  }

  private static GraphicModel graphicModel(LayerType type) {
    Graphic graphic = mock(Graphic.class);
    when(graphic.getLayerType()).thenReturn(type);
    GraphicModel model = mock(GraphicModel.class);
    when(model.getAllGraphics()).thenReturn(List.of(graphic));
    return model;
  }
}
