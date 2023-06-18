/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer;

import org.weasis.core.Messages;

public enum LayerItem {
  ANNOTATIONS("annotations", Messages.getString("AnnotationsLayer.anno"), true), // NON-NLS

  MIN_ANNOTATIONS(
      "minAnnotations", Messages.getString("LayerAnnotation.min_anot"), false), // NON-NLS

  ANONYM_ANNOTATIONS("anonym", Messages.getString("AnnotationsLayer.anonym"), false), // NON-NLS

  SCALE("scale", Messages.getString("AnnotationsLayer.scale"), true), // NON-NLS

  LUT("lut", Messages.getString("AnnotationsLayer.lut"), false), // NON-NLS

  IMAGE_ORIENTATION("orientation", Messages.getString("AnnotationsLayer.or"), true), // NON-NLS

  WINDOW_LEVEL("wl", Messages.getString("AnnotationsLayer.wl"), true), // NON-NLS

  ZOOM("zoom", Messages.getString("AnnotationsLayer.zoom"), true), // NON-NLS

  ROTATION("rotation", Messages.getString("AnnotationsLayer.rot"), false), // NON-NLS

  FRAME("frame", Messages.getString("AnnotationsLayer.fr"), true), // NON-NLS

  PIXEL("pixel", Messages.getString("AnnotationsLayer.pix"), true), // NON-NLS

  PRELOADING_BAR("loading", Messages.getString("AnnotationsLayer.preload_bar"), false), // NON-NLS

  KEY_OBJECT("ko", Messages.getString("AnnotationsLayer.ko"), true); // NON-NLS

  private final String key;
  private final String name;
  private final boolean visible;

  LayerItem(String key, String name, Boolean visible) {
    this.key = key;
    this.name = name;
    this.visible = visible;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public boolean isVisible() {
    return visible;
  }

  @Override
  public String toString() {
    return name;
  }
}
