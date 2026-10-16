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

import java.util.Comparator;
import java.util.Optional;
import org.weasis.core.api.media.data.ImageElement;

/**
 * Translates the values a {@link ViewState} stores by id (window/level preset, stack order) to the
 * objects a view works with, and back. A viewer type provides one when its presets and sort orders
 * have stable identifiers; the default knows none of them.
 */
public interface ViewStateAdapter<E extends ImageElement> {

  /** Stable id of a preset the view applies, when the viewer type gives presets ids. */
  default Optional<String> presetId(Object preset) {
    return Optional.empty();
  }

  /** The preset of that id for the image the view shows. */
  default Optional<Object> preset(ViewCanvas<E> view, String id) {
    return Optional.empty();
  }

  /** Stable key of a stack order the view uses. */
  default Optional<String> sortKey(Comparator<?> comparator) {
    return Optional.empty();
  }

  /** The stack order of that key. */
  default Optional<Comparator<E>> sort(String key) {
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  static <E extends ImageElement> ViewStateAdapter<E> none() {
    return (ViewStateAdapter<E>) None.INSTANCE;
  }

  /** The adapter of a viewer type whose presets and sort orders carry no id. */
  final class None implements ViewStateAdapter<ImageElement> {
    private static final None INSTANCE = new None();

    private None() {}
  }
}
