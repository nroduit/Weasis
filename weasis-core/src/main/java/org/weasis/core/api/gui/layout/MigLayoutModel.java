/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.layout;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;

/**
 * Model for MigLayout-based layout management in plugin containers.
 *
 * <p>Manages layout constraints using MigLayout and provides visual representation through
 * dynamically generated icons. Supports initialization from code or properties files.
 */
public final class MigLayoutModel implements GUIEntry, Copyable<MigLayoutModel> {

  public static final int DEFAULT_INTERCELL_GAP = 5;

  private static final Logger LOGGER = LoggerFactory.getLogger(MigLayoutModel.class);
  private static final int ICON_SIZE = 22;

  private final String id;
  private final String layoutConstraints;
  private final List<MigCell> cells;

  private String title;
  private Icon icon;
  private String columnConstraints;
  private String rowConstraints;

  /**
   * Creates a model with uniform grid layout.
   *
   * @param id unique identifier for this layout
   * @param title display name
   * @param rows number of rows
   * @param cols number of columns
   * @param defaultClass default component class name
   */
  public MigLayoutModel(String id, String title, int rows, int cols, String defaultClass) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.title = Objects.requireNonNull(title, "title cannot be null");
    this.layoutConstraints = "wrap " + cols + ", ins 0, gap " + DEFAULT_INTERCELL_GAP;
    this.columnConstraints = buildDefaultColumnConstraints(cols);
    this.rowConstraints = buildDefaultRowConstraints(rows);
    this.cells = createUniformGrid(rows, cols, defaultClass);
    this.icon = buildIcon();
  }

  /**
   * Creates a model from properties stream.
   *
   * @param stream Properties input stream containing layout definition
   * @param id unique identifier
   * @param title display name (optional, can be defined in properties)
   */
  public MigLayoutModel(InputStream stream, String id, String title) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.cells = new ArrayList<>();

    Properties props = new Properties();
    try {
      props.load(stream);
    } catch (IOException e) {
      LOGGER.error("Failed to load layout properties", e);
      throw new IllegalArgumentException("Invalid layout properties", e);
    }

    this.title = props.getProperty("layout.name", title);
    this.layoutConstraints =
        props.getProperty("layout.constraints", "wrap, ins 0, gap " + DEFAULT_INTERCELL_GAP);
    this.columnConstraints = props.getProperty("layout.columns", "[]");
    this.rowConstraints = props.getProperty("layout.rows", "[]");

    parseLayoutProperties(props);

    String iconPath = props.getProperty("layout.icon");
    this.icon =
        StringUtil.hasText(iconPath)
            ? ResourceUtil.getIcon(iconPath).derive(ICON_SIZE, ICON_SIZE)
            : buildIcon();
  }

  /**
   * Creates a model with explicit configuration.
   *
   * @param id unique identifier
   * @param title display name
   * @param layoutConstraints MigLayout constraints
   * @param columnConstraints column constraints
   * @param rowConstraints row constraints
   * @param cells list of cells
   */
  public MigLayoutModel(
      String id,
      String title,
      String layoutConstraints,
      String columnConstraints,
      String rowConstraints,
      List<MigCell> cells) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.title = Objects.requireNonNull(title, "title cannot be null");
    this.layoutConstraints = Objects.requireNonNull(layoutConstraints);
    this.columnConstraints = columnConstraints;
    this.rowConstraints = rowConstraints;
    this.cells = new ArrayList<>(cells);
    this.icon = buildIcon();
  }

  /** Copy constructor. */
  public MigLayoutModel(MigLayoutModel layoutModel) {
    this.id = layoutModel.id;
    this.title = layoutModel.title;
    this.layoutConstraints = layoutModel.layoutConstraints;
    this.columnConstraints = layoutModel.columnConstraints;
    this.rowConstraints = layoutModel.rowConstraints;
    this.cells = new ArrayList<>(layoutModel.cells.stream().map(MigCell::copy).toList());
    this.icon = layoutModel.icon;
  }

  /**
   * Creates a model with a grid layout allowing merged cells.
   *
   * @param id unique identifier for this layout
   * @param title display name
   * @param rows number of rows
   * @param cols number of columns
   * @param defaultClass default component class name
   * @param mergedCells map of cell positions (row*cols + col) to span dimensions [spanX, spanY]
   * @throws IllegalArgumentException if merged cells overlap or are not rectangular
   */
  public MigLayoutModel(
      String id,
      String title,
      int rows,
      int cols,
      String defaultClass,
      Map<Integer, int[]> mergedCells) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.title = Objects.requireNonNull(title, "title cannot be null");
    this.layoutConstraints = "wrap " + cols + ", ins 0, gap " + DEFAULT_INTERCELL_GAP;
    this.columnConstraints = buildDefaultColumnConstraints(cols);
    this.rowConstraints = buildDefaultRowConstraints(rows);

    validateMergedCells(mergedCells, rows, cols);
    this.cells = createGridWithMergedCells(rows, cols, defaultClass, mergedCells);
    this.icon = buildIcon();
  }

  private void validateMergedCells(Map<Integer, int[]> mergedCells, int rows, int cols) {
    if (mergedCells == null || mergedCells.isEmpty()) {
      return;
    }

    boolean[][] occupied = new boolean[rows][cols];

    for (var entry : mergedCells.entrySet()) {
      validateAndMarkMergedCell(entry.getKey(), entry.getValue(), rows, cols, occupied);
    }
  }

  /** Validates a single merged cell and marks it as occupied. */
  private void validateAndMarkMergedCell(
      int position, int[] span, int rows, int cols, boolean[][] occupied) {
    if (span.length != 2 || span[0] < 1 || span[1] < 1) {
      throw new IllegalArgumentException(
          "Invalid span dimensions: must be [spanX, spanY] with positive values");
    }

    int x = position % cols;
    int y = position / cols;
    int spanX = span[0];
    int spanY = span[1];

    if (x + spanX > cols || y + spanY > rows) {
      throw new IllegalArgumentException(
          "Merged cell at position " + position + " exceeds grid boundaries");
    }

    markCellsAsOccupied(x, y, spanX, spanY, occupied, position);
  }

  /** Marks cells as occupied and throws exception if overlap detected. */
  private void markCellsAsOccupied(
      int x, int y, int spanX, int spanY, boolean[][] occupied, int position) {
    for (int dy = 0; dy < spanY; dy++) {
      for (int dx = 0; dx < spanX; dx++) {
        if (occupied[y + dy][x + dx]) {
          throw new IllegalArgumentException(
              "Overlapping merged cells detected at position " + position);
        }
        occupied[y + dy][x + dx] = true;
      }
    }
  }

  private List<MigCell> createGridWithMergedCells(
      int rows, int cols, String defaultClass, Map<Integer, int[]> mergedCells) {
    List<MigCell> cells = new ArrayList<>();
    boolean[][] occupied = markOccupiedCells(rows, cols, mergedCells);

    int cellIndex = 0;
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        int position = y * cols + x;
        if (occupied[y][x] && mergedCells != null && mergedCells.containsKey(position)) {
          cells.add(createMergedCell(cellIndex++, defaultClass, x, y, mergedCells.get(position)));
        } else if (!occupied[y][x]) {
          cells.add(createRegularCell(cellIndex++, defaultClass, x, y));
        }
      }
    }

    return cells;
  }

  /** Creates an occupied matrix marking merged cell positions. */
  private boolean[][] markOccupiedCells(int rows, int cols, Map<Integer, int[]> mergedCells) {
    boolean[][] occupied = new boolean[rows][cols];
    if (mergedCells != null) {
      for (var entry : mergedCells.entrySet()) {
        int position = entry.getKey();
        int[] span = entry.getValue();
        int x = position % cols;
        int y = position / cols;

        for (int dy = 0; dy < span[1]; dy++) {
          for (int dx = 0; dx < span[0]; dx++) {
            occupied[y + dy][x + dx] = true;
          }
        }
      }
    }
    return occupied;
  }

  /** Creates a merged cell with the specified span. */
  private MigCell createMergedCell(int index, String defaultClass, int x, int y, int[] span) {
    return new MigCell(index, defaultClass, buildCellConstraint(x, y), x, y, span[0], span[1]);
  }

  /** Creates a regular single-cell. */
  private MigCell createRegularCell(int index, String defaultClass, int x, int y) {
    return new MigCell(index, defaultClass, buildCellConstraint(x, y), x, y, 1, 1);
  }

  /** Builds cell constraint string based on position (adds newline if at start of row). */
  private String buildCellConstraint(int x, int y) {
    return (x == 0 && y > 0 ? "newline, " : "") + "grow";
  }

  /** Creates default column constraints with equal grow/fill. */
  private String buildDefaultColumnConstraints(int cols) {
    return "[grow,fill]".repeat(cols);
  }

  /** Creates default row constraints with equal grow/fill. */
  private String buildDefaultRowConstraints(int rows) {
    return "[grow,fill]".repeat(rows);
  }

  /** Creates a uniform grid with all cells having the same size and class. */
  private List<MigCell> createUniformGrid(int rows, int cols, String defaultClass) {
    List<MigCell> cells = new ArrayList<>(rows * cols);
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        String constraint = buildCellConstraint(x, y);
        cells.add(new MigCell(y * cols + x, defaultClass, constraint, x, y, 1, 1));
      }
    }
    return cells;
  }

  /** Parses layout properties to extract cell definitions. */
  private void parseLayoutProperties(Properties props) {
    var list = new ArrayList<Integer>();
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith("cell.") && key.endsWith(".type")) {
        int cellId = parseInt(key.substring(5, key.length() - 5), -1);
        if (cellId < 0) {
          LOGGER.warn("Invalid cell ID in property key: {}", key);
          continue;
        }
        list.add(cellId);
      }
    }

    Collections.sort(list);

    for (Integer cellId : list) {
      MigCell cell = parseCellFromProperties(props, cellId);
      if (cell != null) {
        cells.add(cell);
      }
    }
  }

  /** Parses a single cell configuration from properties. */
  private MigCell parseCellFromProperties(Properties props, int cellId) {
    String prefix = "cell." + cellId + ".";
    String type = props.getProperty(prefix + "type");
    if (!StringUtil.hasText(type)) {
      return null;
    }

    int x = parseInt(props.getProperty(prefix + "x"), 0);
    int y = parseInt(props.getProperty(prefix + "y"), 0);
    int spanX = parseInt(props.getProperty(prefix + "spanX"), 1);
    int spanY = parseInt(props.getProperty(prefix + "spanY"), 1);
    String constraints = props.getProperty(prefix + "constraints", "grow");

    return new MigCell(cellId, type, constraints, x, y, spanX, spanY);
  }

  private int parseInt(String value, int defaultValue) {
    if (!StringUtil.hasText(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return title;
  }

  public String getLayoutConstraints() {
    return layoutConstraints;
  }

  public String getColumnConstraints() {
    return columnConstraints;
  }

  /**
   * Sets column constraints using comprehensive constraint specifications. Provides full control
   * over grow, shrink, fill, and size bounds for each column.
   *
   * @param specs array of constraint specifications, one per column
   */
  public void setColumnConstraintSpecs(ConstraintSpec[] specs) {
    if (isValidConstraintSpecs(specs, getGridSize().width)) {
      this.columnConstraints = buildConstraintsFromSpecs(specs);
    }
  }

  /**
   * Sets row constraints using comprehensive constraint specifications. Provides full control over
   * grow, shrink, fill, and size bounds for each row.
   *
   * @param specs array of constraint specifications, one per row
   */
  public void setRowConstraintSpecs(ConstraintSpec[] specs) {
    if (isValidConstraintSpecs(specs, getGridSize().height)) {
      this.rowConstraints = buildConstraintsFromSpecs(specs);
    }
  }

  private boolean isValidConstraintSpecs(ConstraintSpec[] specs, int expectedCount) {
    return specs != null && specs.length == expectedCount;
  }

  private String buildConstraintsFromSpecs(ConstraintSpec[] specs) {
    StringBuilder sb = new StringBuilder();
    for (ConstraintSpec spec : specs) {
      sb.append(spec.toConstraintString());
    }
    return sb.toString();
  }

  /**
   * Applies the current column and row constraints to an existing MigLayout panel. This is used
   * during interactive resizing to update the layout without rebuilding the grid.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Applies constraint specifications to MigLayout for grow/shrink/fill behavior
   *   <li>Sets preferred sizes on components based on ConstraintSpec preferredSize values
   *   <li>Properly handles merged cells by summing sizes across spanned columns/rows
   * </ul>
   *
   * @param panel the panel whose MigLayout should be updated
   */
  public void applyConstraintsToLayout(JPanel panel) {
    if (!(panel.getLayout() instanceof MigLayout migLayout)) {
      LOGGER.warn("Panel does not use MigLayout, cannot apply constraints");
      return;
    }

    Dimension gridSize = getGridSize();
    ConstraintSpec[] colSpecs = ConstraintSpec.parseAll(columnConstraints, gridSize.width);
    ConstraintSpec[] rowSpecs = ConstraintSpec.parseAll(rowConstraints, gridSize.height);

    // Apply all constraint specifications to the layout
    String newColConstraints = buildConstraintsFromSpecs(colSpecs);
    String newRowConstraints = buildConstraintsFromSpecs(rowSpecs);

    migLayout.setColumnConstraints(newColConstraints);
    migLayout.setRowConstraints(newRowConstraints);

    Component[] components = panel.getComponents();
    if (components.length != cells.size()) {
      LOGGER.warn(
          "Component count mismatch: {} components vs {} cells", components.length, cells.size());
      return;
    }

    for (int i = 0; i < components.length; i++) {
      MigCell cell = cells.get(i);
      Dimension totalSize = panel.getSize();
      Dimension preferredSize = calculateCellPreferredSize(totalSize, cell, colSpecs, rowSpecs);
      if (preferredSize != null) {
        components[i].setPreferredSize(preferredSize);
      }
    }

    panel.revalidate();
    panel.repaint();
  }

  private Dimension calculateCellPreferredSize(
      Dimension totalSize, MigCell cell, ConstraintSpec[] colSpecs, ConstraintSpec[] rowSpecs) {
    Integer width =
        calculateSpannedPreferredSize(totalSize.width, colSpecs, cell.x(), cell.spanX());
    Integer height =
        calculateSpannedPreferredSize(totalSize.height, rowSpecs, cell.y(), cell.spanY());

    if (width == null && height == null) {
      return null; // Let MigLayout handle sizing
    }

    // Use -1 to indicate "use default" for dimensions without size bounds
    return new Dimension(width != null ? width : -1, height != null ? height : -1);
  }

  /**
   * Calculates a preferred size hint across spanned columns or rows using minSize and maxSize.
   *
   * <ul>
   *   <li>If both min and max are defined for a span, the midpoint is used.
   *   <li>If only one bound is defined, that bound is used.
   *   <li>If neither is defined, the proportional share of the total size is used.
   * </ul>
   *
   * @param totalSize total available size in pixels
   * @param specs array of constraint specifications
   * @param start starting index (column or row)
   * @param span number of columns or rows to span
   * @return computed preferred size, or null if no bounds are defined
   */
  private Integer calculateSpannedPreferredSize(
      int totalSize, ConstraintSpec[] specs, int start, int span) {
    boolean hasBounds = false;
    int totalMin = 0;
    int totalMax = 0;
    boolean hasMin = false;
    boolean hasMax = false;
    double spannedWeight = 0.0;

    for (int i = start; i < start + span && i < specs.length; i++) {
      ConstraintSpec s = specs[i];
      if (s.minSize() != null || s.maxSize() != null) {
        hasBounds = true;
        if (s.minSize() != null) {
          totalMin += s.minSize();
          hasMin = true;
        }
        if (s.maxSize() != null) {
          totalMax += s.maxSize();
          hasMax = true;
        }
      } else {
        spannedWeight += s.growWeight();
      }
    }

    if (!hasBounds) {
      // No explicit bounds — use proportional share of total size based on grow weight
      if (spannedWeight <= 0) return null;
      return (int) Math.round(totalSize * spannedWeight / 100.0);
    }

    if (hasMin && hasMax) {
      return (totalMin + totalMax) / 2;
    }
    if (hasMin) {
      return totalMin;
    }
    return totalMax;
  }

  public String getRowConstraints() {
    return rowConstraints;
  }

  public List<MigCell> getCells() {
    return new ArrayList<>(cells);
  }

  public int getCellCount() {
    return cells.size();
  }

  /**
   * Applies this layout to a panel.
   *
   * @param panel the panel to apply layout to
   * @param componentProvider function to create components from type strings
   */
  public void applyLayout(JPanel panel, Function<String, Component> componentProvider) {
    panel.removeAll();
    panel.setLayout(new MigLayout(layoutConstraints, columnConstraints, rowConstraints));

    for (MigCell cell : cells) {
      Component component = componentProvider.apply(cell.type());
      if (component != null) {
        panel.add(component, cell.getFullConstraints());
      }
    }
  }

  /** Returns the grid dimensions based on maximum coordinates. */
  public Dimension getGridSize() {
    int maxX = 0;
    int maxY = 0;
    for (MigCell cell : cells) {
      maxX = Math.max(maxX, cell.x() + cell.spanX());
      maxY = Math.max(maxY, cell.y() + cell.spanY());
    }
    return new Dimension(maxX, maxY);
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return icon;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  @Override
  public String getUIName() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public MigLayoutModel copy() {
    return new MigLayoutModel(this);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MigLayoutModel that
        && Objects.equals(id, that.id)
        && Objects.equals(title, that.title)
        && Objects.equals(cells, that.cells);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, cells);
  }

  private Icon buildIcon() {
    return new LayoutIcon();
  }

  /** Icon renderer for layout visualization. */
  private final class LayoutIcon implements Icon {

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = (Graphics2D) g;
      Color originalColor = g2d.getColor();
      Object[] renderingHints = GuiUtils.setRenderingHints(g2d, true, true, false);

      try {
        ColorScheme colorScheme = determineColorScheme(c);
        drawIconBackground(g2d, x, y, colorScheme.background);
        drawGridCells(g2d, x, y, colorScheme.foreground);
      } finally {
        g2d.setColor(originalColor);
        GuiUtils.resetRenderingHints(g2d, renderingHints);
      }
    }

    private ColorScheme determineColorScheme(Component c) {
      Color background = IconColor.ACTIONS_GREY.color;
      Color foreground = FlatUIUtils.getUIColor("MenuItem.background", Color.WHITE);

      if (c instanceof RadioMenuItem menuItem) {
        if (menuItem.isArmed()) {
          background = FlatUIUtils.getUIColor("MenuItem.selectionForeground", Color.DARK_GRAY);
          foreground = FlatUIUtils.getUIColor("MenuItem.selectionBackground", Color.LIGHT_GRAY);
        } else if (menuItem.isSelected()) {
          foreground = FlatUIUtils.getUIColor("MenuItem.checkBackground", Color.BLUE);
        }
      }

      return new ColorScheme(background, foreground);
    }

    private void drawIconBackground(Graphics2D g2d, int x, int y, Color background) {
      g2d.setColor(background);
      g2d.fillRect(x, y, getIconWidth(), getIconHeight());
    }

    private void drawGridCells(Graphics2D g2d, int x, int y, Color foreground) {
      Dimension gridSize = getGridSize();
      if (gridSize.width == 0 || gridSize.height == 0) {
        return;
      }

      double stepX = (double) getIconWidth() / gridSize.width;
      double stepY = (double) getIconHeight() / gridSize.height;

      for (MigCell cell : cells) {
        Rectangle2D cellRect = createCellRectangle(cell, x, y, stepX, stepY);
        drawCell(g2d, cellRect, foreground);
      }
    }

    private Rectangle2D createCellRectangle(
        MigCell cell, int x, int y, double stepX, double stepY) {
      return new Rectangle2D.Double(
          x + cell.x() * stepX, y + cell.y() * stepY, cell.spanX() * stepX, cell.spanY() * stepY);
    }

    private void drawCell(Graphics2D g2d, Rectangle2D rect, Color borderColor) {
      g2d.setColor(borderColor);
      g2d.draw(rect);
    }

    @Override
    public int getIconWidth() {
      return GuiUtils.getScaleLength(ICON_SIZE);
    }

    @Override
    public int getIconHeight() {
      return GuiUtils.getScaleLength(ICON_SIZE);
    }
  }

  /** Color scheme for icon rendering. */
  private record ColorScheme(Color background, Color foreground) {}
}
