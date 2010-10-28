package org.weasis.base.explorer;

public interface JIFileModel {

    public void setData();

    public void reload();

    public JIExplorerContext getReloadContext();

    public void setReloadContext(JIExplorerContext reloadContext);

    public OrderedFileList getDiskObjectList();

}
