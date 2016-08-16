package org.weasis.base.explorer.list.impl;

import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailListPane;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class JIThumbnailListPane<E extends MediaElement> extends AThumbnailListPane<E>
    implements IThumbnailListPane<E> {

    public JIThumbnailListPane() {
        super(new JIThumbnailList<E>());
    }

}
