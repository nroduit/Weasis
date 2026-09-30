/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * One value type per concept of the command vocabulary. A type is a small public record carrying
 * its validation, plus a {@code public static X parse(String)} factory that throws {@link
 * IllegalArgumentException} with what the type expects; {@link CommandModel} registers it for every
 * option that declares it, so a malformed value is a usage error naming the concept.
 *
 * <p>The kebab-case form of the class name is both the type name of the descriptor and the label an
 * option shows ({@code ViewId} → {@code view-id} → {@code --view <view-id>}), which keeps one
 * spelling per concept.
 */
public interface SemanticType {

  /** Name of the factory every semantic type declares. */
  String PARSE_METHOD = "parse"; // NON-NLS

  /** Vocabulary name of a type: {@code ViewId} → {@code view-id}. */
  static String nameOf(Class<?> type) {
    String name = type.getSimpleName();
    StringBuilder kebab = new StringBuilder(name.length() + 4);
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c) && i > 0) {
        kebab.append('-');
      }
      kebab.append(Character.toLowerCase(c));
    }
    return kebab.toString();
  }

  /** Vocabulary value of an enum constant: {@code BEST_FIT} becomes {@code best-fit}. */
  static String valueOf(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** The label an option of this type shows: {@code <view-id>}. */
  static String labelOf(Class<?> type) {
    return "<" + nameOf(type) + ">";
  }

  /** Converts text with the {@code parse} factory of the type, as a usage error when it refuses. */
  static ITypeConverter<Object> converterOf(Class<?> type) {
    return text -> {
      Method parse;
      try {
        parse = type.getMethod(PARSE_METHOD, String.class);
      } catch (NoSuchMethodException e) {
        throw new TypeConversionException(
            type.getSimpleName() + " declares no 'parse(String)' factory");
      }
      try {
        return parse.invoke(null, text);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new TypeConversionException(cause.getMessage());
      } catch (IllegalAccessException e) {
        throw new TypeConversionException(
            type.getSimpleName() + " has no accessible 'parse(String)' factory");
      }
    };
  }

  /** Reads the integer of an index type, so every index reports the same thing. */
  static int index(String text, String expected) {
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException(expected);
    }
  }

  /** Reads the decimal of a measured value. */
  static double decimal(String text, String expected) {
    try {
      double value = Double.parseDouble(text.trim());
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(expected);
      }
      return value;
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException(expected);
    }
  }

  /** Reads {@code count} integers separated by commas, or any number of them when count is -1. */
  static int[] integers(String text, int count, String expected) {
    String[] parts = split(text, count, expected);
    int[] values = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      values[i] = index(parts[i], expected);
    }
    return values;
  }

  /** Reads {@code count} decimals separated by commas, or any number of them when count is -1. */
  static double[] decimals(String text, int count, String expected) {
    String[] parts = split(text, count, expected);
    double[] values = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      values[i] = decimal(parts[i], expected);
    }
    return values;
  }

  private static String[] split(String text, int count, String expected) {
    String[] parts = text == null ? new String[0] : text.split(",");
    if (parts.length == 0 || (count > 0 && parts.length != count)) {
      throw new IllegalArgumentException(expected);
    }
    return parts;
  }

  /** Reads a handle: any text that is not blank. */
  static String handle(String text, String expected) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(expected);
    }
    return text.trim();
  }

  /** Normalized primitive of the descriptor, for a type that is not a semantic one. */
  static String primitiveOf(Class<?> type) {
    if (type == boolean.class || type == Boolean.class) {
      return "boolean"; // NON-NLS
    }
    if (type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class) {
      return "integer"; // NON-NLS
    }
    if (type == double.class
        || type == Double.class
        || type == float.class
        || type == Float.class) {
      return "number"; // NON-NLS
    }
    if (CharSequence.class.isAssignableFrom(type)) {
      return "string"; // NON-NLS
    }
    if (type.isEnum()) {
      return "enum"; // NON-NLS
    }
    return type.getSimpleName().toLowerCase(Locale.ROOT);
  }
}
