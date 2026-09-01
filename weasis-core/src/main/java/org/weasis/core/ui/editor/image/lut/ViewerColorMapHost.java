/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;

/** Editor host backed by a viewer's event manager: previews through the LUT action. */
public class ViewerColorMapHost<E extends ImageElement> implements ColorMapEditorHost {

  protected final ImageViewerEventManager<E> eventManager;

  public ViewerColorMapHost(ImageViewerEventManager<E> eventManager) {
    this.eventManager = Objects.requireNonNull(eventManager);
  }

  private Optional<ComboItemListener<ByteLut>> lutAction() {
    return eventManager.getAction(ActionW.LUT);
  }

  @Override
  public void preview(ByteLut lut) {
    lutAction().ifPresent(action -> action.setSelectedItem(lut));
  }

  @Override
  public void mapsChanged() {
    ColorMap shown = currentLut() == null ? null : currentLut().source();
    if (shown != null && ColorMapRegistry.getInstance().findById(shown.id()).isEmpty()) {
      preview(ColorLut.IMAGE.getByteLut()); // the shown map was deleted
    }
    ViewCanvas<E> view = eventManager.getSelectedViewPane();
    if (view != null) {
      eventManager.updateComponentsListener(view);
    }
  }

  @Override
  public boolean canPickValue() {
    return eventManager.getSelectedViewPane() != null;
  }

  @Override
  public void pickValue(Consumer<Double> consumer) {
    ViewCanvas<E> view = eventManager.getSelectedViewPane();
    if (view == null) {
      return;
    }
    JComponent component = view.getJComponent();
    Cursor previous = component.getCursor();
    component.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    component.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            component.removeMouseListener(this);
            component.setCursor(previous);
            if (SwingUtilities.isLeftMouseButton(e)) {
              Point2D p = view.getImageCoordinatesFromMouse(e.getX(), e.getY());
              PixelInfo info = view.getPixelInfo(new Point((int) p.getX(), (int) p.getY()));
              double[] values = info.getValues();
              if (values != null && values.length > 0) {
                consumer.accept(values[0]);
              }
            }
          }
        });
  }

  @Override
  public List<ColorMapFormat> formats() {
    return List.of(ColorMapFormats.JSON, ColorMapFormats.TXT);
  }

  @Override
  public ByteLut currentLut() {
    return lutAction()
        .map(action -> action.getSelectedItem() instanceof ByteLut lut ? lut : null)
        .orElse(ColorLut.IMAGE.getByteLut());
  }
}
