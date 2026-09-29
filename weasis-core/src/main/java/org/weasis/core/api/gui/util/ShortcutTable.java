/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Values a key listener looks up by {@link ShortcutManager} shortcut id.
 *
 * <p>The table holds ids and not bindings: an event is resolved against the current binding of each
 * id, so a shortcut changed in the preferences applies at once. When two ids share a binding the
 * first one registered wins.
 *
 * @param <T> what an id stands for: an action, a direction, a step
 * @see ShortcutActions
 */
public final class ShortcutTable<T> {

  private final Map<String, T> values = new LinkedHashMap<>();
  private final BiPredicate<String, KeyEvent> matcher;

  public ShortcutTable() {
    this(ShortcutManager.getInstance()::matches);
  }

  ShortcutTable(BiPredicate<String, KeyEvent> matcher) {
    this.matcher = matcher;
  }

  /** Binds the value to the shortcut id, replacing a previous one. */
  public ShortcutTable<T> on(String id, T value) {
    values.put(Objects.requireNonNull(id), Objects.requireNonNull(value));
    return this;
  }

  /** Value of the first shortcut the event matches. */
  public Optional<T> find(KeyEvent e) {
    for (Map.Entry<String, T> entry : values.entrySet()) {
      if (matcher.test(entry.getKey(), e)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(values.keySet());
  }
}
