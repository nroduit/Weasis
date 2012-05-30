package org.weasis.dicom.codec;

import java.util.HashMap;
import java.util.HashSet;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.iod.module.sr.HierachicalSOPInstanceReference;
import org.dcm4che2.iod.module.sr.KODocumentModule;
import org.dcm4che2.iod.module.sr.SOPInstanceReferenceAndMAC;
import org.dcm4che2.iod.module.sr.SeriesAndInstanceReference;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;

public class KeyObjectReader {
    private final DicomObject dcmobj;
    private final HashMap<String, Object> tags = new HashMap<String, Object>();

    public KeyObjectReader(MediaElement dicom) {
        if (dicom == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null"); //$NON-NLS-1$
        }
        if (dicom.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) dicom.getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
        } else {
            dcmobj = null;
        }
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

    public KODocumentModule getKODocumentModule() {
        if (dcmobj != null) {
            return new KODocumentModule(dcmobj);
        }
        return null;
    }

    public Filter<DicomImageElement> getFilter() {
        KODocumentModule koModule = getKODocumentModule();
        if (koModule != null) {
            final HierachicalSOPInstanceReference[] refs = koModule.getCurrentRequestedProcedureEvidences();
            final HashSet<String> sopUIDList = new HashSet<String>();
            for (HierachicalSOPInstanceReference ref : refs) {
                SeriesAndInstanceReference[] series = ref.getReferencedSeries();
                for (SeriesAndInstanceReference s : series) {
                    SOPInstanceReferenceAndMAC[] sops = s.getReferencedInstances();
                    for (SOPInstanceReferenceAndMAC sop : sops) {
                        sopUIDList.add(sop.getReferencedSOPInstanceUID());
                    }
                }
            }
            Filter<DicomImageElement> filter = new Filter<DicomImageElement>() {

                @Override
                public boolean passes(DicomImageElement dicom) {
                    return sopUIDList.contains(dicom.getTagValue(TagW.SOPInstanceUID));
                }
            };
            return filter;
        }
        return null;
    }
}
