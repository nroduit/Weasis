/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.util.ViewNotification;

/**
 * The graphics cut or copied in the session, and the paste that puts them back on a view. One
 * instance is shared by every viewer, so a graphic copied in one container pastes in another.
 *
 * <p>What it holds are <b>detached copies taken at copy time</b>: editing the original afterwards
 * does not change what will be pasted, and a graphic that was cut survives the deletion of its
 * model. Each paste takes a further copy, so the same content can be pasted many times.
 */
public class GraphicClipboard {

  /** {@code cursor} or {@code inPlace}: where the paste shortcut puts the graphics. */
  public static final String P_PASTE_MODE = "weasis.draw.paste.mode";

  /** Image pixels a duplicate is offset by, so it does not hide the original. */
  public static final double DUPLICATE_OFFSET = 10.0;

  public enum PasteMode {
    /** The bounding box of the pasted graphics is centred on the cursor. */
    CURSOR,
    /** The graphics keep the image coordinates they had on the source image. */
    IN_PLACE
  }

  private final List<Graphic> graphics = new ArrayList<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  /** The clipboard content; never the live graphics of a model. */
  public synchronized List<Graphic> getGraphics() {
    return List.copyOf(graphics);
  }

  /**
   * @deprecated stores live model objects, so an edit of the original changes what is pasted. Use
   *     {@link #copy(List)}.
   */
  @Deprecated(since = "4.8.0", forRemoval = true)
  public void setGraphics(List<Graphic> graphics) { // NOSONAR copy() locks
    copy(graphics);
  }

  public synchronized boolean hasGraphics() {
    return !graphics.isEmpty();
  }

  /** True when a paste has something to put on a view. */
  public boolean hasContent() {
    return hasGraphics();
  }

  /** Replaces the content by detached copies of the given graphics. */
  public void copy(List<? extends Graphic> selection) {
    List<Graphic> detached = new ArrayList<>();
    if (selection != null) {
      for (Graphic graphic : selection) {
        if (graphic != null) {
          detached.add(graphic.copy());
        }
      }
    }
    synchronized (this) {
      graphics.clear();
      graphics.addAll(detached);
    }
    fireChanged();
  }

  /**
   * Copies the graphics, then removes the ones the user may remove: a graphic of a locked layer is
   * copied and left in place.
   */
  public void cut(List<? extends Graphic> selection, ViewCanvas<?> view) {
    copy(selection);
    if (selection == null) {
      return;
    }
    selection.stream()
        .filter(GraphicClipboard::isEditable)
        .toList()
        .forEach(Graphic::fireRemoveAction);
    if (view != null) {
      view.getGraphicManager().setSelectedGraphic(null);
      view.getJComponent().repaint();
    }
  }

  /** Clears the content. */
  public void clear() {
    synchronized (this) {
      graphics.clear();
    }
    fireChanged();
  }

  /** The mode the paste shortcut follows, from the preferences. */
  public static PasteMode defaultPasteMode() {
    String mode;
    try {
      mode = GuiUtils.getUICore().getSystemPreferences().getProperty(P_PASTE_MODE);
    } catch (RuntimeException | LinkageError e) {
      mode = null; // no UI core, e.g. in tests
    }
    return "inPlace".equalsIgnoreCase(mode) ? PasteMode.IN_PLACE : PasteMode.CURSOR; // NON-NLS
  }

  /**
   * Puts a copy of the content on a view and selects it.
   *
   * <p>The points are image coordinates and are never converted: the measurements are recomputed
   * with the adapter of the target, so a line pasted on an image with another pixel spacing
   * measures what it really covers there. The anchor is recomputed against the plane of the target
   * and dropped when the target has no geometry, and a graphic coming from a locked layer lands on
   * the editable layer of the target — that is what makes a copy of an imported graphic editable.
   *
   * @param view the target view
   * @param mode where the graphics land
   * @param cursor the cursor position in image coordinates, for {@link PasteMode#CURSOR}
   * @return the graphics added to the view
   */
  public List<Graphic> paste(ViewCanvas<?> view, PasteMode mode, Point2D cursor) {
    List<Graphic> content = getGraphics();
    return paste(view, content, translation(content, mode, cursor));
  }

