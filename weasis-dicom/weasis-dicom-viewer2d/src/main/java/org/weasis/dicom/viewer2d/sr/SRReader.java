package org.weasis.dicom.viewer2d.sr;

import java.util.HashMap;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.iod.module.general.SeriesAndInstanceReference;
import org.dcm4che2.iod.module.macro.ImageSOPInstanceReference;
import org.dcm4che2.iod.module.macro.SOPInstanceReference;
import org.dcm4che2.iod.module.sr.SRDocumentContentModule;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

public class SRReader {
    private static final TagW[] PATIENT = { TagW.PatientName, TagW.PatientID, TagW.IssuerOfPatientID, TagW.PatientSex,
        TagW.PatientBirthDate };

    private final MediaElement dicom;
    private final DicomObject dcmobj;
    private final HashMap<String, Object> tags = new HashMap<String, Object>();

    public SRReader(MediaElement dicom) {
        if (dicom == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        this.dicom = dicom;
        if (dicom.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) dicom.getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
        } else {
            dcmobj = null;
        }
    }

    public MediaElement getDicom() {
        return dicom;
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

    public void readDocumentGeneralModule(Series series, StringBuffer html) {
        if (dcmobj != null) {

            DataExplorerView dicomView = org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
            DicomModel model = null;
            if (dicomView != null) {
                model = (DicomModel) dicomView.getDataExplorerModel();
            }

            // TITLE of the document
            // DicomElement seq = dcmobj.get(Tag.ConceptNameCodeSequence);
            // if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
            // DicomObject obj = seq.getDicomObject(0);
            // doc.insertString(doc.getLength(), obj.getString(Tag.CodeMeaning), title);
            // }
            String instName = dcmobj.getString(Tag.InstitutionName);
            String refPhy = dcmobj.getString(Tag.ReferringPhysicianName);
            if (instName != null) {
                html.append("By ");
                html.append(instName);
                html.append(", ");
            }
            if (refPhy != null) {
                html.append("Referring Physician: ");
                html.append(DicomMediaUtils.buildPatientName(refPhy));
            }
            if (instName != null || refPhy != null) {
                html.append("<BR>"); //$NON-NLS-1$
            }

            if (model != null) {
                writeItems("Patient", PATIENT, model.getParent(series, DicomModel.patient), html);
            }

            html.append("<hr size=2>");

            SRDocumentContentModule content = new SRDocumentContentModule(dcmobj);

        }
    }

    private void writeItems(String title, TagW[] tags, MediaSeriesGroup group, StringBuffer html) {
        if (tags != null && group != null && html != null) {
            for (TagW t : tags) {
                Object val = group.getTagValue(t);
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
