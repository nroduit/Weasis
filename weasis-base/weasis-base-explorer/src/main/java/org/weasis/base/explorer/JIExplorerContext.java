/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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
