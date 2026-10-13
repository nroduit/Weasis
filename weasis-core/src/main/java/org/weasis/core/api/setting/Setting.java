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

import java.util.Objects;

/**
 * The declaration of one typed setting: its key, its type and default, and the scope that decides
 * where it is stored and whether it follows the user. Declared once as a constant, registered in
 * the {@link SettingCatalog}, read and written through {@link Settings}:
 *
 * <pre>{@code
 * public static final Setting<Boolean> CONFIRM_CLOSING =
 *     Setting.bool("weasis-core", "weasis.confirm.closing", false, Scope.USER);
 *
 * if (Settings.get(CONFIRM_CLOSING)) { ... }
 * }</pre>
 *
 * <p>Two settings are equal when they have the same key: the key is the identity in every store, in
 * the catalog and in an export.
 *
 * @param <T> the value type
 */
public final class Setting<T> {

  private final String group;
  private final String key;
  private final SettingType<T> type;
  private final T defaultValue;
  private final Scope scope;
  private final Backing backing;
  private final boolean lockable;
  private final boolean secret;
  private final boolean contextual;
  private final String since;
  private final String description;

  private Setting(Builder<T> builder) {
    this.group = requireText(builder.group, "group"); // NON-NLS
    this.key = requireText(builder.key, "key"); // NON-NLS
    this.type = Objects.requireNonNull(builder.type, "type");
    this.defaultValue = builder.defaultValue;
    this.scope = Objects.requireNonNull(builder.scope, "scope");
    this.backing = builder.backing == null ? Backing.defaultOf(scope) : builder.backing;
    this.lockable = builder.lockable == null ? scope != Scope.SITE : builder.lockable;
    this.secret = builder.secret;
    this.contextual = builder.contextual;
    this.since = builder.since;
    this.description = builder.description;
    if (scope == Scope.SITE && (contextual || backing != Backing.NONE)) {
      throw new IllegalArgumentException(key + ": a site setting is neither stored nor contextual");
    }
    if (scope != Scope.SITE && backing == Backing.NONE) {
      throw new IllegalArgumentException(key + ": a " + scope.id() + " setting needs a store");
    }
    if (secret && contextual) {
      throw new IllegalArgumentException(key + ": a secret cannot be overridden by a protocol");
    }
  }

  /** Starts a declaration; {@link Builder#declare()} registers it in the catalog. */
  public static <T> Builder<T> builder(
      SettingType<T> type, String group, String key, T defaultValue) {
    return new Builder<>(type, group, key, defaultValue);
  }

  public static Setting<Boolean> bool(String group, String key, boolean def, Scope scope) {
    return builder(SettingType.BOOLEAN, group, key, def).scope(scope).declare();
  }

  public static Setting<Integer> integer(String group, String key, int def, Scope scope) {
    return builder(SettingType.INTEGER, group, key, def).scope(scope).declare();
  }

  public static Setting<Double> decimal(String group, String key, double def, Scope scope) {
    return builder(SettingType.DOUBLE, group, key, def).scope(scope).declare();
  }

  public static Setting<String> string(String group, String key, String def, Scope scope) {
    return builder(SettingType.STRING, group, key, def).scope(scope).declare();
  }

  /** The group: the plugin id or the functional area; one stored file per group later on. */
  public String group() {
    return group;
  }

  public String key() {
    return key;
  }

  public SettingType<T> type() {
    return type;
  }

  /** The built-in value, used when no layer defines one; may be null for a string. */
  public T defaultValue() {
    return defaultValue;
  }

  public Scope scope() {
    return scope;
  }

  /** Where the stored value lives during the transition, see {@link Backing}. */
  public Backing backing() {
    return backing;
  }

  /** Whether the site may lock the setting to its own value. */
  public boolean lockable() {
    return lockable;
  }

  /** Never exported, never logged, never mirrored. */
  public boolean secret() {
    return secret;
  }

  /** Whether a hanging protocol may override it while the protocol is active. */
  public boolean contextual() {
    return contextual;
  }

  /** The version that declared it, or null. */
  public String since() {
    return since;
  }

  /** One sentence for the administrator, shown in the catalog; may be null. */
  public String description() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Setting<?> other && key.equals(other.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return key;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A setting needs a " + name);
    }
    return value;
  }

  /** The optional parts of a declaration. Scope is {@link Scope#USER} unless set. */
  public static final class Builder<T> {
    private final SettingType<T> type;
    private final String group;
    private final String key;
    private final T defaultValue;
    private Scope scope = Scope.USER;
    private Backing backing;
    private Boolean lockable;
    private boolean secret;
    private boolean contextual;
    private String since;
    private String description;

    private Builder(SettingType<T> type, String group, String key, T defaultValue) {
      this.type = type;
      this.group = group;
      this.key = key;
      this.defaultValue = defaultValue;
    }

    public Builder<T> scope(Scope scope) {
      this.scope = scope;
      return this;
    }

    /** Keeps the value in the store where it already is; omit it for the scope's default store. */
    public Builder<T> backing(Backing backing) {
      this.backing = backing;
      return this;
    }

    /** Refuses a site lock; every user or machine setting is lockable otherwise. */
    public Builder<T> notLockable() {
      this.lockable = false;
      return this;
    }

    public Builder<T> secret() {
      this.secret = true;
      return this;
    }

    public Builder<T> contextual() {
      this.contextual = true;
      return this;
    }

    public Builder<T> since(String since) {
      this.since = since;
      return this;
    }

    public Builder<T> description(String description) {
      this.description = description;
      return this;
    }

    /** Builds the setting and registers it in the {@link SettingCatalog}. */
    public Setting<T> declare() {
      return SettingCatalog.register(new Setting<>(this));
    }
  }
}
