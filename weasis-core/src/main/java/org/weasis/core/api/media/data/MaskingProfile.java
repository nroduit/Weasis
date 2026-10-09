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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.util.LayeredEntries;

/**
 * What to do with each {@link TagCategory}, for one use case. Profiles differ along the axes DICOM
 * PS3.15 Annex E names as options — retain patient characteristics, retain longitudinal temporal
 * information, clean descriptors, retain institution identity — rather than by listing tags again,
 * so a newly classified tag is handled by every profile at once. Profiles are defined in JSON, see
 * {@link MaskingModel}.
 *
 * @param id stable identifier, used in preferences and audit records
 * @param name English display name
 * @param labels display name per language code
 * @param actions action per category; a missing category is {@link AnonymizationAction#KEEP}
 * @param tagActions action per normalized tag key, overriding the category of that tag
 * @param shiftsDates whether {@link TagCategory#DATE} uses the mask's date offset
 * @param offerInExport whether export dialogs offer this profile
 * @param locked set by a site document: the user document may not replace this profile
 */
public record MaskingProfile(
    String id,
    String name,
    Map<String, String> labels,
    Map<TagCategory, AnonymizationAction> actions,
    Map<String, AnonymizationAction> tagActions,
    boolean shiftsDates,
    boolean offerInExport,
    boolean locked)
    implements IdentityMask.TagPolicy, LayeredEntries.Entry {

  /** A profile that is not locked. */
  public MaskingProfile(
      String id,
      String name,
      Map<String, String> labels,
      Map<TagCategory, AnonymizationAction> actions,
      Map<String, AnonymizationAction> tagActions,
      boolean shiftsDates,
      boolean offerInExport) {
    this(id, name, labels, actions, tagActions, shiftsDates, offerInExport, false);
  }

  public static final String DISPLAY_ID = "display"; // NON-NLS
  public static final String TEACHING_ID = "teaching"; // NON-NLS
  public static final String PUBLICATION_ID = "publication"; // NON-NLS
  public static final String AI_REQUEST_ID = "ai-request"; // NON-NLS

  public MaskingProfile {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    labels = Map.copyOf(labels);
    actions = Map.copyOf(actions);
    Map<String, AnonymizationAction> normalized = new LinkedHashMap<>();
    tagActions.forEach(
        (tag, action) -> {
          String key = MaskingModel.normalizeKey(tag, null);
          if (key != null) {
            normalized.put(key, action);
          }
        });
    tagActions = Map.copyOf(normalized);
  }

  /** On screen: substitute who it is, keep dates and everything that says what the image is. */
  public static MaskingProfile display() {
    return MaskingModelRegistry.getInstance().require(DISPLAY_ID);
  }

  /** Teaching material: as on screen, with dates shifted. */
  public static MaskingProfile teaching() {
    return MaskingModelRegistry.getInstance().require(TEACHING_ID);
  }

  /** Leaving the institution for good: drop the site, the equipment and every date. */
  public static MaskingProfile publication() {
    return MaskingModelRegistry.getInstance().require(PUBLICATION_ID);
  }

  /** Sent to an external service: keep clinical signals and intervals, remove the site. */
  public static MaskingProfile aiRequest() {
    return MaskingModelRegistry.getInstance().require(AI_REQUEST_ID);
  }

  public AnonymizationAction actionFor(TagCategory category) {
    return actions.getOrDefault(category, AnonymizationAction.KEEP);
  }

  @Override
  public AnonymizationAction actionFor(TagW tag) {
    if (tag == null) {
      return AnonymizationAction.KEEP;
    }
    if (!tagActions.isEmpty()) {
      for (String key : tag.maskingKeys()) {
        AnonymizationAction action = tagActions.get(key);
        if (action != null) {
          return action;
        }
      }
    }
    return actionFor(tag.getCategory());
  }

  /** Whether names and identifiers are masked; a profile that keeps them is refused on load. */
  public boolean hidesDirectIdentifiers() {
    return actionFor(TagCategory.DIRECT_ID) != AnonymizationAction.KEEP;
  }

  public String displayName(Locale locale) {
    return labels.getOrDefault(locale.getLanguage(), name);
  }

  @Override
  public String toString() {
    return displayName(Locale.getDefault());
  }
}
