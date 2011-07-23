package org.weasis.base.explorer;

import java.io.File;

public interface DiskFileList extends JIObservable {

    JIFileModel getFileListModel();

    void loadDirectory(File dir);

}
