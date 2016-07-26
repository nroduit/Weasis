package org.weasis.acquire.explorer.core.bean.helper;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.Optional;

import org.dcm4che3.data.Tag;
import org.mockito.Mock;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.acquire.test.utils.MockHelper;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.TagD;

public class GlobalHelper extends MockHelper {
    protected static String patientIdValue = "12345";
    protected static String patientNameValue = "John DOE";
    protected static String issuerOfPatientIdValue = "6789";
    protected static String patientBirthDateValue = "19850216";
    protected static String patientSexValue = "M";
    protected static String studyDateValue = "20160603";
    protected static String modalityValue = "Lorem ipsum";
    
    @Mock
    protected static Document xml;
    @Mock
    protected static Element patient;

    @Mock
    protected static TagW patientIdW;
    @Mock
    protected static TagW patientNameW;
    @Mock
    protected static TagW issuerOfPatientIdW;
    @Mock
    protected static TagW patientBirthDateW;
    @Mock
    protected static TagW patientSexW;
    @Mock
    protected static TagW studyDateW;
    @Mock
    protected static TagW modalityW;

    @Mock
    protected static NodeList patientIdNodeList;
    @Mock
    protected static NodeList patientNameNodeList;
    @Mock
    protected static NodeList issuerOfPatientIdNodeList;
    @Mock
    protected static NodeList patientBirthDateNodeList;
    @Mock
    protected static NodeList patientSexNodeList;
    @Mock
    protected static NodeList studyDateNodeList;
    @Mock
    protected static NodeList modalityNodeList;

    @Mock
    protected static Node patientIdNode;
    @Mock
    protected static Node patientNameNode;
    @Mock
    protected static Node issuerOfPatientIdNode;
    @Mock
    protected static Node patientBirthDateNode;
    @Mock
    protected static Node patientSexNode;
    @Mock
    protected static Node studyDateNode;
    @Mock
    protected static Node modalityNode;

    protected enum GlobalTag {
        patientId(Tag.PatientID, patientIdW, patientIdNodeList, patientIdNode, TagType.STRING, patientIdValue), 
        patientName(Tag.PatientName, patientNameW, patientNameNodeList, patientNameNode, TagType.STRING, patientNameValue), 
        issuerOfPatientId(Tag.IssuerOfPatientID, issuerOfPatientIdW, issuerOfPatientIdNodeList, issuerOfPatientIdNode, TagType.STRING, issuerOfPatientIdValue), 
        patientBirthDate(Tag.PatientBirthDate, patientBirthDateW, patientBirthDateNodeList, patientBirthDateNode, TagType.DATE, patientBirthDateValue), 
        patientSex(Tag.PatientSex, patientSexW, patientSexNodeList, patientSexNode, TagType.DICOM_SEX, patientSexValue), 
        studyDate(Tag.StudyDate, studyDateW, studyDateNodeList, studyDateNode, TagType.DATE, studyDateValue), 
        modality(Tag.Modality, modalityW, modalityNodeList, modalityNode, TagType.STRING, modalityValue);
        
        public int tagId;
        public TagW tagW;
        public NodeList nodeList;
        public Node node;
        public TagType type;
        public String value;
        
        private GlobalTag(int tagId, TagW tagW, NodeList nodeList, Node node, TagType type, String value) {
            this.tagId = tagId;
            this.tagW = tagW;
            this.nodeList = nodeList;
            this.node = node;
            this.type = type;
            this.value = value;
        }
        
        public void prepareMock() {
            Optional.ofNullable(patient).orElse(mock(Element.class));
            
            tagW = mock(TagW.class);
            nodeList = mock(NodeList.class);
            node = mock(Node.class);
            
            when(TagD.get(eq(tagId))).thenReturn(tagW);
            when(tagW.getType()).thenReturn(type);
            when(tagW.getKeyword()).thenReturn(name());
            
            when(patient.getElementsByTagName(eq(tagW.getKeyword()))).thenReturn(nodeList);
            when(nodeList.getLength()).thenReturn(1);
            when(nodeList.item(anyInt())).thenReturn(node);
            
            when(node.getTextContent()).thenReturn(value);
        }
        
        
        
    }
    
    
}
