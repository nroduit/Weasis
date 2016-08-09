package org.weasis.core.api.media.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import javax.xml.stream.XMLStreamReader;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.LocalUtil;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ TagUtil.class, LocalUtil.class })
public class TagWTest {
    private TagW tag;

    public static final int ID_1 = 123;
    public static final int ID_2 = 234;

    public static final int VM_MIN_1 = 10;
    public static final int VM_MIN_2 = -10;

    public static final int VM_MAX_1 = 20;
    public static final int VM_MAX_2 = -20;

    public static final String DISPLAY_NAME_1 = "lorem ipsum";
    public static final String KEYWORD_1 = "TagWTest";
    public static final String KEYWORD_2 = "TagWTestSecond";

    public static final String STRING_VALUE_1 = "a string value";
    public static final Integer INTEGER_VALUE_1 = 123456789;
    public static final String[] STRING_ARRAY = { "1", "2" };
    public static final Object[] OBJECT_ARRAY = { "1", Boolean.TRUE };

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static final String[] RESPONSE_STRING_ARRAY = { "Lorem", "ipsum", "dolor" };
    public static final String RESPONSE_STRING = "Lorem Ipsum Dolor sit amet";

    public static final LocalDate[] RESPONSE_DATE_ARRAY = { mock(LocalDate.class), mock(LocalDate.class) };
    public static final LocalDate RESPONSE_DATE = mock(LocalDate.class);

    public static final LocalTime[] RESPONSE_TIME_ARRAY = { mock(LocalTime.class), mock(LocalTime.class), mock(LocalTime.class) };
    public static final LocalTime RESPONSE_TIME = mock(LocalTime.class);

    public static final LocalDateTime[] RESPONSE_DATETIME_ARRAY = { mock(LocalDateTime.class) };
    public static final LocalDateTime RESPONSE_DATETIME = mock(LocalDateTime.class);

    public static final int[] RESPONSE_INTEGER_ARRAY = { mock(Integer.class), mock(Integer.class) };
    public static final int RESPONSE_INTEGER = mock(Integer.class);

    public static final float[] RESPONSE_FLOAT_ARRAY = { mock(Float.class), mock(Float.class) };
    public static final float RESPONSE_FLOAT = mock(Float.class);

    public static final double[] RESPONSE_DOUBLE_ARRAY = { mock(Double.class), mock(Double.class) };
    public static final double RESPONSE_DOUBLE = mock(Double.class);

    public static final String RESPONSE_PERSON_NAME = mock(String.class);
    public static final String RESPONSE_PERSON_SEX = mock(String.class);
    public static final String RESPONSE_PERIOD = mock(String.class);
    
    public static final String DATE_STRING = mock(String.class);
    public static final String TIME_STRING = mock(String.class);
    public static final String DATETIME_STRING = mock(String.class);
    
