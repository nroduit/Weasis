package org.weasis.dicom.viewer2d.sr;

import java.awt.Color;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.macro.ImageSOPInstanceReference;
import org.weasis.dicom.codec.macro.SOPInstanceReference;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.PRManager;

public class SRReader {

    private final DicomSpecialElement dicomSR;
    private final Attributes dcmItems;
    private final HashMap<TagW, Object> tags = new HashMap<TagW, Object>();

    public SRReader(Series series, DicomSpecialElement dicomSR) {
        if (dicomSR == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.dicomSR = dicomSR;
        if (dicomSR.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = dicomSR.getMediaReader();
            dcmItems = dicomImageLoader.getDicomObject();
            DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
            if (model instanceof TreeModel) {
                TreeModel treeModel = (TreeModel) model;
                MediaSeriesGroup patient = treeModel.getParent(series, model.getTreeModelNodeForNewPlugin());
                if (patient == null) {
                    String patientID = dcmItems.getString(Tag.PatientID, DicomMediaIO.NO_VALUE);
                    tags.put(TagW.PatientID, patientID);
                    String name = DicomMediaUtils.buildPatientName(dcmItems.getString(Tag.PatientName));
                    tags.put(TagW.PatientName, name);
                    Date birthdate = DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.PatientBirthDate, null);
                    DicomMediaUtils.setTagNoNull(tags, TagW.PatientBirthDate, birthdate);
                    // Global Identifier for the patient.
                    tags.put(TagW.PatientPseudoUID, DicomMediaUtils.buildPatientPseudoUID(patientID,
                        dcmItems.getString(Tag.IssuerOfPatientID), name, null));
                    tags.put(TagW.PatientSex, DicomMediaUtils.buildPatientSex(dcmItems.getString(Tag.PatientSex)));

                } else {
                    tags.put(TagW.PatientName, patient.getTagValue(TagW.PatientName));
                    tags.put(TagW.PatientID, patient.getTagValue(TagW.PatientID));
                    tags.put(TagW.PatientBirthDate, patient.getTagValue(TagW.PatientBirthDate));
                    tags.put(TagW.PatientSex, patient.getTagValue(TagW.PatientSex));
                }

                MediaSeriesGroup study = treeModel.getParent(series, DicomModel.study);
                if (study == null) {
                    DicomMediaUtils.setTagNoNull(tags, TagW.StudyID, dcmItems.getString(Tag.StudyID));
                    DicomMediaUtils.setTagNoNull(tags, TagW.StudyDate, TagW.dateTime(
                        DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.StudyDate, null),
                        DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.StudyTime, null)));
                    DicomMediaUtils.setTagNoNull(tags, TagW.AccessionNumber, dcmItems.getString(Tag.AccessionNumber));
                    DicomMediaUtils.setTagNoNull(tags, TagW.ReferringPhysicianName,
                        DicomMediaUtils.buildPersonName(dcmItems.getString(Tag.ReferringPhysicianName)));
                } else {
                    tags.put(TagW.StudyDate, study.getTagValue(TagW.StudyDate));
                    tags.put(TagW.StudyID, study.getTagValue(TagW.StudyID));
                    tags.put(TagW.AccessionNumber, study.getTagValue(TagW.AccessionNumber));
                    tags.put(TagW.ReferringPhysicianName, series.getTagValue(TagW.ReferringPhysicianName));
                }
            }
        } else {
            dcmItems = null;
        }
    }

    public MediaElement getDicom() {
        return dicomSR;
    }

    public Attributes getDcmobj() {
        return dcmItems;
    }

    public HashMap<TagW, Object> getTags() {
        return tags;
    }

    public Object getTagValue(TagW key, Object defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        Object object = tags.get(key);
        return object == null ? defaultValue : object;
    }

    public SeriesAndInstanceReference getSeriesAndInstanceReference() {
        if (dcmItems != null) {
            return new SeriesAndInstanceReference(dcmItems);
        }
        return null;
    }

    public void readDocumentGeneralModule(StringBuilder html, Map<String, SRImageReference> map) {
        if (dcmItems != null) {
            SRDocumentContentModule content = new SRDocumentContentModule(dcmItems);
            addCodeMeaning(html, content.getConceptNameCode(), "<h1>", "</h1>");

            String instName = dcmItems.getString(Tag.InstitutionName);
            String instDepName = dcmItems.getString(Tag.InstitutionalDepartmentName);
            String stationName = dcmItems.getString(Tag.StationName);
            Date contentDateTime =
                TagW.dateTime(DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.ContentDate, null),
                    DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.ContentTime, null));
            if (instName != null) {
                html.append("By ");
                html.append(instName);
                if (instDepName != null) {
                    html.append(" (");
                    html.append(instDepName);
                    html.append(")");
                }
            }
            if (stationName != null) {
                if (instName != null) {
                    html.append(" on ");
                }
                html.append(stationName);
            }
            if (contentDateTime != null) {
                if (instName != null || stationName != null) {
                    html.append(", ");
                }
                html.append(TagW.formatDateTime(contentDateTime));
            }
            if (instName != null || stationName != null || contentDateTime != null) {
                html.append("<BR>");
            }

            html.append("<table border=\"0\" width=\"100%\" cellspacing=\"5\">");

            html.append("<tr align=\"left\" valign=\"top\"><td width=\"33%\" >");
            html.append("<font size=\"+1\">Patient</font>");
            html.append("<BR>");
            writeItem(TagW.PatientName, html);
            html.append("<BR>");
            writeItem(TagW.PatientID, html);
            html.append("<BR>");
            writeItem(TagW.PatientBirthDate, html);
            html.append("<BR>");
            writeItem(TagW.PatientSex, html);

            html.append("</td><td width=\"33%\" >");
            html.append("<font size=\"+1\">Study</font>");
            html.append("<BR>");
            writeItem(TagW.StudyDate, html);
            html.append("<BR>");
            writeItem(TagW.StudyID, html);
            html.append("<BR>");
            writeItem(TagW.AccessionNumber, html);
            html.append("<BR>");
            writeItem(TagW.ReferringPhysicianName, html);

            html.append("</td><td width=\"33%\" >");
            html.append("<font size=\"+1\">Report Status</font>");
            html.append("<BR>");
            writeItem("Completion Flag", Tag.CompletionFlag, html);
            html.append("<BR>");
            writeItem("Verification Flag", Tag.VerificationFlag, html);
            html.append("<BR>");
            writeVerifyingObservers(html);
            html.append("</td></tr>");

            html.append("</table>");
            html.append("<hr size=2>");
            Sequence cts = content.getContent();
            if (cts != null) {
                for (int i = 0; i < cts.size(); i++) {
                    SRDocumentContent c = new SRDocumentContent(cts.get(i));
                    html.append("<BR>");
                    html.append("<B>");
                    String level = "1." + (i + 1);
                    html.append(level);
                    html.append(" </B>");
                    Code code = c.getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>");
                    convertContentToHTML(html, c, false, code == null, map, level);
                    html.append("<BR>");
                    addContent(html, c, map, level);
                }
            }
        }
    }

    private void convertContentToHTML(StringBuilder html, SRDocumentContent c, boolean continuous, boolean noCodeName,
        Map<String, SRImageReference> map, String level) {
        if (c != null) {
            html.append("<A name=\"");
            html.append(level);
            html.append("\"<></A>");
            String type = c.getValueType();

            if ("TEXT".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                convertTextToHTML(html, c.getTextValue());
            } else if ("CODE".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                addCodeMeaning(html, c.getConceptCode(), null, null);
            } else if ("PNAME".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                convertTextToHTML(html, DicomMediaUtils.buildPersonName(c.getPersonName()));
            } else if ("NUM".equals(type)) {
                html.append(continuous || noCodeName ? " " : " = ");
                Attributes val = c.getMeasuredValue();
                if (val != null) {
                    html.append(val.getFloat(Tag.NumericValue, 0.0f));
                    Attributes item = dcmItems.getNestedDataset(Tag.MeasurementUnitsCodeSequence);
                    if (item != null) {
                        Code unit = new Code(item);
                        html.append(" ");
                        html.append(EscapeChars.forHTML(unit.getCodeValue()));
                    }
                }
            } else if ("CONTAINER".equals(type)) {
                return;
            } else if ("IMAGE".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                Attributes item = c.getAttributes().getNestedDataset(Tag.ReferencedSOPSequence);
                if (item != null) {
                    SRImageReference imgRef = map.get(level);
                    if (imgRef == null) {
                        imgRef = new SRImageReference(level);
                        map.put(level, imgRef);
                    }
                    if (imgRef.getImageSOPInstanceReference() == null) {
                        imgRef.setImageSOPInstanceReference(new ImageSOPInstanceReference(item));
                    }

                    // int[] frames = ref.getReferencedFrameNumber();
                    // if (frames == null || frames.length == 0) {
                    // html.append("<img align=\"top\" src=\"http://localhost:8080/wado?requestType=WADO&studyUID=1&seriesUID=1&objectUID=");
                    // html.append(ref.getReferencedSOPInstanceUID());
                    // html.append("\">");
                    // html.append("<BR>");
                    // }

                    html.append("<a href=\"http://");
                    html.append(level);
                    html.append("\">");
                    html.append("Show the image");
                    html.append("</a>");

                }
            } else if ("DATETIME".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                html.append(c.getDateTime());
            } else if ("DATE".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                html.append(c.getDate());
            } else if ("TIME".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                html.append(c.getTime());
            } else if ("UIDREF".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                convertTextToHTML(html, c.getUID());
            } else if ("COMPOSITE".equals(type)) {
                Sequence sequenceElt = c.getAttributes().getSequence(Tag.ReferencedSOPSequence);
                if (sequenceElt != null && !sequenceElt.isEmpty()) {
                    html.append(continuous || noCodeName ? " " : ": ");
                    for (int i = 0; i < sequenceElt.size(); i++) {
                        SOPInstanceReference sopRef = new SOPInstanceReference(sequenceElt.get(i));
                        // TODO convert UID to text
                        html.append(sopRef.getReferencedSOPClassUID());
                        html.append(" (SOP Instance UID: ");
                        html.append(sopRef.getReferencedSOPInstanceUID());
                        html.append(")");
                    }
                }
            } else if ("SCOORD".equals(type)) {
                SRImageReference imgRef = map.get(level);
                if (imgRef == null) {
                    imgRef = new SRImageReference(level);
                    map.put(level, imgRef);
                }
                // Identifier layerId = new Identifier(350, " [DICOM SR Graphics]");
                // DragLayer layer = new DragLayer(view.getLayerModel(), layerId);$
                Attributes graphicsItems = c.getAttributes();
                try {
                    Graphic graphic =
                        PRManager.buildGraphicFromPR(graphicsItems, Color.MAGENTA, false, 1, 1, false, null, true);
                    if (graphic != null) {
                        imgRef.addGraphic(graphic);
                    }
                } catch (InvalidShapeException e) {
                    e.printStackTrace();
                }
                html.append(continuous || noCodeName ? " " : ": ");
                convertTextToHTML(html, graphicsItems.getString(Tag.GraphicType));
                // } else if ("TCOORD".equals(type)) {
                // html.append(continuous || noCodeName ? " " : ": ");
                // // TODO
                // } else if ("WAVEFORM".equals(type)) {
                // html.append(continuous || noCodeName ? " " : ": ");
                // // TODO
            } else if (type != null) {
                html.append("<i>");
                html.append(type);
                html.append(" is not supported!</i>");
            }

            int[] refs = c.getReferencedContentItemIdentifier();
            if (refs != null) {
                html.append("Content Item by reference: ");
                StringBuilder r = new StringBuilder();
                for (int j = 0; j < refs.length - 1; j++) {
                    r.append(refs[j]);
                    r.append('.');
                }
                if (refs.length - 1 >= 0) {
                    r.append(refs[refs.length - 1]);
                }
                html.append("<a href=\"#");
                html.append(r.toString());
                html.append("\">");
                html.append("node ");
                html.append(r.toString());
                html.append("</a>");

            }

        }
    }

    private void addContent(StringBuilder html, SRDocumentContent c, Map<String, SRImageReference> map, String level) {
        Sequence cts = c.getContent();
        if (cts != null) {
            boolean continuity = "CONTINUOUS".equals(c.getContinuityOfContent());
            if (!continuity) {
                html.append("<OL>");
            }
            for (int i = 0; i < cts.size(); i++) {
                SRDocumentContent srContent = new SRDocumentContent(cts.get(i));
                html.append(continuity ? " " : "<LI>");
                Code code = null;
                if (!continuity) {
                    code = srContent.getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>");
                }
                String level2 = level + "." + (i + 1);
                convertContentToHTML(html, srContent, continuity, code == null, map, level2);
                addContent(html, srContent, map, level2);
                html.append(continuity ? " " : "</LI>");
            }
            if (!continuity) {
                html.append("</OL>");
            }
        }
    }

    private void addCodeMeaning(StringBuilder html, Code code, String startTag, String endTag) {
        if (code != null) {
            if (startTag != null) {
                html.append(startTag);
            }
            html.append(EscapeChars.forHTML(code.getCodeMeaning()));
            if (endTag != null) {
                html.append(endTag);
            }
        }
    }

    private void convertTextToHTML(StringBuilder html, String text) {
        if (text != null) {
            String[] lines = EscapeChars.convertToLines(text);
            if (lines.length > 0) {
                html.append(EscapeChars.forHTML(lines[0]));
                for (int i = 1; i < lines.length; i++) {
                    html.append("<BR>");
                    html.append(EscapeChars.forHTML(lines[i]));
                }
            }
        }
    }

    private void writeItem(TagW tag, StringBuilder html) {
        if (tag != null && html != null) {
            html.append("<B>");
            html.append(tag.toString());
            html.append("</B>: ");
            Object val = tags.get(tag);
            if (val == null) {
                val = dicomSR.getTagValue(tag);
            }
            if (val != null) {
                html.append(tag.getFormattedText(val, tag.getType(), null));
            }
        }
    }

    private void writeItem(String tagName, int tag, StringBuilder html) {
        if (tagName != null && html != null && dcmItems != null) {
            html.append("<B>");
            html.append(tagName);
            html.append("</B>: ");
            String val = dcmItems.getString(tag);
            if (val != null) {
                html.append(val);
            }
        }
    }

    private void writeVerifyingObservers(StringBuilder html) {
        if (html != null && dcmItems != null) {
            Sequence seq = dcmItems.getSequence(Tag.VerifyingObserverSequence);
            if (seq != null && !seq.isEmpty()) {
                html.append("<B>");
                html.append("Verifying Observer");
                html.append("</B>:<BR>");
                for (Attributes v : seq) {
                    Date date = v.getDate(Tag.VerificationDateTime);
                    if (date != null) {
                        html.append(" * ");
                        html.append(TagW.formatDateTime(date));
                        html.append(" - ");
                        String name = DicomMediaUtils.buildPersonName(v.getString(Tag.VerifyingObserverName));
                        if (name != null) {
                            html.append(name);
                            html.append(", ");
                        }
                        String org = v.getString(Tag.VerifyingOrganization);
                        if (org != null) {
                            html.append(org);
                        }
                        html.append("<BR>");
                    }
                }
            }
        }
    }
}
