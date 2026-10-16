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

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredDocument.Entry;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.MrWeighting;

/**
 * The tables that teach the {@link SeriesFeatureExtractor} vendor naming without code: the CT
 * kernel families and the MR weighting rules. Each table is a {@link LayeredDocument}: the built-in
 * resource, the site file of the same name in {@code config/} and the user document, merged by
 * {@code id}, so a site adds a vendor or a rule by shipping one entry.
 *
 * <p>{@value #KERNELS} holds {@code families}: {@code {"id": "SOFT" | "LUNG" | "BONE", "vendors":
 * {"<manufacturer word>": ["<kernel>", ...]}, "patterns": ["<regex on the upper-cased kernel>",
 * ...]}}. The manufacturer word is matched case-insensitively inside Manufacturer (0008,0070); the
 * vendor lists are tried before the patterns; the first family that matches wins, in document
 * order.
 *
 * <p>{@value #MR_WEIGHTING} holds {@code rules}: {@code {"id": "...", "weighting": "<MrWeighting
 * name>", "priority": <int>, "when": {...}}}. Every condition present in {@code when} must hold:
 * {@code scanningSequence}, {@code sequenceVariant} and {@code imageType} (any of the listed values
 * is present in the attribute), {@code tokens} (any of the listed tokens is in the description
 * tokens), {@code trMin}, {@code trMax}, {@code teMin}, {@code teMax}, {@code tiMin}, {@code tiMax}
 * in ms and {@code bValueMin} in s/mm² (the attribute is present and in range). Rules are tried by
 * decreasing priority; the first that holds gives the weighting.
 */
public final class DerivationTables {
  private static final Logger LOGGER = LoggerFactory.getLogger(DerivationTables.class);

  public static final String KERNELS = "hp-kernel-families.json"; // NON-NLS
  public static final String MR_WEIGHTING = "hp-mr-weighting.json"; // NON-NLS
  static final String FAMILIES = "families"; // NON-NLS
  static final String RULES = "rules"; // NON-NLS

  private static volatile DerivationTables instance;

  private final LayeredDocument kernels = new LayeredDocument(KERNELS, FAMILIES);
  private final LayeredDocument weighting = new LayeredDocument(MR_WEIGHTING, RULES);
  private volatile List<Family> families = List.of();
  private volatile List<Rule> rules = List.of();

  /** The MR attributes a weighting rule looks at; a null number means the attribute is absent. */
  public record MrSample(
      Collection<String> scanningSequence,
      Collection<String> sequenceVariant,
      Collection<String> imageType,
      Collection<String> tokens,
      Double repetitionTime,
      Double echoTime,
      Double inversionTime,
      Double bValue) {}

  private record Family(
      CtKernel.Family family, Map<String, List<String>> vendors, List<Pattern> patterns) {}

  private record Rule(MrWeighting weighting, int priority, JsonObject when) {}

  /** The tables with the built-in resources only; {@link #configure} adds the site and the user. */
  public DerivationTables() {
    kernels.setBuiltIn(builtIn(KERNELS, FAMILIES));
    weighting.setBuiltIn(builtIn(MR_WEIGHTING, RULES));
    kernels.addListener(this::reindexKernels);
    weighting.addListener(this::reindexWeighting);
    reindexKernels();
    reindexWeighting();
  }

  public static DerivationTables getInstance() {
    DerivationTables tables = instance;
    if (tables == null) {
      synchronized (DerivationTables.class) {
        tables = instance;
        if (tables == null) {
          tables = new DerivationTables();
          instance = tables;
        }
      }
    }
    return tables;
  }

  /** Replaces the shared instance; tests use it to inject tables of their own. */
  public static void useInstance(DerivationTables tables) {
    instance = tables;
  }

  /** Reads the site files from {@code config/} and the user documents of the document store. */
  public void configure() {
    kernels.configure(SiteDocuments.read(KERNELS), DocumentStore.locate(Scope.USER, KERNELS));
    weighting.configure(
        SiteDocuments.read(MR_WEIGHTING), DocumentStore.locate(Scope.USER, MR_WEIGHTING));
  }

  /**
   * Reads the layers from explicit places, for tests and tools: a {@code config} folder holding the
   * site files, and the user documents; a null path means no such layer.
   */
  public void configure(Path configFolder, Path kernelUser, Path weightingUser) {
    kernels.configure(site(configFolder, KERNELS), kernelUser);
    weighting.configure(site(configFolder, MR_WEIGHTING), weightingUser);
  }

  private static Optional<JsonObject> site(Path configFolder, String name) {
    return configFolder == null ? Optional.empty() : SiteDocuments.read(configFolder, name);
  }

  /** Re-reads the site files, after {@code config:reload}. */
  public void reload() {
    kernels.reload(SiteDocuments.read(KERNELS));
    weighting.reload(SiteDocuments.read(MR_WEIGHTING));
  }

  public LayeredDocument kernelFamilies() {
    return kernels;
  }

  public LayeredDocument mrWeightingRules() {
    return weighting;
  }

