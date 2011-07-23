package org.weasis.base.explorer;

interface JIFileModel {

    void setData();

    void reload();

    JIExplorerContext getReloadContext();

    void setReloadContext(JIExplorerContext reloadContext);

    OrderedFileList getDiskObjectList();

}
