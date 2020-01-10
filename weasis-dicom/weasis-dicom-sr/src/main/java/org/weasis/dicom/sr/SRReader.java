/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.sr;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.macro.Code;
import org.weasis.dicom.codec.macro.SOPInstanceReference;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;

public class SRReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SRReader.class);

    private final DicomSpecialElement dicomSR;
    private final Attributes dcmItems;

    public SRReader(Series series, DicomSpecialElement dicomSR) {
        if (dicomSR == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.dicomSR = dicomSR;
        this.dcmItems = dicomSR.getMediaReader().getDicomObject();
    }

    public MediaElement getDicom() {
        return dicomSR;
    }

    public Attributes getDcmobj() {
        return dcmItems;
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
            addCodeMeaning(html, content.getConceptNameCode(), "<h1>", "</h1>"); //$NON-NLS-1$ //$NON-NLS-2$

            String instName = dcmItems.getString(Tag.InstitutionName);
            String instDepName = dcmItems.getString(Tag.InstitutionalDepartmentName);
            String stationName = dcmItems.getString(Tag.StationName);
            LocalDateTime contentDateTime = TagD.dateTime(Tag.ContentDateAndTime, dcmItems);
            if (instName != null) {
                html.append(Messages.getString("SRReader.by")); //$NON-NLS-1$
                html.append(" "); //$NON-NLS-1$
                html.append(instName);
                if (instDepName != null) {
                    html.append(" ("); //$NON-NLS-1$
                    html.append(instDepName);
                    html.append(")"); //$NON-NLS-1$
                }
            }
            if (stationName != null) {
                if (instName != null) {
                    html.append(" "); //$NON-NLS-1$
                    html.append(Messages.getString("SRReader.on")); //$NON-NLS-1$
                    html.append(" "); //$NON-NLS-1$
                }
                html.append(stationName);
            }
            if (contentDateTime != null) {
                if (instName != null || stationName != null) {
                    html.append(", "); //$NON-NLS-1$
                }
                html.append(TagUtil.formatDateTime(contentDateTime));
            }
            if (instName != null || stationName != null || contentDateTime != null) {
                html.append("<BR>"); //$NON-NLS-1$
            }

            html.append("<table border=\"0\" width=\"100%\" cellspacing=\"5\">"); //$NON-NLS-1$

            html.append("<tr align=\"left\" valign=\"top\"><td width=\"33%\" >"); //$NON-NLS-1$
            html.append("<font size=\"+1\">Patient</font>"); //$NON-NLS-1$
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.PatientName, html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.PatientID, html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.PatientBirthDate, html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.PatientSex, html);

            html.append("</td><td width=\"33%\" >"); //$NON-NLS-1$
            html.append("<font size=\"+1\">"); //$NON-NLS-1$
            html.append(Messages.getString("SRReader.study")); //$NON-NLS-1$
            html.append("</font>"); //$NON-NLS-1$
            html.append("<BR>"); //$NON-NLS-1$
            writeStudyDateTime(html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.StudyID, html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.AccessionNumber, html);
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.ReferringPhysicianName, html);

            html.append("</td><td width=\"33%\" >"); //$NON-NLS-1$
            html.append("<font size=\"+1\">"); //$NON-NLS-1$
            html.append(Messages.getString("SRReader.report_status")); //$NON-NLS-1$
            html.append("</font><BR>"); //$NON-NLS-1$
            writeItem(Tag.CompletionFlag, html); // $NON-NLS-1$
            html.append("<BR>"); //$NON-NLS-1$
            writeItem(Tag.VerificationFlag, html); // $NON-NLS-1$
            html.append("<BR>"); //$NON-NLS-1$
            writeVerifyingObservers(html);
            html.append("</td></tr>"); //$NON-NLS-1$

            html.append("</table>"); //$NON-NLS-1$
            html.append("<hr size=2>"); //$NON-NLS-1$
            Sequence cts = content.getContent();
            if (cts != null) {
                for (int i = 0; i < cts.size(); i++) {
                    SRDocumentContent c = new SRDocumentContent(cts.get(i));
                    html.append("<BR>"); //$NON-NLS-1$
                    html.append("<B>"); //$NON-NLS-1$
                    String level = "1." + (i + 1); //$NON-NLS-1$
                    html.append(level);
                    html.append(" </B>"); //$NON-NLS-1$
                    Code code = c.getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>"); //$NON-NLS-1$ //$NON-NLS-2$
                    convertContentToHTML(html, c, false, code == null, map, level);
                    html.append("<BR>"); //$NON-NLS-1$
                    addContent(html, c, map, level);
                }
            }
        }
    }

    private static void convertContentToHTML(StringBuilder html, SRDocumentContent c, boolean continuous,
        boolean noCodeName, Map<String, SRImageReference> map, String level) {
        if (c != null) {
            html.append("<A name=\""); //$NON-NLS-1$
            html.append(level);
            html.append("\"<></A>"); //$NON-NLS-1$
            String type = c.getValueType();

            if ("TEXT".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                convertTextToHTML(html, c.getTextValue());
            } else if ("CODE".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                addCodeMeaning(html, c.getConceptCode(), null, null);
            } else if ("PNAME".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                convertTextToHTML(html, TagD.getDicomPersonName(c.getPersonName()));
            } else if ("NUM".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : " = "); //$NON-NLS-1$ //$NON-NLS-2$
                Attributes val = c.getMeasuredValue();
                if (val != null) {
                    html.append(val.getFloat(Tag.NumericValue, 0.0f));
                    Attributes item = val.getNestedDataset(Tag.MeasurementUnitsCodeSequence);
                    if (item != null) {
                        Code unit = new Code(item);
                        if (!"1".equals(unit.getCodeValue())) { //$NON-NLS-1$
                            html.append(" "); //$NON-NLS-1$
                            addCodeMeaning(html, unit, null, null);
                        }
                    }
                }
            } else if ("CONTAINER".equals(type)) { //$NON-NLS-1$
                return;
            } else if ("IMAGE".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                SRImageReference imgRef = getReferencedImage(map, level, c.getAttributes());
                if (imgRef != null) {
                    html.append("<a href=\"http://"); //$NON-NLS-1$
                    html.append(level);
                    html.append("\" style=\"color:#FF9900\">"); //$NON-NLS-1$
                    html.append(Messages.getString("SRReader.show_img")); //$NON-NLS-1$
                    html.append("</a>"); //$NON-NLS-1$
                }
            } else if ("DATETIME".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                html.append(c.getDateTime());
            } else if ("DATE".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                html.append(c.getDate());
            } else if ("TIME".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                html.append(c.getTime());
            } else if ("UIDREF".equals(type)) { //$NON-NLS-1$
                html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                convertTextToHTML(html, c.getUID());
            } else if ("COMPOSITE".equals(type)) { //$NON-NLS-1$
                Sequence sequenceElt = c.getAttributes().getSequence(Tag.ReferencedSOPSequence);
                if (sequenceElt != null && !sequenceElt.isEmpty()) {
                    html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                    for (int i = 0; i < sequenceElt.size(); i++) {
                        SOPInstanceReference sopRef = new SOPInstanceReference(sequenceElt.get(i));
                        html.append(sopRef.getReferencedSOPClassUID());
                        html.append(" (SOP Instance UID"); //$NON-NLS-1$
                        html.append(StringUtil.COLON_AND_SPACE);
                        html.append(sopRef.getReferencedSOPInstanceUID());
                        html.append(")"); //$NON-NLS-1$
                    }
                }
            } else if ("SCOORD".equals(type)) { //$NON-NLS-1$
                Attributes graphicsItems = c.getAttributes();
                Sequence sc = c.getContent();
                if (sc != null) {
                    for (Attributes attributes : sc) {
                        SRDocumentContent c2 = new SRDocumentContent(attributes);
                        String id = getReferencedContentItemIdentifier(c2.getReferencedContentItemIdentifier());
                        SRImageReference imgRef = null;
                        if (id == null) {
                            imgRef = getReferencedImage(map, level, attributes);
                            id = level;
                        } else {
                            imgRef = map.get(id);
                            if (imgRef == null) {
                                imgRef = new SRImageReference(id);
                                map.put(id, imgRef);
                            }
                        }

                        if (imgRef != null) {
                            try {
                                Graphic graphic = PrGraphicUtil.buildGraphic(graphicsItems, Color.MAGENTA, false, 1, 1,
                                    false, null, true);
                                if (graphic != null) {
                                    imgRef.addGraphic(graphic);
                                }
                            } catch (InvalidShapeException e) {
                                LOGGER.error("Cannot build graphic from SR", e); //$NON-NLS-1$
                            }

                            html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE); //$NON-NLS-1$

                            html.append("<a href=\"http://"); //$NON-NLS-1$
                            html.append(id);
                            html.append("\" style=\"color:#FF9900\">"); //$NON-NLS-1$
                            html.append(graphicsItems.getString(Tag.GraphicType));
                            html.append("</a>"); //$NON-NLS-1$
                        }
                    }
                }

                // } else if ("TCOORD".equals(type)) {
                // html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE);
                // // TODO
                // } else if ("WAVEFORM".equals(type)) {
                // html.append(continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE);
                // // TODO
            } else if (type != null) {
                html.append("<i>"); //$NON-NLS-1$
                html.append(type);
                html.append(" "); //$NON-NLS-1$
                html.append(Messages.getString("SRReader.tag_missing")); //$NON-NLS-1$
                html.append("</i>"); //$NON-NLS-1$

            }

            int[] refs = c.getReferencedContentItemIdentifier();
            if (refs != null) {
                html.append(Messages.getString("SRReader.content_ref") + StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
                String id = getReferencedContentItemIdentifier(refs);
                html.append("<a href=\"#"); //$NON-NLS-1$
                html.append(id);
                html.append("\">"); //$NON-NLS-1$
                html.append(Messages.getString("SRReader.node")); //$NON-NLS-1$
                html.append(" "); //$NON-NLS-1$
                html.append(id);
                html.append("</a>"); //$NON-NLS-1$

            }

        }
    }

    private static SRImageReference getReferencedImage(Map<String, SRImageReference> map, String level,
        Attributes attributes) {
        Attributes item = attributes.getNestedDataset(Tag.ReferencedSOPSequence);
        if (item == null) {
            return null;
        }
        SRImageReference imgRef = map.computeIfAbsent(level, k -> new SRImageReference(level));
        if (imgRef.getSopInstanceReference() == null) {
            imgRef.setSopInstanceReference(new SOPInstanceReference(item));
        }
        return imgRef;
    }

    private static String getReferencedContentItemIdentifier(int[] refs) {
        if (refs != null) {
            StringBuilder r = new StringBuilder();
            for (int j = 0; j < refs.length - 1; j++) {
                r.append(refs[j]);
                r.append('.');
            }
            if (refs.length - 1 >= 0) {
                r.append(refs[refs.length - 1]);
            }
            return r.toString();
        }
        return null;
    }

    private static void addContent(StringBuilder html, SRDocumentContent c, Map<String, SRImageReference> map,
        String level) {
        Sequence cts = c.getContent();
        if (cts != null) {
            boolean continuity = "CONTINUOUS".equals(c.getContinuityOfContent()); //$NON-NLS-1$
            if (!continuity) {
                html.append("<OL>"); //$NON-NLS-1$
            }
            for (int i = 0; i < cts.size(); i++) {
                SRDocumentContent srContent = new SRDocumentContent(cts.get(i));
                html.append(continuity ? " " : "<LI>"); //$NON-NLS-1$ //$NON-NLS-2$
                Code code = null;
                if (!continuity) {
                    code = srContent.getConceptNameCode();
                    addCodeMeaning(html, code, "<B>", "</B>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                String level2 = level + "." + (i + 1); //$NON-NLS-1$
                convertContentToHTML(html, srContent, continuity, code == null, map, level2);
                addContent(html, srContent, map, level2);
                html.append(continuity ? " " : "</LI>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (!continuity) {
                html.append("</OL>"); //$NON-NLS-1$
            }
        }
    }

    private static void addCodeMeaning(StringBuilder html, Code code, String startTag, String endTag) {
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

    private static void convertTextToHTML(StringBuilder html, String text) {
        if (text != null) {
            String[] lines = EscapeChars.convertToLines(text);
            if (lines.length > 0) {
                html.append(EscapeChars.forHTML(lines[0]));
                for (int i = 1; i < lines.length; i++) {
                    html.append("<BR>"); //$NON-NLS-1$
                    html.append(EscapeChars.forHTML(lines[i]));
                }
            }
        }
    }

    private void writeItem(int tagID, StringBuilder html) {
        TagW tag = TagD.getNullable(tagID, null);
        if (tag != null && html != null && dcmItems != null) {
            html.append("<B>"); //$NON-NLS-1$
            html.append(tag.getDisplayedName());
            html.append("</B>"); //$NON-NLS-1$
            html.append(StringUtil.COLON_AND_SPACE);
            html.append(tag.getFormattedTagValue(tag.getValue(dcmItems), null));
        }
    }

    private void writeStudyDateTime(StringBuilder html) {
        TagW tagDate = TagD.getNullable(Tag.StudyDate, null);
        if (tagDate != null && html != null && dcmItems != null) {
            LocalDateTime date = TagD.dateTime(Tag.StudyDateAndTime, dcmItems);
            if (date != null) {
                html.append("<B>"); //$NON-NLS-1$
                html.append(tagDate.getDisplayedName());
                html.append("</B>"); //$NON-NLS-1$
                html.append(StringUtil.COLON_AND_SPACE);
                html.append(TagUtil.formatDateTime(date));
            }
        }
    }

    private void writeVerifyingObservers(StringBuilder html) {
        if (html != null && dcmItems != null) {
            Sequence seq = dcmItems.getSequence(Tag.VerifyingObserverSequence);
            if (seq != null && !seq.isEmpty()) {
                html.append("<B>"); //$NON-NLS-1$
                html.append(Messages.getString("SRReader.ver_observer")); //$NON-NLS-1$
                html.append("</B>"); //$NON-NLS-1$
                html.append(StringUtil.COLON);
                html.append("<BR>"); //$NON-NLS-1$
                for (Attributes v : seq) {
                    TemporalAccessor date = (TemporalAccessor) TagD.get(Tag.VerificationDateTime).getValue(v);
                    if (date != null) {
                        html.append(" * "); //$NON-NLS-1$
                        html.append(TagUtil.formatDateTime(date));
                        html.append(" - "); //$NON-NLS-1$
                        String name = TagD.getDicomPersonName(v.getString(Tag.VerifyingObserverName));
                        if (name != null) {
                            html.append(name);
                            html.append(", "); //$NON-NLS-1$
                        }
                        String org = v.getString(Tag.VerifyingOrganization);
                        if (org != null) {
                            html.append(org);
                        }
                        html.append("<BR>"); //$NON-NLS-1$
                    }
                }
            }
        }
    }
}
