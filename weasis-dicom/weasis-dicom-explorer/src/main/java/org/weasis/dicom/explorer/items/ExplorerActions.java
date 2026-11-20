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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;

/**
 * The action providers of the loaded plugins, asked on behalf of the explorer. A provider that
 * throws is logged and skipped: a plugin never breaks a menu or a thumbnail.
 */
public final class ExplorerActions {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerActions.class);

  private static volatile Supplier<List<ExplorerActionProvider>> source =
      ExplorerActions::extensions;

  private ExplorerActions() {}

  /**
   * The actions of one provider for one target.
   *
   * @param provider the provider
   * @param target what the actions apply to
   * @param actions the actions, never empty
   */
  public record Group(
      ExplorerActionProvider provider, ExplorerTarget target, List<ExplorerAction> actions) {}

  /**
   * What one provider offers on a study: on the selected series when the selection is a part of the
   * study, and on the whole study. At least one of the two is present.
   */
  public record Offer(
      ExplorerActionProvider provider, Optional<Group> selection, Optional<Group> study) {}

  private static List<ExplorerActionProvider> extensions() {
    try {
      return GuiUtils.getUICore().getPluginManager().getExtensions(ExplorerActionProvider.class);
    } catch (RuntimeException | LinkageError e) {
      return List.of(); // no plugin manager (a test)
    }
  }

  /** Replaces the providers, for tests; null puts the plugin manager back. */
  public static void setSource(Supplier<List<ExplorerActionProvider>> providers) {
    source = providers == null ? ExplorerActions::extensions : providers;
  }

  public static List<ExplorerActionProvider> providers() {
    List<ExplorerActionProvider> list = source.get();
    return list == null ? List.of() : list;
  }

  /** The actions of a provider for a target, empty when it has none or fails. */
  public static Optional<Group> group(ExplorerActionProvider provider, ExplorerTarget target) {
    try {
      List<ExplorerAction> actions = provider.getActions(target);
      return actions == null || actions.isEmpty()
          ? Optional.empty()
          : Optional.of(new Group(provider, target, List.copyOf(actions)));
    } catch (RuntimeException | LinkageError e) {
      LOGGER.warn("Explorer action provider {} failed", provider.getClass().getName(), e);
      return Optional.empty();
    }
  }

  /**
   * What every provider offers on a study, given the series selected in the explorer; the providers
   * with nothing for it are left out.
   */
  public static List<Offer> offers(
      DicomModel model, MediaSeriesGroup study, List<DicomSeries> selection) {
    List<ExplorerActionProvider> providers = providers();
    if (providers.isEmpty() || model == null || study == null) {
      return List.of();
    }
    ExplorerTarget whole = ExplorerTarget.ofStudy(model, study);
    Optional<ExplorerTarget> part =
        ExplorerTarget.ofSelection(model, study, selection == null ? List.of() : selection);
    List<Offer> offers = new ArrayList<>();
    for (ExplorerActionProvider provider : providers) {
      Optional<Group> onSelection = part.flatMap(target -> group(provider, target));
      Optional<Group> onStudy = group(provider, whole);
      if (onSelection.isPresent() || onStudy.isPresent()) {
        offers.add(new Offer(provider, onSelection, onStudy));
      }
    }
    return offers;
  }

  /** The marks the providers put on a series, in the order of the providers. */
  public static List<SeriesBadge> badges(DicomSeries series) {
    List<ExplorerActionProvider> providers = providers();
    if (providers.isEmpty() || series == null) {
      return List.of();
    }
    List<SeriesBadge> badges = new ArrayList<>();
    for (ExplorerActionProvider provider : providers) {
      try {
        provider.getBadge(series).ifPresent(badges::add);
      } catch (RuntimeException | LinkageError e) {
        LOGGER.warn("Explorer action provider {} failed", provider.getClass().getName(), e);
      }
    }
    return badges;
  }

  /** The icons of the marks of a series added to the icons of its objects, for its thumbnail. */
  public static Set<ResourceIconPath> withBadgeIcons(
      Set<ResourceIconPath> icons, DicomSeries series) {
    List<SeriesBadge> badges = badges(series);
    if (badges.isEmpty()) {
      return icons;
    }
    Set<ResourceIconPath> all = new LinkedHashSet<>(icons == null ? Set.of() : icons);
    badges.forEach(badge -> all.add(badge.icon()));
    return all;
  }
}
