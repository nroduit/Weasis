/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
