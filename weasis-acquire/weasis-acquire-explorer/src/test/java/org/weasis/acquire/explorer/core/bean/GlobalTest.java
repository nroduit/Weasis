package org.weasis.acquire.explorer.core.bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.weasis.acquire.explorer.core.bean.helper.GlobalHelper;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.dicom.codec.TagD;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ TagD.class, LocalUtil.class })
@SuppressStaticInitializationFor("org.weasis.dicom.codec.TagD")
public class GlobalTest extends GlobalHelper {
    private Global global;

    @Before
    public void setUp() throws Exception {
        global = null;
    
        xml = PowerMockito.mock(Document.class);
        patient = PowerMockito.mock(Element.class);
        
        PowerMockito.mockStatic(LocalUtil.class);
        PowerMockito.when(LocalUtil.getDateFormatter()).thenReturn(dateformat);
        PowerMockito.when(LocalUtil.getLocaleFormat()).thenReturn(Locale.ENGLISH);
        
        PowerMockito.mockStatic(TagD.class);
        Arrays.stream(GlobalTag.values()).forEach(e -> e.prepareMock());
    }

    @Test
    public void testGlobal() {
        assertThat(global).isNull();
        global = new Global();

        assertThat(global).isNotNull();
        assertThat(global.getTagEntrySetIterator()).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void testInitWithNullXml() throws Exception {
        global = new Global();

        // Method to test
        global.init(null);

        // Tests
        fail("should raise anexception");
    }

    @Test
    public void testInitWithXmlEmpty() throws Exception {
        global = new Global();
        PowerMockito.when(xml.getDocumentElement()).thenReturn(null);

        // Method to test
        global.init(xml);

        // Tests
        assertThat(global.getTagEntrySetIterator().hasNext()).isFalse();
    }

    @Ignore
    @Test
    @SuppressWarnings("unchecked")
    public void testInit() throws Exception {
        global = new Global();
        PowerMockito.when(xml.getDocumentElement()).thenReturn(patient);

        // Method to test
        global.init(xml);

        // Tests
        assertThat(global.getTagEntrySet()).containsExactlyInAnyOrder(
            entry(GlobalTag.patientId),
            entry(GlobalTag.patientName),
            entry(GlobalTag.issuerOfPatientId),
            entry(GlobalTag.patientBirthDate),
            entry(GlobalTag.patientSex),
            entry(GlobalTag.studyDate),
            entry(GlobalTag.modality)
        );
    }

    private MapEntry<TagW, Object> entry(GlobalTag tag) throws ParseException{
        Object value;
        
        if(tag.type.equals(TagType.DATE)) {
           value = new SimpleDateFormat("yyyyMMdd", LocalUtil.getLocaleFormat()).parse(tag.value);
        } else {
            value = tag.value;
        }
        return Assertions.entry(tag.tagW, value);
    }
}
