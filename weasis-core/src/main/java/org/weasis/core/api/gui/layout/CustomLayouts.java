/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.layout;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;

/**
 * The layouts of the user: grids of views, with merged cells, and grids of tiles for a viewport.
 * They are entries of the user document {@value #DOCUMENT}, so they follow the settings of the
 * profile (export, import, rollback).
 *
 * <p>Every size reads rows then columns, as the ids of the grids a viewer offers ({@code 1x2} is
 * one row of two).
 */
public final class CustomLayouts {
  private static final Logger LOGGER = LoggerFactory.getLogger(CustomLayouts.class);

  public static final String DOCUMENT = "layouts.json"; // NON-NLS

  /** Prefix of the id of a grid of the user, apart from the ids a viewer builds itself. */
  public static final String ID_PREFIX = "user."; // NON-NLS

  /** Largest side of a grid of views. */
  public static final int MAX_GRID = 8;

  /** Largest side of a grid of tiles. */
  public static final int MAX_TILES = 10;

  private static final String KIND = "kind"; // NON-NLS
  private static final String KIND_GRID = "grid"; // NON-NLS
  private static final String KIND_TILED = "tiled"; // NON-NLS
  private static final String TILED_PREFIX = "tiled."; // NON-NLS

  private static final LayeredDocument DOC = new LayeredDocument(DOCUMENT, "layouts"); // NON-NLS
  private static boolean configured;

  private CustomLayouts() {}

  /** A merged cell: its top left cell, from 0, and the rows and columns it covers. */
  public record Span(int row, int col, int rowSpan, int colSpan) {
    boolean intersects(int r, int c, int rs, int cs) {
      return row < r + rs && r < row + rowSpan && col < c + cs && c < col + colSpan;
    }

    boolean within(int rows, int cols) {
      return row >= 0
          && col >= 0
          && rowSpan >= 1
          && colSpan >= 1
          && row + rowSpan <= rows
          && col + colSpan <= cols
          && rowSpan * colSpan > 1;
    }
  }

  /**
   * What a cell holds instead of a viewport: the id of a kind of component (a histogram, the
   * attributes of the image), at the top left cell of the cell it fills.
   */
  public record Content(int row, int col, String component) {}

