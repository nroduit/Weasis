/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.SeriesViewer;

/**
 * The kinds of {@link LayoutComponent} a cell of a layout can hold instead of a viewport. The
 * application registers the histogram, the DICOM plugins the attributes of the image; a plugin
 * registers its own from its {@code start()}.
 */
public final class LayoutComponents {

  /** The id of the histogram of the selected image. */
  public static final String HISTOGRAM = "histogram"; // NON-NLS

  /**
   * A kind of component.
   *
   * @param id the stable id written in the layouts of the user, such as {@code histogram}
   * @param type the class name of the component, the type of its cell in a layout
   * @param title the name shown to the user
   * @param icon the icon shown next to the name, may give null
   * @param factory creates the component for a viewer
   * @param accepts whether a viewer can show it
   */
  public record Kind(
      String id,
      String type,
      Supplier<String> title,
      Supplier<Icon> icon,
      Function<SeriesViewer<?>, ? extends LayoutComponent> factory,
      Predicate<SeriesViewer<?>> accepts) {
    public Kind {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(factory, "factory");
      icon = icon == null ? () -> null : icon;
      accepts = accepts == null ? viewer -> true : accepts;
    }

    @Override
    public String toString() {
      return title.get();
    }
  }

  private static final Map<String, Kind> KINDS = new ConcurrentSkipListMap<>();

  static {
    register(
        new Kind(
            HISTOGRAM,
            HistogramView.class.getName(),
            () -> Messages.common("histogram"), // NON-NLS
            () -> ResourceUtil.getIcon("svg/other/histogram.svg"), // NON-NLS
            HistogramView::new,
            ImageViewerPlugin.class::isInstance));
  }

  private LayoutComponents() {}

  /** Declares a kind of component; one of the same id is replaced. */
  public static void register(Kind kind) {
    KINDS.put(kind.id(), kind);
  }

  public static void unregister(String id) {
    KINDS.remove(id);
  }

  /** Every kind declared, by id. */
  public static List<Kind> kinds() {
    return List.copyOf(KINDS.values());
  }

  /** The kinds that viewer can show. */
  public static List<Kind> kindsFor(SeriesViewer<?> viewer) {
    return KINDS.values().stream().filter(k -> viewer == null || k.accepts().test(viewer)).toList();
  }

  public static Optional<Kind> find(String id) {
    return id == null ? Optional.empty() : Optional.ofNullable(KINDS.get(id));
  }

  /** The kind whose component is of that class name, the type of a cell. */
  public static Optional<Kind> ofType(String type) {
    return KINDS.values().stream().filter(k -> k.type().equals(type)).findFirst();
  }

  /** The cell type of a kind, or null when no such kind is declared (its plugin is absent). */
  public static String typeOf(String id) {
    return find(id).map(Kind::type).orElse(null);
  }
}
