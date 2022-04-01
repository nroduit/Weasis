/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.explorer.model;

import java.util.Objects;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;

/**
 * A tree model node defines the type and the position of the node in TreeModelNode. An instance of
 * TreeModelNode must have only one position in TreeModel.
 */
public record TreeModelNode(int depth, int nodePosition, TagW tagElement, TagView tagView) {

  public static final TreeModelNode ROOT = new TreeModelNode(0, 0, TagW.RootElement, null);

  public TreeModelNode(int depth, int nodePosition, TagW tagElement, TagView tagView) {
    this.depth = depth;
    this.nodePosition = nodePosition;
    this.tagElement = Objects.requireNonNull(tagElement);
    this.tagView = tagView;
  }
}
