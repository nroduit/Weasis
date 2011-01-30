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

import java.util.Collection;
import java.util.List;

import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;

public interface TreeModel {

    public final static String ROOT_IDENTIFIER = "__ROOT__"; //$NON-NLS-1$
    public final static MediaSeriesGroup rootNode =
        new MediaSeriesGroupNode(TagW.RootElement, ROOT_IDENTIFIER, TagW.RootElement);

    public List<TreeModelNode> getModelStructure();

    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node);

    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value);

    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf);

    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf);

    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode);

}
