/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions.Cell;
import org.weasis.dicom.codec.hp.WeasisExtensions.Display;
import org.weasis.dicom.codec.hp.WeasisExtensions.DisplaySet;
import org.weasis.dicom.codec.hp.WeasisExtensions.Loading;
import org.weasis.dicom.codec.hp.WeasisExtensions.Mpr;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;
import org.weasis.dicom.hp.HPComparatorFactory;
import org.weasis.dicom.hp.HPDefinition;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageBox;
import org.weasis.dicom.hp.HPImageSet;
import org.weasis.dicom.hp.HPNavigationGroup;
import org.weasis.dicom.hp.HPScreenDefinition;
import org.weasis.dicom.hp.HPScrollingGroup;
import org.weasis.dicom.hp.HPSelectorFactory;
import org.weasis.dicom.hp.HPTimeBasedImageSet;
import org.weasis.dicom.hp.HangingProtocol;
import org.weasis.dicom.hp.RelativeTime;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.hp.enums.RelativeTimeUnits;
import org.weasis.dicom.hp.enums.SortingDirection;
import org.weasis.dicom.hp.filter.FilterOp;
import org.weasis.dicom.macro.Code;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ProtocolJsonTest {

  private static HangingProtocol chestCt() {
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName("CT chest"); // NON-NLS
    hp.setHangingProtocolDescription("Axial soft and lung, prior beside"); // NON-NLS
    hp.setHangingProtocolLevel("SITE"); // NON-NLS
    hp.setHangingProtocolCreator("Weasis"); // NON-NLS
    hp.setNumberOfScreens(2);
    hp.setNumberOfPriorsReferenced(1);
    hp.setPartialDataDisplayHandling("ADAPT_LAYOUT"); // NON-NLS

    HPDefinition def = new HPDefinition();
    def.setModality("CT"); // NON-NLS
    def.setLaterality("U"); // NON-NLS
    Code chest = new Code();
    chest.setCodeValue("51185008"); // NON-NLS
    chest.setCodingSchemeDesignator("SCT"); // NON-NLS
    chest.setCodeMeaning("Thoracic structure"); // NON-NLS
    def.addAnatomicRegionCodes(chest);
    hp.addHangingProtocolDefinition(def);

    HPScreenDefinition screen = new HPScreenDefinition();
    screen.setNumberOfHorizontalPixels(2560);
    screen.setNumberOfVerticalPixels(1440);
    screen.setDisplayEnvironmentSpatialPosition(new double[] {0, 1, 1, 0});
    hp.addNominalScreenDefinition(screen);

    HPImageSet current = hp.addNewImageSet(null);
    current.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    // the model reads only attribute selectors back from a dataset (no Filter-by Category), so
    // the round trip is checked on those
    current.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "NO_MATCH", null, Tag.ImageType, 3, VR.CS, new String[] {"LOCALIZER"})); // NON-NLS
    HPTimeBasedImageSet now = new HPTimeBasedImageSet();
    now.setImageSetLabel("Current"); // NON-NLS
    now.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
    current.addTimeBasedImageSet(now);
    // the IOD numbers image sets by their time-based items, one per set (C.23.1.2); a set that
    // shared the selectors of another would copy its time-based items too, so the prior gets its
    // own selector
    HPImageSet prior = hp.addNewImageSet(null);
    prior.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    HPTimeBasedImageSet before = new HPTimeBasedImageSet();
    before.setImageSetLabel("Prior"); // NON-NLS
    before.setRelativeTime(new RelativeTime(1, 2, RelativeTimeUnits.YEARS));
    prior.addTimeBasedImageSet(before);

    HPDisplaySet soft = new HPDisplaySet();
    soft.setImageSet(current);
    soft.setDisplaySetPresentationGroup(1);
    soft.setDisplaySetLabel("Soft tissue"); // NON-NLS
    HPImageBox box = new HPImageBox();
    box.setImageBoxLayoutType(ImageBoxLayoutType.TILED);
    box.setImageBoxTileHorizontalDimension(2);
    box.setImageBoxTileVerticalDimension(2);
    box.setDisplayEnvironmentSpatialPosition(new double[] {0, 1, 0.5, 0});
    soft.addImageBox(box);
    soft.addFilterOperation(
        HPSelectorFactory.createAttributeValueSelector(
            null,
            Tag.ImageType,
            1,
            VR.CS,
            new String[] {"ORIGINAL"},
            FilterOp.MEMBER_OF)); // NON-NLS
    soft.addSortingOperation(HPComparatorFactory.createSortAlongAxis(SortingDirection.INCREASING));
    soft.setVOIType("SOFT_TISSUE"); // NON-NLS
    hp.addDisplaySet(soft);

    HPDisplaySet lung = new HPDisplaySet();
    lung.setImageSet(current);
    lung.setDisplaySetPresentationGroup(1);
    lung.addImageBox(new HPImageBox());
    lung.setReformattingOperationType("MPR"); // NON-NLS
    lung.setReformattingThickness(5);
    hp.addDisplaySet(lung);

    HPDisplaySet priorSet = new HPDisplaySet();
    priorSet.setImageSet(prior);
    priorSet.setDisplaySetPresentationGroup(2);
    priorSet.addImageBox(new HPImageBox());
    hp.addDisplaySet(priorSet);

    HPScrollingGroup scrolling = new HPScrollingGroup();
    scrolling.addDisplaySet(soft);
    scrolling.addDisplaySet(lung);
    scrolling.updateAttributes();
    hp.addScrollingGroup(scrolling);
    HPNavigationGroup navigation = new HPNavigationGroup();
    navigation.setNavigationDisplaySet(soft);
    navigation.addReferenceDisplaySet(priorSet);
    navigation.updateAttributes();
    hp.addNavigationGroup(navigation);
    return hp;
  }

  private static WeasisExtensions extensions() {
    return new WeasisExtensions(
        Map.of(
            1,
            new WeasisExtensions.ImageSet(
                "Axial soft tissue", // NON-NLS
                List.of(
                    FeatureSelector.match(FeatureSelector.Key.ORIENTATION, "AXIAL"), // NON-NLS
                    FeatureSelector.noMatch(FeatureSelector.Key.ROLE, "LOCALIZER"), // NON-NLS
                    FeatureSelector.prefer(
                        FeatureSelector.Key.CT_KERNEL_FAMILY, 2, "SOFT"), // NON-NLS
                    FeatureSelector.prefer(
                        FeatureSelector.Key.SLICE_THICKNESS, 1, FeatureSelector.Extreme.MIN)))),
        Map.of(
            1,
            new DisplaySet(
                ViewerType.VIEWER_2D,
                new Display(
                    "weasis.ct.mediastinum", // NON-NLS
                    null,
                    "LINEAR", // NON-NLS
                    null,
                    null,
                    null,
                    "ct-chest", // NON-NLS
                    "BEST_FIT", // NON-NLS
                    "stack", // NON-NLS
                    null,
                    null,
                    WeasisExtensions.Start.MIDDLE),
                List.of(new Cell("INFO", 1)), // NON-NLS
                null),
            2,
            new DisplaySet(
                ViewerType.MPR,
                new Display(
                    "weasis.ct.lung", // NON-NLS
                    null,
                    null,
                    null,
                    null,
                    new Mpr(List.of("AXIAL", "CORONAL"), 5.0, "MIP"), // NON-NLS
                    null,
                    null,
                    null),
                List.of(),
                new Loading(null, null, 1, false))),
        Map.of(1, "ui.ct.reading", 2, "ui.ct.compare"), // NON-NLS
        Json.createObjectBuilder().add("windows", Json.createArrayBuilder()).build(), // NON-NLS
        "TEACHING", // NON-NLS
        new Loading("PROTOCOL_THEN_REST", "STEPS", null, null), // NON-NLS
        "ui.ct", // NON-NLS
        Json.createObjectBuilder()
            .add("future", Json.createObjectBuilder().add("x", 1))
            .build()); // NON-NLS
  }

  @Test
  void a_protocol_survives_the_round_trip_with_its_sets_boxes_filters_and_groups() {
    HangingProtocol hp = chestCt();
    WeasisExtensions weasis = extensions();

    JsonObject json = ProtocolJson.toJson("ct-chest", hp, weasis); // NON-NLS
    Document back = ProtocolJson.fromJson(JsonUtil.readObject(json.toString()));
    HangingProtocol read = back.protocol();

    assertAll(
        () -> assertEquals("ct-chest", back.id()), // NON-NLS
        () -> assertEquals(ProtocolJson.SCHEMA, json.getInt(ProtocolJson.SCHEMA_KEY)),
        () ->
            assertEquals(
                "CT chest",
                json.getJsonObject("dicom").getString("HangingProtocolName")), // NON-NLS
        () -> assertEquals("CT chest", read.getHangingProtocolName()), // NON-NLS
        () -> assertEquals("SITE", read.getHangingProtocolLevel()), // NON-NLS
        () -> assertEquals(2, read.getNumberOfScreens()),
        () -> assertEquals(1, read.getNumberOfPriorsReferenced()),
        () -> assertEquals("ADAPT_LAYOUT", read.getPartialDataDisplayHandling()), // NON-NLS
        () -> assertEquals(1, read.getHangingProtocolDefinitions().size()),
        () ->
            assertEquals(
                "CT", read.getHangingProtocolDefinitions().getFirst().getModality()), // NON-NLS
        () ->
            assertEquals(
                "51185008", // NON-NLS
                read.getHangingProtocolDefinitions()
                    .getFirst()
                    .getAnatomicRegionCode()
                    .iterator()
                    .next()
                    .getCodeValue()),
        () -> assertEquals(1, read.getNominalScreenDefinitions().size()),
        () ->
            assertEquals(
                2560, read.getNominalScreenDefinitions().getFirst().getNumberOfHorizontalPixels()),
        () ->
            assertArrayEquals(
                new double[] {0, 1, 1, 0},
                read.getNominalScreenDefinitions()
                    .getFirst()
                    .getDisplayEnvironmentSpatialPosition(),
                1e-9),
        () -> assertEquals(2, read.getImageSets().size()),
        () -> assertEquals(2, read.getImageSets().getFirst().getImageSetSelectors().size()),
        () ->
            assertEquals(
                "MATCH",
                read.getImageSets()
                    .getFirst()
                    .getImageSetSelectors()
                    .getFirst()
                    .getImageSetSelectorUsageFlag()), // NON-NLS
        () ->
            assertEquals(
                "NO_MATCH",
                read.getImageSets()
                    .getFirst()
                    .getImageSetSelectors()
                    .get(1)
                    .getImageSetSelectorUsageFlag()), // NON-NLS
        () ->
            assertEquals(
                3,
                read.getImageSets()
                    .getFirst()
                    .getImageSetSelectors()
                    .get(1)
                    .getSelectorValueNumber()),
        () -> assertEquals(1, read.getImageSets().get(1).getImageSetSelectors().size()),
        () ->
            assertEquals(
                "Prior",
                read.getImageSets().get(1).getAttributes().getString(Tag.ImageSetLabel),
                "an image set read from a dataset is its time-based item"), // NON-NLS
        () -> assertEquals(3, read.getDisplaySets().size()),
        () ->
            assertEquals(
                ImageBoxLayoutType.TILED,
                read.getDisplaySets()
                    .getFirst()
                    .getImageBoxes()
                    .getFirst()
                    .getImageBoxLayoutType()),
        () ->
            assertEquals(
                2,
                read.getDisplaySets()
                    .getFirst()
                    .getImageBoxes()
                    .getFirst()
                    .getImageBoxTileHorizontalDimension()),
        () -> assertEquals(1, read.getDisplaySets().getFirst().getFilterOperations().size()),
        () -> assertEquals(1, read.getDisplaySets().getFirst().getSortingOperations().size()),
        () -> assertEquals("SOFT_TISSUE", read.getDisplaySets().getFirst().getVOIType()), // NON-NLS
        () ->
            assertEquals(
                "MPR", read.getDisplaySets().get(1).getReformattingOperationType()), // NON-NLS
        () -> assertEquals(5.0, read.getDisplaySets().get(1).getReformattingThickness(), 1e-9),
        () -> assertEquals(2, read.getDisplaySets().get(2).getDisplaySetPresentationGroup()),
        () -> assertEquals(2, read.getNumberOfPresentationGroups()),
        () -> assertEquals(2, read.getDisplaySetsOfPresentationGroup(1).size()),
        () -> assertEquals(1, read.getScrollingGroups().size()),
        () -> assertEquals(2, read.getScrollingGroups().getFirst().getDisplaySets().size()),
        () -> assertEquals(1, read.getNavigationGroups().size()),
        () ->
            assertEquals(1, read.getNavigationGroups().getFirst().getReferenceDisplaySets().size()),
        () -> assertEquals(weasis, back.weasis()),
        () ->
            assertEquals(
                json, ProtocolJson.toJson(back), "writing it again gives the same document"));
  }

  @Test
  void the_extensions_keep_what_this_version_does_not_know() {
    JsonObject weasis =
        Json.createObjectBuilder()
            .add("uiProfile", "ui.ct") // NON-NLS
            .add(
                "displaySets",
                Json.createObjectBuilder()
                    .add("1", Json.createObjectBuilder().add("viewer", "MPR"))) // NON-NLS
            .add("priors", Json.createObjectBuilder().add("max", 3)) // NON-NLS
            .add("note", "from a newer Weasis") // NON-NLS
            .build();
    WeasisExtensions read = WeasisExtensions.fromJson(weasis);
    JsonObject written = read.toJson();
    assertAll(
        () -> assertEquals("ui.ct", read.uiProfile()), // NON-NLS
        () -> assertEquals(ViewerType.MPR, read.displaySets().get(1).viewerType()),
        () -> assertEquals(3, written.getJsonObject("priors").getInt("max")), // NON-NLS
        () -> assertEquals("from a newer Weasis", written.getString("note")), // NON-NLS
        () -> assertEquals(weasis, written),
        () -> assertEquals(WeasisExtensions.NONE, WeasisExtensions.fromJson(null)),
        () -> assertTrue(WeasisExtensions.NONE.toJson().isEmpty()));
  }

  @Test
  void a_newer_schema_or_a_missing_part_is_refused() {
    JsonObject newer =
        Json.createObjectBuilder()
            .add("id", "x") // NON-NLS
            .add(ProtocolJson.SCHEMA_KEY, ProtocolJson.SCHEMA + 1)
            .add(ProtocolJson.DICOM, JsonValue.EMPTY_JSON_OBJECT)
            .build();
    JsonObject noId =
        Json.createObjectBuilder().add(ProtocolJson.DICOM, JsonValue.EMPTY_JSON_OBJECT).build();
    JsonObject noDicom = Json.createObjectBuilder().add("id", "x").build(); // NON-NLS
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> ProtocolJson.fromJson(newer)),
        () -> assertThrows(IllegalArgumentException.class, () -> ProtocolJson.fromJson(noId)),
        () -> assertThrows(IllegalArgumentException.class, () -> ProtocolJson.fromJson(noDicom)));
  }

  @Test
  void a_protocol_without_extensions_has_no_weasis_block() {
    JsonObject json = ProtocolJson.toJson("plain", new HangingProtocol(), null); // NON-NLS
    assertFalse(json.containsKey(ProtocolJson.WEASIS));
    assertEquals(WeasisExtensions.NONE, ProtocolJson.fromJson(json).weasis());
  }

  @Test
  void the_dicom_json_keeps_types_multiplicity_empties_and_private_elements() {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.HangingProtocolName, VR.SH, "Name"); // NON-NLS
    dcm.setInt(Tag.NumberOfScreens, VR.US, 2);
    dcm.setDouble(Tag.ReformattingThickness, VR.FD, 2.5);
    dcm.setDouble(Tag.DisplayEnvironmentSpatialPosition, VR.FD, 0, 1, 1, 0);
    dcm.setInt(Tag.SelectorAttribute, VR.AT, Tag.Modality);
    dcm.setNull(Tag.HangingProtocolDescription, VR.LO);
    dcm.setString("WEASIS", 0x00990010, VR.LO, "note", "two"); // NON-NLS
    Attributes item = new Attributes();
    item.setString(Tag.Modality, VR.CS, "CT"); // NON-NLS
    dcm.newSequence(Tag.HangingProtocolDefinitionSequence, 1).add(item);

    JsonObject json = DicomJson.toJson(dcm);
    Attributes back = DicomJson.toAttributes(JsonUtil.readObject(json.toString()));

    assertAll(
        () -> assertEquals("Name", json.getString("HangingProtocolName")), // NON-NLS
        () -> assertEquals(2, json.getInt("NumberOfScreens")), // NON-NLS
        () ->
            assertEquals(
                2.5, json.getJsonNumber("ReformattingThickness").doubleValue(), 1e-9), // NON-NLS
        () ->
            assertEquals(
                4, json.getJsonArray("DisplayEnvironmentSpatialPosition").size()), // NON-NLS
        () -> assertEquals("00080060", json.getString("SelectorAttribute")), // NON-NLS
        () -> assertTrue(json.isNull("HangingProtocolDescription")), // NON-NLS
        () ->
            assertEquals(
                "CT",
                json.getJsonArray("HangingProtocolDefinitionSequence")
                    .getJsonObject(0)
                    .getString("Modality")), // NON-NLS
        () -> assertEquals("LO", json.getJsonObject("00991010").getString("vr")), // NON-NLS
        () -> assertEquals("Name", back.getString(Tag.HangingProtocolName)), // NON-NLS
        () -> assertEquals(2, back.getInt(Tag.NumberOfScreens, 0)),
        () ->
            assertArrayEquals(
                new double[] {0, 1, 1, 0},
                back.getDoubles(Tag.DisplayEnvironmentSpatialPosition),
                1e-9),
        () -> assertEquals(Tag.Modality, back.getInt(Tag.SelectorAttribute, 0)),
        () -> assertTrue(back.contains(Tag.HangingProtocolDescription)),
        () -> assertNull(back.getString(Tag.HangingProtocolDescription)),
        () ->
            assertArrayEquals(
                new String[] {"note", "two"}, back.getStrings("WEASIS", 0x00990010)), // NON-NLS
        () ->
            assertEquals(
                "CT",
                back.getNestedDataset(Tag.HangingProtocolDefinitionSequence)
                    .getString(Tag.Modality)), // NON-NLS
        () -> assertEquals(json, DicomJson.toJson(back)));
  }
}