  /**
   * A grid of views of the user. Every cell is a viewport unless {@code contents} names a component
   * for it; one viewport at least always remains.
   */
  public record Grid(
      String id, String name, int rows, int cols, List<Span> merged, List<Content> contents) {
    public Grid {
      Objects.requireNonNull(id, "id");
      rows = clamp(rows, MAX_GRID);
      cols = clamp(cols, MAX_GRID);
      name = name == null || name.isBlank() ? rows + "x" + cols : name.trim(); // NON-NLS
      merged = valid(merged, rows, cols);
      contents = validContents(contents, merged, rows, cols);
    }

    public Grid(String id, String name, int rows, int cols, List<Span> merged) {
      this(id, name, rows, cols, merged, List.of());
    }

    /** Drops the spans that leave the grid or overlap one kept before them. */
    private static List<Span> valid(List<Span> spans, int rows, int cols) {
      List<Span> kept = new ArrayList<>();
      if (spans != null) {
        for (Span span : spans) {
          if (span.within(rows, cols)
              && kept.stream()
                  .noneMatch(
                      k -> k.intersects(span.row(), span.col(), span.rowSpan(), span.colSpan()))) {
            kept.add(span);
          }
        }
      }
      kept.sort(Comparator.comparingInt(Span::row).thenComparingInt(Span::col));
      return List.copyOf(kept);
    }

    /**
     * Keeps one content per cell, at the top left of a cell of the grid, and leaves one viewport.
     */
    private static List<Content> validContents(
        List<Content> contents, List<Span> merged, int rows, int cols) {
      List<Content> kept = new ArrayList<>();
      if (contents != null) {
        for (Content c : contents) {
          boolean inside = c.row() >= 0 && c.col() >= 0 && c.row() < rows && c.col() < cols;
          boolean origin =
              merged.stream()
                  .filter(s -> s.intersects(c.row(), c.col(), 1, 1))
                  .allMatch(s -> s.row() == c.row() && s.col() == c.col());
          boolean free = kept.stream().noneMatch(k -> k.row() == c.row() && k.col() == c.col());
          if (inside && origin && free && c.component() != null && !c.component().isBlank()) {
            kept.add(c);
          }
        }
      }
      int covered = merged.stream().mapToInt(s -> s.rowSpan() * s.colSpan() - 1).sum();
      int cells = rows * cols - covered;
      while (!kept.isEmpty() && kept.size() >= cells) {
        kept.removeLast(); // a layout without a viewport shows nothing
      }
      kept.sort(Comparator.comparingInt(Content::row).thenComparingInt(Content::col));
      return List.copyOf(kept);
    }

    /** Number of cells of the grid, viewports and components. */
    public int cellCount() {
      int covered = merged.stream().mapToInt(s -> s.rowSpan() * s.colSpan() - 1).sum();
      return rows * cols - covered;
    }

    /** Number of viewports of the grid. */
    public int viewCount() {
      return cellCount() - contents.size();
    }

    /** The span covering that cell, if it is part of a merged cell. */
    public Optional<Span> spanAt(int row, int col) {
      return merged.stream().filter(s -> s.intersects(row, col, 1, 1)).findFirst();
    }

    /** The id of the component of the cell at that place, or null for a viewport. */
    public String contentAt(int row, int col) {
      int r = spanAt(row, col).map(Span::row).orElse(row);
      int c = spanAt(row, col).map(Span::col).orElse(col);
      return contents.stream()
          .filter(k -> k.row() == r && k.col() == c)
          .map(Content::component)
          .findFirst()
          .orElse(null);
    }

    /**
     * The same grid with that cell holding a component, or a viewport when {@code component} is
     * null; unchanged when it would leave no viewport.
     */
    public Grid withContent(int row, int col, String component) {
      int r = spanAt(row, col).map(Span::row).orElse(row);
      int c = spanAt(row, col).map(Span::col).orElse(col);
      if (component != null && contentAt(r, c) == null && viewCount() <= 1) {
        return this; // the last viewport stays one
      }
      List<Content> list = new ArrayList<>();
      contents.stream().filter(k -> k.row() != r || k.col() != c).forEach(list::add);
      if (component != null) {
        list.add(new Content(r, c, component));
      }
      return new Grid(id, name, rows, cols, merged, list);
    }

    /** The same grid with the cells of that rectangle merged; the spans it touches are absorbed. */
    public Grid merge(int row, int col, int rowSpan, int colSpan) {
      int r0 = row;
      int c0 = col;
      int r1 = row + rowSpan;
      int c1 = col + colSpan;
      boolean grown = true;
      while (grown) {
        grown = false;
        for (Span s : merged) {
          if (s.intersects(r0, c0, r1 - r0, c1 - c0)) {
            int nr0 = Math.min(r0, s.row());
            int nc0 = Math.min(c0, s.col());
            int nr1 = Math.max(r1, s.row() + s.rowSpan());
            int nc1 = Math.max(c1, s.col() + s.colSpan());
            if (nr0 != r0 || nc0 != c0 || nr1 != r1 || nc1 != c1) {
              r0 = nr0;
              c0 = nc0;
              r1 = nr1;
              c1 = nc1;
              grown = true;
            }
          }
        }
      }
      List<Span> spans = new ArrayList<>();
      spans.add(new Span(r0, c0, r1 - r0, c1 - c0));
      spans.addAll(merged);
      return new Grid(id, name, rows, cols, spans, contents);
    }

    /** The same grid with the merged cells touching that rectangle split again. */
    public Grid split(int row, int col, int rowSpan, int colSpan) {
      List<Span> spans =
          merged.stream().filter(s -> !s.intersects(row, col, rowSpan, colSpan)).toList();
      return new Grid(id, name, rows, cols, spans, contents);
    }

    public Grid resized(int newRows, int newCols) {
      return new Grid(id, name, newRows, newCols, merged, contents);
    }

    public Grid renamed(String newName) {
      return new Grid(id, newName, rows, cols, merged, contents);
    }

    /** The layout a viewer applies, every cell holding a component of that class. */
    public MigLayoutModel toModel(String defaultClass) {
      return toModel(defaultClass, component -> null);
    }

    /**
     * The layout a viewer applies.
     *
     * @param defaultClass the class of a viewport
     * @param typeOf the class of a component from its id; a cell whose component gives null (its
     *     plugin is absent) is a viewport
     */
    public MigLayoutModel toModel(String defaultClass, UnaryOperator<String> typeOf) {
      return toModel(defaultClass, typeOf, component -> null);
    }

    /**
     * The layout a viewer applies, its title naming the components it holds: {@code Reading
     * (Histogram, DICOM information)}.
     *
     * @param titleOf the name of a component from its id, null to leave it out of the title
     */
    public MigLayoutModel toModel(
        String defaultClass, UnaryOperator<String> typeOf, UnaryOperator<String> titleOf) {
      MergedCellsBuilder builder = new MergedCellsBuilder(rows, cols, id, name, defaultClass);
      for (Span s : merged) {
        builder.addMergedCell(s.row(), s.col(), s.colSpan(), s.rowSpan());
      }
      MigLayoutModel model = builder.build();
      if (contents.isEmpty()) {
        return model;
      }
      List<MigCell> cells = new ArrayList<>();
      List<String> titles = new ArrayList<>();
      boolean changed = false;
      for (MigCell cell : model.getCells()) {
        String component = contentAt(cell.y(), cell.x());
        String type = component == null ? null : typeOf.apply(component);
        changed |= type != null;
        String title = type == null ? null : titleOf.apply(component);
        if (title != null && !titles.contains(title)) {
          titles.add(title);
        }
        cells.add(
            type == null
                ? cell
                : new MigCell(
                    cell.position(),
                    type,
                    cell.constraints(),
                    cell.x(),
                    cell.y(),
                    cell.spanX(),
                    cell.spanY()));
      }
      return changed
          ? new MigLayoutModel(
              id,
              titles.isEmpty() ? name : name + " (" + String.join(", ", titles) + ")",
              model.getLayoutConstraints(),
              model.getColumnConstraints(),
              model.getRowConstraints(),
              cells)
          : model;
    }

    public JsonObject toJson() {
      JsonArrayBuilder spans = Json.createArrayBuilder();
      for (Span s : merged) {
        spans.add(
            Json.createObjectBuilder()
                .add("row", s.row()) // NON-NLS
                .add("col", s.col()) // NON-NLS
                .add("rows", s.rowSpan()) // NON-NLS
                .add("cols", s.colSpan())); // NON-NLS
      }
      JsonArrayBuilder components = Json.createArrayBuilder();
      for (Content c : contents) {
        components.add(
            Json.createObjectBuilder()
                .add("row", c.row()) // NON-NLS
                .add("col", c.col()) // NON-NLS
                .add("component", c.component())); // NON-NLS
      }
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("id", id) // NON-NLS
              .add(KIND, KIND_GRID)
              .add("name", name) // NON-NLS
              .add("rows", rows) // NON-NLS
              .add("cols", cols) // NON-NLS
              .add("merged", spans); // NON-NLS
      return json.add("components", components).build(); // NON-NLS
    }

    static Grid fromJson(JsonObject json) {
      List<Span> spans = new ArrayList<>();
      JsonArray array = json.getJsonArray("merged"); // NON-NLS
      if (array != null) {
        for (JsonObject s : array.getValuesAs(JsonObject.class)) {
          spans.add(
              new Span(
                  s.getInt("row", 0), // NON-NLS
                  s.getInt("col", 0), // NON-NLS
                  s.getInt("rows", 1), // NON-NLS
                  s.getInt("cols", 1))); // NON-NLS
        }
      }
      List<Content> contents = new ArrayList<>();
      JsonArray components = json.getJsonArray("components"); // NON-NLS
      if (components != null) {
        for (JsonObject c : components.getValuesAs(JsonObject.class)) {
          contents.add(
              new Content(
                  c.getInt("row", 0), // NON-NLS
                  c.getInt("col", 0), // NON-NLS
                  c.getString("component", null))); // NON-NLS
        }
      }
      return new Grid(
          json.getString("id"), // NON-NLS
          json.getString("name", null), // NON-NLS
          json.getInt("rows", 1), // NON-NLS
          json.getInt("cols", 1), // NON-NLS
          spans,
          contents);
    }
  }

