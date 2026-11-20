/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.dockable;

import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.dicom.viewer2d.dockable.DisplayTool;
import org.weasis.dicom.wsi.Messages;
import org.weasis.dicom.wsi.WsiSettings;

/**
 * The display tool of the slide viewer: what a slide view can show and hide. A slide has no
 * overlay, shutter or padding, no window, lookup table, frame or patient orientation, and no lines
 * of other views: those items of the 2D viewer are left out, and the overview and the rendering
 * information of the slide take their place.
 */
public class WsiDisplayTool extends DisplayTool {

  /** An item of the slide node, kept by a setting of the slide viewer. */
  private record SlideItem(String label, Setting<Boolean> setting) {
    @Override
    public String toString() {
      return label;
    }
  }

  // Assigned by addViewerNodes, which the constructor of the 2D tool calls: no initializer here
  private DefaultMutableTreeNode slideNode;

  public WsiDisplayTool(String pluginName) {
    super(pluginName);
  }

  @Override
  protected boolean hasImageNode() {
    return false;
  }

  @Override
  protected List<LayerItem> annotationItems() {
    return List.of(
        LayerItem.ANNOTATIONS,
        LayerItem.MIN_ANNOTATIONS,
        LayerItem.SCALE,
        LayerItem.ZOOM,
        LayerItem.ROTATION,
        LayerItem.PIXEL);
  }

  @Override
  protected boolean hasCrosslines() {
    return false;
  }

  @Override
  protected void addViewerNodes(DefaultMutableTreeNode root) {
    slideNode = new DefaultMutableTreeNode(Messages.getString("WsiDisplayTool.slide"), true);
    slideNode.add(
        new DefaultMutableTreeNode(
            new SlideItem(Messages.getString("WsiDisplayTool.overview"), WsiSettings.OVERVIEW),
            false));
    slideNode.add(
        new DefaultMutableTreeNode(
            new SlideItem(
                Messages.getString("WsiDisplayTool.rendering"), WsiSettings.RENDERING_INFO),
            false));
    root.add(slideNode);
  }

  @Override
  protected boolean viewerNodeChanged(Object node, boolean selected) {
    if (node == slideNode) {
      // The node only groups its items
      return true;
    }
    if (node instanceof DefaultMutableTreeNode treeNode
        && treeNode.getUserObject() instanceof SlideItem item) {
      // The slide views follow the setting
      Settings.set(item.setting(), selected);
      return true;
    }
    return false;
  }

  @Override
  protected void initViewerNodes(ViewCanvas<?> view) {
    setNodeChecked(slideNode, true);
    for (int i = 0; i < slideNode.getChildCount(); i++) {
      if (slideNode.getChildAt(i) instanceof DefaultMutableTreeNode node
          && node.getUserObject() instanceof SlideItem item) {
        setNodeChecked(node, Settings.get(item.setting()));
      }
    }
  }
}
