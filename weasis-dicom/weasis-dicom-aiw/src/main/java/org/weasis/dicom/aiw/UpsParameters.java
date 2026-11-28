/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * Encoder of the Scheduled Processing Parameters Sequence (0074,1210) of a UPS workitem (PS3.3
 * C.30.2). Each parameter is one Content Item (Content Item Macro, PS3.3 Table 10-2): the Value
 * Type (0040,A040) CS says which kind of value follows, the Concept Name Code Sequence (0040,A043)
 * names the parameter, and the value itself travels in the field its type dictates: NUMERIC in
 * Numeric Value (0040,A30A) DS, TEXT in Text Value (0040,A160) UT, CODE in Concept Code Sequence
 * (0040,A168) SQ.
 *
 * <p>Parameter names are locally defined, so their coding scheme designator uses the private 99
 * prefix (PS3.3 8.2): 99CLARUS, the designator the tasks already use. The sequence is Type 2 on
 * Create (PS3.4 Table CC.2.5-3): required, so a workitem without parameters still carries it empty.
 */
final class UpsParameters {

  /** The local coding scheme of the parameter names (PS3.3 8.2, private 99 prefix). */
  static final String SCHEME = "99CLARUS"; // NON-NLS

  /**
   * The Decimal String forms (PS3.5 6.2) a NUMERIC value may take: an integer, a fixed point or a
   * scientific notation with an E/e exponent. The pattern is ASCII-only, so a matching value is
   * one byte per character.
   */
  private static final Pattern NUMERIC =
      Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

  private UpsParameters() {}

  /** One parameter of a task: its name, its value type and the value as its DICOM string form. */
  record Parameter(String name, String type, String value) {

    Parameter {
      Objects.requireNonNull(name, "Parameter name cannot be null");
      Objects.requireNonNull(type, "Parameter type cannot be null");
      Objects.requireNonNull(value, "Parameter value cannot be null");
      // The Value Type is a Code String (PS3.3 Table 10-2): DICOM CS values are case-insensitive
      // on the wire, so accept any case but keep the uppercase form the encoder emits.
      type = type.trim().toUpperCase(Locale.ROOT);
      if (!type.equals("NUMERIC") && !type.equals("TEXT") && !type.equals("CODE")) {
        throw new IllegalArgumentException(
            "A parameter type is NUMERIC, TEXT or CODE, not: " + type);
      }
      // Validating at construction rather than at encode time makes a malformed value a command
      // error the moment --param is read, before any request goes out.
      if (type.equals("NUMERIC")) {
        validateNumeric(value);
      } else if (type.equals("CODE")) {
        splitCode(value);
      }
    }
  }

  /** The Content Item of one parameter, as a dataset of the (0074,1210) sequence. */
  static Attributes item(Parameter parameter) {
    Attributes item = new Attributes();
    item.setString(Tag.ValueType, VR.CS, parameter.type());
    // The Concept Name Code Sequence names the parameter with one Code; its meaning is the name
    // itself because a local parameter carries no separate display text yet.
    Attributes name = new Attributes();
    setCodeValue(name, parameter.name());
    name.setString(Tag.CodingSchemeDesignator, VR.SH, SCHEME);
    name.setString(Tag.CodeMeaning, VR.LO, parameter.name());
    item.newSequence(Tag.ConceptNameCodeSequence, 1).add(name);
    switch (parameter.type()) {
      case "NUMERIC": // NON-NLS
        item.setString(Tag.NumericValue, VR.DS, parameter.value());
        // Measurement Units Code Sequence is Type 1 for a NUMERIC item (PS3.3 Table 10-2): the
        // unit of CLIP_LIMIT and TILE_GRID is dimensionless, so the UCUM "unity" code 1.
        Attributes units = new Attributes();
        units.setString(Tag.CodeValue, VR.SH, "1");
        units.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
        units.setString(Tag.CodeMeaning, VR.LO, "unity");
        item.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(units);
        break;
      case "TEXT": // NON-NLS
        item.setString(Tag.TextValue, VR.UT, parameter.value());
        break;
      case "CODE": // NON-NLS
        String[] code = splitCode(parameter.value());
        Attributes value = new Attributes();
        setCodeValue(value, code[1]);
        value.setString(Tag.CodingSchemeDesignator, VR.SH, code[0]);
        // Code Meaning is Type 1 (PS3.3 Table 8.8-1); a local code carries its value as its
        // meaning, which is what a reader shows when it does not know the scheme.
        value.setString(Tag.CodeMeaning, VR.LO, code[1]);
        item.newSequence(Tag.ConceptCodeSequence, 1).add(value);
        break;
      default:
        throw new IllegalArgumentException("Unknown parameter type: " + parameter.type());
    }
    return item;
  }

