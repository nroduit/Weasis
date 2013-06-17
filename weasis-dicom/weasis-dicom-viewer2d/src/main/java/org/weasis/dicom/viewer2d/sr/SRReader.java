package org.weasis.dicom.viewer2d.sr;

import java.awt.Color;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.dcm4che.data.DicomElement;
import org.dcm4che.data.DicomObject;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.iod.module.general.SeriesAndInstanceReference;
import org.dcm4che.iod.module.macro.Code;
import org.dcm4che.iod.module.macro.ImageSOPInstanceReference;
import org.dcm4che.iod.module.macro.SOPInstanceReference;
import org.dcm4che.iod.module.pr.GraphicObject;
import org.dcm4che.iod.module.sr.MeasuredValue;
import org.dcm4che.iod.module.sr.SRDocumentContent;
import org.dcm4che.iod.module.sr.SRDocumentContentModule;
import org.dcm4che.iod.module.sr.VerifyingObserver;
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
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.PRManager;

public class SRReader {

    private final DicomSpecialElement dicomSR;
    private final DicomObject dcmobj;
    private final HashMap<TagW, Object> tags = new HashMap<TagW, Object>();

    public SRReader(Series series, DicomSpecialElement dicomSR) {
        if (dicomSR == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.dicomSR = dicomSR;
        if (dicomSR.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) dicomSR.getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
            DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
            if (model instanceof TreeModel) {
                TreeModel treeModel = (TreeModel) model;
                MediaSeriesGroup patient = treeModel.getParent(series, model.getTreeModelNodeForNewPlugin());
                if (patient == null) {
                    String patientID = dcmobj.getString(Tag.PatientID, DicomMediaIO.NO_VALUE);
                    tags.put(TagW.PatientID, patientID);
                    String name = DicomMediaUtils.buildPatientName(dcmobj.getString(Tag.PatientName));
                    tags.put(TagW.PatientName, name);
                    Date birthdate = DicomMediaUtils.getDateFromDicomElement(dcmobj, Tag.PatientBirthDate, null);
                    DicomMediaUtils.setTagNoNull(tags, TagW.PatientBirthDate, birthdate);
                    // Global Identifier for the patient.
                    tags.put(TagW.PatientPseudoUID, DicomMediaUtils.buildPatientPseudoUID(patientID,
                        dcmobj.getString(Tag.IssuerOfPatientID), name, birthdate));
                    tags.put(TagW.PatientSex, DicomMediaUtils.buildPatientSex(dcmobj.getString(Tag.PatientSex)));

                } else {
                    tags.put(TagW.PatientName, patient.getTagValue(TagW.PatientName));
                    tags.put(TagW.PatientID, patient.getTagValue(TagW.PatientID));
                    tags.put(TagW.PatientBirthDate, patient.getTagValue(TagW.PatientBirthDate));
                    tags.put(TagW.PatientSex, patient.getTagValue(TagW.PatientSex));
                }

                MediaSeriesGroup study = treeModel.getParent(series, DicomModel.study);
                if (study == null) {
                    DicomMediaUtils.setTagNoNull(tags, TagW.StudyID, dcmobj.getString(Tag.StudyID));
                    DicomMediaUtils.setTagNoNull(tags, TagW.StudyDate, TagW.dateTime(
                        DicomMediaUtils.getDateFromDicomElement(dcmobj, Tag.StudyDate, null),
                        DicomMediaUtils.getDateFromDicomElement(dcmobj, Tag.StudyTime, null)));
                    DicomMediaUtils.setTagNoNull(tags, TagW.AccessionNumber, dcmobj.getString(Tag.AccessionNumber));
                    DicomMediaUtils.setTagNoNull(tags, TagW.ReferringPhysicianName,
                        DicomMediaUtils.buildPersonName(dcmobj.getString(Tag.ReferringPhysicianName)));
                } else {
                    tags.put(TagW.StudyDate, study.getTagValue(TagW.StudyDate));
                    tags.put(TagW.StudyID, study.getTagValue(TagW.StudyID));
                    tags.put(TagW.AccessionNumber, study.getTagValue(TagW.AccessionNumber));
                    tags.put(TagW.ReferringPhysicianName, series.getTagValue(TagW.ReferringPhysicianName));
                }
            }
        } else {
            dcmobj = null;
        }
    }

    public MediaElement getDicom() {
        return dicomSR;
    }

    public DicomObject getDcmobj() {
        return dcmobj;
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
        if (dcmobj != null) {
            return new SeriesAndInstanceReference(dcmobj);
        }
        return null;
    }

