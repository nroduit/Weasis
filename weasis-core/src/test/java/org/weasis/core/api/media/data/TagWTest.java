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

import static org.junit.jupiter.api.Assertions.*;

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

  private TagW tag;

  @Test
  void test_constructors() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new TagW(
                    ID_1,
                    KEYWORD_1,
                    DISPLAY_NAME_1,
                    null,
                    VM_MIN_2,
                    VM_MAX_2,
                    new String[] {"value1", "value2"}), // NON-NLS
            "Expected new TagW() to throw, but it didn't");
    assertTrue(thrown.getMessage().contains("defaultValue is not compliant to the tag type"));

    tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, TagType.STRING, VM_MIN_1, VM_MAX_1, null);
    assertNotNull(tag);
    assertEquals(ID_1, tag.getId());
    assertEquals(KEYWORD_1, tag.getKeyword());
    assertEquals(DISPLAY_NAME_1, tag.getDisplayedName());
    assertEquals(DISPLAY_NAME_1, tag.toString());
    assertEquals(TagType.STRING, tag.getType());
    assertEquals(VM_MAX_1, tag.getValueMultiplicity());
    assertEquals(VM_MAX_1, tag.vmMax);
    assertEquals(VM_MIN_1, tag.vmMin);

    tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, null, VM_MIN_2, VM_MAX_2, null);
    assertNotNull(tag);
    assertEquals(ID_1, tag.getId());
    assertEquals(KEYWORD_1, tag.getKeyword());
    assertEquals(DISPLAY_NAME_1, tag.getDisplayedName());
    assertEquals(DISPLAY_NAME_1, tag.toString());
    assertEquals(TagType.STRING, tag.getType());
    assertEquals(1, tag.getValueMultiplicity());
    assertEquals(1, tag.vmMax);
    assertEquals(1, tag.vmMin);

    tag = new TagW(ID_2, KEYWORD_1, TagType.STRING, VM_MIN_1, VM_MAX_1);
    assertNotNull(tag);
    assertEquals(ID_2, tag.getId());
    assertEquals(KEYWORD_1, tag.getKeyword());
    assertEquals("Tag W Test", tag.getDisplayedName()); // NON-NLS
    assertEquals("Tag W Test", tag.toString()); // NON-NLS
    assertEquals(TagType.STRING, tag.getType());
    assertEquals(VM_MAX_1, tag.getValueMultiplicity());
    assertEquals(VM_MAX_1, tag.vmMax);
    assertEquals(VM_MIN_1, tag.vmMin);

    tag = new TagW(ID_2, KEYWORD_1, TagType.BOOLEAN);
    assertNotNull(tag);
    assertEquals(ID_2, tag.getId());
    assertEquals(KEYWORD_1, tag.getKeyword());
    assertEquals("Tag W Test", tag.getDisplayedName()); // NON-NLS
    assertEquals("Tag W Test", tag.toString()); // NON-NLS
    assertEquals(TagType.BOOLEAN, tag.getType());
    assertEquals(1, tag.getValueMultiplicity());
    assertEquals(1, tag.vmMax);
    assertEquals(1, tag.vmMin);
  }

  @Test
  void test_isTypeCompliant() {
    tag = new TagW(KEYWORD_1, TagType.STRING);

    assertTrue(tag.isTypeCompliant(null));
    assertTrue(tag.isTypeCompliant(STRING_VALUE_1));
    assertFalse(tag.isTypeCompliant(INTEGER_VALUE_1));
    assertFalse(tag.isTypeCompliant(OBJECT_ARRAY));

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, VM_MAX_1);
    assertTrue(tag.isTypeCompliant(null));
    assertTrue(tag.isTypeCompliant(STRING_VALUE_1));
    assertFalse(tag.isTypeCompliant(INTEGER_VALUE_1));
    assertFalse(tag.isTypeCompliant(OBJECT_ARRAY));
    assertFalse(tag.isTypeCompliant(STRING_ARRAY));

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, STRING_ARRAY.length);
    assertTrue(tag.isTypeCompliant(null));
    assertTrue(tag.isTypeCompliant(STRING_VALUE_1));
    assertFalse(tag.isTypeCompliant(INTEGER_VALUE_1));
    assertFalse(tag.isTypeCompliant(OBJECT_ARRAY));
    assertTrue(tag.isTypeCompliant(STRING_ARRAY));

    tag = new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, Integer.MAX_VALUE);
    assertTrue(tag.isTypeCompliant(null));
    assertTrue(tag.isTypeCompliant(STRING_VALUE_1));
    assertFalse(tag.isTypeCompliant(INTEGER_VALUE_1));
    assertFalse(tag.isTypeCompliant(OBJECT_ARRAY));
    assertTrue(tag.isTypeCompliant(STRING_ARRAY));
  }

  @Test
  void test_getValueMultiplicity_with_object() {
    assertEquals(0, TagW.getValueMultiplicity(null));

    assertEquals(0, TagW.getValueMultiplicity(new String[0]));
    assertEquals(2, TagW.getValueMultiplicity(OBJECT_ARRAY));
    assertEquals(2, TagW.getValueMultiplicity(STRING_ARRAY));

    assertEquals(1, TagW.getValueMultiplicity(INTEGER_VALUE_1));
    assertEquals(1, TagW.getValueMultiplicity(STRING_VALUE_1));
  }

  @Test
  void test_getValueFromIndex() {
    assertNull(TagW.getValueFromIndex(null, -1));
    assertNull(TagW.getValueFromIndex(null, 0));
    assertNull(TagW.getValueFromIndex(null, 1));

    assertEquals(STRING_VALUE_1, TagW.getValueFromIndex(STRING_VALUE_1, -1));
    assertEquals(STRING_VALUE_1, TagW.getValueFromIndex(STRING_VALUE_1, 0));
    assertEquals(STRING_VALUE_1, TagW.getValueFromIndex(STRING_VALUE_1, 1));

    assertNull(TagW.getValueFromIndex(STRING_ARRAY, -1));
    assertEquals(STRING_ARRAY[0], TagW.getValueFromIndex(STRING_ARRAY, 0));
    assertEquals(STRING_ARRAY[1], TagW.getValueFromIndex(STRING_ARRAY, 1));
    assertNull(TagW.getValueFromIndex(STRING_ARRAY, 2));
  }

  @Test
  void test_isStringFamilyType() {
    assertFalse(new TagW(KEYWORD_1, TagType.BOOLEAN).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.BYTE).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.COLOR).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.DATE).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.DATETIME).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.DOUBLE).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.FLOAT).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.INTEGER).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.LIST).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.OBJECT).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.THUMBNAIL).isStringFamilyType());
    assertFalse(new TagW(KEYWORD_1, TagType.TIME).isStringFamilyType());
    assertTrue(new TagW(KEYWORD_1, TagType.STRING).isStringFamilyType());
    assertTrue(new TagW(KEYWORD_1, TagType.TEXT).isStringFamilyType());
    assertTrue(new TagW(KEYWORD_1, TagType.URI).isStringFamilyType());
  }

  @Test
  void test_equals() {
    TagW t1, t2;

    t1 = new TagW(KEYWORD_1, TagType.STRING);
    t2 = new TagW(KEYWORD_1, TagType.STRING);
    assertNotEquals(t2, t1);

    t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    assertNotEquals(t1, t2);

    t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_2, TagType.DICOM_PERSON_NAME);
    assertNotEquals(t1, t2);

    t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
    assertEquals(t1, t2);

    t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    t2 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
    assertEquals(t1, t2);
  }

  @Test
  void test_hashCode() {
    assertEquals(-707519243, new TagW(ID_1, KEYWORD_1, TagType.STRING).hashCode());
    assertEquals(4774, new TagW(ID_1, null, TagType.STRING).hashCode());
  }

  @Test
  void test_getFormattedText() {
    assertTrue(TagW.getFormattedText(null, null).isEmpty());
    assertTrue(TagW.getFormattedText("", null).isEmpty());
    assertEquals(STRING_VALUE_1, TagW.getFormattedText(STRING_VALUE_1, null));
    assertEquals("Lorem\\ipsum\\dolor", TagW.getFormattedText(RESPONSE_STRING_ARRAY, null));

    float[] floatValues = {1.23f, 4.56f, 7.89f};
    assertEquals("1.23, 4.56, 7.89", TagW.getFormattedText(floatValues, null));

    double[] doubleValues = {9.8765d, 4.3210d};
    assertEquals("9.8765, 4.321", TagW.getFormattedText(doubleValues, null));

    int[] intValues = {1234, 567, 890};
    assertEquals("1234, 567, 890", TagW.getFormattedText(intValues, null));

    assertEquals(Boolean.TRUE.toString(), TagW.getFormattedText(Boolean.TRUE, null));
  }

  @Test
  void test_getFormattedText_with_pattern() {
    String value = "Lorem Ipsum"; // NON-NLS
    assertEquals(value, TagW.getFormattedText(value, ""));
    assertEquals(value, TagW.getFormattedText(value, "$V"));
    assertEquals(value, TagW.getFormattedText(value, "$V  ")); // NON-NLS
    assertEquals(value, TagW.getFormattedText(value, "  $V")); // NON-NLS
    assertEquals("test: " + value, TagW.getFormattedText(value, "test: $V")); // NON-NLS
    assertEquals(
        "test: " + value + " and $V", TagW.getFormattedText(value, "test: $V and $V")); // NON-NLS

    assertEquals(
        "test: " + STRING_ARRAY[0] + "\\" + STRING_ARRAY[1] + " and $V plus $V",
        TagW.getFormattedText(STRING_ARRAY, "test: $V and $V plus $V")); // NON-NLS
  }
}
