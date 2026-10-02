/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition.Type;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;

/**
 * The rules splitting a series into sub-series, by modality. The built-in defaults of the code are
 * replaced, modality by modality, by the site document {@value #SITE_FILE} of the resources package
 * (see {@link SplittingRulesJson}), or until 5.2 by the legacy {@code series-splitting-rules.xml}
 * of the package root when the site ships no JSON.
 */
public class SplittingRules {
  private static final Logger LOGGER = LoggerFactory.getLogger(SplittingRules.class);

  /** The site document, in the {@code config} folder of the resources package. */
  public static final String SITE_FILE = "seriesSplittingRules.json"; // NON-NLS

  /** The name of the array of entries in the site document. */
  public static final String ENTRIES = SplittingRulesJson.ENTRIES;

  private final Map<Modality, SplittingModalityRules> rules;

  /** The rules of the resources package of the running Weasis. */
  public SplittingRules() {
    this(
        SiteDocuments.find(SITE_FILE).orElse(null),
        ResourceUtil.getResource(DicomResource.SERIES_SPITTING_RULES).toPath());
  }

  /**
   * The rules of another resources package: {@code config/seriesSplittingRules.json} under {@code
   * root} when it exists, else {@code series-splitting-rules.xml} in {@code root}.
   */
  SplittingRules(Path root) {
    this(
        root.resolve(SiteDocuments.FOLDER).resolve(SITE_FILE),
        root.resolve(DicomResource.SERIES_SPITTING_RULES.getPath()));
  }

  private SplittingRules(Path json, Path xml) {
    List<SplittingModalityRules> builtIn = builtIn();
    SplittingRulesJson.Document site = loadSite(json, xml);
    LayeredEntries.Merged<SplittingModalityRules> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, builtIn, Set.of()),
                Layer.of(Origin.SITE, site.entries(), site.locked())),
            r -> r.getModality().name(),
            SplittingRulesJson.WHAT);
    rules = new EnumMap<>(Modality.class);
    merged.entries().forEach(r -> rules.put(r.getModality(), r));
  }

  /** The defaults of the code: a fresh copy, the rules being mutable. */
  public static List<SplittingModalityRules> builtIn() {
    SplittingModalityRules defRules = new SplittingModalityRules(Modality.DEFAULT);

    defRules.addSingleFrameTags(Tag.ImageType, null);
    defRules.addSingleFrameTags(Tag.SOPClassUID, null);
    defRules.addSingleFrameTags(Tag.ContrastBolusAgent, null);
    defRules.addMultiFrameTags(Tag.ImageType, null);
    defRules.addMultiFrameTags(Tag.SOPInstanceUID, null);
    defRules.addMultiFrameTags(Tag.FrameType, null);
    defRules.addMultiFrameTags(Tag.StackID, null);

    SplittingModalityRules ctRules = new SplittingModalityRules(Modality.CT, defRules);
    ctRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
    ctRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);
    // Make a condition to exclude projection image type
    And allOf = new And();
    allOf.addChild(
        new DefaultCondition(
            TagD.get(Tag.ImageType), Condition.Type.notContainsIgnoreCase, "PROJECTION"));
    ctRules.addSingleFrameTags(TagW.ImageOrientationPlane, allOf);

    SplittingModalityRules ptRules = new SplittingModalityRules(Modality.PT, defRules);
    ptRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
    ptRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);

    SplittingModalityRules mrRules = new SplittingModalityRules(Modality.MR, defRules);
    mrRules.addSingleFrameTags(Tag.ScanningSequence, null);
    mrRules.addSingleFrameTags(Tag.SequenceVariant, null);
    mrRules.addSingleFrameTags(Tag.ScanOptions, null);
    mrRules.addSingleFrameTags(Tag.RepetitionTime, null);
    mrRules.addSingleFrameTags(Tag.EchoTime, null);
    mrRules.addSingleFrameTags(Tag.FlipAngle, null);
    // Reuse the condition for ImageOrientationPlane
    mrRules.addSingleFrameTags(TagW.ImageOrientationPlane, allOf);

    return List.of(defRules, ctRules, ptRules, mrRules);
  }

  /** The built-in defaults of the code, as the base of an {@code extends} in a site document. */
  public static Function<String, Optional<JsonObject>> builtInBases() {
    List<SplittingModalityRules> builtIn = builtIn();
    return id ->
        builtIn.stream()
            .filter(r -> r.getModality().name().equals(id))
            .findFirst()
            .map(SplittingRulesJson::toJson);
  }

  // The JSON site document first, else the legacy XML, else nothing: the built-in only
  private static SplittingRulesJson.Document loadSite(Path json, Path xml) {
    if (json != null && Files.isRegularFile(json)) {
      try {
        SplittingRulesJson.Document site = SplittingRulesJson.read(json, builtInBases());
        LOGGER.debug("Splitting rules read from the site document {}", json);
        return site;
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read the site splitting rules: {}", json, e);
        return SplittingRulesJson.Document.EMPTY;
      }
    }
    if (xml != null && Files.isRegularFile(xml)) {
      try {
        SplittingRulesJson.Document site =
            SplittingRulesJson.parse(SplittingRulesJson.convert(xml), builtInBases());
        LOGGER.debug("Splitting rules read from the legacy document {}", xml);
        return site;
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read series-splitting-rules.xml: {}", xml, e);
        return SplittingRulesJson.Document.EMPTY;
      }
    }
    LOGGER.debug("Splitting rules: no site document, the built-in defaults only");
    return SplittingRulesJson.Document.EMPTY;
  }

  public SplittingModalityRules getSplittingModalityRules(Modality key, Modality defaultKey) {
    SplittingModalityRules val = rules.get(key);
    if (val == null) {
      val = rules.get(defaultKey);
    }
    return val;
  }

  static Type getConditionType(String type) {
    try {
      return Condition.Type.valueOf(type);
    } catch (Exception e) {
      LOGGER.error("{} is not a valid condition type", type, e);
    }
    return null;
  }

  static Modality getModality(String name) {
    try {
      return Modality.valueOf(name);
    } catch (Exception e) {
      LOGGER.error("Modality reference of {} is missing", name, e);
    }
    return null;
  }
}
