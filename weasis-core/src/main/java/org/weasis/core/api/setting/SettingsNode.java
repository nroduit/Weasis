/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.prefs.AbstractPreferences;

/**
 * A {@code java.util.prefs} node held in memory by the {@link SettingsFileStore}. The node tree
 * mirrors the JDK layout ({@code /org/weasis/<group>/…}); every node below a group belongs to that
 * group's file, and a change marks the file to be saved.
 */
final class SettingsNode extends AbstractPreferences {

  /** The values and children of one node, as read from and written to the group file. */
  static final class Data {
    final Map<String, String> values = new TreeMap<>();
    final Map<String, Data> children = new TreeMap<>();
  }

  private final SettingsFileStore store;
  private final SettingsFileStore.Group group;
  private final Data data;

  /** The root of the tree. */
  SettingsNode(SettingsFileStore store) {
    super(null, "");
    this.store = store;
    this.group = null;
    this.data = new Data();
  }

  private SettingsNode(SettingsNode parent, String name, SettingsFileStore.Group group, Data data) {
    super(parent, name);
    this.store = parent.store;
    this.group = group;
    this.data = data;
  }

  @Override
  protected void putSpi(String key, String value) {
    write(() -> data.values.put(key, value));
  }

  @Override
  protected String getSpi(String key) {
    return read(() -> data.values.get(key));
  }

  @Override
  protected void removeSpi(String key) {
    write(() -> data.values.remove(key));
  }

  @Override
  protected void removeNodeSpi() {
    if (group != null && group.root() == this) {
      store.clear(group);
      return;
    }
    SettingsNode parent = (SettingsNode) parent();
    write(() -> parent.data.children.remove(name()));
  }

  @Override
  protected String[] keysSpi() {
    return read(() -> data.values.keySet().toArray(String[]::new));
  }

  @Override
  protected String[] childrenNamesSpi() {
    return read(() -> data.children.keySet().toArray(String[]::new));
  }

  @Override
  protected AbstractPreferences childSpi(String name) {
    if (group == null && store.isGroupParent(this)) {
      return store.openGroup(this, name);
    }
    // A node exists once asked for, as in the JDK store; an empty node is not written to the file,
    // so creating one does not mark the group to be saved
    Data child = read(() -> data.children.computeIfAbsent(name, n -> new Data()));
    return new SettingsNode(this, name, group, child);
  }

  /** The root node of a group, holding the data read from the group's file. */
  SettingsNode groupRoot(String name, SettingsFileStore.Group group) {
    return new SettingsNode(this, name, group, group.data());
  }

  @Override
  protected void syncSpi() {
    flushSpi();
  }

  @Override
  protected void flushSpi() {
    if (group != null) {
      store.save(group);
    }
  }

  private void write(Runnable change) {
    if (group == null) {
      synchronized (data) {
        change.run();
      }
      return;
    }
    synchronized (group) {
      change.run();
      group.markDirty();
    }
    store.scheduleSave(group);
  }

  private <T> T read(Supplier<T> reader) {
    synchronized (group == null ? data : group) {
      return reader.get();
    }
  }
}
