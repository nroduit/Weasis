package org.weasis.base.explorer;

import java.io.File;

public interface DiskFileList extends JIObservable {

    public JIFileModel getFileListModel();

    public void loadDirectory(File dir);

}
