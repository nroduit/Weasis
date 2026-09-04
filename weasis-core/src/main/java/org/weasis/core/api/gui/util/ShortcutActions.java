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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * The actions a key listener runs, by {@link ShortcutManager} shortcut id; see {@link
 * ShortcutTable} for how an event is resolved.
 */
public final class ShortcutActions {

  private final ShortcutTable<Consumer<KeyEvent>> table;

  public ShortcutActions() {
    this.table = new ShortcutTable<>();
  }

  ShortcutActions(BiPredicate<String, KeyEvent> matcher) {
    this.table = new ShortcutTable<>(matcher);
  }

  /** Binds the action to the shortcut id, replacing a previous one. */
  public ShortcutActions on(String id, Runnable action) {
    Objects.requireNonNull(action);
    return on(id, (KeyEvent e) -> action.run());
  }

  /** Same as {@link #on(String, Runnable)} for an action that needs the event. */
  public ShortcutActions on(String id, Consumer<KeyEvent> action) {
    table.on(id, action);
    return this;
  }

  /** Runs the action of the first shortcut the event matches; false when there is none. */
  public boolean dispatch(KeyEvent e) {
    Optional<Consumer<KeyEvent>> action = table.find(e);
    action.ifPresent(a -> a.accept(e));
    return action.isPresent();
  }

  public Set<String> ids() {
    return table.ids();
  }
}
