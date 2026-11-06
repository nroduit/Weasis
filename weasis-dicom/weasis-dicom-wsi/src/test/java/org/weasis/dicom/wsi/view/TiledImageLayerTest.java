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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.weasis.dicom.wsi.model.SyntheticSlides.instance;
import static org.weasis.dicom.wsi.model.SyntheticSlides.sparse;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;
import org.weasis.dicom.wsi.model.SyntheticSlides;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.dicom.wsi.tile.TileCache;
import org.weasis.dicom.wsi.tile.TileScheduler;
import org.weasis.opencv.data.PlanarImage;

/** Tiles painted into an image with colours that tell their level and column apart. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TiledImageLayerTest {

  private static final Color L0_EVEN = Color.RED;
  private static final Color L0_ODD = Color.ORANGE;
  private static final Color L1_EVEN = Color.BLUE;
  private static final Color L1_ODD = Color.CYAN;
  private static final Color L2_EVEN = Color.GREEN;
  private static final Color L2_ODD = Color.MAGENTA;
  private static final Color THUMBNAIL = Color.YELLOW;
  private static final Color CANVAS = Color.BLACK;

  /** Mock tiles, remembered so that the converter can paint each one in its colour. */
  private static final class FakeSource implements TileSource {
    final Slide slide;
    final Map<PlanarImage, TileId> tiles = Collections.synchronizedMap(new IdentityHashMap<>());
    final List<Integer> brokenLevels = new ArrayList<>();
    final AtomicInteger reads = new AtomicInteger();
    boolean striped;

    FakeSource(Attributes... headers) {
      this.slide = SlideBuilder.build(List.of(headers)).getFirst();
    }

    @Override
    public Slide slide() {
      return slide;
    }

    @Override
    public Optional<PlanarImage> readTile(TileId tile) throws IOException {
      reads.incrementAndGet();
      if (brokenLevels.contains(tile.level())) {
        throw new IOException("broken"); // NON-NLS
      }
      PlanarImage image = mock(PlanarImage.class);
      when(image.physicalBytes()).thenReturn(1000L);
      tiles.put(image, tile);
      return Optional.of(image);
    }

    @Override
    public Optional<PlanarImage> readAssociatedImage(WsiImageType type) {
      return Optional.of(mock(PlanarImage.class));
    }
  }

  private final List<TiledImageLayer> layers = new ArrayList<>();
  private final AtomicInteger repaints = new AtomicInteger();

  @AfterEach
  void tearDown() {
    layers.forEach(TiledImageLayer::close);
  }

  private TiledImageLayer layer(FakeSource source) {
    TileScheduler scheduler =
        new TileScheduler(
            source, TileCache.getInstance(), Runnable::run, 1, t -> repaints.incrementAndGet());
    TiledImageLayer layer =
        new TiledImageLayer(
            scheduler, repaints::incrementAndGet, Runnable::run, i -> paintOf(source, i));
    layer.setPlanDelay(0);
    layers.add(layer);
    return layer;
  }

  private static BufferedImage paintOf(FakeSource source, PlanarImage image) {
    TileId tile = source.tiles.get(image);
    int size = tile == null ? 64 : source.slide.levels().get(tile.level()).tileWidth();
    Color color;
    if (tile == null) {
      color = THUMBNAIL;
    } else if (tile.level() == 0) {
      color = tile.column() % 2 == 0 ? L0_EVEN : L0_ODD;
    } else if (tile.level() == 1) {
      color = tile.column() % 2 == 0 ? L1_EVEN : L1_ODD;
    } else {
      color = tile.column() % 2 == 0 ? L2_EVEN : L2_ODD;
    }
    BufferedImage paint = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = paint.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, size, size);
    if (source.striped && tile != null) {
      // Black and white columns: a tile that aliases when sampled instead of averaged
      for (int x = 0; x < size; x += 2) {
        g.setColor(Color.BLACK);
        g.fillRect(x, 0, 1, size);
        g.setColor(Color.WHITE);
        g.fillRect(x + 1, 0, 1, size);
      }
    }
    g.dispose();
    return paint;
  }

  private static BufferedImage paint(
      TiledImageLayer layer, AffineTransform modelToView, int w, int h) {
    return paint(layer, modelToView, w, h, false);
  }

  private static BufferedImage paint(
      TiledImageLayer layer, AffineTransform modelToView, int w, int h, boolean antialiasing) {
    BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = canvas.createGraphics();
    if (antialiasing) {
      // As the 2D view paints its graphics
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    g.setColor(CANVAS);
    g.fillRect(0, 0, w, h);
    layer.paint(g, modelToView, new Rectangle(0, 0, w, h));
    g.dispose();
    return canvas;
  }

  private static Color at(BufferedImage image, int x, int y) {
    return new Color(image.getRGB(x, y));
  }

  private static FakeSource twoLevels() {
    return new FakeSource(
        tiledFull("L0", 1024, 1024, 256), tiledFull("L1", 512, 512, 256)); // NON-NLS
  }

  @Test
  void tiles_of_the_zoom_level_are_placed_on_the_base_pixels() {
    TiledImageLayer layer = layer(twoLevels());
    BufferedImage full = paint(layer, new AffineTransform(), 600, 600);
    BufferedImage half = paint(layer, AffineTransform.getScaleInstance(0.5, 0.5), 600, 600);
    assertAll(
        () -> assertEquals(L0_EVEN, at(full, 250, 10)),
        () -> assertEquals(L0_ODD, at(full, 260, 10), "the next tile starts at x = 256"),
        () -> assertEquals(L1_EVEN, at(half, 250, 10), "level 1 at downsample 2"),
        () -> assertEquals(L1_ODD, at(half, 260, 10)),
        () -> assertEquals(CANVAS, at(half, 520, 10), "beyond the slide"));
  }

  @Test
  void the_coarser_level_fills_in_for_tiles_not_loaded() {
    FakeSource source = twoLevels();
    source.brokenLevels.add(0);
    BufferedImage image = paint(layer(source), new AffineTransform(), 600, 600);
    assertEquals(L1_EVEN, at(image, 100, 100));
  }

  @Test
  void edge_tiles_are_clipped_to_the_slide() {
    BufferedImage image =
        paint(
            layer(new FakeSource(tiledFull("E", 1000, 700, 256))), // NON-NLS
            new AffineTransform(),
            1100,
            800);
    assertAll(
        () -> assertEquals(L0_ODD, at(image, 990, 690), "column 3"),
        () -> assertEquals(CANVAS, at(image, 1010, 100)),
        () -> assertEquals(CANVAS, at(image, 100, 750)));
  }

  @Test
  void a_sparse_level_shows_the_slide_background_where_no_frame_is() {
    FakeSource source =
        new FakeSource(sparse("S", 1024, 1024, 512, new double[] {1, 1})); // NON-NLS
    BufferedImage image = paint(layer(source), new AffineTransform(), 1024, 1024);
    assertAll(
        () -> assertEquals(L0_EVEN, at(image, 100, 100)),
        () -> assertEquals(Color.WHITE, at(image, 700, 700)));
  }

  @Test
  void the_thumbnail_fills_a_single_level_slide_zoomed_out() {
    FakeSource source =
        new FakeSource(
            tiledFull("BIG", 65536, 65536, 512),
            instance("TH", "THUMBNAIL", 64, 64, 64)); // NON-NLS
    TiledImageLayer layer = layer(source);
    AffineTransform zoomedOut = AffineTransform.getScaleInstance(1 / 64.0, 1 / 64.0);
    paint(layer, zoomedOut, 1024, 1024); // asks for the thumbnail
    BufferedImage image = paint(layer, zoomedOut, 1024, 1024);
    assertAll(
        () -> assertTrue(layer.plan().truncated()),
        () -> assertEquals(THUMBNAIL, at(image, 5, 5), "corner, outside the planned tiles"),
        () -> assertTrue(repaints.get() > 0));
  }

  @Test
  void adjacent_tiles_leave_no_gap_at_fractional_scales() {
    TiledImageLayer layer = layer(twoLevels());
    for (double scale : new double[] {0.37, 0.61, 0.73, 1.37, 2.71}) {
      for (double shift : new double[] {0.0, 0.3, 0.55}) {
        AffineTransform t = AffineTransform.getTranslateInstance(shift, shift * 0.7);
        t.scale(scale, scale);
        BufferedImage image = paint(layer, t, 800, 800, true);
        int seams = whitePixels(image, (int) (1024 * scale) - 2);
        assertEquals(0, seams, "scale %.2f shift %.2f".formatted(scale, shift));
      }
    }
  }

  // White is the slide background: inside the slide, a white pixel is a gap between tiles
  private static int whitePixels(BufferedImage image, int extent) {
    int count = 0;
    int max = Math.min(extent, Math.min(image.getWidth(), image.getHeight()) - 1);
    for (int y = 2; y < max; y++) {
      for (int x = 2; x < max; x++) {
        if (image.getRGB(x, y) == Color.WHITE.getRGB()) {
          count++;
        }
      }
    }
    return count;
  }

  @Test
  void tiles_abut_on_whole_device_pixels() {
    TiledImageLayer layer = layer(twoLevels());
    // Level 1 at downsample 2.7: a tile is 512 base pixels, 189.44 view pixels, from x = 0.3
    AffineTransform t = AffineTransform.getTranslateInstance(0.3, 0.3);
    t.scale(0.37, 0.37);
    BufferedImage image = paint(layer, t, 400, 400);
    int mixed = pixelsOutside(image, 378, L1_EVEN, L1_ODD);
    assertAll(
        () -> assertEquals(L1_EVEN, at(image, 189, 10), "the first tile ends on the rounded edge"),
        () -> assertEquals(L1_ODD, at(image, 190, 10), "the second starts on it"),
        () -> assertEquals(0, mixed, "no filtered edge, no gap"));
  }

  @Test
  void a_minified_tile_is_averaged_rather_than_sampled() {
    FakeSource source = twoLevels();
    source.striped = true;
    // Level 1 at 0.4 view pixel per tile pixel: drawn from the halved copy, which is grey
    BufferedImage image =
        paint(layer(source), AffineTransform.getScaleInstance(0.2, 0.2), 300, 300);
    int[] range = blueRange(image, 200);
    assertAll(
        () -> assertEquals(1, TiledImageLayer.halvings(0.4)),
        () -> assertEquals(0, TiledImageLayer.halvings(0.6)),
        () -> assertEquals(3, TiledImageLayer.halvings(0.05)),
        () ->
            assertTrue(
                range[0] >= 100 && range[1] <= 160,
                "grey everywhere: %d – %d".formatted(range[0], range[1])));
  }

  // Pixels of the square from the origin that have none of the colours
  private static int pixelsOutside(BufferedImage image, int extent, Color... colours) {
    int count = 0;
    for (int y = 0; y < extent; y++) {
      for (int x = 0; x < extent; x++) {
        Color c = at(image, x, y);
        if (Arrays.stream(colours).noneMatch(c::equals)) {
          count++;
        }
      }
    }
    return count;
  }

  // Least and greatest blue value of the square from (2, 2)
  private static int[] blueRange(BufferedImage image, int extent) {
    int min = 255;
    int max = 0;
    for (int y = 2; y < extent; y++) {
      for (int x = 2; x < extent; x++) {
        int v = image.getRGB(x, y) & 0xFF;
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }
    return new int[] {min, max};
  }

  @Test
  void halving_averages_each_two_by_two_block() {
    BufferedImage bgr = new BufferedImage(4, 2, BufferedImage.TYPE_3BYTE_BGR);
    bgr.setRGB(0, 0, 0xFF0000);
    bgr.setRGB(1, 0, 0x0000FF);
    bgr.setRGB(0, 1, 0x00FF00);
    bgr.setRGB(1, 1, 0x000000);
    bgr.setRGB(2, 0, 0xFFFFFF);
    bgr.setRGB(3, 0, 0xFFFFFF);
    bgr.setRGB(2, 1, 0xFFFFFF);
    bgr.setRGB(3, 1, 0x000000);
    BufferedImage gray = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
    gray.getRaster().setSample(0, 0, 0, 0);
    gray.getRaster().setSample(1, 0, 0, 255);
    gray.getRaster().setSample(0, 1, 0, 255);
    gray.getRaster().setSample(1, 1, 0, 255);
    BufferedImage rgb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    rgb.setRGB(0, 0, 0xFFFFFF);
    rgb.setRGB(1, 1, 0xFFFFFF);
    BufferedImage halfBgr = TiledImageLayer.halve(bgr);
    BufferedImage halfGray = TiledImageLayer.halve(gray);
    BufferedImage halfRgb = TiledImageLayer.halve(rgb);
    assertAll(
        () -> assertEquals(2, halfBgr.getWidth()),
        () -> assertEquals(1, halfBgr.getHeight()),
        () -> assertEquals(0x404040, halfBgr.getRGB(0, 0) & 0xFFFFFF, "mean of red, blue, green"),
        () -> assertEquals(0xBFBFBF, halfBgr.getRGB(1, 0) & 0xFFFFFF, "three whites, one black"),
        () -> assertEquals(191, halfGray.getRaster().getSample(0, 0, 0)),
        () -> assertEquals(1, halfRgb.getWidth()),
        () -> assertTrue(Math.abs((halfRgb.getRGB(0, 0) & 0xFF) - 128) <= 1, "generic path"));
  }

  @Test
  void the_finer_cached_level_shows_over_the_coarser_ones_while_the_planned_level_loads() {
    FakeSource source =
        new FakeSource(
            tiledFull("L0", 4096, 4096, 256),
            tiledFull("L1", 2048, 2048, 256),
            tiledFull("L2", 1024, 1024, 256)); // NON-NLS
    source.brokenLevels.add(1);
    TiledImageLayer layer = layer(source);
    paint(layer, new AffineTransform(), 600, 600); // level 0 and the top level are cached
    BufferedImage image = paint(layer, AffineTransform.getScaleInstance(0.5, 0.5), 600, 600);
    assertAll(
        () -> assertEquals(1, layer.plan().level()),
        () -> assertEquals(L0_EVEN, at(image, 100, 100), "level 0 over the top level"),
        () -> assertEquals(L2_ODD, at(image, 590, 590), "the top level where nothing finer is"));
  }

  @Test
  void a_moving_view_plans_at_most_once_per_delay_and_cached_levels_fill_in() {
    FakeSource source = twoLevels();
    TiledImageLayer layer = layer(source);
    layer.setPlanDelay(60_000_000_000L);
    paint(layer, new AffineTransform(), 600, 600);
    int reads = source.reads.get();
    AffineTransform moved = AffineTransform.getTranslateInstance(-300, -200);
    BufferedImage image = paint(layer, moved, 600, 600);
    assertAll(
        () -> assertEquals(reads, source.reads.get(), "no new plan within the delay"),
        () -> assertEquals(0, whitePixels(image, 600 - 2), "filled from the cached levels"));
  }

  @Test
  void an_animated_zoom_plans_the_level_of_its_end() {
    TiledImageLayer layer = layer(twoLevels());
    BufferedImage canvas = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = canvas.createGraphics();
    layer.paint(
        g,
        new AffineTransform(),
        new Rectangle(0, 0, 600, 600),
        new TiledImageLayer.PlanTarget(new Rectangle(0, 0, 1024, 1024), 2.0));
    g.dispose();
    assertEquals(1, layer.plan().level());
  }

  @Test
  void the_plane_and_path_shown_select_the_tiles_read() {
    Attributes dcm = tiledFull("Z", 1024, 1024, 256); // NON-NLS
    SyntheticSlides.opticalPaths(dcm, "DAPI", "FITC"); // NON-NLS
    dcm.setInt(Tag.TotalPixelMatrixFocalPlanes, VR.UL, 2);
    dcm.setInt(Tag.NumberOfFrames, VR.IS, 16 * 2 * 2);
    FakeSource source = new FakeSource(dcm);
    TiledImageLayer layer = layer(source);
    paint(layer, new AffineTransform(), 600, 600);
    assertTrue(
        source.tiles.values().stream().allMatch(t -> t.focalPlane() == 0 && t.opticalPath() == 0),
        "the first plane and path by default");
    source.tiles.clear();
    layer.setPlane(1, 1);
    BufferedImage image = paint(layer, new AffineTransform(), 600, 600);
    assertAll(
        () -> assertFalse(source.tiles.isEmpty()),
        () ->
            assertTrue(
                source.tiles.values().stream()
                    .allMatch(t -> t.focalPlane() == 1 && t.opticalPath() == 1)),
        () -> assertEquals(L0_EVEN, at(image, 100, 100), "drawn from the tiles of that plane"));
  }

  @Test
  void repainting_the_same_view_does_not_plan_again() {
    FakeSource source = twoLevels();
    TiledImageLayer layer = layer(source);
    paint(layer, new AffineTransform(), 600, 600);
    int reads = source.reads.get();
    paint(layer, new AffineTransform(), 600, 600);
    layer.setPlane(0, 0);
    paint(layer, new AffineTransform(), 600, 600);
    assertEquals(reads, source.reads.get(), "everything is cached");
  }
}
