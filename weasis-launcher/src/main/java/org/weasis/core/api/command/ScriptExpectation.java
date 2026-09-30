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

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * What a script asserts about what a step returned: {@code @expect <value> <operator> <value>},
 * with a tolerance for a measured number ({@code ~ 61.5 +-0.2}) and a regular expression for text.
 * A failed expectation fails its step, which is what makes a script a test.
 *
 * @param actual the left side, once its references are resolved
 * @param expected the right side
 * @param tolerance the {@code +-} of a {@code ~} comparison
 */
public record ScriptExpectation(String actual, String operator, String expected, double tolerance) {

  /** Operators a script may use, longest first so {@code <=} is not read as {@code <}. */
  private static final List<String> OPERATORS =
      List.of("==", "!=", "<=", ">=", "matches", "~", "<", ">"); // NON-NLS

  private static final String TOLERANCE = "+-"; // NON-NLS

  /** Reads the two sides and the operator of an expectation, both sides already substituted. */
  public static ScriptExpectation parse(String text) {
    for (String operator : OPERATORS) {
      int at = indexOf(text, operator);
      if (at < 0) {
        continue;
      }
      String left = text.substring(0, at).strip();
      String right = text.substring(at + operator.length()).strip();
      double tolerance = 0;
      int plusMinus = right.indexOf(TOLERANCE);
      if (plusMinus >= 0) {
        tolerance = number(right.substring(plusMinus + TOLERANCE.length()).strip());
        right = right.substring(0, plusMinus).strip();
      }
      return new ScriptExpectation(left, operator, right, tolerance);
    }
    throw new IllegalArgumentException(
        "An expectation compares two values: " + String.join(" ", OPERATORS));
  }

  /** Why the expectation does not hold, or {@code null} when it does. */
  public String failure() {
    return switch (operator) {
      case "==" -> equal() ? null : "expected " + expected + ", got " + actual;
      case "!=" -> equal() ? "expected anything but " + expected : null;
      case "matches" -> matches() ? null : actual + " does not match " + expected;
      case "~" -> close() ? null : "expected " + expected + " +-" + tolerance + ", got " + actual;
      default -> compared();
    };
  }

  private boolean equal() {
    if (isNumber(actual) && isNumber(expected)) {
      return Double.compare(number(actual), number(expected)) == 0;
    }
    return actual.equals(expected);
  }

  private boolean matches() {
    try {
      return Pattern.compile(expected).matcher(actual).find();
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Not a regular expression: " + expected);
    }
  }

  private boolean close() {
    return Math.abs(number(actual) - number(expected)) <= Math.abs(tolerance);
  }

  private String compared() {
    double left = number(actual);
    double right = number(expected);
    boolean held =
        switch (operator) {
          case "<" -> left < right;
          case "<=" -> left <= right;
          case ">" -> left > right;
          default -> left >= right;
        };
    return held ? null : actual + " is not " + operator + " " + expected;
  }

  private static int indexOf(String text, String operator) {
    int at = text.indexOf(operator);
    if (at < 0 || !"matches".equals(operator)) { // NON-NLS
      return at;
    }
    boolean word =
        (at == 0 || Character.isWhitespace(text.charAt(at - 1)))
            && (at + operator.length() >= text.length()
                || Character.isWhitespace(text.charAt(at + operator.length())));
    return word ? at : -1;
  }

  private static boolean isNumber(String text) {
    try {
      Double.parseDouble(text);
      return true;
    } catch (NumberFormatException | NullPointerException e) {
      return false;
    }
  }

  private static double number(String text) {
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException("Not a number: " + text);
    }
  }
}