    @Before
    public void setUp() {
        mockStatic(LocalUtil.class);
        when(LocalUtil.getDateInstance(anyInt())).thenReturn(DATE_FORMAT);
        when(LocalUtil.getLocaleFormat()).thenReturn(Locale.ENGLISH);

        mockStatic(TagUtil.class);
        when(TagUtil.getStringArrayTagAttribute(any(XMLStreamReader.class), anyString(), any()))
            .thenReturn(RESPONSE_STRING_ARRAY);
        when(TagUtil.getTagAttribute(any(XMLStreamReader.class), anyString(), any())).thenReturn(RESPONSE_STRING);
        when(TagUtil.getDatesFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.DATE), any()))
            .thenReturn(RESPONSE_DATE_ARRAY);
        when(TagUtil.getDateFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.DATE), any()))
            .thenReturn(RESPONSE_DATE);
        when(TagUtil.getDatesFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.TIME), any()))
            .thenReturn(RESPONSE_TIME_ARRAY);
        when(TagUtil.getDateFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.TIME), any()))
            .thenReturn(RESPONSE_TIME);
        when(TagUtil.getDatesFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.DATETIME), any()))
            .thenReturn(RESPONSE_DATETIME_ARRAY);
        when(TagUtil.getDateFromElement(any(XMLStreamReader.class), anyString(), eq(TagType.DATETIME), any()))
            .thenReturn(RESPONSE_DATETIME);
        when(TagUtil.getIntArrayTagAttribute(any(XMLStreamReader.class), anyString(), any()))
            .thenReturn(RESPONSE_INTEGER_ARRAY);
        when(TagUtil.getIntegerTagAttribute(any(XMLStreamReader.class), anyString(), any()))
            .thenReturn(RESPONSE_INTEGER);
        when(TagUtil.getFloatArrayTagAttribute(any(XMLStreamReader.class), anyString(), any()))
            .thenReturn(RESPONSE_FLOAT_ARRAY);
        when(TagUtil.getFloatTagAttribute(any(XMLStreamReader.class), anyString(), any())).thenReturn(RESPONSE_FLOAT);
        when(TagUtil.getDoubleArrayTagAttribute(any(XMLStreamReader.class), anyString(), any()))
            .thenReturn(RESPONSE_DOUBLE_ARRAY);
        when(TagUtil.getDoubleTagAttribute(any(XMLStreamReader.class), anyString(), any())).thenReturn(RESPONSE_DOUBLE);
        
        when(TagUtil.formatDateTime(any(LocalDate.class))).thenReturn(DATE_STRING);
        when(TagUtil.formatDateTime(any(LocalTime.class))).thenReturn(TIME_STRING);
        when(TagUtil.formatDateTime(any(LocalDateTime.class))).thenReturn(DATETIME_STRING);
     //   when(TagUtil.buildDicomPersonName(anyString())).thenReturn(RESPONSE_PERSON_NAME);
     //   when(TagUtil.buildDicomPatientSex(anyString())).thenReturn(RESPONSE_PERSON_SEX);
     //   when(TagUtil.getDicomPeriod(anyString())).thenReturn(RESPONSE_PERIOD);
    }

    @Test
    public void test_constructors() throws Exception {
        try {
            new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, null, VM_MIN_2, VM_MAX_2, new String[] { "value1", "value2" });
            Assert.fail("Must throws an exception");
        } catch (Exception e) {
            assertThat(e).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("defaultValue is not compliant to the tag type");
        }

        tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, TagType.STRING, VM_MIN_1, VM_MAX_1, null);
        assertThat(tag).isNotNull().hashCode();
        assertThat(tag.getId()).isEqualTo(ID_1);
        assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
        assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo(DISPLAY_NAME_1);
        assertThat(tag.getType()).isEqualTo(TagType.STRING);
        assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(VM_MAX_1);
        assertThat(tag.vmMin).isEqualTo(VM_MIN_1);

        tag = new TagW(ID_1, KEYWORD_1, DISPLAY_NAME_1, null, VM_MIN_2, VM_MAX_2, null);
        assertThat(tag).isNotNull().hashCode();
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
        assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test");
        assertThat(tag.getType()).isEqualTo(TagType.STRING);
        assertThat(tag.getValueMultiplicity()).isEqualTo(tag.vmMax).isEqualTo(VM_MAX_1);
        assertThat(tag.vmMin).isEqualTo(VM_MIN_1);

        tag = new TagW(ID_2, KEYWORD_1, TagType.BOOLEAN);
        assertThat(tag).isNotNull().hashCode();
        assertThat(tag.getId()).isEqualTo(ID_2);
        assertThat(tag.getKeyword()).isEqualTo(KEYWORD_1);
        assertThat(tag.getDisplayedName()).isEqualTo(tag.toString()).isEqualTo("Tag W Test");
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
 //       assertThat(new TagW(KEYWORD_1, TagType.DICOM_SEQUENCE).isStringFamilyType()).isFalse();
        assertThat(new TagW(KEYWORD_1, TagType.THUMBNAIL).isStringFamilyType()).isFalse();
        assertThat(new TagW(KEYWORD_1, TagType.TIME).isStringFamilyType()).isFalse();

//        assertThat(new TagW(KEYWORD_1, TagType.DICOM_PERIOD).isStringFamilyType()).isTrue();
//        assertThat(new TagW(KEYWORD_1, TagType.DICOM_PERSON_NAME).isStringFamilyType()).isTrue();
//        assertThat(new TagW(KEYWORD_1, TagType.DICOM_SEX).isStringFamilyType()).isTrue();
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

