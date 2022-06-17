/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Fail;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

class TagWTest {

  public static final int ID_1 = 123;
  public static final int ID_2 = 234;

  public static final int VM_MIN_1 = 10;
  public static final int VM_MIN_2 = -10;

  public static final int VM_MAX_1 = 20;
  public static final int VM_MAX_2 = -20;

  public static final String DISPLAY_NAME_1 = "lorem ipsum"; // NON-NLS
  public static final String KEYWORD_1 = "TagWTest";
  public static final String KEYWORD_2 = "TagWTestSecond";

  public static final String STRING_VALUE_1 = "a string value"; // NON-NLS
  public static final Integer INTEGER_VALUE_1 = 123456789;
  public static final String[] STRING_ARRAY = {"1", "2"}; // NON-NLS
  public static final Object[] OBJECT_ARRAY = {"1", Boolean.TRUE};

  public static final String[] RESPONSE_STRING_ARRAY = {"Lorem", "ipsum", "dolor"}; // NON-NLS
  public static final String RESPONSE_STRING = "Lorem Ipsum Dolor sit amet"; // NON-NLS

  private TagW tag;

  @Test
  void test_constructors() {
    try {
      new TagW(
          ID_1,
          KEYWORD_1,
          DISPLAY_NAME_1,
          null,
          VM_MIN_2,
          VM_MAX_2,
          new String[] {"value1", "value2"}); // NON-NLS
      Fail.fail("Must throws an exception"); // NON-NLS
    } catch (Exception e) {
      assertThat(e)
          .isExactlyInstanceOf(IllegalArgumentException.class)
          .hasMessage("defaultValue is not compliant to the tag type"); // NON-NLS
    }

    tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, TagType.STRING, VM_MIN_1, VM_MAX_1, null);
    assertThat(tag).isNotNull();
    assertThat(tag.getId()).isEqualTo(ID_1);
    assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
    assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo(DISPLAY_NAME_1);
    assertThat(tag.getType()).isEqualTo(TagType.STRING);
    assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(VM_MAX_1);
    assertThat(tag.vmMin).isEqualTo(VM_MIN_1);

    tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, null, VM_MIN_2, VM_MAX_2, null);
    assertThat(tag).isNotNull();
    assertThat(tag.getId()).isEqualTo(ID_1);
    assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
    assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo(DISPLAY_NAME_1);
    assertThat(tag.getType()).isEqualTo(TagType.STRING);
    assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(1);
    assertThat(tag.vmMin).isEqualTo(1);

    tag = new TagW(ID_2, KEYWORD_1, TagType.STRING, VM_MIN_1, VM_MAX_1);
    assertThat(tag).isNotNull();
    assertThat(tag.getId()).isEqualTo(ID_2);
    assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
    assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test"); // NON-NLS
    assertThat(tag.getType()).isEqualTo(TagType.STRING);
    assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(VM_MAX_1);
    assertThat(tag.vmMin).isEqualTo(VM_MIN_1);

    tag = new TagW(ID_2, KEYWORD_1, TagType.BOOLEAN);
    assertThat(tag).isNotNull();
    assertThat(tag.getId()).isEqualTo(ID_2);
    assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
    assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test"); // NON-NLS
    assertThat(tag.getType()).isEqualTo(TagType.BOOLEAN);
    assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(1);
    assertThat(tag.vmMin).isEqualTo(1);
  }

  @Test
  void test_isTypeCompliant() {
    tag = new TagW(KEYWORD_1, TagType.STRING);

    assertThat(tag.isTypeCompliant(null)).isTrue();
    assertThat(tag.isTypeCompliant(STRING_VALUE_1)).isTrue();
    assertThat(tag.isTypeCompliant(INTEGER_VALUE_1)).isFalse();
    assertThat(tag.isTypeCompliant(OBJECT_ARRAY)).isFalse();

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, VM_MAX_1);
    assertThat(tag.isTypeCompliant(null)).isTrue();
    assertThat(tag.isTypeCompliant(STRING_VALUE_1)).isTrue();
    assertThat(tag.isTypeCompliant(INTEGER_VALUE_1)).isFalse();
    assertThat(tag.isTypeCompliant(OBJECT_ARRAY)).isFalse();
    assertThat(tag.isTypeCompliant(STRING_ARRAY)).isFalse();

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, STRING_ARRAY.length);
    assertThat(tag.isTypeCompliant(null)).isTrue();
    assertThat(tag.isTypeCompliant(STRING_VALUE_1)).isTrue();
    assertThat(tag.isTypeCompliant(INTEGER_VALUE_1)).isFalse();
    assertThat(tag.isTypeCompliant(OBJECT_ARRAY)).isFalse();
    assertThat(tag.isTypeCompliant(STRING_ARRAY)).isTrue();

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, Integer.MAX_VALUE);
    assertThat(tag.isTypeCompliant(null)).isTrue();
    assertThat(tag.isTypeCompliant(STRING_VALUE_1)).isTrue();
    assertThat(tag.isTypeCompliant(INTEGER_VALUE_1)).isFalse();
    assertThat(tag.isTypeCompliant(OBJECT_ARRAY)).isFalse();
    assertThat(tag.isTypeCompliant(STRING_ARRAY)).isTrue();
  }

  @Test
  void test_getValueMultiplicity_with_object() {
    assertThat(TagW.getValueMultiplicity(null)).isZero();

    assertThat(TagW.getValueMultiplicity(new String[0])).isZero();
    assertThat(TagW.getValueMultiplicity(OBJECT_ARRAY)).isEqualTo(2);
    assertThat(TagW.getValueMultiplicity(STRING_ARRAY)).isEqualTo(2);

    assertThat(TagW.getValueMultiplicity(INTEGER_VALUE_1)).isEqualTo(1);
    assertThat(TagW.getValueMultiplicity(STRING_VALUE_1)).isEqualTo(1);
  }

  @Test
  void test_getValueFromIndex() {
    assertThat(TagW.getValueFromIndex(null, -1)).isNull();
    assertThat(TagW.getValueFromIndex(null, 0)).isNull();
    assertThat(TagW.getValueFromIndex(null, 1)).isNull();

    assertThat(TagW.getValueFromIndex(STRING_VALUE_1, -1)).isEqualTo(STRING_VALUE_1);
    assertThat(TagW.getValueFromIndex(STRING_VALUE_1, 0)).isEqualTo(STRING_VALUE_1);
    assertThat(TagW.getValueFromIndex(STRING_VALUE_1, 1)).isEqualTo(STRING_VALUE_1);

    assertThat(TagW.getValueFromIndex(STRING_ARRAY, -1)).isNull();
    assertThat(TagW.getValueFromIndex(STRING_ARRAY, 0)).isEqualTo(STRING_ARRAY[0]);
    assertThat(TagW.getValueFromIndex(STRING_ARRAY, 1)).isEqualTo(STRING_ARRAY[1]);
    assertThat(TagW.getValueFromIndex(STRING_ARRAY, 2)).isNull();
  }

  @Test
  void test_isStringFamilyType() {
    assertThat(new TagW(KEYWORD_1, TagType.BOOLEAN).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.BYTE).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.COLOR).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.DATE).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.DATETIME).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.DOUBLE).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.FLOAT).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.INTEGER).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.LIST).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.OBJECT).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.THUMBNAIL).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.TIME).isStringFamilyType()).isFalse();
    assertThat(new TagW(KEYWORD_1, TagType.STRING).isStringFamilyType()).isTrue();
    assertThat(new TagW(KEYWORD_1, TagType.TEXT).isStringFamilyType()).isTrue();
    assertThat(new TagW(KEYWORD_1, TagType.URI).isStringFamilyType()).isTrue();
  }

  @Test
  void test_equals() {
    TagW t1, t2;

    t1 = new TagW(KEYWORD_1, TagType.STRING);
    t2 = new TagW(KEYWORD_1, TagType.STRING);
    assertThat(t1).isNotEqualTo(t2);

    t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    assertThat(t1.equals(t2)).isFalse();

    t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_2, TagType.DICOM_PERSON_NAME);
    assertThat(t1.equals(t2)).isFalse();

    t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    assertThat(t1.equals(t2)).isTrue();

    t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    assertThat(t1.equals(t2)).isTrue();
  }

  @Test
  void test_hashCode() {
    assertThat(new TagW(ID_1, KEYWORD_1, TagType.STRING).hashCode()).isEqualTo(-707519243);
    assertThat(new TagW(ID_1, null, TagType.STRING).hashCode()).isEqualTo(4774);
  }

  @Test
  void test_getFormattedText() {
    assertThat(TagW.getFormattedText(null, null)).isEmpty();
    assertThat(TagW.getFormattedText("", null)).isEmpty();
    assertThat(TagW.getFormattedText(STRING_VALUE_1, null)).isEqualTo(STRING_VALUE_1);
    assertThat(TagW.getFormattedText(RESPONSE_STRING_ARRAY, null))
        .isEqualTo("Lorem\\ipsum\\dolor"); // NON-NLS

    float[] floatValues = {1.23f, 4.56f, 7.89f};
    assertThat(TagW.getFormattedText(floatValues, null)).isEqualTo("1.23, 4.56, 7.89");

    double[] doubleValues = {9.8765d, 4.3210d};
    assertThat(TagW.getFormattedText(doubleValues, null)).isEqualTo("9.8765, 4.321");

    int[] intValues = {1234, 567, 890};
    assertThat(TagW.getFormattedText(intValues, null)).isEqualTo("1234, 567, 890");

    assertThat(TagW.getFormattedText(Boolean.TRUE, null)).isEqualTo(Boolean.TRUE.toString());
  }

  @Test
  void test_getFormattedText_with_pattern() {
    String value = "Lorem Ipsum"; // NON-NLS
    assertThat(TagW.getFormattedText(value, "")).isEqualTo(value);
    assertThat(TagW.getFormattedText(value, "$V")).isEqualTo(value);
    assertThat(TagW.getFormattedText(value, "$V  ")).isEqualTo(value); // NON-NLS
    assertThat(TagW.getFormattedText(value, "  $V")).isEqualTo(value); // NON-NLS
    assertThat(TagW.getFormattedText(value, "test: $V")).isEqualTo("test: " + value); // NON-NLS
    assertThat(TagW.getFormattedText(value, "test: $V and $V")) // NON-NLS
        .isEqualTo("test: " + value + " and $V"); // NON-NLS

    assertThat(TagW.getFormattedText(STRING_ARRAY, "test: $V and $V plus $V")) // NON-NLS
        .isEqualTo(
            "test: " + STRING_ARRAY[0] + "\\" + STRING_ARRAY[1] + " and $V plus $V"); // NON-NLS
  }
}
