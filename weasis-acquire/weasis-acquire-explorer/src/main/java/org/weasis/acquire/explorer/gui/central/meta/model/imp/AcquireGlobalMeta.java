package org.weasis.acquire.explorer.gui.central.meta.model.imp;

import java.util.Optional;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

public class AcquireGlobalMeta extends AcquireMetadataTableModel {
    private static final long serialVersionUID = 8912202268139591519L;

    private static final TagW[] TAGS_TO_DISPLAY = TagD.getTagFromIDs(Tag.PatientID, Tag.PatientName,
        Tag.PatientBirthDate, Tag.PatientSex, Tag.AccessionNumber, Tag.StudyDescription);
    
    private static final TagW[] TAGS_EDITABLE = TagD.getTagFromIDs(Tag.StudyDescription);

    public AcquireGlobalMeta() {
        super(AcquireManager.GLOBAL);
    }

    @Override
    protected Optional<TagW[]> tagsToDisplay() {
        return Optional.of(TAGS_TO_DISPLAY);
    }

    @Override
    protected Optional<TagW[]> tagsEditable() {
        return Optional.of(TAGS_EDITABLE);
    }
}
