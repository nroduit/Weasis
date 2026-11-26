/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What a viewer hangs: a grid, the synchronization, the selected slot and, per cell of the grid,
 * the tiling of its viewport and the {@link ViewState} of every pane. It is the typed form of the
 * JSON {@link ImageViewerPlugin#saveState()} writes, the object a hanging plan produces, and the
 * one headless call {@link ImageViewerPlugin#hang} takes: "this series, in this cell of this grid,
 * shown this way".
 *
 * <p>A null {@code grid} keeps the grid of the viewer, a null {@code synch} keeps its
 * synchronization, {@code selected} 0 keeps the selection. A cell that is not listed is left as it
 * is; a listed cell is set exactly: its tiling (none = stack), its series (none = cleared) and the
 * presentation of its panes.
 *
 * @param grid the id of the grid, from the ones the viewer offers
 * @param synch the command of the synchronization
 * @param selected the 1-based slot to select, 0 for none
 * @param cells the cells to hang, by grid position
 * @param cine whether the selected pane starts playing its stack once hung (a cine run, an
 *     ultrasound clip); a layout that does not ask stops the cine left running
 */
public record HangingLayout(
    String grid, String synch, int selected, List<Cell> cells, boolean cine) {

  public HangingLayout {
    cells = cells == null ? List.of() : List.copyOf(cells);
  }

  /** A layout that does not start the cine. */
  public HangingLayout(String grid, String synch, int selected, List<Cell> cells) {
    this(grid, synch, selected, cells, false);
  }

  /** Side of a tiled viewport. */
  public record Tiling(int cols, int rows) {
    public Tiling {
      cols = Math.max(1, cols);
      rows = Math.max(1, rows);
    }

    /**
     * Reads {@code <rows>x<cols>}, the order of the grid ids of a viewer ({@code 1x2} is one row of
     * two).
     */
    public static Tiling parse(String text) {
      String[] parts = text == null ? new String[0] : text.trim().split("[xX]"); // NON-NLS
      if (parts.length != 2) {
        throw new IllegalArgumentException("Expected <rows>x<cols>, got: " + text);
      }
      return new Tiling(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[0].trim()));
    }

    @Override
    public String toString() {
      return rows + "x" + cols; // NON-NLS
    }
  }

  /**
   * One cell of the grid: its 1-based position, its tiling (null = a single stack pane) and the
   * state of its panes in tile order. The series of the cell is the one of its first pane; a tiled
   * cell shows one series on every tile.
   */
  public record Cell(int position, Tiling tiling, List<ViewState> panes) {
    public Cell {
      panes = panes == null ? List.of() : List.copyOf(panes);
    }

    /** A stack cell showing a series with the default presentation. */
    public static Cell of(int position, String seriesId) {
      return new Cell(
          position,
          null,
          List.of(
              new ViewState(
                  new ViewState.ViewData(seriesId, 0, null, null),
                  ViewState.ViewPresentation.DEFAULT)));
    }

    /** The same cell showing another series in its panes, from their first image. */
    public Cell withSeries(String seriesId) {
      List<ViewState> replaced =
          panes.stream()
              .map(
                  pane ->
                      new ViewState(
                          new ViewState.ViewData(seriesId, 0, null, null),
                          pane.view(),
                          pane.blend()))
              .toList();
      return new Cell(position, tiling, replaced.isEmpty() ? List.of() : replaced);
    }

    /** The series id of the cell: the one of its first pane, empty for an empty cell. */
    public Optional<String> seriesId() {
      return panes.isEmpty()
          ? Optional.empty()
          : Optional.ofNullable(panes.getFirst().data().series());
    }

    public JsonObject toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder().add("cell", position); // NON-NLS
      if (tiling != null) {
        json.add(
            "tiled", // NON-NLS
            Json.createObjectBuilder()
                .add("cols", tiling.cols()) // NON-NLS
                .add("rows", tiling.rows())); // NON-NLS
      }
      JsonArrayBuilder views = Json.createArrayBuilder();
      panes.forEach(p -> views.add(p.toJson()));
      return json.add("views", views).build(); // NON-NLS
    }

    public static Cell fromJson(JsonObject json) {
      JsonObject tiled = json.getJsonObject("tiled"); // NON-NLS
      Tiling tiling =
          tiled == null
              ? null
              : new Tiling(tiled.getInt("cols", 1), tiled.getInt("rows", 1)); // NON-NLS
      JsonArray views = json.getJsonArray("views"); // NON-NLS
      List<ViewState> panes =
          views == null
              ? List.of()
              : views.getValuesAs(JsonObject.class).stream().map(ViewState::fromJson).toList();
      return new Cell(json.getInt("cell", -1), tiling, panes); // NON-NLS
    }
  }

  /** Writes the fields of this layout into {@code json}; the base state of the viewer is kept. */
  public JsonObjectBuilder writeTo(JsonObjectBuilder json) {
    if (grid != null) {
      json.add("grid", grid); // NON-NLS
    }
    if (synch != null) {
      json.add("synch", synch); // NON-NLS
    }
    if (selected > 0) {
      json.add("selected", selected); // NON-NLS
    }
    if (cine) {
      json.add("cine", true); // NON-NLS
    }
    JsonArrayBuilder array = Json.createArrayBuilder();
    cells.forEach(c -> array.add(c.toJson()));
    return json.add("cells", array); // NON-NLS
  }

  public JsonObject toJson() {
    return writeTo(Json.createObjectBuilder()).build();
  }

  /** Reads a layout written by {@link #toJson()} or {@link ImageViewerPlugin#saveState()}. */
  public static HangingLayout fromJson(JsonObject json) {
    JsonArray array = json.getJsonArray("cells"); // NON-NLS
    List<Cell> cells =
        array == null
            ? List.of()
            : array.getValuesAs(JsonObject.class).stream().map(Cell::fromJson).toList();
    return new HangingLayout(
        json.getString("grid", null), // NON-NLS
        json.getString("synch", null), // NON-NLS
        json.getInt("selected", 0), // NON-NLS
        cells,
        json.getBoolean("cine", false)); // NON-NLS
  }

  /** The same layout with the cell at {@code position} showing another series. */
  public HangingLayout withCellSeries(int position, String seriesId) {
    List<Cell> replaced =
        cells.stream()
            .map(cell -> cell.position() == position ? cell.withSeries(seriesId) : cell)
            .toList();
    return new HangingLayout(grid, synch, selected, replaced, cine);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Assembles a layout cell by cell, the way a hanging plan or a verb does. */
  public static final class Builder {
    private String grid;
    private String synch;
    private int selected;
    private boolean cine;
    private final TreeMap<Integer, Cell> cells = new TreeMap<>();

    private Builder() {}

    public Builder grid(String id) {
      this.grid = id;
      return this;
    }

    public Builder synch(String command) {
      this.synch = command;
      return this;
    }

    public Builder select(int slot) {
      this.selected = slot;
      return this;
    }

    public Builder cine(boolean start) {
      this.cine = start;
      return this;
    }

    /** Puts a series in a cell, keeping the tiling already given for that cell. */
    public Builder series(int position, String seriesId) {
      Cell current = cells.get(position);
      Tiling tiling = current == null ? null : current.tiling();
      ViewState state =
          new ViewState(
              new ViewState.ViewData(seriesId, 0, null, null), ViewState.ViewPresentation.DEFAULT);
      cells.put(position, new Cell(position, tiling, List.of(state)));
      return this;
    }

    /** Tiles a cell, keeping the series already given for that cell. */
    public Builder tiled(int position, Tiling tiling) {
      Cell current = cells.get(position);
      cells.put(
          position, new Cell(position, tiling, current == null ? List.of() : current.panes()));
      return this;
    }

    public Builder cell(Cell cell) {
      cells.put(cell.position(), cell);
      return this;
    }

    public HangingLayout build() {
      return new HangingLayout(grid, synch, selected, new ArrayList<>(cells.values()), cine);
    }
  }
}
