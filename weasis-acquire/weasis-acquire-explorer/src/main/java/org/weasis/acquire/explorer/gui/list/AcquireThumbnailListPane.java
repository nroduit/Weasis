package org.weasis.acquire.explorer.gui.list;

import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireThumbnailListPane<E extends MediaElement<?>> extends AThumbnailListPane<E> {

    private final ImageGroupPane centralPane;

    public AcquireThumbnailListPane(ImageGroupPane centralPane) {
        super(new AcquireThumbnailList<E>());
        this.centralPane = centralPane;
        ((AcquireThumbnailList<E>) getThumbnailList()).setMainPanel(this);
    }

    public ImageGroupPane getCentralPane() {
        return centralPane;
    }
}
