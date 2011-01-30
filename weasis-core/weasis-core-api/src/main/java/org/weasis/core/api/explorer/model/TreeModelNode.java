/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.explorer.model;

import org.weasis.core.api.Messages;
import org.weasis.core.api.media.data.TagW;

/**
 * A tree model node defines the type and the position of the node in TreeModelNode. An instance of TreeModelNode must
 * have only one position in TreeModel.
 * 
 * 
 */
public class TreeModelNode {

    private final int depth;
    private final int nodePosition;
    private final TagW tagElement;

    public TreeModelNode(int depth, int nodePosition, TagW tagElement) {
        if (tagElement == null) {
            throw new IllegalArgumentException("TagW is null"); //$NON-NLS-1$
        }
        this.depth = depth;
        this.nodePosition = nodePosition;
        this.tagElement = tagElement;
    }

    public int getDepth() {
        return depth;
    }

    public int getNodePosition() {
        return nodePosition;
    }

    public TagW getTagElement() {
        return tagElement;
    }

}
