/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DisplayProfile;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.LiveCaptureView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewTransferHandler;
import org.weasis.opencv.op.ImageConversion;

/**
 * Turns a {@link CaptureScope} into pixels. A single 2D view is re-rendered off-screen, which gives
 * a clean frame with no interactive-only annotation; everything else — a 3D view, a whole layout,
 * the application window — is painted as displayed, since that is the only way to pick up an OpenGL
 * surface and the surrounding interface.
 *
 * <p>The frame size is fixed once by {@link #prepare()} so a window resized halfway through a
 * recording cannot produce frames the encoder will refuse. A recording that follows the selection
 * fits whatever view or layout is selected into that size.
 *
 * <p>Frames are produced into reused buffers rather than allocated per grab: a window-scope
 * recording would otherwise allocate the full frame at every sample. {@link #grab()} returns the
 * grabber's own buffer, valid until the next call; a caller that keeps frames alive across grabs
 * supplies its own through {@link #grab(BufferedImage)}.
 */
public class FrameGrabber {

  private final CaptureScope scope;
  private final ViewCanvas<?> view;
  private final ImageViewerPlugin<?> container;
  private final MaskingProfile profile;
  private final double scale;

  private Dimension frameSize;
  private boolean previousSessionMasking;
  private boolean pointerVisible;
  private boolean imageOnly;
  private boolean followSelection;
  private Function<ViewCanvas<?>, DisplayProfile> displayProfile = _ -> null;
  private BufferedImage paintBuffer;
  private BufferedImage frameBuffer;

  public FrameGrabber(
      CaptureScope scope,
      ViewCanvas<?> view,
      ImageViewerPlugin<?> container,
      MaskingProfile profile,
      double scale) {
    this.scope = scope;
    this.view = view;
    this.container = container;
    this.profile = profile;
    this.scale = scale <= 0 ? 1.0 : scale;
  }

  public CaptureScope getScope() {
    return scope;
  }

  /** Whether a synthetic pointer is drawn where the mouse is, for recordings. */
  public void setPointerVisible(boolean pointerVisible) {
    this.pointerVisible = pointerVisible;
  }

  /**
   * Whether the view or layout scope moves to the one the user selects while capturing, instead of
   * staying on the one chosen at the start.
   */
  public void setFollowSelection(boolean followSelection) {
    this.followSelection = followSelection;
  }

  /** Whether a single 2D view is cut to its image, leaving out the canvas around it. */
  public void setImageOnly(boolean imageOnly) {
    this.imageOnly = imageOnly;
  }

  /**
   * What a captured view shows, resolved per view; {@code null} means as displayed. A 2D view is
   * re-rendered with it, a {@link LiveCaptureView} applies it for the time of the paint.
   */
  public void setDisplayProfile(Function<ViewCanvas<?>, DisplayProfile> displayProfile) {
    this.displayProfile = displayProfile;
  }

  /** Whether the capture re-renders a single 2D view, the only case {@link #setImageOnly} fits. */
  public boolean isRenderedOffScreen() {
    return scope == CaptureScope.VIEW && view instanceof DefaultView2d<?>;
  }

  /** Whether {@link #setDisplayProfile} applies: a single view that supports it. */
  public boolean supportsDisplayProfile() {
    return scope == CaptureScope.VIEW && DisplayProfile.supports(view);
  }

  /**
   * The image area of a 2D view in canvas pixels, or {@code null} when the view has none or the
   * crop is off. A rotated image gives its axis-aligned bounds.
   */
  private Rectangle imageArea(ViewCanvas<?> canvas) {
    if (!imageOnly || !(canvas instanceof DefaultView2d<?> view2d) || scope != CaptureScope.VIEW) {
      return null;
    }
    Rectangle2D area = view2d.getViewModel().getModelArea();
    if (area == null || area.isEmpty()) {
      return null;
    }
    Point[] corners = {
      view2d.getMouseCoordinatesFromImage(area.getMinX(), area.getMinY()),
      view2d.getMouseCoordinatesFromImage(area.getMaxX(), area.getMinY()),
      view2d.getMouseCoordinatesFromImage(area.getMaxX(), area.getMaxY()),
      view2d.getMouseCoordinatesFromImage(area.getMinX(), area.getMaxY())
    };
    Component component = view2d.getJComponent();
    return imageBounds(corners, new Rectangle(0, 0, component.getWidth(), component.getHeight()));
  }