  /**
   * The family of a reconstruction kernel.
   *
   * @param manufacturer Manufacturer (0008,0070), may be null
   * @param kernel Convolution Kernel (0018,1210), may be null
   */
  public CtKernel.Family kernelFamily(String manufacturer, String kernel) {
    if (kernel == null || kernel.isBlank()) {
      return CtKernel.Family.UNKNOWN;
    }
    String upper = kernel.trim().toUpperCase(Locale.ROOT);
    String vendor = manufacturer == null ? "" : manufacturer.toUpperCase(Locale.ROOT);
    for (Family family : families) {
      for (Map.Entry<String, List<String>> e : family.vendors().entrySet()) {
        if (vendor.contains(e.getKey()) && e.getValue().contains(upper)) {
          return family.family();
        }
      }
    }
    for (Family family : families) {
      for (Pattern pattern : family.patterns()) {
        if (pattern.matcher(upper).find()) {
          return family.family();
        }
      }
    }
    return CtKernel.Family.UNKNOWN;
  }

  /** The weighting of an MR series, by the first rule that holds. */
  public MrWeighting mrWeighting(MrSample sample) {
    for (Rule rule : rules) {
      if (holds(rule.when(), sample)) {
        return rule.weighting();
      }
    }
    return MrWeighting.UNKNOWN;
  }

  private static boolean holds(JsonObject when, MrSample sample) {
    return anyOf(when, "scanningSequence", sample.scanningSequence()) // NON-NLS
        && anyOf(when, "sequenceVariant", sample.sequenceVariant()) // NON-NLS
        && anyOf(when, "imageType", sample.imageType()) // NON-NLS
        && anyOf(when, "tokens", sample.tokens()) // NON-NLS
        && min(when, "trMin", sample.repetitionTime()) // NON-NLS
        && max(when, "trMax", sample.repetitionTime()) // NON-NLS
        && min(when, "teMin", sample.echoTime()) // NON-NLS
        && max(when, "teMax", sample.echoTime()) // NON-NLS
        && min(when, "tiMin", sample.inversionTime()) // NON-NLS
        && max(when, "tiMax", sample.inversionTime()) // NON-NLS
        && min(when, "bValueMin", sample.bValue()); // NON-NLS
  }

  private static boolean anyOf(JsonObject when, String key, Collection<String> values) {
    if (!(when.get(key) instanceof JsonArray wanted)) {
      return true;
    }
    if (values == null) {
      return false;
    }
    return wanted.getValuesAs(JsonString.class).stream()
        .map(JsonString::getString)
        .anyMatch(w -> values.stream().anyMatch(w::equalsIgnoreCase));
  }

  private static boolean min(JsonObject when, String key, Double value) {
    return !(when.get(key) instanceof JsonNumber bound)
        || (value != null && value >= bound.doubleValue());
  }

  private static boolean max(JsonObject when, String key, Double value) {
    return !(when.get(key) instanceof JsonNumber bound)
        || (value != null && value <= bound.doubleValue());
  }

  private void reindexKernels() {
    List<Family> list = new ArrayList<>();
    for (Entry entry : kernels.entries()) {
      family(entry.json()).ifPresent(list::add);
    }
    families = List.copyOf(list);
  }

  private static Optional<Family> family(JsonObject json) {
    CtKernel.Family family;
    try {
      family = CtKernel.Family.valueOf(json.getString(ListDocument.ID).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Unknown kernel family {} in {}", json.getString(ListDocument.ID), KERNELS);
      return Optional.empty();
    }
    Map<String, List<String>> vendors = new java.util.LinkedHashMap<>();
    if (json.get("vendors") instanceof JsonObject v) { // NON-NLS
      for (Map.Entry<String, JsonValue> e : v.entrySet()) {
        if (e.getValue() instanceof JsonArray a) {
          vendors.put(
              e.getKey().toUpperCase(Locale.ROOT),
              a.getValuesAs(JsonString.class).stream()
                  .map(s -> s.getString().toUpperCase(Locale.ROOT))
                  .toList());
        }
      }
    }
    List<Pattern> patterns = new ArrayList<>();
    for (String regex : JsonUtil.getStringList(json, "patterns")) { // NON-NLS
      try {
        patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
      } catch (PatternSyntaxException e) {
        LOGGER.warn("Invalid kernel pattern {} in {}", regex, KERNELS);
      }
    }
    return Optional.of(new Family(family, vendors, patterns));
  }

  private void reindexWeighting() {
    List<Rule> list = new ArrayList<>();
    for (Entry entry : weighting.entries()) {
      JsonObject json = entry.json();
      try {
        MrWeighting w =
            MrWeighting.valueOf(json.getString("weighting").toUpperCase(Locale.ROOT)); // NON-NLS
        JsonObject when =
            json.get("when") instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT; // NON-NLS
        list.add(new Rule(w, JsonUtil.getInt(json, "priority", 0), when)); // NON-NLS
      } catch (RuntimeException e) {
        LOGGER.warn("Invalid MR weighting rule {} in {}", entry.id(), MR_WEIGHTING);
      }
    }
    list.sort(Comparator.comparingInt(Rule::priority).reversed());
    rules = List.copyOf(list);
  }

  private static List<JsonObject> builtIn(String resource, String entries) {
    try (InputStream in = DerivationTables.class.getResourceAsStream("/" + resource)) {
      if (in == null) {
        return List.of();
      }
      JsonObject doc = JsonUtil.readObject(in.readAllBytes());
      return doc.get(entries) instanceof JsonArray a ? JsonUtil.objects(a) : List.of();
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in {}", resource, e);
      return List.of();
    }
  }
}