  /** The Content Items of the parameters, in order: one item per value, the name may repeat. */
  static List<Attributes> items(List<Parameter> parameters) {
    List<Attributes> items = new ArrayList<>(parameters.size());
    for (Parameter parameter : parameters) {
      items.add(item(parameter));
    }
    return items;
  }

  /**
   * Writes a code value to Code Value (0008,0100) SH when it fits 16 characters, and to Long Code
   * Value (0008,0119) UC otherwise (PS3.3 Table 8.8-1): the two are alternatives, never both.
   */
  private static void setCodeValue(Attributes code, String value) {
    if (value.length() <= 16) {
      code.setString(Tag.CodeValue, VR.SH, value);
    } else {
      code.setString(Tag.LongCodeValue, VR.UC, value);
    }
  }

  /**
   * Validates a NUMERIC value against Decimal String (PS3.5 6.2): one value, a decimal or an
   * exponent form, at most 16 bytes. A Decimal String may be padded with leading and trailing
   * spaces (PS3.5 Table 6.2-1), so those are stripped before the shape check; embedded spaces stay
   * rejected. dcm4che3's VR.DS setter stores whatever it is given, so the gate lives here instead
   * of trusting the wire encoder.
   */
  private static void validateNumeric(String value) {
    if (value.indexOf('\\') >= 0) {
      throw new IllegalArgumentException(
          "A NUMERIC parameter carries one value, not several: " + value);
    }
    // PS3.5 6.2: padding is only the ASCII space (20H); the 16-byte budget (PS3.5 Table 6.2-1)
    // counts the padding too, so the length check below runs on the value as given, not stripped.
    int begin = 0;
    int end = value.length();
    while (begin < end && value.charAt(begin) == ' ') {
      begin++;
    }
    while (end > begin && value.charAt(end - 1) == ' ') {
      end--;
    }
    if (!NUMERIC.matcher(value.substring(begin, end)).matches()) {
      throw new IllegalArgumentException("A NUMERIC parameter is a decimal number: " + value);
    }
    if (value.length() > 16) {
      throw new IllegalArgumentException("A NUMERIC parameter is at most 16 bytes: " + value);
    }
  }

  /**
   * Splits a CODE value, which is "value" or "scheme:value". Only the first colon separates them,
   * so a value may itself carry colons; a bare value uses the local 99CLARUS scheme. Both
   * components must be non-empty (PS3.3 8.8): an empty Code Value or scheme reads as no code.
   */
  private static String[] splitCode(String value) {
    int colon = value.indexOf(':');
    if (colon < 0) {
      if (value.isEmpty()) {
        throw new IllegalArgumentException("A CODE parameter has a non-empty value");
      }
      return new String[] {SCHEME, value};
    }
    String scheme = value.substring(0, colon);
    String codeValue = value.substring(colon + 1);
    if (scheme.isEmpty() || codeValue.isEmpty()) {
      throw new IllegalArgumentException(
          "A CODE parameter is scheme:value, neither empty: " + value);
    }
    return new String[] {scheme, codeValue};
  }
}
