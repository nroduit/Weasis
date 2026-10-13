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

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.weasis.core.api.service.WProperties;

/**
 * The value type of a setting and its text form. The text form is the one the existing stores
 * already hold ({@code weasis.properties}, {@code persistence.properties}, {@code base.json}), so a
 * declared setting reads the values written before it was declared.
 *
 * @param <T> the Java type of the value
 */
public final class SettingType<T> {

  public static final SettingType<Boolean> BOOLEAN =
      new SettingType<>("boolean", Boolean.class, SettingType::parseBoolean, String::valueOf);
  public static final SettingType<Integer> INTEGER =
      new SettingType<>("integer", Integer.class, Integer::valueOf, String::valueOf);
  public static final SettingType<Long> LONG =
      new SettingType<>("long", Long.class, Long::valueOf, String::valueOf);
  public static final SettingType<Double> DOUBLE =
      new SettingType<>("double", Double.class, Double::valueOf, String::valueOf);
  public static final SettingType<String> STRING =
      new SettingType<>("string", String.class, Function.identity(), Function.identity());
  public static final SettingType<Color> COLOR =
      new SettingType<>(
          "color",
          Color.class,
          SettingType::parseColor,
          c -> WProperties.color2Hexadecimal(c, true));

  private final String name;
  private final Class<T> javaType;
  private final Function<String, T> parser;
  private final Function<T, String> formatter;
  private final List<String> values;

  private SettingType(
      String name, Class<T> javaType, Function<String, T> parser, Function<T, String> formatter) {
    this(name, javaType, parser, formatter, List.of());
  }

  private SettingType(
      String name,
      Class<T> javaType,
      Function<String, T> parser,
      Function<T, String> formatter,
      List<String> values) {
    this.name = name;
    this.javaType = javaType;
    this.parser = parser;
    this.formatter = formatter;
    this.values = values;
  }

  /** An enumeration stored by constant name; the catalog lists the allowed names. */
  public static <E extends Enum<E>> SettingType<E> of(Class<E> enumType) {
    List<String> names = Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList();
    return new SettingType<>(
        "enum", enumType, text -> Enum.valueOf(enumType, text.trim()), Enum::name, names);
  }

  /** The name written in the catalog: boolean, integer, long, double, string, color or enum. */
  public String name() {
    return name;
  }

  public Class<T> javaType() {
    return javaType;
  }

  /** The allowed constant names of an enumeration, empty for the other types. */
  public List<String> values() {
    return values;
  }

  /** The value of a stored text, empty when the text is absent or not of this type. */
  public Optional<T> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(parser.apply(text));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** The text stored for a value. */
  public String format(T value) {
    return formatter.apply(value);
  }

  private static Boolean parseBoolean(String text) {
    String value = text.trim().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "true" -> Boolean.TRUE;
      case "false" -> Boolean.FALSE;
      default -> throw new IllegalArgumentException("Not a boolean: " + text);
    };
  }

  private static Color parseColor(String text) {
    String value = text.trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Empty color");
    }
    // Same reading as WProperties.hexadecimal2Color, without its fallback to black on bad input
    long bits = Long.parseLong(value, 16);
    int argb = value.length() > 6 ? (int) bits : 0xff000000 | (int) bits;
    return new Color(argb, true);
  }

  @Override
  public String toString() {
    return name;
  }
}
