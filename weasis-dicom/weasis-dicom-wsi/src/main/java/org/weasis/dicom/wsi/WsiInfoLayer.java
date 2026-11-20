/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer2d.InfoLayer;
import org.weasis.dicom.wsi.model.PixelSpacing;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.tile.TilePlan;
import org.weasis.dicom.wsi.view.ScaleBar;
import org.weasis.dicom.wsi.view.TiledImageLayer;

/**
 * The corner text of a slide view. A slide has no window, no frame and no patient orientation: the
 * bottom leading corner holds the zoom, the plane and path shown, the value under the pointer and
 * the size of the slide; the magnification stands under the overview, in the top trailing corner,
 * above the text of that corner; the scale bar lies at the bottom, in the middle. Nothing is drawn
 * over the middle of the slide. The level and the tiles painted show on demand ({@link
 * WsiSettings#RENDERING_INFO}).
 */
final class WsiInfoLayer extends InfoLayer {

  private static final int SCALE_BAR_MAX = 160;
  private static final float MAGNIFICATION_FONT_RATIO = 1.5f;

  private final WsiView2d slideView;

  /**
   * @param view the view painted: the slide view, or its copy for a capture
   * @param slideView the view that holds the slide
   */
  WsiInfoLayer(
      ViewCanvas<DicomImageElement> view, WsiView2d slideView, boolean useGlobalPreferences) {
    super(view, useGlobalPreferences);
    this.slideView = slideView;
  }

  @Override
  public LayerAnnotation<DicomImageElement> getLayerCopy(
      ViewCanvas<DicomImageElement> view2DPane, boolean useGlobalPreferences) {
    WsiInfoLayer layer = new WsiInfoLayer(view2DPane, slideView, useGlobalPreferences);
    copyLayerValues(layer.displayPreferences);
    return layer;
  }

  private double viewScale() {
    return view2DPane.getViewModel().getViewScale();
  }

  // The overview is drawn by the slide view itself, not in a capture
  private Optional<Rectangle> overviewBox() {
    return view2DPane == slideView ? slideView.overviewBox() : Optional.empty();
  }

  // The ruler and the color bars of an image do not apply: the scale bar is drawn with the corner
  @Override
  protected void drawImageInfo(
      Graphics2D g2,
      DicomImageElement image,
      Rectangle bound,
      int fontHeight,
      int midFontHeight,
      boolean hideMin) {
    // Nothing along the edges
  }

  @Override
  protected float paintBottomLeftInfo(
      Graphics2D g2,
      DicomImageElement image,
      OpManager disOp,
      float drawY,
      int fontHeight,
      boolean hideMin) {
    Optional<TiledImageLayer> tiles = slideView.layer();
    // The last line is the size of the slide
    drawY -= fontHeight;
    drawY = checkAndPaintLossyImage(g2, image, drawY, fontHeight, border);
    if (tiles.isEmpty()) {
      return drawY;
    }
    Slide slide = tiles.get().slide();
    if (hideMin && Settings.get(WsiSettings.RENDERING_INFO) && view2DPane == slideView) {
      FontTools.paintFontOutline(g2, renderingInfo(tiles.get()), border, drawY);
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(LayerItem.PIXEL) && hideMin) {
      drawY = paintPixelInfo(g2, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ZOOM) && hideMin) {
      drawY = paintZoom(g2, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ROTATION) && hideMin) {
      drawY = paintRotation(g2, drawY, fontHeight);
    }
    if (slide.base().focalPlanes() > 1 || slide.base().opticalPaths().size() > 1) {
      String plane =
          Messages.format(
              "WsiView.plane",
              slideView.focalPlane() + 1,
              slide.base().focalPlanes(),
              slide.base().opticalPaths().get(slideView.opticalPath()));
      FontTools.paintFontOutline(g2, plane, border, drawY);
      drawY -= fontHeight;
    }
    Rectangle bound = view2DPane.getJComponent().getBounds();
    if (getDisplayPreferences(LayerItem.SCALE)) {
      paintScaleBar(g2, slide, bound);
    }
    if (getDisplayPreferences(LayerItem.ZOOM)) {
      paintMagnification(g2, slide, bound);
    }
    return drawY;
  }

  private String renderingInfo(TiledImageLayer tiles) {
    TilePlan plan = tiles.plan();
    String text =
        Messages.format(
            "WsiView.diagnostics",
            plan.level() + 1,
            tiles.slide().levels().size(),
            1.0 / viewScale(),
            tiles.readyTiles(),
            plan.painted().size(),
            slideView.paintMillis());
    return plan.truncated() ? text + " · " + Messages.getString("WsiView.truncated") : text;
  }

