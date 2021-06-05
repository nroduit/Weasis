/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import java.awt.geom.AffineTransform;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.data.PlanarImage;

public interface ImageLayer<E extends ImageElement> extends MeasurableLayer {

  E getSourceImage();

  PlanarImage getDisplayImage();

  void setImage(E image, OpManager preprocessing);

  AffineTransform getTransform();

  void setTransform(AffineTransform transform);

  SimpleOpManager getDisplayOpManager();

  void updateDisplayOperations();

  boolean isEnableDispOperations();

  void setEnableDispOperations(boolean enabled);

  // Duplicate of Layer interface
  void setVisible(Boolean visible);

  Boolean getVisible();
}
