package org.weasis.acquire.explorer.gui.central.meta.model.imp;

import java.util.Optional;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

public class AcquireImageMeta extends AcquireMetadataTableModel {
    private static final long serialVersionUID = 8912202268139591519L;

    public static final TagW[] TAGS_TO_DISPLAY =
        TagD.getTagFromIDs(Tag.ImageComments, Tag.BodyPartExamined, Tag.ContentDate, Tag.ContentTime);

    private static final TagW[] TAGS_EDITABLE =
        TagD.getTagFromIDs(Tag.ImageComments, Tag.BodyPartExamined, Tag.ContentDate, Tag.ContentTime);

    public AcquireImageMeta(AcquireImageInfo imageInfo) {
        super((imageInfo == null) ? null : imageInfo.getImage());
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
