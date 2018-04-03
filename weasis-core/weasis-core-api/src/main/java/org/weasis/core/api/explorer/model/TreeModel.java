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
package org.weasis.core.api.explorer.model;

import java.util.Collection;
import java.util.List;

import org.weasis.core.api.media.data.MediaSeriesGroup;

public interface TreeModel {

    List<TreeModelNode> getModelStructure();

    Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node);

    MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value);

    void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf);

    void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf);

    MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode);

}
