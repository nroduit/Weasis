/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * The tools available to the palettes, the preference pages, the XML binding and the commands.
 * Populated by {@link GraphicToolProvider}s; the core provider is registered first. Listeners are
 * told after every change so open viewers can rebuild their palettes. A hidden tool is absent from
 * the palettes only: its graphics still load from files and it can still be used by key.
 */
public final class GraphicRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicRegistry.class);

  /** Old property names still honoured for hiding a tool. */
  private static final Map<String, String> LEGACY_HIDE_PROPERTIES =
      Map.of("weasis.draw.text", "weasis.draw.textGrahic"); // NON-NLS

  private static final class Holder {
    private static final GraphicRegistry INSTANCE =
        new GraphicRegistry(GraphicRegistry::isHiddenBySystemPreferences);
  }

  private record Entry(
      GraphicToolProvider provider, GraphicToolDescriptor descriptor, Graphic prototype) {}

  private final Predicate<String> hiddenProperty;
  private final Map<String, Boolean> hidden = new ConcurrentHashMap<>();
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  /** A registry with its own hide rule, for hosts without the UI core such as tests. */
  public GraphicRegistry(Predicate<String> hiddenProperty) {
    this.hiddenProperty = Objects.requireNonNull(hiddenProperty);
    register(new BuiltinGraphicTools());
  }

  public static GraphicRegistry getInstance() {
    return Holder.INSTANCE;
  }

  /** Adds the tools of a provider; a duplicate key is skipped with a log entry. */
  public void register(GraphicToolProvider provider) {
    Objects.requireNonNull(provider);
    boolean changed = false;
    synchronized (entries) {
      for (GraphicToolDescriptor d : provider.getTools()) {
        if (entries.containsKey(d.key())) {
          LOGGER.warn("Graphic tool {} already registered, ignoring {}", d.key(), provider);
        } else {
          entries.put(d.key(), new Entry(provider, d, d.prototype().get()));
          changed = true;
        }
      }
    }
    if (changed) {
      registerShortcuts();
      fireChanged();
    }
  }

  /**
   * Gives every palette tool an entry in the shortcut preferences, with the default key of its
   * descriptor. To call again after the shortcut defaults have been reset.
   */
  public void registerShortcuts() {
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    List<Entry> tools;
    synchronized (entries) {
      tools = List.copyOf(entries.values());
    }
    for (Entry e : tools) {
      GraphicToolDescriptor d = e.descriptor();
      if (d.category() != ToolCategory.ADVANCED && e.prototype() != BuiltinGraphicTools.SELECTION) {
        shortcuts.registerGraphicTool(
            d.key(),
            e.prototype().getUIName(),
            d.category() == ToolCategory.DRAW
                ? ShortcutManager.CATEGORY_DRAWINGS
                : ShortcutManager.CATEGORY_MEASURES,
            d.shortcutKeyCode(),
            d.shortcutModifier());
      }
    }
  }

  public void unregister(GraphicToolProvider provider) {
    boolean changed;
    synchronized (entries) {
      changed = entries.values().removeIf(e -> e.provider() == provider);
    }
    if (changed) {
      fireChanged();
    }
  }

  public List<GraphicToolDescriptor> descriptors() {
    synchronized (entries) {
      return entries.values().stream().map(Entry::descriptor).toList();
    }
  }

  /** The visible tools of a palette, in registration order. */
  public List<GraphicToolDescriptor> descriptors(ToolCategory category) {
    synchronized (entries) {
      return entries.values().stream()
          .map(Entry::descriptor)
          .filter(d -> d.category() == category && !isHidden(d))
          .toList();
    }
  }

  public Optional<GraphicToolDescriptor> descriptor(String key) {
    synchronized (entries) {
      return Optional.ofNullable(entries.get(key)).map(Entry::descriptor);
    }
  }

  /** The visible palette items of a category, in registration order; each is a prototype. */
  public List<Graphic> prototypes(ToolCategory category) {
    synchronized (entries) {
      return entries.values().stream()
          .filter(e -> e.descriptor().category() == category && !isHidden(e.descriptor()))
          .map(Entry::prototype)
          .toList();
    }
  }

  private boolean isHidden(GraphicToolDescriptor descriptor) {
    return hidden.computeIfAbsent(descriptor.hideProperty(), hiddenProperty::test);
  }

  public Optional<Graphic> prototype(String key) {
    synchronized (entries) {
      return Optional.ofNullable(entries.get(key)).map(Entry::prototype);
    }
  }

  /**
   * Key of the tool that produced a graphic: the prototype itself, else the tool of the same class
   * on the same layer type, else the first tool of the same class.
   */
  public Optional<String> keyOf(Graphic graphic) {
    if (graphic == null) {
      return Optional.empty();
    }
    Class<?> type = GraphicToolDescriptor.baseClass(graphic);
    LayerType layerType = graphic.getLayerType();
    synchronized (entries) {
      String byClass = null;
      for (Entry e : entries.values()) {
        if (e.prototype() == graphic) {
          return Optional.of(e.descriptor().key());
        }
        if (GraphicToolDescriptor.baseClass(e.prototype()) == type) {
          if (e.prototype().getLayerType() == layerType) {
            return Optional.of(e.descriptor().key());
          }
          if (byClass == null) {
            byClass = e.descriptor().key();
          }
        }
      }
      return Optional.ofNullable(byClass);
    }
  }

  /** Classes to bind in the presentation XML context, without duplicates. */
  public List<Class<?>> xmlTypes() {
    synchronized (entries) {
      List<Class<?>> types = new ArrayList<>();
      for (Entry e : entries.values()) {
        Class<?> type = e.descriptor().xmlType();
        if (type != null && !types.contains(type)) {
          types.add(type);
        }
      }
      return Collections.unmodifiableList(types);
    }
  }

  public Graphic selectionGraphic() {
    return BuiltinGraphicTools.SELECTION;
  }

  public void addListener(Runnable listener) {
    listeners.add(Objects.requireNonNull(listener));
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void fireChanged() {
    listeners.forEach(Runnable::run);
  }

  private static boolean isHiddenBySystemPreferences(String property) {
    try {
      WProperties p = GuiUtils.getUICore().getSystemPreferences();
      String value = p.getProperty(property);
      String legacy = LEGACY_HIDE_PROPERTIES.get(property);
      if (value == null && legacy != null) {
        value = p.getProperty(legacy);
      }
      return Boolean.FALSE.toString().equalsIgnoreCase(value);
    } catch (RuntimeException | LinkageError e) {
      // No UI core (headless tests, library use): nothing can hide a tool
      LOGGER.debug("System preferences unavailable, tool {} kept", property, e);
      return false;
    }
  }
}
