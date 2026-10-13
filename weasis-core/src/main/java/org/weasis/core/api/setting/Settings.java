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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes declared settings. A value is resolved through the layers, the first that
 * defines a valid value wins:
 *
 * <ol>
 *   <li><b>context</b>: a hanging protocol or session active now, for a {@link Setting#contextual()
 *       contextual} setting only; in memory, never stored;
 *   <li><b>stored</b>: the user's or the workstation's value, by the setting's scope;
 *   <li><b>site</b>: a Java system property, else the launch configuration ({@code base.json} and
 *       the Viewer Hub overrides);
 *   <li><b>default</b>: the declaration.
 * </ol>
 *
 * <p>The site locks a setting by listing its key in {@value #LOCKED_PROPERTY} (comma separated):
 * the context and stored layers are then skipped and a write is refused, so the site value, or the
 * default when the site gives none, is the one in force. A setting declared not lockable ignores
 * the lock.
 *
 * <p>A write changes the store in memory; the store is saved to disk by its owner at its usual time
 * (preference dialog, exit).
 */
public final class Settings {
  private static final Logger LOGGER = LoggerFactory.getLogger(Settings.class);

  /** Site property listing the locked keys, comma separated. */
  public static final String LOCKED_PROPERTY = "weasis.settings.locked"; // NON-NLS

  /** The layer a resolved value comes from. */
  public enum Origin {
    CONTEXT,
    USER,
    MACHINE,
    SITE,
    DEFAULT;

    public String id() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  /** A resolved value, the layer it comes from, and whether the site locks it. */
  public record Resolution<T>(T value, Origin origin, boolean locked) {}

  private static volatile SettingEnvironment environment = SettingEnvironment.inMemory(Map.of());

  private static final List<Context> CONTEXTS = new CopyOnWriteArrayList<>();
  private static final Map<String, List<Consumer<Object>>> LISTENERS = new ConcurrentHashMap<>();

  private Settings() {}

  /** Sets what the resolver reads from; called once by the application at start. */
  public static void install(SettingEnvironment env) {
    environment = Objects.requireNonNull(env);
  }

  /** The value in force. */
  public static <T> T get(Setting<T> setting) {
    return resolve(setting).value();
  }

  /**
   * The value in force, or the computed one when no layer defines a value: for a default that
   * depends on the screen or on the content, such as a width scaled to the display.
   */
  public static <T> T get(Setting<T> setting, Supplier<T> computedDefault) {
    Resolution<T> resolution = resolve(setting);
    return resolution.origin() == Origin.DEFAULT ? computedDefault.get() : resolution.value();
  }

  /** The value in force with the layer it comes from. */
  public static <T> Resolution<T> resolve(Setting<T> setting) {
    SettingEnvironment env = environment;
    boolean locked = isLocked(env, setting);
    if (!locked) {
      if (setting.contextual()) {
        for (int i = CONTEXTS.size() - 1; i >= 0; i--) {
          Object value = CONTEXTS.get(i).values.get(setting);
          if (value != null) {
            return new Resolution<>(setting.type().javaType().cast(value), Origin.CONTEXT, false);
          }
        }
      }
      if (setting.scope() != Scope.SITE) {
        Optional<T> stored = parse(setting, env.store(setting).get(setting.key()), "stored");
        if (stored.isPresent()) {
          Origin origin = setting.scope() == Scope.MACHINE ? Origin.MACHINE : Origin.USER;
          return new Resolution<>(stored.get(), origin, false);
        }
      }
    }
    Optional<T> site = parse(setting, env.site(setting.key()), "site");
    return site.map(v -> new Resolution<>(v, Origin.SITE, locked))
        .orElseGet(() -> new Resolution<>(setting.defaultValue(), Origin.DEFAULT, locked));
  }

  /** Whether the site locks the setting to its own value. */
  public static boolean isLocked(Setting<?> setting) {
    return isLocked(environment, setting);
  }

  /**
   * Stores a value for the user or the workstation, by the setting's scope; null removes the stored
   * value, as {@link #reset(Setting)}.
   *
   * @return false when refused: a site setting, or a setting the site locks
   */
  public static <T> boolean set(Setting<T> setting, T value) {
    if (value == null) {
      return reset(setting);
    }
    if (!isWritable(setting)) {
      return false;
    }
    environment.store(setting).put(setting.key(), setting.type().format(value));
    notifyListeners(setting);
    return true;
  }

  /**
   * Removes the stored value, so that the site value or the default applies again.
   *
   * @return false when refused: a site setting, or a setting the site locks
   */
  public static boolean reset(Setting<?> setting) {
    if (!isWritable(setting)) {
      return false;
    }
    environment.store(setting).remove(setting.key());
    notifyListeners(setting);
    return true;
  }

  /**
   * Applies the values of a hanging protocol or a session on top of the stored ones until the
   * returned context is closed. Contexts stack: the last applied wins. Only contextual settings are
   * accepted.
   *
   * @param id the protocol or session, for the log
   * @throws IllegalArgumentException when a setting is not contextual or a value has the wrong type
   */
  public static Context applyContext(String id, Map<Setting<?>, ?> values) {
    Map<Setting<?>, Object> checked = new LinkedHashMap<>();
    values.forEach(
        (setting, value) -> {
          if (!setting.contextual()) {
            throw new IllegalArgumentException(setting.key() + " is not contextual");
          }
          if (value != null) {
            checked.put(setting, setting.type().javaType().cast(value));
          }
        });
    Context context = new Context(id, Map.copyOf(checked));
    CONTEXTS.add(context);
    context.values.keySet().forEach(Settings::notifyListeners);
    return context;
  }

  /** Called with the value in force each time a write, a reset or a context changes it. */
  @SuppressWarnings("unchecked")
  public static <T> void addListener(Setting<T> setting, Consumer<T> listener) {
    LISTENERS
        .computeIfAbsent(setting.key(), k -> new CopyOnWriteArrayList<>())
        .add((Consumer<Object>) listener);
  }

  public static void removeListener(Setting<?> setting, Consumer<?> listener) {
    List<Consumer<Object>> list = LISTENERS.get(setting.key());
    if (list != null) {
      list.remove(listener);
    }
  }

  private static boolean isWritable(Setting<?> setting) {
    if (setting.scope() == Scope.SITE) {
      LOGGER.warn("Setting {} is a site setting, Weasis does not write it", setting.key());
      return false;
    }
    if (isLocked(setting)) {
      LOGGER.info("Setting {} is locked by the site, the change is ignored", setting.key());
      return false;
    }
    return true;
  }

  private static boolean isLocked(SettingEnvironment env, Setting<?> setting) {
    if (!setting.lockable()) {
      return false;
    }
    return lockedKeys(env.site(LOCKED_PROPERTY)).contains(setting.key());
  }

  static Set<String> lockedKeys(String property) {
    if (property == null || property.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(property.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static <T> Optional<T> parse(Setting<T> setting, String text, String layer) {
    Optional<T> value = setting.type().parse(text);
    if (text != null && value.isEmpty()) {
      if (setting.secret()) {
        LOGGER.warn("Setting {}: the {} value is not a {}", setting.key(), layer, setting.type());
      } else {
        LOGGER.warn(
            "Setting {}: the {} value '{}' is not a {}",
            setting.key(),
            layer,
            text,
            setting.type());
      }
    }
    return value;
  }

  private static void notifyListeners(Setting<?> setting) {
    List<Consumer<Object>> list = LISTENERS.get(setting.key());
    if (list == null || list.isEmpty()) {
      return;
    }
    Object value = get(setting);
    for (Consumer<Object> listener : new ArrayList<>(list)) {
      try {
        listener.accept(value);
      } catch (RuntimeException e) {
        LOGGER.error("Listener of setting {}", setting.key(), e);
      }
    }
  }

  /** The values of one protocol or session, in force until closed. */
  public static final class Context implements AutoCloseable {
    private final String id;
    private final Map<Setting<?>, Object> values;

    private Context(String id, Map<Setting<?>, Object> values) {
      this.id = id;
      this.values = values;
    }

    public String id() {
      return id;
    }

    /** Removes the values; closing twice does nothing. */
    @Override
    public void close() {
      if (CONTEXTS.remove(this)) {
        values.keySet().forEach(Settings::notifyListeners);
      }
    }
  }
}