  /** The canvas part covered by an image whose corners are given, or {@code null} if none. */
  static Rectangle imageBounds(Point[] corners, Rectangle canvas) {
    Rectangle bounds = new Rectangle(corners[0]);
    for (Point p : corners) {
      bounds.add(p);
    }
    Rectangle visible = bounds.intersection(canvas);
    return visible.width < 2 || visible.height < 2 ? null : visible;
  }

  /** The component captured now, or {@code null} when the followed selection has none. */
  public Component getTarget() {
    return target(currentView(), currentContainer());
  }

  private Component target(ViewCanvas<?> canvas, Component layout) {
    return switch (scope) {
      case VIEW -> canvas == null ? null : canvas.getJComponent();
      case CONTAINER -> layout;
      case APPLICATION_WINDOW -> GuiUtils.getUICore().getApplicationWindow();
    };
  }

  private ViewCanvas<?> currentView() {
    if (!followSelection) {
      return view;
    }
    return GuiUtils.getUICore().getSelectedPlugin().orElse(null)
            instanceof ImageViewerPlugin<?> selected
        ? selected.getSelectedViewCanvas()
        : null;
  }

  private Component currentContainer() {
    return followSelection ? GuiUtils.getUICore().getSelectedPlugin().orElse(null) : container;
  }

  /** Size of every produced frame, rounded to even dimensions for the sake of the DICOM sink. */
  public Dimension getFrameSize() {
    if (frameSize == null) {
      frameSize = computeFrameSize();
    }
    return frameSize;
  }

  private Dimension computeFrameSize() {
    Component target = target(view, container);
    Rectangle area = imageArea(view);
    int w = area != null ? area.width : target == null ? 0 : target.getWidth();
    int h = area != null ? area.height : target == null ? 0 : target.getHeight();
    w = Math.max(2, (int) Math.round(w * scale)) & ~1;
    h = Math.max(2, (int) Math.round(h * scale)) & ~1;
    return new Dimension(w, h);
  }

  /** A buffer of the frame size and type, for callers that keep frames alive across grabs. */
  public BufferedImage newFrameBuffer() {
    Dimension size = getFrameSize();
    return new BufferedImage(size.width, size.height, BufferedImage.TYPE_3BYTE_BGR);
  }

  /**
   * Freezes the frame size and, for a full-window capture, masks what only re-renders on demand.
   */
  public void prepare() {
    frameSize = computeFrameSize();
    if (needsSessionMasking()) {
      previousSessionMasking = IdentityMask.isSessionMasking();
      IdentityMask.setSessionMasking(true);
    }
  }

  public void release() {
    if (needsSessionMasking()) {
      IdentityMask.setSessionMasking(previousSessionMasking);
    }
    paintBuffer = null;
    frameBuffer = null;
  }

  /**
   * A window capture shows docking tab titles and explorer labels, which are built once and cached
   * rather than resolved per paint, so a scoped mask would not reach them.
   */
  private boolean needsSessionMasking() {
    return profile != null && scope == CaptureScope.APPLICATION_WINDOW;
  }

  /**
   * Grabs one frame into the grabber's own buffer, which the next call overwrites. Must run on the
   * EDT.
   */
  public BufferedImage grab() {
    if (frameBuffer == null) {
      frameBuffer = newFrameBuffer();
    }
    return grab(frameBuffer);
  }

  /** Grabs one frame into {@code target}, a buffer from {@link #newFrameBuffer()}. On the EDT. */
  public BufferedImage grab(BufferedImage target) {
    BufferedImage frame =
        profile == null
            ? capture(target)
            : IdentityMask.forProfile(profile).callMasked(() -> capture(target));
    if (frame != null && pointerVisible) {
      drawPointer(frame);
    }
    return frame;
  }

