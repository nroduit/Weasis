/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.Timer;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.dicom.wsi.tile.TilePlan;
import org.weasis.dicom.wsi.tile.TileScheduler;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * Paints a slide from its tiles. The model space is the base level pixels, as for the graphics of
 * the view: the same model-to-view transform places the tiles and the measurements, whatever level
 * is shown. Each paint draws the slide background and the tiles of the planned level; while some of
 * them are missing, the coarser tiles and, when they cannot cover the view, the thumbnail go under
 * them, so that a view is filled at once and sharpens as the tiles arrive.
 *
 * <p>Each tile is drawn on whole device pixels: its corners are snapped, so that neighbours abut
 * exactly however the pipeline rounds, and none of its edge pixels is filtered against the
 * transparent outside. A minified tile is filtered from a copy halved until it is minified by less
 * than two; a magnified tile keeps its pixels.
 *
 * <p>To use on the event dispatch thread; tiles that arrive call the repaint callback from worker
 * threads.
 */
public final class TiledImageLayer implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TiledImageLayer.class);

  /** The paint images of one tile: the tile, then its halves built as needed. */
  private static final class PaintBuffer {
    private final PlanarImage source;
    private final BufferedImage[] halvings = new BufferedImage[MAX_HALVINGS + 1];

    PaintBuffer(PlanarImage source, BufferedImage image) {
      this.source = source;
      halvings[0] = image;
    }

    BufferedImage halved(int times) {
      for (int i = 1; i <= times; i++) {
        if (halvings[i] == null) {
          halvings[i] = halve(halvings[i - 1]);
        }
      }
      return halvings[times];
    }
  }

  /** Where an animation of the view goes: its viewport in base level pixels and its downsample. */
  public record PlanTarget(Rectangle2D viewport, double downsample) {}

  /** Least time between two plans while the view moves. */
  private static final long PLAN_DELAY_NANOS = 100_000_000L;

  /** Time without motion after which the view is still. */
  private static final int SETTLE_MS = 150;

  private static final long SETTLE_NANOS = SETTLE_MS * 1_000_000L;

  /** Most tiles of one level drawn as filler. */
  private static final int MAX_FALLBACK_TILES = 512;

  /** Most halvings of a tile drawn minified; beyond, a coarser level is planned anyway. */
  static final int MAX_HALVINGS = 3;

  private static final double AXIS_EPSILON = 1e-9;

  private final TileScheduler scheduler;
  private final Runnable repaint;
  private final Function<PlanarImage, BufferedImage> converter;
  private final Executor executor;
  private final Map<TileId, PaintBuffer> paintBuffers = new HashMap<>();

  private int focalPlane;
  private int opticalPath;
  private Rectangle2D plannedViewport;
  private double plannedDownsample;
  private long plannedAt;
  private long planDelayNanos = PLAN_DELAY_NANOS;
  private Rectangle2D paintedViewport;
  private double paintedDownsample;
  private long lastMotion;
  private final Timer settleTimer;
  private boolean thumbnailRequested;
  private volatile BufferedImage thumbnail;

  /**
   * @param scheduler the tile loading of the view, closed with this layer; its listener should call
   *     the repaint callback
   * @param repaint asks the view to repaint, from any thread
   * @param executor reads the thumbnail in the background
   */
  public TiledImageLayer(TileScheduler scheduler, Runnable repaint, Executor executor) {
    this(scheduler, repaint, executor, TiledImageLayer::toPaintImage);
  }

  TiledImageLayer(
      TileScheduler scheduler,
      Runnable repaint,
      Executor executor,
      Function<PlanarImage, BufferedImage> converter) {
    this.scheduler = Objects.requireNonNull(scheduler);
    this.repaint = Objects.requireNonNull(repaint);
    this.executor = Objects.requireNonNull(executor);
    this.converter = Objects.requireNonNull(converter);
    this.settleTimer = new Timer(SETTLE_MS, _ -> repaint.run());
    this.settleTimer.setRepeats(false);
  }

  public Slide slide() {
    return scheduler.source().slide();
  }

  /** The focal plane and optical path shown, as indices of the levels. */
  public void setPlane(int focalPlane, int opticalPath) {
    this.focalPlane = focalPlane;
    this.opticalPath = opticalPath;
    plannedViewport = null;
  }

  /** The tiles planned by the last paint. */
  public TilePlan plan() {
    return scheduler.plan();
  }

  /** The thumbnail of the slide once read; asks for it on the first call. */
  public Optional<BufferedImage> thumbnail() {
    BufferedImage image = thumbnail;
    if (image == null) {
      requestThumbnail();
    }
    return Optional.ofNullable(image);
  }

  /** How many painted tiles of the plan are loaded. */
  public long readyTiles() {
    return scheduler.plan().painted().stream().filter(t -> scheduler.tile(t).isPresent()).count();
  }

  /**
   * Waits, off the event dispatch thread, until every painted tile of the plan is loaded or has
   * failed, at most that long.
   *
   * @return whether they all are
   */
  public boolean awaitPlanned(long millis) {
    long deadline = System.nanoTime() + millis * 1_000_000L;
    while (true) {
      boolean complete =
          scheduler.plan().painted().stream()
              .allMatch(t -> scheduler.tile(t).isPresent() || scheduler.hasFailed(t));
      if (complete || System.nanoTime() >= deadline) {
        return complete;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  /**
   * @param g2d graphics whose user space is the view pixels
   * @param modelToView the transform of the view, from base level pixels to view pixels
   * @param viewBounds the area of the view to paint, in view pixels
   */
  public void paint(Graphics2D g2d, AffineTransform modelToView, Rectangle2D viewBounds) {
    paint(g2d, modelToView, viewBounds, null);
  }

  /**
   * @param target where an animation of the view goes, to plan the tiles of its end rather than of
   *     each frame; null for the view as it is
   */
  public void paint(
      Graphics2D g2d, AffineTransform modelToView, Rectangle2D viewBounds, PlanTarget target) {
    Slide slide = slide();
    Rectangle2D slideBounds = new Rectangle2D.Double(0, 0, slide.width(), slide.height());
    AffineTransform viewToModel;
    try {
      viewToModel = modelToView.createInverse();
    } catch (NoninvertibleTransformException e) {
      LOGGER.debug("View transform cannot be inverted", e);
      return;
    }
    Rectangle2D viewport =
        viewToModel
            .createTransformedShape(viewBounds)
            .getBounds2D()
            .createIntersection(slideBounds);
    double downsample = 1.0 / GeomUtil.extractScalingFactor(modelToView);
    long now = System.nanoTime();
    boolean moving = trackMotion(viewport, downsample, now) || target != null;
    TilePlan plan =
        target == null
            ? plan(viewport, downsample, now)
            : plan(target.viewport().createIntersection(slideBounds), target.downsample(), now);

    List<TileId> borrowed = new ArrayList<>();
    Graphics2D g = (Graphics2D) g2d.create();
    try {
      g.transform(modelToView);
      g.clip(slideBounds);
      g.setColor(slide.background());
      g.fill(slideBounds);
      // Once every visible tile is there (pinned, they stay) and the view is still, they are the
      // only thing to draw; otherwise the cached tiles of every level fill in under them, the
      // coarsest first, and the thumbnail under those when they cannot cover the view
      boolean complete = plan.visible().stream().allMatch(t -> scheduler.tile(t).isPresent());
      if (plan.truncated() || !complete || moving) {
        if (plan.truncated() || plan.coarse().isEmpty()) {
          drawThumbnail(g, slide);
        }
        if (!drawCachedLevels(g, slide, viewport, plan.level(), borrowed)) {
          drawTiles(g, slide, plan.visible());
        }
      } else {
        drawTiles(g, slide, plan.visible());
      }
    } finally {
      g.dispose();
      borrowed.forEach(scheduler::giveBack);
    }
    Set<TileId> drawn = new HashSet<>(plan.painted());
    drawn.addAll(borrowed);
    paintBuffers.keySet().retainAll(drawn);
  }

  /** Stops loading and frees the tiles of the view. */
  @Override
  public void close() {
    settleTimer.stop();
    scheduler.close();
    paintBuffers.clear();
    thumbnail = null;
  }

  /** Disables the delay between two plans, for the tests. */
  void setPlanDelay(long nanos) {
    planDelayNanos = nanos;
  }

  // Whether the view changed within the settle time; a still view gets one more paint, with
  // filtering and a plan of what it shows
  private boolean trackMotion(Rectangle2D viewport, double downsample, long now) {
    if (!viewport.equals(paintedViewport) || downsample != paintedDownsample) {
      paintedViewport = viewport;
      paintedDownsample = downsample;
      lastMotion = now;
      settleTimer.restart();
    }
    return now - lastMotion < SETTLE_NANOS;
  }

  // A new plan when the view changed, at most one per delay while it moves; a repaint for an
  // arrived tile keeps the queue
  private TilePlan plan(Rectangle2D viewport, double downsample, long now) {
    if (viewport.equals(plannedViewport) && downsample == plannedDownsample) {
      return scheduler.plan();
    }
    if (plannedViewport != null && now - plannedAt < planDelayNanos) {
      settleTimer.restart(); // plans what the view shows once it is still
      return scheduler.plan();
    }
    Point2D motion =
        plannedViewport == null
            ? null
            : new Point2D.Double(
                viewport.getCenterX() - plannedViewport.getCenterX(),
                viewport.getCenterY() - plannedViewport.getCenterY());
    plannedViewport = viewport;
    plannedDownsample = downsample;
    plannedAt = now;
    return scheduler.update(viewport, downsample, focalPlane, opticalPath, motion);
  }

  /**
   * Draws the cached tiles of every level over the viewport, the coarsest first and the planned
   * level last: what the view can show while the planned tiles load or the plan lags a moving view.
   * They are borrowed from the cache until the paint ends.
   *
   * @return whether the planned level was drawn; a level with too many tiles over the viewport is
   *     left out
   */
  private boolean drawCachedLevels(
      Graphics2D g, Slide slide, Rectangle2D viewport, int planned, List<TileId> borrowed) {
    for (int index = slide.levels().size() - 1; index >= 0; index--) {
      if (index != planned) {
        drawCachedLevel(g, slide, index, viewport, borrowed);
      }
    }
    return planned >= 0 && drawCachedLevel(g, slide, planned, viewport, borrowed);
  }

  private boolean drawCachedLevel(
      Graphics2D g, Slide slide, int index, Rectangle2D viewport, List<TileId> borrowed) {
    PyramidLevel level = slide.levels().get(index);
    Rectangle range = TilePlan.tileRange(level, viewport);
    if ((long) range.width * range.height > MAX_FALLBACK_TILES) {
      return false; // a fine level zoomed out: too many tiles for a filler
    }
    for (int row = range.y; row < range.y + range.height; row++) {
      for (int col = range.x; col < range.x + range.width; col++) {
        TileId tile = new TileId(index, col, row, focalPlane, opticalPath);
        Optional<PlanarImage> image = scheduler.borrow(tile);
        if (image.isPresent()) {
          borrowed.add(tile);
          drawTile(g, slide, tile, image.get());
        }
      }
    }
    return true;
  }

  private void drawTiles(Graphics2D g, Slide slide, List<TileId> tiles) {
    for (TileId tile : tiles) {
      scheduler.tile(tile).ifPresent(image -> drawTile(g, slide, tile, image));
    }
  }

  private void drawTile(Graphics2D g, Slide slide, TileId tile, PlanarImage image) {
    PyramidLevel level = slide.levels().get(tile.level());
    Rectangle bounds = level.tileBounds(tile.column(), tile.row());
    if (bounds.isEmpty()) {
      return;
    }
    PaintBuffer buffer = paintBuffer(tile, image, bounds);
    BufferedImage full = buffer.halved(0);
    double sx = level.downsampleX();
    double sy = level.downsampleY();
    AffineTransform device = g.getTransform();
    boolean aligned = isAxisAligned(device);
    Point2D origin = corner(device, bounds.x * sx, bounds.y * sy, aligned);
    Point2D right = corner(device, (bounds.x + full.getWidth()) * sx, bounds.y * sy, aligned);
    Point2D bottom = corner(device, bounds.x * sx, (bounds.y + full.getHeight()) * sy, aligned);
    // Device pixels per tile pixel: magnified tiles keep their pixels, minified ones are filtered
    // from the copy halved to less than two tile pixels per device pixel
    double pixels =
        Math.max(
            origin.distance(right) / full.getWidth(), origin.distance(bottom) / full.getHeight());
    boolean magnified = pixels > 1.0;
    BufferedImage paint = buffer.halved(magnified ? 0 : halvings(pixels));
    if (!aligned && !magnified) {
      // A filter taps outside the tile at the edge pixels of a rotated one and the background
      // would show through: one device pixel outward on every side, covered by the neighbour
      // drawn after
      Point2D u = unit(origin, right);
      Point2D v = unit(origin, bottom);
      origin = moved(origin, -u.getX() - v.getX(), -u.getY() - v.getY());
      right = moved(right, u.getX() - v.getX(), u.getY() - v.getY());
      bottom = moved(bottom, -u.getX() + v.getX(), -u.getY() + v.getY());
    }
    AffineTransform placement =
        new AffineTransform(
            (right.getX() - origin.getX()) / paint.getWidth(),
            (right.getY() - origin.getY()) / paint.getWidth(),
            (bottom.getX() - origin.getX()) / paint.getHeight(),
            (bottom.getY() - origin.getY()) / paint.getHeight(),
            origin.getX(),
            origin.getY());
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        magnified
            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setTransform(new AffineTransform());
    g.drawImage(paint, placement, null);
    g.setTransform(device);
  }

  // Whether the device axes are the model axes, possibly swapped or flipped
  private static boolean isAxisAligned(AffineTransform t) {
    return (Math.abs(t.getShearX()) < AXIS_EPSILON && Math.abs(t.getShearY()) < AXIS_EPSILON)
        || (Math.abs(t.getScaleX()) < AXIS_EPSILON && Math.abs(t.getScaleY()) < AXIS_EPSILON);
  }

  // A model point in device pixels, on a whole pixel when the axes are aligned: neighbours
  // compute the same corner and abut exactly
  private static Point2D corner(AffineTransform device, double x, double y, boolean snap) {
    Point2D p = device.transform(new Point2D.Double(x, y), null);
    return snap ? new Point2D.Double(Math.rint(p.getX()), Math.rint(p.getY())) : p;
  }

  private static Point2D unit(Point2D from, Point2D to) {
    double length = from.distance(to);
    return length > 0
        ? new Point2D.Double((to.getX() - from.getX()) / length, (to.getY() - from.getY()) / length)
        : new Point2D.Double();
  }

  private static Point2D moved(Point2D p, double dx, double dy) {
    return new Point2D.Double(p.getX() + dx, p.getY() + dy);
  }

  /** Halvings of a tile drawn at this many device pixels per tile pixel, below one. */
  static int halvings(double devicePixels) {
    int count = 0;
    while (count < MAX_HALVINGS && devicePixels * (2 << count) <= 1.0) {
      count++;
    }
    return count;
  }

  private PaintBuffer paintBuffer(TileId tile, PlanarImage image, Rectangle bounds) {
    PaintBuffer buffer = paintBuffers.get(tile);
    if (buffer == null || buffer.source != image) {
      buffer = new PaintBuffer(image, crop(converter.apply(image), bounds.width, bounds.height));
      paintBuffers.put(tile, buffer);
    }
    return buffer;
  }

  // An edge tile is decoded at the full tile size: its padding beyond the level is left out
  private static BufferedImage crop(BufferedImage image, int width, int height) {
    int w = Math.min(width, image.getWidth());
    int h = Math.min(height, image.getHeight());
    if (w == image.getWidth() && h == image.getHeight()) {
      return image;
    }
    BufferedImage sub = image.getSubimage(0, 0, w, h);
    return new BufferedImage(
        sub.getColorModel(), sub.copyData(null), sub.isAlphaPremultiplied(), null);
  }

  /** The image at half size, each pixel the mean of a 2 × 2 block. */
  static BufferedImage halve(BufferedImage image) {
    int w = Math.max(1, image.getWidth() / 2);
    int h = Math.max(1, image.getHeight() / 2);
    int channels = packedChannels(image);
    if (channels > 0 && image.getWidth() >= 2 && image.getHeight() >= 2) {
      return halveBytes(image, w, h, channels);
    }
    int type =
        image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : image.getType();
    BufferedImage result = new BufferedImage(w, h, type);
    Graphics2D g = result.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, 0, 0, w, h, 0, 0, 2 * w, 2 * h, null);
    g.dispose();
    return result;
  }

  // Bytes per pixel of an 8-bit gray or BGR image whose rows are packed one after the other, as
  // the paint images are built; 0 for any other layout
  private static int packedChannels(BufferedImage image) {
    int type = image.getType();
    if (type != BufferedImage.TYPE_BYTE_GRAY && type != BufferedImage.TYPE_3BYTE_BGR) {
      return 0;
    }
    int channels = type == BufferedImage.TYPE_BYTE_GRAY ? 1 : 3;
    boolean packed =
        image.getRaster().getDataBuffer() instanceof DataBufferByte buffer
            && buffer.getNumBanks() == 1
            && buffer.getOffset() == 0
            && image.getSampleModel() instanceof ComponentSampleModel model
            && model.getScanlineStride() == image.getWidth() * channels
            && model.getPixelStride() == channels
            && image.getRaster().getSampleModelTranslateX() == 0
            && image.getRaster().getSampleModelTranslateY() == 0;
    return packed ? channels : 0;
  }

  private static BufferedImage halveBytes(BufferedImage image, int w, int h, int channels) {
    byte[] src = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    int stride = image.getWidth() * channels;
    BufferedImage result = new BufferedImage(w, h, image.getType());
    byte[] dst = ((DataBufferByte) result.getRaster().getDataBuffer()).getData();
    for (int y = 0; y < h; y++) {
      int top = 2 * y * stride;
      int out = y * w * channels;
      for (int x = 0; x < w; x++) {
        int left = top + 2 * x * channels;
        for (int c = 0; c < channels; c++) {
          int sum =
              (src[left + c] & 0xFF)
                  + (src[left + channels + c] & 0xFF)
                  + (src[left + stride + c] & 0xFF)
                  + (src[left + stride + channels + c] & 0xFF);
          dst[out + x * channels + c] = (byte) ((sum + 2) >> 2);
        }
      }
    }
    return result;
  }

  // As the paint image of RenderedImageLayer: 8-bit gray and BGR, the tiles of nearly every slide,
  // have a Java2D type with the memory layout of the Mat and are copied as they are
  private static BufferedImage toPaintImage(PlanarImage image) {
    int type;
    if (image.type() == CvType.CV_8UC1) {
      type = BufferedImage.TYPE_BYTE_GRAY;
    } else if (image.type() == CvType.CV_8UC3) {
      type = BufferedImage.TYPE_3BYTE_BGR;
    } else {
      return ImageConversion.toBufferedImage(image);
    }
    BufferedImage result = new BufferedImage(image.width(), image.height(), type);
    image.get(0, 0, ((DataBufferByte) result.getRaster().getDataBuffer()).getData());
    return result;
  }

  // The thumbnail is the whole slide at low resolution (PS3.3 C.8.12.4.1.1): stretched over the
  // slide, it fills what the tiles leave
  private void drawThumbnail(Graphics2D g, Slide slide) {
    BufferedImage image = thumbnail;
    if (image == null) {
      requestThumbnail();
      return;
    }
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(
        image,
        AffineTransform.getScaleInstance(
            (double) slide.width() / image.getWidth(), (double) slide.height() / image.getHeight()),
        null);
  }

  private void requestThumbnail() {
    if (thumbnailRequested || slide().associatedImage(WsiImageType.THUMBNAIL).isEmpty()) {
      return;
    }
    thumbnailRequested = true;
    executor.execute(
        () -> {
          try {
            var image = scheduler.source().readAssociatedImage(WsiImageType.THUMBNAIL);
            if (image.isPresent()) {
              thumbnail = converter.apply(image.get());
              image.get().release();
              repaint.run();
            }
          } catch (Exception e) {
            LOGGER.warn("Cannot read the thumbnail", e);
          }
        });
  }
}
