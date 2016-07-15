package org.weasis.base.explorer.list;

import java.util.List;

import org.weasis.core.api.media.data.MediaElement;

public interface IThumbnailListPane<E extends MediaElement<?>> extends IDiskFileList {

    void loadDirectory(String pathname);

    List<E> getSelectedValuesList();
}
