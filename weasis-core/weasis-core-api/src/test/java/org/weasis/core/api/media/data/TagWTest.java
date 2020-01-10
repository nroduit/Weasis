/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.weasis.core.api.media.data.TagW.TagType;

public class TagWTest {

    public static final int ID_1 = 123;
    public static final int ID_2 = 234;

    public static final int VM_MIN_1 = 10;
    public static final int VM_MIN_2 = -10;

    public static final int VM_MAX_1 = 20;
    public static final int VM_MAX_2 = -20;

    public static final String DISPLAY_NAME_1 = "lorem ipsum"; //$NON-NLS-1$
    public static final String KEYWORD_1 = "TagWTest"; //$NON-NLS-1$
    public static final String KEYWORD_2 = "TagWTestSecond"; //$NON-NLS-1$

    public static final String STRING_VALUE_1 = "a string value"; //$NON-NLS-1$
    public static final Integer INTEGER_VALUE_1 = 123456789;
    public static final String[] STRING_ARRAY = { "1", "2" }; //$NON-NLS-1$ //$NON-NLS-2$
    public static final Object[] OBJECT_ARRAY = { "1", Boolean.TRUE }; //$NON-NLS-1$

    public static final String[] RESPONSE_STRING_ARRAY = { "Lorem", "ipsum", "dolor" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    public static final String RESPONSE_STRING = "Lorem Ipsum Dolor sit amet"; //$NON-NLS-1$

    private TagW tag;

    @Before
    public void setUp() {

    }

    @Test
    public void test_constructors() throws Exception {
        try {
            new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, null, VM_MIN_2, VM_MAX_2, new String[] { "value1", "value2" }); //$NON-NLS-1$ //$NON-NLS-2$
            Assert.fail("Must throws an exception"); //$NON-NLS-1$
        } catch (Exception e) {
            assertThat(e).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultValue is not compliant to the tag type"); //$NON-NLS-1$
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
        assertThat(tag).isNotNull().hashCode();
        assertThat(tag.getId()).isEqualTo(ID_2);
        assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
        assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test"); //$NON-NLS-1$
        assertThat(tag.getType()).isEqualTo(TagType.STRING);
        assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(VM_MAX_1);
        assertThat(tag.vmMin).isEqualTo(VM_MIN_1);

        tag = new TagW(ID_2, KEYWORD_1, TagType.BOOLEAN);
        assertThat(tag).isNotNull().hashCode();
        assertThat(tag.getId()).isEqualTo(ID_2);
        assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
        assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test"); //$NON-NLS-1$
        assertThat(tag.getType()).isEqualTo(TagType.BOOLEAN);
        assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(1);
        assertThat(tag.vmMin).isEqualTo(1);
    }

    @Test
    public void test_isTypeCompliant() throws Exception {
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
    public void test_getValueMultiplicity_with_object() throws Exception {
        assertThat(TagW.getValueMultiplicity(null)).isEqualTo(0);

        assertThat(TagW.getValueMultiplicity(new String[0])).isEqualTo(0);
        assertThat(TagW.getValueMultiplicity(OBJECT_ARRAY)).isEqualTo(2);
        assertThat(TagW.getValueMultiplicity(STRING_ARRAY)).isEqualTo(2);

        assertThat(TagW.getValueMultiplicity(INTEGER_VALUE_1)).isEqualTo(1);
        assertThat(TagW.getValueMultiplicity(STRING_VALUE_1)).isEqualTo(1);
    }

    @Test
    public void test_getValueFromIndex() throws Exception {
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
    public void test_isStringFamilyType() throws Exception {
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
    public void test_equals() throws Exception {
        TagW t1, t2;

        t1 = new TagW(KEYWORD_1, TagType.STRING);
        assertThat(t1.equals(t1)).isTrue();
        assertThat(t1.equals(null)).isFalse();
        assertThat(t1.equals(KEYWORD_1)).isFalse();

        t1 = new TagW(KEYWORD_1, TagType.STRING);
        t2 = new TagW(KEYWORD_1, TagType.STRING);
        assertThat(t1.equals(t2)).isFalse();

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
    public void test_hashCode() throws Exception {
        assertThat(new TagW(ID_1, KEYWORD_1, TagType.STRING).hashCode()).isEqualTo(-707519243);
        assertThat(new TagW(ID_1, null, TagType.STRING).hashCode()).isEqualTo(4774);

    }

    @Test
    public void test_getFormattedText() throws Exception {
        assertThat(TagW.getFormattedText(null, null)).isEmpty();
        assertThat(TagW.getFormattedText("", null)).isEmpty(); //$NON-NLS-1$
        assertThat(TagW.getFormattedText(STRING_VALUE_1, null)).isEqualTo(STRING_VALUE_1);
        assertThat(TagW.getFormattedText(RESPONSE_STRING_ARRAY, null)).isEqualTo("Lorem\\ipsum\\dolor"); //$NON-NLS-1$

        float[] floatValues = { 1.23f, 4.56f, 7.89f };
        assertThat(TagW.getFormattedText(floatValues, null)).isEqualTo("1.23, 4.56, 7.89"); //$NON-NLS-1$

        double[] doubleValues = { 9.8765d, 4.3210d };
        assertThat(TagW.getFormattedText(doubleValues, null)).isEqualTo("9.8765, 4.321"); //$NON-NLS-1$

        int[] intValues = { 1234, 567, 890 };
        assertThat(TagW.getFormattedText(intValues, null)).isEqualTo("1234, 567, 890"); //$NON-NLS-1$

        assertThat(TagW.getFormattedText(Boolean.TRUE, null)).isEqualTo(Boolean.TRUE.toString());
    }

    @Test
    public void test_getFormattedText_with_pattern() throws Exception {
        String value = "Lorem Ipsum"; //$NON-NLS-1$
        assertThat(TagW.getFormattedText(value, "")).isEqualTo(value); //$NON-NLS-1$
        assertThat(TagW.getFormattedText(value, "$V")).isEqualTo(value); //$NON-NLS-1$
        assertThat(TagW.getFormattedText(value, "$V  ")).isEqualTo(value); //$NON-NLS-1$
        assertThat(TagW.getFormattedText(value, "  $V")).isEqualTo(value); //$NON-NLS-1$
        assertThat(TagW.getFormattedText(value, "test: $V")).isEqualTo("test: " + value); //$NON-NLS-1$ //$NON-NLS-2$
        assertThat(TagW.getFormattedText(value, "test: $V and $V")).isEqualTo("test: " + value + " and $V"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        assertThat(TagW.getFormattedText(STRING_ARRAY, "test: $V and $V plus $V")) //$NON-NLS-1$
            .isEqualTo("test: " + STRING_ARRAY[0] + "\\" + STRING_ARRAY[1] + " and $V plus $V"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

}