  private static List<Graphic> paste(ViewCanvas<?> view, List<Graphic> content, Point2D delta) {
    if (view == null || content.isEmpty()) {
      return List.of();
    }

    List<Graphic> pasted = new ArrayList<>(content.size());
    for (Graphic source : content) {
      Graphic graphic = source.copy();
      translate(graphic, delta);
      graphic.setLayerType(editableLayerType(source));
      AbstractGraphicModel.addGraphicToModel(view, graphic);
      graphic.setAnchor(null);
      graphic.anchorOn(view);
      pasted.add(graphic);
    }

    view.getGraphicManager().setSelectedGraphic(pasted);
    warnWhenOutside(view, pasted);
    // Repaint all: the labels are drawn outside the bounds of the graphics
    view.getJComponent().repaint();
    return pasted;
  }

  /**
   * Pastes a copy of the selection at once, offset so it does not hide the original. The paste
   * takes the copies itself, so what the user had copied stays on the clipboard.
   */
  public List<Graphic> duplicate(List<? extends Graphic> selection, ViewCanvas<?> view) {
    if (selection == null) {
      return List.of();
    }
    List<Graphic> content =
        selection.stream().filter(Objects::nonNull).map(Graphic.class::cast).toList();
    return paste(view, content, new Point2D.Double(DUPLICATE_OFFSET, DUPLICATE_OFFSET));
  }

  public void addListener(Runnable listener) {
    listeners.add(Objects.requireNonNull(listener));
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void fireChanged() {
    listeners.forEach(Runnable::run);
  }

  /** A graphic of a locked layer can be copied, never cut. */
  public static boolean isEditable(Graphic graphic) {
    return graphic != null
        && graphic.getLayer() != null
        && !Boolean.TRUE.equals(graphic.getLayer().getLocked());
  }

  /** A graphic of a locked layer becomes an editable copy on the draw layers of the target. */
  private static LayerType editableLayerType(Graphic graphic) {
    LayerType type = graphic.getLayerType();
    if (type != null && !Boolean.TRUE.equals(type.getLocked())) {
      return type;
    }
    return graphic.getMeasurementList().isEmpty() ? LayerType.DRAW : LayerType.MEASURE;
  }

  private static Point2D translation(List<Graphic> content, PasteMode mode, Point2D cursor) {
    if (mode != PasteMode.CURSOR || cursor == null) {
      return new Point2D.Double();
    }
    Rectangle2D bounds = boundsOf(content);
    if (bounds == null) {
      return new Point2D.Double();
    }
    return new Point2D.Double(
        cursor.getX() - bounds.getCenterX(), cursor.getY() - bounds.getCenterY());
  }

  private static Rectangle2D boundsOf(List<Graphic> content) {
    Rectangle2D bounds = null;
    for (Graphic graphic : content) {
      for (Point2D point : graphic.getPts()) {
        if (point == null) {
          continue;
        }
        if (bounds == null) {
          bounds = new Rectangle2D.Double(point.getX(), point.getY(), 0, 0);
        } else {
          bounds.add(point);
        }
      }
    }
    return bounds;
  }

  private static void translate(Graphic graphic, Point2D delta) {
    if (delta.getX() == 0 && delta.getY() == 0) {
      return;
    }
    for (Point2D point : graphic.getPts()) {
      if (point != null) {
        point.setLocation(point.getX() + delta.getX(), point.getY() + delta.getY());
      }
    }
    if (graphic instanceof DragGraphic dragGraphic) {
      dragGraphic.buildShape(null);
    }
  }

  /** A graphic outside the image is pasted anyway: the user is told, never asked. */
  private static void warnWhenOutside(ViewCanvas<?> view, List<Graphic> pasted) {
    Rectangle2D area = view.getViewModel().getModelArea();
    boolean outside =
        pasted.stream()
            .anyMatch(
                g -> {
                  Rectangle bounds = g.getBounds(null);
                  return bounds == null || !bounds.intersects(area);
                });
    if (outside) {
      ViewNotification.show(
          view.getJComponent(), Messages.getString("GraphicEditActions.outside_image"));
    }
  }
}
