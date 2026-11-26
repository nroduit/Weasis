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
 * prefix (PS3.3 8.2): 99CLARUS, the designator the tasks already use. The sequence is Type 3, so a
 * workitem without parameters omits it rather than carrying an empty one.
 */
final class UpsParameters {

  /** The local coding scheme of the parameter names (PS3.3 8.2, private 99 prefix). */
  static final String SCHEME = "99CLARUS"; // NON-NLS

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
    }
  }

  /** The Content Item of one parameter, as a dataset of the (0074,1210) sequence. */
  static Attributes item(Parameter parameter) {
    Attributes item = new Attributes();
    item.setString(Tag.ValueType, VR.CS, parameter.type());
    // The Concept Name Code Sequence names the parameter with one Code; its meaning is the name
    // itself because a local parameter carries no separate display text yet.
    Attributes name = new Attributes();
    name.setString(Tag.CodeValue, VR.SH, parameter.name());
    name.setString(Tag.CodingSchemeDesignator, VR.SH, SCHEME);
    name.setString(Tag.CodeMeaning, VR.LO, parameter.name());
    item.newSequence(Tag.ConceptNameCodeSequence, 1).add(name);
    switch (parameter.type()) {
      case "NUMERIC": // NON-NLS
        item.setString(Tag.NumericValue, VR.DS, parameter.value());
        break;
      case "TEXT": // NON-NLS
        item.setString(Tag.TextValue, VR.UT, parameter.value());
        break;
      case "CODE": // NON-NLS
        String[] code = splitCode(parameter.value());
        Attributes value = new Attributes();
        value.setString(Tag.CodeValue, VR.SH, code[1]);
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
   * Splits a CODE value, which is "value" or "scheme:value". Only the first colon separates them,
   * so a value may itself carry colons; a bare value uses the local 99CLARUS scheme.
   */
  private static String[] splitCode(String value) {
    int colon = value.indexOf(':');
    if (colon < 0) {
      return new String[] {SCHEME, value};
    }
    return new String[] {value.substring(0, colon), value.substring(colon + 1)};
  }
}
