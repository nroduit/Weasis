/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.TagD;

class DicomMediaUtilsTest {
  public static final String[] STRING_ARRAY = {"RECTANGULAR", "CIRCULAR", "POLYGONAL"}; // NON-NLS

  private static final Attributes attributes = new Attributes();

  @BeforeAll
  static void setup() {
    attributes.setString(Tag.ShutterShape, VR.CS, STRING_ARRAY);
  }

  @Test
  void testGetPeriod() {
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("19610625"), TagD.getDicomDate("20120624")))
        .isEqualTo("050Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("19610625"), TagD.getDicomDate("20120625")))
        .isEqualTo("051Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("19610714"), TagD.getDicomDate("20120625")))
        .isEqualTo("050Y");

    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20120103"), TagD.getDicomDate("20120625")))
        .isEqualTo("005M");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20120525"), TagD.getDicomDate("20120625")))
        .isEqualTo("031D");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20120622"), TagD.getDicomDate("20120625")))
        .isEqualTo("003D");

    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000229"), TagD.getDicomDate("20110301")))
        .isEqualTo("011Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000229"), TagD.getDicomDate("20110228")))
        .isEqualTo("010Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000229"), TagD.getDicomDate("20120228")))
        .isEqualTo("011Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000229"), TagD.getDicomDate("20120229")))
        .isEqualTo("012Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000229"), TagD.getDicomDate("20120301")))
        .isEqualTo("012Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000228"), TagD.getDicomDate("20120228")))
        .isEqualTo("012Y");
    assertThat(
            DicomMediaUtils.getPeriod(TagD.getDicomDate("20000228"), TagD.getDicomDate("20120229")))
        .isEqualTo("012Y");

    LocalDate date1 = TagD.getDicomDate("20000228");
    LocalDate date2 = TagD.getDicomDate("20122406"); // invalid => null
    assertThatThrownBy(() -> DicomMediaUtils.getPeriod(date1, date2))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void testGetStringFromDicomElement() {
    assertThat(DicomMediaUtils.getStringFromDicomElement(attributes, Tag.ShutterShape))
        .isEqualTo("RECTANGULAR\\CIRCULAR\\POLYGONAL");
    assertThat(DicomMediaUtils.getStringFromDicomElement(attributes, Tag.ShutterPresentationValue))
        .isNull();
  }

  @Test
  void testGetStringArrayFromDicomElementAttributesInt() {
    assertThat(DicomMediaUtils.getStringArrayFromDicomElement(attributes, Tag.ShutterShape))
        .containsExactly(STRING_ARRAY);
    assertThat(
            DicomMediaUtils.getStringArrayFromDicomElement(
                attributes, Tag.ShutterPresentationValue))
        .isNull();
  }
}