  /** A grid of tiles kept by the user for the viewports. */
  public record Tiles(int rows, int cols) {
    public Tiles {
      rows = clamp(rows, MAX_TILES);
      cols = clamp(cols, MAX_TILES);
    }

    String id() {
      return TILED_PREFIX + rows + "x" + cols; // NON-NLS
    }

    @Override
    public String toString() {
      return rows + "x" + cols; // NON-NLS
    }
  }

  private static int clamp(int value, int max) {
    return Math.clamp(value, 1, max);
  }

  private static synchronized LayeredDocument doc() {
    if (!configured) {
      Path file = DocumentStore.locate(Scope.USER, DOCUMENT);
      DOC.configure(Optional.empty(), file);
      configured = true;
    }
    return DOC;
  }

  /** Uses another file, or none (memory only), for tests. */
  static synchronized void install(Path file) {
    DOC.configure(Optional.empty(), file);
    configured = true;
  }

  /** The grids of views of the user, in the order they were created. */
  public static List<Grid> grids() {
    List<Grid> list = new ArrayList<>();
    for (JsonObject json : doc().userEntries()) {
      if (KIND_GRID.equals(json.getString(KIND, KIND_GRID))) {
        try {
          list.add(Grid.fromJson(json));
        } catch (RuntimeException e) {
          LOGGER.warn("Layout left out of {}: {}", DOCUMENT, json, e);
        }
      }
    }
    return list;
  }

