package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.nio.file.Path;

import javax.swing.JList;

import org.weasis.base.explorer.list.AThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireCentralThumbnailModel<E extends MediaElement> extends AThumbnailModel<E> {

    public AcquireCentralThumbnailModel(JList<E> list) {
        super(list);
    }

    @Override
    public void setData(Path path) {
        // Only get images from model
    }
}
