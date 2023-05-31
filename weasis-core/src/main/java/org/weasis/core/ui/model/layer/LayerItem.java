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
  ANNOTATIONS("annotations", Messages.getString("AnnotationsLayer.anno"), true),

  MIN_ANNOTATIONS("minAnnotations", Messages.getString("LayerAnnotation.min_anot"), false),

  ANONYM_ANNOTATIONS("anonym", Messages.getString("AnnotationsLayer.anonym"), false),

  SCALE("scale", Messages.getString("AnnotationsLayer.scale"), true),

  LUT("lut", Messages.getString("AnnotationsLayer.lut"), false),

  IMAGE_ORIENTATION("orientation", Messages.getString("AnnotationsLayer.or"), true),

  WINDOW_LEVEL("wl", Messages.getString("AnnotationsLayer.wl"), true),

  ZOOM("zoom", Messages.getString("AnnotationsLayer.zoom"), true),

  ROTATION("rotation", Messages.getString("AnnotationsLayer.rot"), false),

  FRAME("frame", Messages.getString("AnnotationsLayer.fr"), true),

  PIXEL("pixel", Messages.getString("AnnotationsLayer.pix"), true),
  PRELOADING_BAR("loading", Messages.getString("AnnotationsLayer.preload_bar"), false),

  KEY_OBJECT("ko", Messages.getString("AnnotationsLayer.ko"), true);

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