  // The size of the slide, and of its pixels, in place of the size of the image of a 2D view
  @Override
  protected void paintBottomLeftAnnotations(
      Graphics2D g2,
      DicomImageElement image,
      Series series,
      Rectangle bound,
      FontMetrics fontMetrics,
      int fontHeight,
      int midFontHeight,
      float midX,
      float midY,
      Modality mod) {
    Optional<Slide> slide = slideView.slide();
    if (slide.isEmpty()) {
      return;
    }
    StringBuilder text = new StringBuilder(mod.name());
    text.append(" (").append(slide.get().width()).append("x").append(slide.get().height());
    text.append(")");
    slide
        .get()
        .base()
        .pixelSpacing()
        .ifPresent(
            s ->
                text.append(" · ").append(Messages.format("WsiView.spacing", s.column() * 1000.0)));
    FontTools.paintFontOutline(
        g2, text.toString(), border, bound.height - border - GuiUtils.getScaleLength(1.5f));
  }

  @Override
  protected float topTrailingStart(Graphics2D g2, Rectangle bound, int fontHeight) {
    float y = overviewBox().map(b -> (float) b.getMaxY() + border / 2f).orElse(0f);
    if (getDisplayPreferences(LayerItem.ZOOM) && magnification().isPresent()) {
      y += g2.getFontMetrics(magnificationFont(g2)).getHeight();
    }
    return y + fontHeight;
  }

  private Optional<String> magnification() {
    return slideView
        .slide()
        .map(Slide::magnification)
        .filter(java.util.OptionalDouble::isPresent)
        .map(m -> Messages.format("WsiView.magnification", m.getAsDouble() * viewScale()));
  }

  private static Font magnificationFont(Graphics2D g2) {
    Font base = g2.getFont();
    return base.deriveFont(Font.BOLD, base.getSize2D() * MAGNIFICATION_FONT_RATIO);
  }

  // Under the overview, where the eye goes to see where it is on the slide
  private void paintMagnification(Graphics2D g2, Slide slide, Rectangle bound) {
    Optional<String> text = magnification();
    if (text.isEmpty()) {
      return;
    }
    Font base = g2.getFont();
    g2.setFont(magnificationFont(g2));
    FontMetrics fm = g2.getFontMetrics();
    float top = overviewBox().map(b -> (float) b.getMaxY() + border / 2f).orElse(0f);
    FontTools.paintFontOutline(
        g2,
        text.get(),
        bound.width - border - (float) fm.stringWidth(text.get()),
        top + fm.getAscent());
    g2.setFont(base);
  }

  // At the bottom, in the middle: out of the corners and off the middle of the slide
  private void paintScaleBar(Graphics2D g2, Slide slide, Rectangle bound) {
    Optional<PixelSpacing> spacing = slide.base().pixelSpacing();
    if (spacing.isEmpty()) {
      return;
    }
    Optional<ScaleBar> bar =
        ScaleBar.of(spacing.get().column() / viewScale(), GuiUtils.getScaleLength(SCALE_BAR_MAX));
    if (bar.isEmpty()) {
      return;
    }
    FontMetrics fm = g2.getFontMetrics();
    double length = bar.get().lengthPx();
    double thickness = GuiUtils.getScaleLength(4f);
    double tick = thickness * 2.5;
    double x = (bound.width - length) / 2;
    double y = bound.height - border - GuiUtils.getScaleLength(1.5f) - thickness;
    java.awt.geom.Path2D shape = new java.awt.geom.Path2D.Double();
    shape.append(new Rectangle2D.Double(x, y, length, thickness), false);
    shape.append(new Rectangle2D.Double(x, y - tick + thickness, 1.5, tick), false);
    shape.append(new Rectangle2D.Double(x + length - 1.5, y - tick + thickness, 1.5, tick), false);
    java.awt.Stroke stroke = g2.getStroke();
    g2.setStroke(new BasicStroke(2f));
    g2.setPaint(Color.BLACK);
    g2.draw(shape);
    g2.setPaint(Color.WHITE);
    g2.fill(shape);
    g2.setStroke(stroke);
    String label = bar.get().label();
    FontTools.paintFontOutline(
        g2,
        label,
        (float) (x + (length - fm.stringWidth(label)) / 2),
        (float) (y - fm.getDescent() - GuiUtils.getScaleLength(2f)));
  }
}