//        t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
//        t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
//        assertThat(t1.equals(t2)).isFalse();
//
//        t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
//        t2 = new TagW(ID_1, KEYWORD_2, TagType.DICOM_PERSON_NAME);
//        assertThat(t1.equals(t2)).isFalse();
//
//        t1 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
//        t2 = new TagW(ID_1, KEYWORD_1, TagType.DICOM_PERSON_NAME);
//        assertThat(t1.equals(t2)).isTrue();
//
//        t1 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
//        t2 = new TagW(ID_1, null, TagType.DICOM_PERSON_NAME);
//        assertThat(t1.equals(t2)).isTrue();
    }

    @Test
    public void test_hashCode() throws Exception {
        assertThat(new TagW(ID_1, KEYWORD_1, TagType.STRING).hashCode()).isEqualTo(-707519243);
        assertThat(new TagW(ID_1, null, TagType.STRING).hashCode()).isEqualTo(4774);

    }

    @Test
    public void test_getValue() throws Exception {
        tag = new TagW(KEYWORD_1, TagType.STRING);
        assertThat(tag.getValue(null)).isNull();
        assertThat(tag.getValue("a string value")).isNull();

        XMLStreamReader data = mock(XMLStreamReader.class);

        assertThat(new TagW(KEYWORD_1, TagType.STRING).getValue(data)).isEqualTo(RESPONSE_STRING);
        assertThat(new TagW(KEYWORD_1, TagType.STRING, VM_MIN_1, VM_MAX_1).getValue(data))
            .isEqualTo(RESPONSE_STRING_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.DATE).getValue(data)).isEqualTo(RESPONSE_DATE);
        assertThat(new TagW(KEYWORD_1, TagType.DATE, VM_MIN_1, VM_MAX_1).getValue(data)).isEqualTo(RESPONSE_DATE_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.TIME).getValue(data)).isEqualTo(RESPONSE_TIME);
        assertThat(new TagW(KEYWORD_1, TagType.TIME, VM_MIN_1, VM_MAX_1).getValue(data)).isEqualTo(RESPONSE_TIME_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.DATETIME).getValue(data)).isEqualTo(RESPONSE_DATETIME);
        assertThat(new TagW(KEYWORD_1, TagType.DATETIME, VM_MIN_1, VM_MAX_1).getValue(data))
            .isEqualTo(RESPONSE_DATETIME_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.INTEGER).getValue(data)).isEqualTo(RESPONSE_INTEGER);
        assertThat(new TagW(KEYWORD_1, TagType.INTEGER, VM_MIN_1, VM_MAX_1).getValue(data))
            .isEqualTo(RESPONSE_INTEGER_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.FLOAT).getValue(data)).isEqualTo(RESPONSE_FLOAT);
        assertThat(new TagW(KEYWORD_1, TagType.FLOAT, VM_MIN_1, VM_MAX_1).getValue(data))
            .isEqualTo(RESPONSE_FLOAT_ARRAY);

        assertThat(new TagW(KEYWORD_1, TagType.DOUBLE).getValue(data)).isEqualTo(RESPONSE_DOUBLE);
        assertThat(new TagW(KEYWORD_1, TagType.DOUBLE, VM_MIN_1, VM_MAX_1).getValue(data))
            .isEqualTo(RESPONSE_DOUBLE_ARRAY);

    //    assertThat(new TagW(KEYWORD_1, TagType.DICOM_SEQUENCE).getValue(data)).isEqualTo(RESPONSE_STRING);
    //    assertThat(new TagW(KEYWORD_1, TagType.DICOM_SEQUENCE, VM_MIN_1, VM_MAX_1).getValue(data)).isEqualTo(RESPONSE_STRING);

        assertThat(new TagW(KEYWORD_1, TagType.COLOR).getValue(data)).isEqualTo(RESPONSE_STRING);
        assertThat(new TagW(KEYWORD_1, TagType.COLOR, VM_MIN_1, VM_MAX_1).getValue(data)).isEqualTo(RESPONSE_STRING_ARRAY);
    }

    @Test
    public void test_getFormattedText() throws Exception {
        assertThat(TagW.getFormattedText(null,  null)).isEmpty();
        assertThat(TagW.getFormattedText("",  null)).isEmpty();
        assertThat(TagW.getFormattedText(STRING_VALUE_1,  null)).isEqualTo(STRING_VALUE_1);
        assertThat(TagW.getFormattedText(RESPONSE_STRING_ARRAY,  null)).isEqualTo("Lorem\\ipsum\\dolor");
                       
        LocalDate date = mock(LocalDate.class);
        assertThat(date).isNotNull();
        assertThat(TagW.getFormattedText(date,  null)).isEqualTo(DATE_STRING);
        PowerMockito.verifyStatic();
        TagUtil.formatDateTime(eq(date));
        
        LocalTime time = mock(LocalTime.class);
        assertThat(time).isNotNull();
        assertThat(TagW.getFormattedText(time,  null)).isEqualTo(TIME_STRING);
        PowerMockito.verifyStatic();
        TagUtil.formatDateTime(eq(time));
        
        LocalDateTime datetime = mock(LocalDateTime.class);
        assertThat(datetime).isNotNull();
        assertThat(TagW.getFormattedText(datetime,  null)).isEqualTo(DATETIME_STRING);
        PowerMockito.verifyStatic();
        TagUtil.formatDateTime(eq(datetime));
        
//        String personName = "John Doe";
//        assertThat(personName).isNotNull();
//        assertThat(TagW.getFormattedText(personName,  null)).isEqualTo(RESPONSE_PERSON_NAME);
//        PowerMockito.verifyStatic();
//        TagUtil.buildDicomPersonName(eq(personName));
//        
//        String sex = "M";
//        assertThat(sex).isNotNull();
//        assertThat(TagW.getFormattedText(sex,  null)).isEqualTo(RESPONSE_PERSON_SEX);
//        PowerMockito.verifyStatic();
//        TagUtil.buildDicomPatientSex(eq(sex));
//        
//        String period = "a period";
//        assertThat(period).isNotNull();
//        assertThat(TagW.getFormattedText(period,  null)).isEqualTo(RESPONSE_PERIOD);
//        PowerMockito.verifyStatic();
//        TagUtil.getDicomPeriod(eq(period));
        
        float[] floatValues = { 1.23f, 4.56f, 7.89f };
        assertThat(TagW.getFormattedText(floatValues,  null)).isEqualTo("1.23, 4.56, 7.89");
        
        double[] doubleValues = { 9.8765d, 4.3210d };
        assertThat(TagW.getFormattedText(doubleValues,  null)).isEqualTo("9.8765, 4.321");
        
        int[] intValues = { 1234, 567, 890 };
        assertThat(TagW.getFormattedText(intValues,  null)).isEqualTo("1234, 567, 890");
        
        assertThat(TagW.getFormattedText(Boolean.TRUE, null)).isEqualTo("true");
    }
    
    @Test
    public void test_getFormattedText_with_pattern() throws Exception {
        String value = "Lorem Ipsum";
        assertThat(TagW.getFormattedText(value,  "")).isEqualTo(value);
        assertThat(TagW.getFormattedText(value,  "$V")).isEqualTo(value);
        assertThat(TagW.getFormattedText(value,  "$V  ")).isEqualTo(value);
        assertThat(TagW.getFormattedText(value,  "  $V")).isEqualTo(value);
        assertThat(TagW.getFormattedText(value,  "test: $V")).isEqualTo("test: " + value);
        assertThat(TagW.getFormattedText(value,  "test: $V and $V")).isEqualTo("test: " + value + " and $V");
        
        assertThat(TagW.getFormattedText(STRING_ARRAY,  "test: $V and $V plus $V")).isEqualTo("test: " + STRING_ARRAY[0] + "\\" +STRING_ARRAY[1] + " and $V plus $V");
    }
    
    
    public void test_readValue() throws Exception {

    }

    public void testGetFormattedTextObject() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    public void testGetFormattedTextObjectString() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    public void testGetAnonymizationType() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    public void testSetAnonymizationType() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    

    public void testAddTag() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    public void testGet() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

    public void testGetTagValue() throws Exception {
        throw new RuntimeException("not yet implemented");
    }

}
