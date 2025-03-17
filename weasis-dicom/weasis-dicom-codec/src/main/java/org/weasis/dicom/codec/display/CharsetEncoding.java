/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import org.dcm4che3.data.SpecificCharacterSet;

public enum CharsetEncoding {
  ISO_IR_192("ISO_IR 192", "UTF-8", "Default - Unicode"),
  ASCII("", "US-ASCII", "ASCII"),
  ISO_IR_100("ISO_IR 100", "ISO-8859-1", "Western European (Latin-1)"),
  ISO_IR_101("ISO_IR 101", "ISO-8859-2", "Central European (Latin-2)"),
  ISO_IR_109("ISO_IR 109", "ISO-8859-3", "Southern European (Latin-3)"),
  ISO_IR_110("ISO_IR 110", "ISO-8859-4", "Northern European (Latin-4)"),
  ISO_IR_144("ISO_IR 144", "ISO-8859-5", "Cyrillic"),
  ISO_IR_127("ISO_IR 127", "ISO-8859-6", "Arabic"),
  ISO_IR_126("ISO_IR 126", "ISO-8859-7", "Greek"),
  ISO_IR_138("ISO_IR 138", "ISO-8859-8", "Hebrew"),
  ISO_IR_148("ISO_IR 148", "ISO-8859-9", "Turkish"),
  ISO_IR_13("ISO_IR 13", "JIS_X0201", "Japanese (Kana)"),
  ISO_IR_166("ISO_IR 166", "TIS-620", "Thai"),
  ISO_2022_IR_87("ISO 2022 IR 87", "x-JIS0208", "Japanese Kanji"),
  ISO_2022_IR_159("ISO 2022 IR 159", "JIS_X0212-1990", "Japanese Kanji Supplement"),
  ISO_2022_IR_149("ISO 2022 IR 149", "EUC-KR", "Korean"),
  ISO_2022_IR_58("ISO 2022 IR 58", "GB2312", "Simplified Chinese"),
  GB18030("GB18030", "GB18030", "Chinese");

  private final String label;
  private final String code;
  private final String readableText;

  CharsetEncoding(String label, String code, String readableText) {
    this.label = label;
    this.code = code;
    this.readableText = readableText;
  }

  public String getLabel() {
    return label;
  }

  public String getCode() {
    return code;
  }

  public String getReadableText() {
    return readableText;
  }

  public SpecificCharacterSet getSpecificCharacterSet() {
    return SpecificCharacterSet.valueOf(label);
  }

  /**
   * Find the enum value based on its label.
   *
   * @param label the label to search for.
   * @return the corresponding Encoding, or null if not found.
   */
  public static CharsetEncoding fromLabel(String label) {
    for (CharsetEncoding encoding : values()) {
      if (encoding.label.equals(label)) {
        return encoding;
      }
    }
    return null;
  }

  /**
   * Find the enum value based on its code.
   *
   * @param code the code to search for.
   * @return the corresponding Encoding, or null if not found.
   */
  public static CharsetEncoding fromCode(String code) {
    for (CharsetEncoding encoding : values()) {
      if (encoding.code.equals(code)) {
        return encoding;
      }
    }
    return ISO_IR_192;
  }

  @Override
  public String toString() {
    return readableText + " (" + code + ")";
  }
}
