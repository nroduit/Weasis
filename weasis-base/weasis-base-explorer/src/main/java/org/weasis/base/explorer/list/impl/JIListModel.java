
package org.weasis.base.explorer.list.impl;

import java.nio.file.Path;
import java.util.Optional;

import javax.swing.JList;

import org.weasis.base.explorer.DefaultExplorer;
import org.weasis.base.explorer.TreeNode;
import org.weasis.base.explorer.list.AThumbnailModel;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class JIListModel<E extends MediaElement> extends AThumbnailModel<E> implements IThumbnailModel<E> {

    public JIListModel(final JList<E> list) {
        super(list);
    }

    @Override
    public void setData(Path path) {
        if (this.loading) {
            return;
        }

        this.reloadContext = DefaultExplorer.getTreeContext();
        if (this.reloadContext.getSelectedDirNodes() != null) {
            synchronized (this) {
                this.loading = true;
            }
            this.list.getSelectionModel().setValueIsAdjusting(true);
            this.list.requestFocusInWindow();
            Optional<TreeNode> t = this.reloadContext.getSelectedDirNodes().stream().findFirst();
            if (t.isPresent()) {
                loadContent(t.get().getNodePath());
            }
            synchronized (this) {
                fireContentsChanged(this, 0, getSize() - 1);
                this.loading = false;
            }
            this.list.getSelectionModel().setValueIsAdjusting(false);
        }
    }

}
