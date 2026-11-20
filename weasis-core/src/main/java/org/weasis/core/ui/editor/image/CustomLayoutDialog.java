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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.layout.CustomLayouts;
import org.weasis.core.api.gui.layout.CustomLayouts.Grid;
import org.weasis.core.api.gui.layout.CustomLayouts.Span;
import org.weasis.core.api.gui.layout.CustomLayouts.Tiles;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * Creates and edits the layouts of the user: the grids of views, whose cells can be merged, and the
 * grids of tiles of a viewport. Every size reads rows then columns.
 */
public final class CustomLayoutDialog extends JDialog {

  private final DefaultListModel<Grid> grids = new DefaultListModel<>();
  private final DefaultListModel<Tiles> tiles = new DefaultListModel<>();
  private final JList<Grid> gridList = new JList<>(grids);
  private final JList<Tiles> tileList = new JList<>(tiles);
  private final JTabbedPane tabs = new JTabbedPane();

  private final JTextField name = new JTextField(18);
  private final JSpinner rows = spinner(CustomLayouts.MAX_GRID);
  private final JSpinner cols = spinner(CustomLayouts.MAX_GRID);
  private final GridEditor editor = new GridEditor();
  private final JButton merge = new JButton(Messages.getString("CustomLayoutDialog.merge"));
  private final JButton split = new JButton(Messages.getString("CustomLayoutDialog.split"));
  private final JButton duplicate = new JButton(Messages.common("duplicate"));
  private final JButton delete = new JButton(Messages.common("delete"));

  /** What the selected cells hold: the first entry is a viewport, the others a component. */
  private final JComboBox<Object> content = new JComboBox<>();

  private final transient List<LayoutComponents.Kind> kinds;

  private final JSpinner tileRows = spinner(CustomLayouts.MAX_TILES);
  private final JSpinner tileCols = spinner(CustomLayouts.MAX_TILES);

  private final transient Consumer<Grid> applyGrid;
  private final transient Consumer<Tiles> applyTiles;
  private boolean updating;

  /**
   * @param applyGrid shows a grid of the user in the selected viewer, once saved; null when no
   *     viewer takes one
   * @param applyTiles shows the selected viewport on that grid of tiles; null when none takes one
   * @param tiledFirst opens on the tab of the tile grids
   * @param kinds the components a cell can hold instead of a viewport, for the viewer the layouts
   *     are edited for
   */
  public CustomLayoutDialog(
      Window owner,
      Consumer<Grid> applyGrid,
      Consumer<Tiles> applyTiles,
      boolean tiledFirst,
      List<LayoutComponents.Kind> kinds) {
    super(owner, Messages.getString("CustomLayoutDialog.title"), ModalityType.APPLICATION_MODAL);
    this.kinds = kinds == null ? List.of() : List.copyOf(kinds);
    this.applyGrid = applyGrid;
    this.applyTiles = applyTiles;
    CustomLayouts.grids().forEach(grids::addElement);
    CustomLayouts.tiles().forEach(tiles::addElement);

    tabs.addTab(Messages.getString("CustomLayoutDialog.views"), buildGridTab());
    tabs.addTab(Messages.getString("CustomLayoutDialog.tiled"), buildTileTab());
    tabs.setSelectedIndex(tiledFirst ? 1 : 0);

    JButton apply = new JButton(Messages.common("apply"));
    apply.addActionListener(e -> apply());
    JButton ok = new JButton(Messages.common("ok"));
    ok.addActionListener(
        e -> {
          if (save()) {
            dispose();
          }
        });
    JButton cancel = new JButton(Messages.common("cancel"));
    cancel.addActionListener(e -> dispose());

    JPanel content = new JPanel(new MigLayout("fill, ins 10", "[grow]", "[grow][]")); // NON-NLS
    content.add(tabs, "grow, wrap"); // NON-NLS
    content.add(apply, "split 3, align trailing, sg b"); // NON-NLS
    content.add(ok, "sg b"); // NON-NLS
    content.add(cancel, "sg b"); // NON-NLS
    setContentPane(content);
    getRootPane().setDefaultButton(ok);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    if (!grids.isEmpty()) {
      gridList.setSelectedIndex(0);
    }
    showSelectedGrid();
    pack();
  }

