/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;

/**
 * A special element that draws vector graphics over the images it references, such as the SCOORD
 * regions of a Structured Report. The viewer asks every element of the patient for its graphics
 * each time an image is displayed, the way segmentation contours are attached, so the overlay
 * appears without any action from the user.
 *
 * <p>Implementations must make {@link #appliesTo} cheap (a set lookup) because it runs for every
 * element on every image change; the graphics themselves are built only for matching images.
 */
public interface SpecialElementOverlay {

  /** Level of the first overlay layer; each element gets its own layer above the previous one. */
  int OVERLAY_LAYER_LEVEL = 305;

  /** The layer type of the graphics, e.g. {@link LayerType#DICOM_SR}. */
  LayerType getOverlayLayerType();

  /** The name of the layer holding this element's graphics, shown to the user. */
  String getOverlayLayerName();

  /** Whether this element may draw something on the image. Must be cheap. */
  boolean appliesTo(DicomImageElement image);

  /**
   * Builds new graphic instances for the image. A graphic belongs to a single graphic model, so a
   * fresh instance is expected on every call.
   *
   * @return the graphics, never null
   */
  List<Graphic> buildOverlayGraphics(DicomImageElement image);

  /**
   * Replaces the layers of a type in a graphic model by the graphics of the given elements, one
   * locked and unselectable layer per element that draws something.
   *
   * @param model the graphic model of the image
   * @param image the displayed image
   * @param type the layer type to rebuild
   * @param elements the candidate elements; those of another layer type are ignored
   */
  static void apply(
      GraphicModel model,
      DicomImageElement image,
      LayerType type,
      Collection<? extends SpecialElementOverlay> elements) {
    model.deleteByLayerType(type);
    if (image == null || elements == null) {
      return;
    }
    int level = OVERLAY_LAYER_LEVEL;
    for (SpecialElementOverlay element : elements) {
      if (!type.equals(element.getOverlayLayerType()) || !element.appliesTo(image)) {
        continue;
      }
      List<Graphic> graphics = element.buildOverlayGraphics(image);
      if (graphics == null || graphics.isEmpty()) {
        continue;
      }
      GraphicLayer layer = new DefaultLayer(type);
      layer.setName(element.getOverlayLayerName());
      layer.setSerializable(false);
      layer.setLocked(true);
      layer.setSelectable(false);
      layer.setLevel(level++);
      for (Graphic graphic : graphics) {
        graphic.setLayer(layer);
        for (PropertyChangeListener listener : model.getGraphicsListeners()) {
          graphic.addPropertyChangeListener(listener);
        }
        model.addGraphic(graphic);
      }
    }
  }
}
