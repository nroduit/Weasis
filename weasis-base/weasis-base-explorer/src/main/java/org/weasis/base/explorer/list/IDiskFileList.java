package org.weasis.base.explorer.list;

import java.nio.file.Path;

public interface IDiskFileList extends JIObservable {

    @SuppressWarnings("rawtypes")
    IThumbnailModel getFileListModel();

    void loadDirectory(Path dir);

}