  /** The grids of tiles of the user, the smallest first. */
  public static List<Tiles> tiles() {
    List<Tiles> list = new ArrayList<>();
    for (JsonObject json : doc().userEntries()) {
      if (KIND_TILED.equals(json.getString(KIND, null))) {
        list.add(new Tiles(json.getInt("rows", 1), json.getInt("cols", 1))); // NON-NLS
      }
    }
    list.sort(Comparator.comparingInt((Tiles t) -> t.rows() * t.cols()).thenComparing(Tiles::rows));
    return list;
  }

  /** The grids of the user as layouts of a viewer. */
  public static List<MigLayoutModel> models(String defaultClass) {
    return models(defaultClass, component -> null);
  }

  /**
   * The grids of the user as layouts of a viewer.
   *
   * @param typeOf the class of a component from its id, see {@link Grid#toModel(String,
   *     UnaryOperator)}
   */
  public static List<MigLayoutModel> models(String defaultClass, UnaryOperator<String> typeOf) {
    return models(defaultClass, typeOf, component -> null);
  }

  /** The grids of the user as layouts of a viewer, their titles naming their components. */
  public static List<MigLayoutModel> models(
      String defaultClass, UnaryOperator<String> typeOf, UnaryOperator<String> titleOf) {
    return grids().stream().map(g -> g.toModel(defaultClass, typeOf, titleOf)).toList();
  }

  /** True for the id of a grid of the user. */
  public static boolean isCustom(String id) {
    return id != null && id.startsWith(ID_PREFIX);
  }

  /** An id no grid of the user has, nor any of {@code taken}. */
  public static String newId(Set<String> taken) {
    Set<String> used = grids().stream().map(Grid::id).collect(Collectors.toSet());
    int n = 1;
    while (used.contains(ID_PREFIX + n) || taken.contains(ID_PREFIX + n)) {
      n++;
    }
    return ID_PREFIX + n;
  }

  /**
   * Replaces the grids and the tile grids of the user by these.
   *
   * @throws IOException when the document cannot be written
   */
  public static void replace(List<Grid> grids, List<Tiles> tiles) throws IOException {
    LayeredDocument doc = doc();
    Set<String> kept = grids.stream().map(Grid::id).collect(Collectors.toSet());
    tiles.forEach(t -> kept.add(t.id()));
    for (JsonObject json : doc.userEntries()) {
      String id = json.getString("id", null); // NON-NLS
      if (id != null && !kept.contains(id)) {
        doc.remove(id);
      }
    }
    for (Grid grid : grids) {
      doc.put(grid.toJson());
    }
    for (Tiles t : tiles) {
      doc.put(
          Json.createObjectBuilder()
              .add("id", t.id()) // NON-NLS
              .add(KIND, KIND_TILED)
              .add("rows", t.rows()) // NON-NLS
              .add("cols", t.cols()) // NON-NLS
              .build());
    }
  }

  /** Called after the layouts of the user changed. */
  public static void addListener(Runnable listener) {
    doc().addListener(listener);
  }
}