  private static JSpinner spinner(int max) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1, max, 1));
    GuiUtils.setPreferredWidth(spinner, 60);
    return spinner;
  }

  private JPanel buildGridTab() {
    gridList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    gridList.setCellRenderer(new GridRenderer());
    gridList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            showSelectedGrid();
          }
        });

    JButton create = new JButton(Messages.getString("CustomLayoutDialog.new"));
    create.addActionListener(e -> addGrid(null));
    duplicate.addActionListener(e -> addGrid(gridList.getSelectedValue()));
    delete.addActionListener(
        e -> {
          int index = gridList.getSelectedIndex();
          if (index >= 0) {
            grids.remove(index);
            gridList.setSelectedIndex(Math.min(index, grids.size() - 1));
            showSelectedGrid();
          }
        });

    name.getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                edited(g -> g.renamed(name.getText()));
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                edited(g -> g.renamed(name.getText()));
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                // attribute changes do not change the text
              }
            });
    rows.addChangeListener(e -> resize());
    cols.addChangeListener(e -> resize());
    merge.addActionListener(
        e -> {
          Rectangle s = editor.selection();
          if (s != null) {
            edited(g -> g.merge(s.y, s.x, s.height, s.width));
          }
        });
    split.addActionListener(
        e -> {
          Rectangle s = editor.selection();
          if (s != null) {
            edited(g -> g.split(s.y, s.x, s.height, s.width));
          }
        });
    editor.onSelection(this::updateCellButtons);
    editor.setTitles(
        id ->
            kinds.stream()
                .filter(k -> k.id().equals(id))
                .map(LayoutComponents.Kind::toString)
                .findFirst()
                .orElse(id));
    content.addItem(Messages.getString("CustomLayoutDialog.viewport"));
    kinds.forEach(content::addItem);
    content.setRenderer(new ContentRenderer());
    content.addActionListener(e -> applyContent());

    JScrollPane scroll = new JScrollPane(gridList);
    scroll.setPreferredSize(GuiUtils.getDimension(190, 240));

    JPanel panel =
        new JPanel(new MigLayout("fill, ins 8", "[][grow]", "[][][grow][][]")); // NON-NLS
    panel.add(scroll, "spany 3, growy"); // NON-NLS
    panel.add(new JLabel(Messages.getString("CustomLayoutDialog.name")), "split 2"); // NON-NLS
    panel.add(name, "growx, wrap"); // NON-NLS
    panel.add(new JLabel(Messages.getString("CustomLayoutDialog.rows")), "split 4"); // NON-NLS
    panel.add(rows);
    panel.add(
        new JLabel(Messages.getString("CustomLayoutDialog.columns")), "gapbefore 12"); // NON-NLS
    panel.add(cols, "wrap"); // NON-NLS
    panel.add(editor, "grow, wrap"); // NON-NLS
    panel.add(create, "split 3, sg g"); // NON-NLS
    panel.add(duplicate, "sg g"); // NON-NLS
    panel.add(delete, "sg g"); // NON-NLS
    panel.add(merge, "split 4, sg c"); // NON-NLS
    panel.add(split, "sg c"); // NON-NLS
    panel.add(
        new JLabel(Messages.getString("CustomLayoutDialog.content")), "gapbefore 12"); // NON-NLS
    panel.add(content, "wrap"); // NON-NLS
    panel.add(new JLabel(Messages.getString("CustomLayoutDialog.hint")), "skip, wrap"); // NON-NLS
    return panel;
  }

  private JPanel buildTileTab() {
    tileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    tileList.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Tiles t) {
              setText(
                  Messages.format("ImageViewerEventManager.viewport_tiled", t.rows(), t.cols()));
            }
            return this;
          }
        });
    JButton add = new JButton(Messages.common("add"));
    add.addActionListener(
        e -> {
          Tiles t = new Tiles((Integer) tileRows.getValue(), (Integer) tileCols.getValue());
          if (!tiles.contains(t)) {
            tiles.addElement(t);
          }
          tileList.setSelectedValue(t, true);
        });
    JButton remove = new JButton(Messages.common("delete"));
    remove.addActionListener(
        e -> {
          int index = tileList.getSelectedIndex();
          if (index >= 0) {
            tiles.remove(index);
          }
        });
    tileRows.setValue(3);
    tileCols.setValue(4);

    JScrollPane scroll = new JScrollPane(tileList);
    scroll.setPreferredSize(GuiUtils.getDimension(190, 240));
    JPanel panel = new JPanel(new MigLayout("fill, ins 8", "[][grow]", "[][][grow]")); // NON-NLS
    panel.add(scroll, "spany 3, growy"); // NON-NLS
    panel.add(new JLabel(Messages.getString("CustomLayoutDialog.rows")), "split 4"); // NON-NLS
    panel.add(tileRows);
    panel.add(
        new JLabel(Messages.getString("CustomLayoutDialog.columns")), "gapbefore 12"); // NON-NLS
    panel.add(tileCols, "wrap"); // NON-NLS
    panel.add(add, "split 2, sg t"); // NON-NLS
    panel.add(remove, "sg t, wrap"); // NON-NLS
    panel.add(
        new JLabel(Messages.getString("CustomLayoutDialog.tiled_hint")), "aligny top"); // NON-NLS
    return panel;
  }

  private void addGrid(Grid source) {
    Set<String> taken = new HashSet<>();
    for (int i = 0; i < grids.size(); i++) {
      taken.add(grids.get(i).id());
    }
    String id = CustomLayouts.newId(taken);
    Grid grid =
        source == null
            ? new Grid(
                id,
                Messages.format(
                    "CustomLayoutDialog.default_name",
                    id.substring(CustomLayouts.ID_PREFIX.length())),
                2,
                2,
                List.of())
            : new Grid(
                id,
                Messages.format("CustomLayoutDialog.copy_name", source.name()),
                source.rows(),
                source.cols(),
                source.merged());
    grids.addElement(grid);
    gridList.setSelectedIndex(grids.size() - 1);
    showSelectedGrid();
    name.requestFocusInWindow();
    name.selectAll();
  }

  /** Puts the selected grid in the fields, or disables them when there is none. */
  private void showSelectedGrid() {
    Grid grid = gridList.getSelectedValue();
    updating = true;
    try {
      boolean has = grid != null;
      name.setEnabled(has);
      rows.setEnabled(has);
      cols.setEnabled(has);
      editor.setEnabled(has);
      duplicate.setEnabled(has);
      delete.setEnabled(has);
      if (has) {
        if (!grid.name().equals(name.getText().trim())) {
          name.setText(grid.name());
        }
        rows.setValue(grid.rows());
        cols.setValue(grid.cols());
      } else {
        name.setText("");
      }
      editor.setGrid(grid);
    } finally {
      updating = false;
    }
    updateCellButtons();
  }

  private void resize() {
    edited(g -> g.resized((Integer) rows.getValue(), (Integer) cols.getValue()));
  }

  private void edited(java.util.function.UnaryOperator<Grid> change) {
    int index = gridList.getSelectedIndex();
    if (updating || index < 0) {
      return;
    }
    Grid changed = change.apply(grids.get(index));
    updating = true;
    try {
      grids.set(index, changed);
      editor.setGrid(changed);
    } finally {
      updating = false;
    }
    updateCellButtons();
  }

  private void updateCellButtons() {
    Grid grid = editor.grid();
    Rectangle s = editor.selection();
    boolean any = grid != null && s != null;
    merge.setEnabled(
        any
            && s.width * s.height > 1
            && grid.spanAt(s.y, s.x)
                .map(p -> p.rowSpan() != s.height || p.colSpan() != s.width)
                .orElse(true));
    split.setEnabled(any && grid.merged().stream().anyMatch(p -> intersects(p, s)));
    // what the first selected cell holds; a layout keeps one viewport at least
    boolean was = updating;
    updating = true;
    try {
      String id = any ? grid.contentAt(s.y, s.x) : null;
      Object item = content.getItemAt(0);
      for (LayoutComponents.Kind kind : kinds) {
        if (kind.id().equals(id)) {
          item = kind;
        }
      }
      content.setSelectedItem(item);
      content.setEnabled(any && !kinds.isEmpty());
    } finally {
      updating = was;
    }
  }

  /** Gives the selected cells the content chosen: a viewport or a component. */
  private void applyContent() {
    Rectangle s = editor.selection();
    if (updating || s == null) {
      return;
    }
    String id = content.getSelectedItem() instanceof LayoutComponents.Kind kind ? kind.id() : null;
    edited(
        g -> {
          Grid changed = g;
          for (int row = s.y; row < s.y + s.height; row++) {
            for (int col = s.x; col < s.x + s.width; col++) {
              changed = changed.withContent(row, col, id);
            }
          }
          return changed;
        });
  }

  private static final class ContentRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focus) {
      super.getListCellRendererComponent(list, value, index, selected, focus);
      setIcon(value instanceof LayoutComponents.Kind kind ? kind.icon().get() : null);
      return this;
    }
  }

  private static boolean intersects(Span p, Rectangle s) {
    return new Rectangle(p.col(), p.row(), p.colSpan(), p.rowSpan()).intersects(s);
  }

  private boolean save() {
    List<Grid> gridValues = new ArrayList<>();
    for (int i = 0; i < grids.size(); i++) {
      gridValues.add(grids.get(i));
    }
    List<Tiles> tileValues = new ArrayList<>();
    for (int i = 0; i < tiles.size(); i++) {
      tileValues.add(tiles.get(i));
    }
    try {
      CustomLayouts.replace(gridValues, tileValues);
      return true;
    } catch (IOException | IllegalStateException e) {
      JOptionPane.showMessageDialog(
          this,
          Messages.format("CustomLayoutDialog.save_error", e.getMessage()),
          getTitle(),
          JOptionPane.ERROR_MESSAGE);
      return false;
    }
  }

  /** Saves, then shows the selected entry of the open tab in the viewer. */
  private void apply() {
    if (!save()) {
      return;
    }
    if (tabs.getSelectedIndex() == 0) {
      Grid grid = gridList.getSelectedValue();
      if (grid != null && applyGrid != null) {
        applyGrid.accept(grid);
      }
    } else {
      Tiles t = tileList.getSelectedValue();
      if (t == null) {
        t = new Tiles((Integer) tileRows.getValue(), (Integer) tileCols.getValue());
      }
      if (applyTiles != null) {
        applyTiles.accept(t);
      }
    }
  }

  private static final class GridRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focus) {
      super.getListCellRendererComponent(list, value, index, selected, focus);
      if (value instanceof Grid grid) {
        setText(grid.name());
        setIcon(grid.toModel(ImageViewerPlugin.VIEWPORT_CLASS.getName()).getIcon());
      }
      return this;
    }
  }

  /** The grid drawn with its merged cells; dragging over cells selects a rectangle of them. */
  static final class GridEditor extends JComponent {
    private static final int GAP = 4;
    private transient Grid grid;
    private Rectangle selection;
    private int anchorRow = -1;
    private int anchorCol = -1;
    private transient Runnable listener = () -> {};
    private transient java.util.function.UnaryOperator<String> titles = id -> id;

    GridEditor() {
      setPreferredSize(GuiUtils.getDimension(340, 240));
      setMinimumSize(GuiUtils.getDimension(200, 140));
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              int[] cell = cellAt(e.getX(), e.getY());
              if (cell != null) {
                anchorRow = cell[0];
                anchorCol = cell[1];
                select(cell[0], cell[1]);
              }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              int[] cell = cellAt(e.getX(), e.getY());
              if (cell != null && anchorRow >= 0) {
                select(cell[0], cell[1]);
              }
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    /** The name of a component from its id, drawn in its cell. */
    void setTitles(java.util.function.UnaryOperator<String> titles) {
      this.titles = titles;
    }

    void onSelection(Runnable listener) {
      this.listener = listener;
    }

    Grid grid() {
      return grid;
    }

    /** The selected cells as columns (x, width) and rows (y, height), or null. */
    Rectangle selection() {
      return selection == null ? null : new Rectangle(selection);
    }

    void setGrid(Grid grid) {
      boolean sameShape =
          this.grid != null
              && grid != null
              && this.grid.id().equals(grid.id())
              && this.grid.rows() == grid.rows()
              && this.grid.cols() == grid.cols();
      this.grid = grid;
      if (!sameShape) {
        selection = null;
        anchorRow = -1;
      } else if (selection != null) {
        selection = cover(selection);
      }
      repaint();
    }

    private void select(int row, int col) {
      int r0 = Math.min(anchorRow, row);
      int c0 = Math.min(anchorCol, col);
      Rectangle next =
          cover(
              new Rectangle(c0, r0, Math.abs(anchorCol - col) + 1, Math.abs(anchorRow - row) + 1));
      if (!next.equals(selection)) {
        selection = next;
        repaint();
        listener.run();
      }
    }

    /** Grows a rectangle of cells until it holds every merged cell it touches. */
    private Rectangle cover(Rectangle cells) {
      Rectangle r = new Rectangle(cells);
      boolean grown = true;
      while (grown && grid != null) {
        grown = false;
        for (Span p : grid.merged()) {
          Rectangle span = new Rectangle(p.col(), p.row(), p.colSpan(), p.rowSpan());
          if (span.intersects(r) && !r.contains(span)) {
            r = r.union(span);
            grown = true;
          }
        }
      }
      return r;
    }

    private int[] cellAt(int x, int y) {
      if (grid == null || !isEnabled()) {
        return null;
      }
      double w = getWidth() / (double) grid.cols();
      double h = getHeight() / (double) grid.rows();
      int col = Math.clamp((int) (x / w), 0, grid.cols() - 1);
      int row = Math.clamp((int) (y / h), 0, grid.rows() - 1);
      return new int[] {row, col};
    }

    private Rectangle bounds(int row, int col, int rowSpan, int colSpan) {
      double w = getWidth() / (double) grid.cols();
      double h = getHeight() / (double) grid.rows();
      int x0 = (int) Math.round(col * w);
      int y0 = (int) Math.round(row * h);
      int x1 = (int) Math.round((col + colSpan) * w);
      int y1 = (int) Math.round((row + rowSpan) * h);
      return new Rectangle(
          x0 + GAP / 2, y0 + GAP / 2, x1 - x0 - GAP, y1 - y0 - GAP); // NON-RTL: a grid of views
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (grid == null) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      Color cell = color("TextField.background", Color.DARK_GRAY); // NON-NLS
      Color border = color("Component.borderColor", Color.GRAY); // NON-NLS
      Color selected = color("List.selectionBackground", Color.BLUE); // NON-NLS
      Color text = color("Label.foreground", Color.WHITE); // NON-NLS
      Color selectedText = color("List.selectionForeground", Color.WHITE); // NON-NLS
      int number = 1;
      for (int row = 0; row < grid.rows(); row++) {
        for (int col = 0; col < grid.cols(); col++) {
          Span span = grid.spanAt(row, col).orElse(null);
          if (span != null && (span.row() != row || span.col() != col)) {
            continue; // drawn with the first cell of the merged cell
          }
          int rs = span == null ? 1 : span.rowSpan();
          int cs = span == null ? 1 : span.colSpan();
          Rectangle b = bounds(row, col, rs, cs);
          boolean isSelected =
              selection != null && selection.contains(new Rectangle(col, row, cs, rs));
          g2.setColor(isSelected ? selected : cell);
          g2.fillRoundRect(b.x, b.y, b.width, b.height, 6, 6);
          g2.setColor(border);
          g2.drawRoundRect(b.x, b.y, b.width, b.height, 6, 6);
          String component = grid.contentAt(row, col);
          // a viewport carries its number in the layout, a component its name
          String label = component == null ? String.valueOf(number++) : titles.apply(component);
          if (component != null && !isSelected) {
            g2.setColor(color("Button.background", Color.GRAY)); // NON-NLS
            g2.fillRoundRect(b.x + 1, b.y + 1, b.width - 2, b.height - 2, 6, 6);
          }
          FontMetrics fm = g2.getFontMetrics();
          g2.setColor(isSelected ? selectedText : text);
          g2.drawString(
              label,
              b.x + (b.width - fm.stringWidth(label)) / 2,
              b.y + (b.height + fm.getAscent() - fm.getDescent()) / 2);
        }
      }
      g2.dispose();
    }

    private static Color color(String key, Color fallback) {
      Color c = UIManager.getColor(key);
      return c == null ? fallback : c;
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
  }
}
