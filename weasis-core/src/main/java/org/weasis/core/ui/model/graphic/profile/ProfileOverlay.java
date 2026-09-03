/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.ViewSetting;

/**
 * Lays the sections of a profile over the user's own settings and takes them off again. A profile
 * only states some sections; the others must show what the user chose, not what the previous
 * profile left behind. So before a profile is applied the settings go back to the user's, and what
 * the new profile is about to replace is kept aside. A setting the user changes while the profile
 * is active becomes the user's own and is kept. The user's settings are also what is saved when the
 * application closes.
 */
final class ProfileOverlay {

  private final ViewSetting setting;
  private final GraphicRegistry registry;
  private final Measurement[] statistics;

  /** The settings the profile replaced, by target; several tools may share one measurement. */
  private final Map<Object, Held<?>> held = new LinkedHashMap<>();

  private record Held<T>(Supplier<T> getter, Consumer<T> setter, T own, T applied) {
    void restore() {
      if (Objects.equals(getter.get(), applied)) {
        setter.accept(own);
      }
    }
  }

  private record LabelFlag(Measurement measurement) {}

  private record ComputedFlag(Measurement measurement) {}

  ProfileOverlay(ViewSetting setting, GraphicRegistry registry, Measurement[] statistics) {
    this.setting = setting;
    this.registry = registry;
    this.statistics = statistics;
  }

  /** Applies the defaults, labels and statistics of a profile over the user's settings. */
  synchronized void apply(MeasurementProfile profile) {
    restore();
    if (profile == null) {
      return;
    }
    applyDefaults(profile.defaults());
    profile.labels().forEach(this::applyLabels);
    applyStatistics(profile.statistics());
  }

  /** Takes the current profile off: the settings it still holds are the user's own again. */
  synchronized void restore() {
    held.values().forEach(Held::restore);
    held.clear();
  }

  /** Writes a value over a setting, keeping the user's value from the first time it is replaced. */
  @SuppressWarnings("unchecked")
  private <T> void overlay(Object target, Supplier<T> getter, Consumer<T> setter, T value) {
    if (value == null) {
      return;
    }
    Held<T> previous = (Held<T>) held.get(target);
    T own = previous == null ? getter.get() : previous.own();
    if (!Objects.equals(getter.get(), value)) {
      setter.accept(value);
    }
    // The setter may clamp the value: what is compared on restore is what was stored
    held.put(target, new Held<>(getter, setter, own, getter.get()));
  }

  private void applyDefaults(Defaults defaults) {
    if (defaults == null) {
      return;
    }
    overlay("color", setting::getLineColor, setting::setLineColor, defaults.color()); // NON-NLS
    overlay(
        "lineWidth", setting::getLineWidth, setting::setLineWidth, defaults.lineWidth()); // NON-NLS
    overlay("fill", setting::isFilled, setting::setFilled, defaults.fill()); // NON-NLS
    overlay(
        "fillOpacity", // NON-NLS
        setting::getFillOpacity,
        setting::setFillOpacity,
        defaults.fillOpacity());
    overlay(
        "uprightByDrag", // NON-NLS
        setting::isUprightByDrag,
        setting::setUprightByDrag,
        defaults.uprightByDrag());
    overlay("decimals", setting::getDecimals, setting::setDecimals, defaults.decimals()); // NON-NLS
  }

  private void applyLabels(String toolKey, List<String> shown) {
    registry
        .prototype(toolKey)
        .ifPresent(
            graphic ->
                graphic
                    .getMeasurementList()
                    .forEach(
                        m ->
                            overlay(
                                new LabelFlag(m),
                                m::getGraphicLabel,
                                m::setGraphicLabel,
                                shown.contains(m.getKey()))));
  }

  private void applyStatistics(List<String> computed) {
    if (computed == null) {
      return;
    }
    for (Measurement m : statistics) {
      overlay(new ComputedFlag(m), m::getComputed, m::setComputed, computed.contains(m.getKey()));
    }
    boolean any = !computed.isEmpty();
    overlay(
        "basicStatistics", // NON-NLS
        setting::isBasicStatistics,
        setting::setBasicStatistics,
        any);
    overlay(
        "moreStatistics", setting::isMoreStatistics, setting::setMoreStatistics, any); // NON-NLS
  }
}
