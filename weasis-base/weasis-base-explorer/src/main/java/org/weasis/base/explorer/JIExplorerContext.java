package org.weasis.base.explorer;

import java.util.List;

public class JIExplorerContext {

    protected List<TreeNode> selectedDirNodes = null;
    protected int lastSelectedDirNodesIndex = -1;

    public final synchronized List<TreeNode> getSelectedDirNodes() {
        return this.selectedDirNodes;
    }

    public final synchronized void setSelectedDirNodes(final List<TreeNode> selectedDirNodes,
        final TreeNode lastSelectedDirNodes) {
        this.selectedDirNodes = selectedDirNodes;
        this.lastSelectedDirNodesIndex = this.selectedDirNodes.indexOf(lastSelectedDirNodes);
    }

    public final synchronized void setSelectedDirNodes(final List<TreeNode> selectedDirNodes,
        final int lastSelectedDirNodesIndex) {
        this.selectedDirNodes = selectedDirNodes;
        this.lastSelectedDirNodesIndex = lastSelectedDirNodesIndex;
    }
}
