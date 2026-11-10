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
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.color.IccImageFiles;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DisplayProfile;
import org.weasis.core.ui.editor.image.LiveCaptureView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewTransferHandler;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;

/**
 * The one render of a capture, shared by the animation grabber and the {@code capture} verbs: a 2D
 * view re-rendered off-screen, a 3D view, a layout or the application window painted as displayed
 * (the only way to pick up an OpenGL frame), then the same bilinear downscale and the same file
 * writer. No masking is bound here: the caller binds its own around the call.
 */
public final class ViewCapture {

  /** What a capture covers, as the verbs name it. */
  public enum Scope {
    VIEW,
    LAYOUT,
    WINDOW;

    public CaptureScope toCaptureScope() {
      return switch (this) {
        case VIEW -> CaptureScope.VIEW;
        case LAYOUT -> CaptureScope.CONTAINER;
        case WINDOW -> CaptureScope.APPLICATION_WINDOW;
      };
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** Where a source lands in a target of another size: letterboxed, centered. */
  public record Placement(int x, int y, int w, int h, double ratio) {

    public static Placement of(BufferedImage source, BufferedImage target) {
      return of(source.getWidth(), source.getHeight(), target);
    }

    public static Placement of(int sourceWidth, int sourceHeight, BufferedImage target) {
      double ratio =
          Math.min(
              target.getWidth() / (double) sourceWidth, target.getHeight() / (double) sourceHeight);
      int w = Math.max(1, (int) Math.round(sourceWidth * ratio));
      int h = Math.max(1, (int) Math.round(sourceHeight * ratio));
      return new Placement((target.getWidth() - w) / 2, (target.getHeight() - h) / 2, w, h, ratio);
    }
  }

  private ViewCapture() {}

  /** The component a scope captures, null when there is none. */
  public static Component target(CaptureScope scope, ViewCanvas<?> view, Component layout) {
    return switch (scope) {
      case VIEW -> view == null ? null : view.getJComponent();
      case CONTAINER -> layout;
      case APPLICATION_WINDOW -> GuiUtils.getUICore().getApplicationWindow();
    };
  }

  /**
   * Renders a scope at its on-screen size into a new {@code TYPE_3BYTE_BGR} image: a 2D view
   * through its off-screen renderer, anything else painted as displayed. Null when the component
   * has no size. On the EDT.
   *
   * @param profile what the view shows for the capture, null for what it shows on screen
   */
  public static BufferedImage render(
      CaptureScope scope,
      ViewCanvas<?> view,
      Component layout,
      Function<ViewCanvas<?>, DisplayProfile> profile) {
    Component component = target(scope, view, layout);
    if (component == null || component.getWidth() <= 0 || component.getHeight() <= 0) {
      return null;
    }
    DisplayProfile shown = view == null || profile == null ? null : profile.apply(view);
    if (scope == CaptureScope.VIEW && view instanceof DefaultView2d<?> view2d) {
      RenderedImage rendered = ViewTransferHandler.createComponentImage(view2d, shown);
      return ImageConversion.convertRenderedImage(rendered);
    }
    Runnable restore =
        scope == CaptureScope.VIEW && view instanceof LiveCaptureView live
            ? live.beginCapture(shown)
            : () -> {};
    try {
      BufferedImage image =
          new BufferedImage(
              component.getWidth(), component.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
      paint(component, image);
      return image;
    } finally {
      restore.run();
    }
  }

  /** Paints a component as displayed, a window with the dialogs and menus it owns. */
  public static void paint(Component component, BufferedImage image) {
    Graphics2D g2d = image.createGraphics();
    try {
      component.paint(g2d);
      if (component instanceof Window window && window.isShowing()) {
        paintOwnedWindows(window, window.getLocationOnScreen(), g2d);
      }
    } finally {
      g2d.dispose();
    }
  }

  /**
   * Paints the dialogs, menus and tooltips the window owns over it, where they are on screen. They
   * are windows of their own, not children of the frame, so painting the frame leaves them out.
   * Owned windows come in creation order, which puts a dialog opened from another one on top.
   */
  private static void paintOwnedWindows(Window owner, Point origin, Graphics2D g2d) {
    for (Window owned : owner.getOwnedWindows()) {
      if (!owned.isShowing() || owned.getWidth() <= 0 || owned.getHeight() <= 0) {
        continue;
      }
      Point location = owned.getLocationOnScreen();
      Graphics2D g = (Graphics2D) g2d.create();
      try {
        g.translate(location.x - origin.x, location.y - origin.y);
        owned.paint(g);
      } finally {
        g.dispose();
      }
      paintOwnedWindows(
          owned,
          location,
          (Graphics2D)
              g2d.create(
                  location.x - origin.x,
                  location.y - origin.y,
                  owned.getWidth(),
                  owned.getHeight()));
    }
  }

  /**
   * The factor that brings {@code width × height} under {@code maxPixels}; 1 when it already is.
   */
  public static double scaleFor(int width, int height, int maxPixels) {
    long pixels = (long) width * height;
    return pixels <= maxPixels ? 1.0 : Math.sqrt((double) maxPixels / pixels);
  }

  /** The image scaled down bilinearly; the image itself when the scale is 1 or more. */
  public static BufferedImage scale(BufferedImage source, double scale) {
    if (scale >= 1.0) {
      return source;
    }
    int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = dst.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(source, 0, 0, w, h, null);
    } finally {
      g.dispose();
    }
    return dst;
  }

  /** The source fitted into {@code target}, letterboxed on black when the ratios differ. */
  public static BufferedImage fit(BufferedImage source, BufferedImage target) {
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

  /** The image format of a capture file by its extension: png or jpg. */
  public static String formatOf(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1);
    return switch (ext) {
      case "png" -> "png"; // NON-NLS
      case "jpg", "jpeg" -> "jpg"; // NON-NLS
      default -> throw new IllegalArgumentException("--out must end with .png, .jpg or .jpeg");
    };
  }

  /** Writes a capture; a color capture carries the display profile when the format can embed it. */
  public static void write(BufferedImage image, String format, Path file) throws IOException {
    IccProfile profile = IccImageFiles.displayedProfile().orElse(null);
    if (profile != null
        && image.getRaster().getNumBands() == 3
        && IccImageFiles.canEmbed(file, profile)) {
      ImageCV mat = ImageConversion.toMat(image);
      try {
        if (!IccImageFiles.write(mat, file, null, profile)) {
          throw new IOException("Cannot write " + file);
        }
      } finally {
        mat.release();
      }
    } else if (!ImageIO.write(image, format, file.toFile())) {
      throw new IOException("No image writer for " + format);
    }
  }
}