  private BufferedImage capture(BufferedImage target) {
    Component component = getTarget();
    if (component == null || component.getWidth() <= 0 || component.getHeight() <= 0) {
      return null;
    }
    ViewCanvas<?> canvas = currentView();
    if (scope == CaptureScope.VIEW && canvas instanceof DefaultView2d<?> view2d) {
      RenderedImage rendered =
          ViewTransferHandler.createComponentImage(view2d, displayProfile.apply(view2d));
      BufferedImage full = ImageConversion.convertRenderedImage(rendered);
      Rectangle area = imageArea(view2d);
      if (area != null) {
        full = full.getSubimage(area.x, area.y, area.width, area.height);
      }
      return fit(full, target);
    }
    Runnable restore =
        scope == CaptureScope.VIEW && canvas instanceof LiveCaptureView live
            ? live.beginCapture(displayProfile.apply(canvas))
            : () -> {};
    try {
      if (component.getWidth() == target.getWidth()
          && component.getHeight() == target.getHeight()) {
        paint(component, target);
        return target;
      }
      paint(component, paintBuffer(component));
      return fit(paintBuffer, target);
    } finally {
      restore.run();
    }
  }

  private BufferedImage paintBuffer(Component component) {
    if (paintBuffer == null
        || paintBuffer.getWidth() != component.getWidth()
        || paintBuffer.getHeight() != component.getHeight()) {
      paintBuffer =
          new BufferedImage(
              component.getWidth(), component.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
    }
    return paintBuffer;
  }

  private static void paint(Component component, BufferedImage image) {
    Graphics2D g2d = image.createGraphics();
    try {
      component.paint(g2d);
    } finally {
      g2d.dispose();
    }
  }

  /** Scales into the target, keeping the aspect ratio and padding with black. */
  private static BufferedImage fit(BufferedImage source, BufferedImage target) {
    if (source == target) {
      return target;
    }
    Graphics2D g2d = target.createGraphics();
    try {
      Placement placement = Placement.of(source, target);
      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      if (placement.w() != target.getWidth() || placement.h() != target.getHeight()) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, target.getWidth(), target.getHeight());
      }
      g2d.drawImage(source, placement.x(), placement.y(), placement.w(), placement.h(), null);
    } finally {
      g2d.dispose();
    }
    return target;
  }

  /** Where the mouse is in the frame, or {@code null} when it is outside the captured area. */
  private Point pointerInFrame(BufferedImage frame) {
    PointerInfo info = MouseInfo.getPointerInfo();
    Component component = getTarget();
    if (info == null || component == null) {
      return null;
    }
    Point p = info.getLocation();
    SwingUtilities.convertPointFromScreen(p, component);
    if (p.x < 0 || p.y < 0 || p.x >= component.getWidth() || p.y >= component.getHeight()) {
      return null;
    }
    Placement placement = Placement.of(component.getWidth(), component.getHeight(), frame);
    return new Point(
        placement.x() + (int) Math.round(p.x * placement.ratio()),
        placement.y() + (int) Math.round(p.y * placement.ratio()));
  }

  /** A small arrow, white with a dark outline so it reads on any background. */
  private void drawPointer(BufferedImage frame) {
    Point p = pointerInFrame(frame);
    if (p == null) {
      return;
    }
    int s = Math.max(8, frame.getHeight() / 60);
    int[] xs = {p.x, p.x, p.x + s * 3 / 10, p.x + s * 6 / 10, p.x + s * 8 / 10, p.x + s / 2};
    int[] ys = {p.y, p.y + s, p.y + s * 7 / 10, p.y + s * 11 / 10, p.y + s, p.y + s * 6 / 10};
    Graphics2D g2d = frame.createGraphics();
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setColor(Color.WHITE);
      g2d.fillPolygon(xs, ys, xs.length);
      g2d.setColor(Color.DARK_GRAY);
      g2d.drawPolygon(xs, ys, xs.length);
    } finally {
      g2d.dispose();
    }
  }

  /** Where a source of one size lands in a frame of another, aspect ratio kept, centered. */
  record Placement(int x, int y, int w, int h, double ratio) {

    static Placement of(BufferedImage source, BufferedImage target) {
      return of(source.getWidth(), source.getHeight(), target);
    }

    static Placement of(int sourceWidth, int sourceHeight, BufferedImage target) {
      double ratio =
          Math.min(
              target.getWidth() / (double) sourceWidth, target.getHeight() / (double) sourceHeight);
      int w = Math.max(1, (int) Math.round(sourceWidth * ratio));
      int h = Math.max(1, (int) Math.round(sourceHeight * ratio));
      return new Placement((target.getWidth() - w) / 2, (target.getHeight() - h) / 2, w, h, ratio);
    }
  }
}
