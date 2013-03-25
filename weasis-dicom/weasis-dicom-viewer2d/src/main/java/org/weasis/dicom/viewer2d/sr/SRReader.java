package org.weasis.dicom.viewer2d.sr;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.iod.module.general.SeriesAndInstanceReference;
import org.dcm4che2.iod.module.macro.Code;
import org.dcm4che2.iod.module.macro.ImageSOPInstanceReference;
import org.dcm4che2.iod.module.macro.SOPInstanceReference;
import org.dcm4che2.iod.module.sr.MeasuredValue;
import org.dcm4che2.iod.module.sr.SRDocumentContent;
import org.dcm4che2.iod.module.sr.SRDocumentContentModule;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;

public class SRReader {
    private static final TagW[] PATIENT = { TagW.PatientName, TagW.PatientID, TagW.IssuerOfPatientID, TagW.PatientSex,
        TagW.PatientBirthDate };

    private final DicomSpecialElement dicomSR;
    private final Series series;
    private final DicomObject dcmobj;
    private final HashMap<String, Object> tags = new HashMap<String, Object>();

    public SRReader(DicomSpecialElement dicomSR) {
        this(null, dicomSR);
    }

    public SRReader(Series series, DicomSpecialElement dicomSR) {
        if (dicomSR == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.dicomSR = dicomSR;
        this.series = series;
        if (dicomSR.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) dicomSR.getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
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

    public HashMap<String, Object> getTags() {
        return tags;
    }

    public Object getTagValue(String key, Object defaultValue) {
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

    public boolean isSRAppicable(DicomImageElement img) {
        if (dcmobj != null && img != null) {
            SeriesAndInstanceReference refs = new SeriesAndInstanceReference(dcmobj);
            String suid = refs.getSeriesInstanceUID();
            if (suid != null && suid.equals(img.getTagValue(TagW.SeriesInstanceUID))) {
                SOPInstanceReference[] sops = refs.getReferencedInstances();
                if (sops == null) {
                    return true;
                }
                String imgSop = (String) img.getTagValue(TagW.SOPInstanceUID);
                if (imgSop != null) {
                    for (SOPInstanceReference sop : sops) {
                        if (imgSop.equals(sop.getReferencedSOPInstanceUID())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isModuleAppicable(ImageSOPInstanceReference[] sops, DicomImageElement img) {
        if (dcmobj != null && img != null) {
            if (sops == null) {
                return true;
            }
            String imgSop = (String) img.getTagValue(TagW.SOPInstanceUID);
            if (imgSop != null) {
                for (ImageSOPInstanceReference sop : sops) {
                    if (imgSop.equals(sop.getReferencedSOPInstanceUID())) {
                        int[] frames = sop.getReferencedFrameNumber();
                        if (frames == null) {
                            return true;
                        }
                        int frame = 0;
                        if (img.getKey() instanceof Integer) {
                            frame = (Integer) img.getKey();
                        }
                        for (int f : frames) {
                            if (f == frame) {
                                return true;
                            }
                        }
                        // if the frame has been excluded
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public void readDocumentGeneralModule(StringBuffer html) {
        if (dcmobj != null) {
            SRDocumentContentModule content = new SRDocumentContentModule(dcmobj);
            addCodeMeaning(html, content.getConceptNameCode(), "<h2>", "</h2>");

            String instName = dcmobj.getString(Tag.InstitutionName);
            String refPhy = dcmobj.getString(Tag.ReferringPhysicianName);
            if (instName != null) {
                html.append("By ");
                html.append(instName);
                html.append(", ");
            }
            if (refPhy != null) {
                html.append("Referring Physician: ");
                html.append(DicomMediaUtils.buildPersonName(refPhy));
            }
            if (instName != null || refPhy != null) {
                html.append("<BR>");
            }

            writeItems("Patient", PATIENT, html);

            html.append("<hr size=2>");

            SRDocumentContent[] cts = content.getContent();
            if (cts != null) {
                for (SRDocumentContent c : cts) {
                    html.append("<BR>");
                    addCodeMeaning(html, c.getConceptNameCode(), "<font size=\"+1\">", "</font>");
                    convertContentToHTML(html, c, false);
                }
            }
        }
    }

    private void convertContentToHTML(StringBuffer html, SRDocumentContent c, boolean continuous) {
        if (c != null) {
            String type = c.getValueType();
            html.append(continuous ? " " : "<BR>");

            if ("TEXT".equals(type)) {
                convertTextToHTML(html, c.getTextValue());
                html.append(continuous ? " " : "<BR>");
            } else if ("CODE".equals(type)) {
                addCodeMeaning(html, c.getConceptCode(), null, null);
                html.append(continuous ? " " : "<BR>");
            } else if ("PNAME".equals(type)) {
                convertTextToHTML(html, DicomMediaUtils.buildPersonName(c.getPersonName()));
                html.append(continuous ? " " : "<BR>");
            } else if ("NUM".equals(type)) {
                MeasuredValue val = c.getMeasuredValue();
                if (val != null) {
                    html.append(val.getNumericValue());
                    Code unit = val.getMeasurementUnitsCode();
                    if (unit != null) {
                        html.append(" ");
                        html.append(EscapeChars.forHTML(unit.getCodeValue()));
                    }
                    html.append(continuous ? " " : "<BR>");
                }
            } else if ("CONTAINER".equals(type)) {
                SRDocumentContent[] cts = c.getContent();
                if (cts != null) {
                    boolean continuity = "CONTINUOUS".equals(c.getContinuityOfContent());
                    if (!continuity) {
                        html.append("<UL>");
                    }
                    for (SRDocumentContent c2 : cts) {
                        html.append(continuity ? " " : "<LI>");
                        if (!continuity) {
                            addCodeMeaning(html, c2.getConceptNameCode(), "<B>", "</B>");
                        }
                        convertContentToHTML(html, c2, continuity);
                        html.append(continuity ? " " : "</LI>");
                    }
                    if (!continuity) {
                        html.append("</UL>");
                    }
                    html.append(continuous ? " " : "<BR>");
                }

            } else if ("IMAGE".equals(type)) {
                if (series != null) {
                    DicomObject item = c.getDicomObject().getNestedDicomObject(Tag.ReferencedSOPSequence);
                    if (item != null) {
                        SOPInstanceReference ref = new SOPInstanceReference(item);
                        if (ref != null) {
                            html.append("<img align=\"top\" src=\"http://localhost:8080/wado?requestType=WADO&studyUID=1&seriesUID=1&objectUID=");
                            html.append(ref.getReferencedSOPInstanceUID());
                            html.append("\">");
                            html.append("<BR>");

                            // DataExplorerView dicomView =
                            // org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
                            // DicomModel model = null;
                            // if (dicomView != null) {
                            // model = (DicomModel) dicomView.getDataExplorerModel();
                            // }
                            // if (model != null) {
                            // MediaSeriesGroup study = model.getParent(series, DicomModel.study);
                            // MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
                            // Series s =
                            // findSOPInstanceReference(model, patient, study, ref.getReferencedSOPInstanceUID());
                            // if (s != null) {
                            // SeriesViewerFactory plugin = UIManager.getViewerFactory(s.getMimeType());
                            // if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                            // ViewerPluginBuilder.openSequenceInPlugin(plugin, s,
                            // dicomView.getDataExplorerModel(), true, true);
                            // // GET view and set series and media
                            // int[] seqFrame = ref.getDicomObject().getInts(Tag.ReferencedFrameNumber);
                            // }
                            // }
                            // }
                        }
                    }
                }

            } else {
                html.append("<i>This Value Type is not supported yet!</i>");
            }
        }
    }

    private Series findSOPInstanceReference(DicomModel model, MediaSeriesGroup patient, MediaSeriesGroup study,
        String sopUID) {
        if (model != null && patient != null && sopUID != null) {
            Series series = null;
            if (study != null) {
                series = findSOPInstanceReference(model, study, sopUID);
                if (series != null) {
                    return series;
                }
            }

            if (series == null) {
                Collection<MediaSeriesGroup> studyList = model.getChildren(patient);
                synchronized (model) {
                    for (Iterator<MediaSeriesGroup> it = studyList.iterator(); it.hasNext();) {
                        MediaSeriesGroup st = it.next();
                        if (st != study) {
                            series = findSOPInstanceReference(model, st, sopUID);
                        }
                        if (series != null) {
                            return series;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Series findSOPInstanceReference(DicomModel model, MediaSeriesGroup study, String sopUID) {
        if (model != null && study != null) {
            Collection<MediaSeriesGroup> seriesList = model.getChildren(study);
            synchronized (model) {
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup seq = it.next();
                    if (seq instanceof Series) {
                        Series s = (Series) seq;
                        if (s.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
                            return s;
                        }
                    }
                }
            }
        }
        return null;
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

    private void writeItems(String title, TagW[] tags, StringBuffer html) {
        if (tags != null && html != null) {
            for (TagW t : tags) {
                Object val = dicomSR.getTagValue(t);
                if (val != null) {
                    html.append(t.toString());
                    html.append(": ");
                    html.append(t.getFormattedText(val, t.getType(), null));
                    html.append("<BR>");
                }
            }
        }
    }
}