    public void readDocumentGeneralModule(StringBuffer html, Map<String, SRImageReference> map) {
        if (dcmobj != null) {
            SRDocumentContentModule content = new SRDocumentContentModule(dcmobj);
            addCodeMeaning(html, content.getConceptNameCode(), "<h1>", "</h1>");

            String instName = dcmobj.getString(Tag.InstitutionName);
            String instDepName = dcmobj.getString(Tag.InstitutionalDepartmentName);
            String stationName = dcmobj.getString(Tag.StationName);
            Date contentDateTime =
                TagW.dateTime(DicomMediaUtils.getDateFromDicomElement(dcmobj, Tag.ContentDate, null),
                    DicomMediaUtils.getDateFromDicomElement(dcmobj, Tag.ContentTime, null));
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
            SRDocumentContent[] cts = content.getContent();
            if (cts != null) {
                for (int i = 0; i < cts.length; i++) {
                    html.append("<BR>");
                    html.append("<B>");
                    String level = "1." + (i + 1);
                    html.append(level);
                    html.append(" </B>");
                    Code code = cts[i].getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>");
                    convertContentToHTML(html, cts[i], false, code == null, map, level);
                    html.append("<BR>");
                    addContent(html, cts[i], map, level);
                }
            }
        }
    }

    private void convertContentToHTML(StringBuffer html, SRDocumentContent c, boolean continuous, boolean noCodeName,
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
                MeasuredValue val = c.getMeasuredValue();
                if (val != null) {
                    html.append(val.getNumericValue());
                    Code unit = val.getMeasurementUnitsCode();
                    if (unit != null) {
                        html.append(" ");
                        html.append(EscapeChars.forHTML(unit.getCodeValue()));
                    }
                }
            } else if ("CONTAINER".equals(type)) {
                return;
            } else if ("IMAGE".equals(type)) {
                html.append(continuous || noCodeName ? " " : ": ");
                DicomObject item = c.getDicomObject().getNestedDicomObject(Tag.ReferencedSOPSequence);
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
                DicomElement sequenceElt = c.getDicomObject().get(Tag.ReferencedSOPSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    html.append(continuous || noCodeName ? " " : ": ");
                    for (int i = 0; i < sequenceElt.countItems(); i++) {
                        SOPInstanceReference sopRef = new SOPInstanceReference(sequenceElt.getDicomObject(i));
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
                // DragLayer layer = new DragLayer(view.getLayerModel(), layerId);
                GraphicObject gr = new GraphicObject(c.getDicomObject());
                try {
                    Graphic graphic = PRManager.buildGraphicFromPR(gr, Color.MAGENTA, false, 1, 1, false, null, true);
                    if (graphic != null) {
                        imgRef.addGraphic(graphic);
                    }
                } catch (InvalidShapeException e) {
                    e.printStackTrace();
                }
                html.append(continuous || noCodeName ? " " : ": ");
                convertTextToHTML(html, gr.getGraphicType());
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
                StringBuffer r = new StringBuffer();
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

    private void addContent(StringBuffer html, SRDocumentContent c, Map<String, SRImageReference> map, String level) {
        SRDocumentContent[] cts = c.getContent();
        if (cts != null) {
            boolean continuity = "CONTINUOUS".equals(c.getContinuityOfContent());
            if (!continuity) {
                html.append("<OL>");
            }
            for (int i = 0; i < cts.length; i++) {
                html.append(continuity ? " " : "<LI>");
                Code code = null;
                if (!continuity) {
                    code = cts[i].getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>");
                }
                String level2 = level + "." + (i + 1);
                convertContentToHTML(html, cts[i], continuity, code == null, map, level2);
                addContent(html, cts[i], map, level2);
                html.append(continuity ? " " : "</LI>");
            }
            if (!continuity) {
                html.append("</OL>");
            }
        }
    }

    private void addCodeMeaning(StringBuffer html, Code code, String startTag, String endTag) {
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

    private void convertTextToHTML(StringBuffer html, String text) {
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

    private void writeItem(TagW tag, StringBuffer html) {
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

    private void writeItem(String tagName, int tag, StringBuffer html) {
        if (tagName != null && html != null && dcmobj != null) {
            html.append("<B>");
            html.append(tagName);
            html.append("</B>: ");
            String val = dcmobj.getString(tag);
            if (val != null) {
                html.append(val);
            }
        }
    }

    private void writeVerifyingObservers(StringBuffer html) {
        if (html != null && dcmobj != null) {
            DicomElement sequenceElt = dcmobj.get(Tag.VerifyingObserverSequence);
            if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                VerifyingObserver[] verObs = VerifyingObserver.toVerifyingObservers(sequenceElt);
                if (verObs != null) {
                    html.append("<B>");
                    html.append("Verifying Observer");
                    html.append("</B>:<BR>");
                    for (VerifyingObserver v : verObs) {
                        Date date = v.getVerificationDateTime();
                        if (date != null) {
                            html.append(" * ");
                            html.append(TagW.formatDateTime(date));
                            html.append(" - ");
                            String name = DicomMediaUtils.buildPersonName(v.getVerifyingObserverName());
                            if (name != null) {
                                html.append(name);
                                html.append(", ");
                            }
                            String org = v.getVerifyingOrganization();
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
}
