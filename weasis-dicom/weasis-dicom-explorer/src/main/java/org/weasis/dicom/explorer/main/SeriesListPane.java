/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GhostGlassPane;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.AbstractKOSpecialElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.DownloadSummary;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.ObjectItem;
import org.weasis.dicom.explorer.items.SeriesItem;
import org.weasis.dicom.explorer.items.StudyContents;
import org.weasis.dicom.explorer.wado.LoadSeries;

/**
 * The compact list mode of the DICOM explorer: one row per series and per non-image object of the
 * selected patient, the studies as group rows, read from {@link StudyContents} with the series
 * filter applied. Selection is shared with the thumbnails through the {@link SeriesSelectionModel};
 * a double click or Enter opens, a right click shows the series context menu, and a series row is
 * dragged onto a view like a thumbnail.
 */
public class SeriesListPane extends JPanel implements Scrollable {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm"); // NON-NLS

  /** One row of the table. */
  sealed interface Row permits StudyRow, SeriesRow, ObjectRow {}

  record StudyRow(MediaSeriesGroup study, StudyContents contents) implements Row {}

  record SeriesRow(SeriesItem item) implements Row {}

  record ObjectRow(ObjectItem item) implements Row {}

  private final DicomExplorer explorer;
  private final ListModel tableModel = new ListModel();
  private final JTable table = new JTable(tableModel);
  private final SeriesSelectionModel.SelectionChangeListener selectionListener =
      _ -> syncFromSelection();
  private final java.util.Map<Integer, TableColumn> optionalColumns = new java.util.HashMap<>();
  private final java.util.Set<String> collapsed = new java.util.HashSet<>();
  private final java.util.Map<DicomSeries, ImageIcon> thumbnailCache = new java.util.HashMap<>();
  private final javax.swing.Timer thumbnailPoll = new javax.swing.Timer(400, _ -> table.repaint());
  private ListDensity density = ListDensity.MEDIUM;
  private List<Row> rows = List.of();
  private boolean syncing;
  private int pressedRow = -1;

  public SeriesListPane(DicomExplorer explorer) {
    super(new MigLayout("fill, ins 0, gap 0", "[grow,fill]", "[]0[grow,fill]")); // NON-NLS
    this.explorer = Objects.requireNonNull(explorer);
    setupTable();
    // The header is only automatic when the table itself is the viewport view
    add(table.getTableHeader(), "growx, wrap"); // NON-NLS
    add(table, "grow"); // NON-NLS
    explorer.getSelectionList().addSelectionChangeListener(selectionListener);
  }

  private void setupTable() {
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setFillsViewportHeight(true);
    table.setShowGrid(false);
    table.setIntercellSpacing(new Dimension(0, 0));
    thumbnailPoll.setRepeats(false);
    applyDensity(explorer.getListDensity());
    table.setFont(FontItem.SMALL.getFont());
    table.getTableHeader().setFont(FontItem.SMALL_SEMIBOLD.getFont());
    table.getTableHeader().setReorderingAllowed(false);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.setDefaultRenderer(Object.class, new RowRenderer());
    table.setBorder(BorderFactory.createEmptyBorder());
    // A series row is dragged like a thumbnail; files dropped on the list are imported
    table.setDragEnabled(true);
    table.setTransferHandler(new RowTransferHandler());
    TableColumnModel columns = table.getColumnModel();
    columns.getColumn(0).setPreferredWidth(GuiUtils.getScaleLength(44));
    columns.getColumn(0).setMaxWidth(GuiUtils.getScaleLength(70));
    columns.getColumn(1).setPreferredWidth(GuiUtils.getScaleLength(44));
    columns.getColumn(1).setMaxWidth(GuiUtils.getScaleLength(80));
    columns.getColumn(2).setPreferredWidth(GuiUtils.getScaleLength(160));
    columns.getColumn(3).setPreferredWidth(GuiUtils.getScaleLength(40));
    columns.getColumn(3).setMaxWidth(GuiUtils.getScaleLength(70));
    columns.getColumn(4).setPreferredWidth(GuiUtils.getScaleLength(44));
    columns.getColumn(4).setMaxWidth(GuiUtils.getScaleLength(70));
    columns.getColumn(5).setPreferredWidth(GuiUtils.getScaleLength(60));
    columns.getColumn(5).setMaxWidth(GuiUtils.getScaleLength(110));
    optionalColumns.put(5, columns.getColumn(5));
    optionalColumns.put(4, columns.getColumn(4));
    optionalColumns.put(3, columns.getColumn(3));
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            updateColumnsForWidth();
          }
        });

    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) {
                syncToSelection();
                focusLeadObject();
              }
            });
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            pressedRow = viewRow;
            if (viewRow < 0) {
              return;
            }
            Row row = rows.get(viewRow);
            if (SwingUtilities.isRightMouseButton(e) && row instanceof SeriesRow s) {
              if (!table.isRowSelected(viewRow)) {
                table.setRowSelectionInterval(viewRow, viewRow);
              }
              showContextMenu(e, s.item().series());
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            if (viewRow < 0 || !SwingUtilities.isLeftMouseButton(e)) {
              return;
            }
            Row row = rows.get(viewRow);
            if (row instanceof StudyRow st) {
              // A click on a group row folds or unfolds it
              toggleCollapsed(st.contents().studyUID());
            } else if (e.getClickCount() == 2) {
              open(row);
            }
          }
        });
    table.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              explorer.getSelectionList().openSelectedSeries(explorer.getDataExplorerModel());
              e.consume();
            }
          }
        });
  }

  /**
   * Shows the optional columns (images, time, objects) from the right as the width allows, so a
   * narrow panel keeps the modality, the number and a readable description.
   */
  private void updateColumnsForWidth() {
    int width = getWidth();
    if (width <= 0) {
      return;
    }
    // Widths below which a column leaves, from the rightmost one
    boolean showObjects = width >= GuiUtils.getScaleLength(360);
    boolean showTime = width >= GuiUtils.getScaleLength(300);
    boolean showImages = width >= GuiUtils.getScaleLength(240);
    setColumnVisible(5, showObjects);
    setColumnVisible(4, showTime);
    setColumnVisible(3, showImages);
  }

  private void setColumnVisible(int modelIndex, boolean visible) {
    TableColumn column = optionalColumns.get(modelIndex);
    TableColumnModel columns = table.getColumnModel();
    boolean shown = false;
    for (int i = 0; i < columns.getColumnCount(); i++) {
      if (columns.getColumn(i) == column) {
        shown = true;
        break;
      }
    }
    if (visible && !shown) {
      columns.addColumn(column);
      // keep the model order: move the column back before any later one already shown
      int target = 0;
      for (int i = 0; i < columns.getColumnCount(); i++) {
        if (columns.getColumn(i) != column && columns.getColumn(i).getModelIndex() < modelIndex) {
          target = i + 1;
        }
      }
      columns.moveColumn(columns.getColumnCount() - 1, target);
    } else if (!visible && shown) {
      columns.removeColumn(column);
    }
  }

  /** Rebuilds the rows from the model for the selected patient, applying the series filter. */
  public void refresh() {
    DicomModel model = explorer.getDataExplorerModel();
    MediaSeriesGroup patient = explorer.getSelectedPatient();
    SeriesFilter filter = explorer.getSeriesFilter();
    List<Row> list = new ArrayList<>();
    if (patient != null) {
      for (MediaSeriesGroup study : StudyContents.studiesOf(model, patient)) {
        StudyContents contents = StudyContents.of(model, study);
        List<SeriesItem> visible =
            contents.series().stream().filter(s -> filter.test(s.series(), study)).toList();
        if (visible.isEmpty() && !contents.hasObjects()) {
          continue;
        }
        list.add(new StudyRow(study, contents));
        if (collapsed.contains(contents.studyUID())) {
          continue;
        }
        visible.forEach(s -> list.add(new SeriesRow(s)));
        if (explorer.isObjectsShown()) {
          contents
              .visibleObjects(explorer.isRejectionListed())
              .forEach(o -> list.add(new ObjectRow(o)));
        }
      }
    }
    rows = List.copyOf(list);
    thumbnailCache
        .keySet()
        .retainAll(
            list.stream()
                .filter(SeriesRow.class::isInstance)
                .map(r -> ((SeriesRow) r).item().series())
                .collect(Collectors.toSet()));
    syncing = true;
    try {
      tableModel.fireTableDataChanged();
    } finally {
      syncing = false;
    }
    syncFromSelection();
  }

  // --- Scrollable: the pane is as wide as the viewport (the columns share that width) and as
  // tall as its rows, so the scroll pane scrolls vertically only, like the thumbnail pane.

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return table.getRowHeight();
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  /** Applies the row height and the thumbnail size of a density. */
  public void applyDensity(ListDensity newDensity) {
    density = Objects.requireNonNull(newDensity);
    thumbnailCache.clear();
    table.setRowHeight(GuiUtils.getScaleLength(density.getRowHeight()));
    int first =
        GuiUtils.getScaleLength(
            density.getThumbnailSize() > 0 ? density.getThumbnailSize() + 44 : 44);
    // Fixed columns keep their width under the shared resize; the description absorbs the rest
    table.getColumnModel().getColumn(0).setMinWidth(first);
    table.getColumnModel().getColumn(0).setPreferredWidth(first);
    table.getColumnModel().getColumn(0).setMaxWidth(first + GuiUtils.getScaleLength(26));
    table.getColumnModel().getColumn(1).setMinWidth(GuiUtils.getScaleLength(40));
    table.repaint();
  }

  /**
   * The micro-thumbnail of a series from the thumbnail cache, null while it is being built (the
   * table polls until it is); nothing is decoded that the thumbnails would not decode.
   */
  private ImageIcon microThumbnail(DicomSeries series) {
    int size = GuiUtils.getScaleLength(density.getThumbnailSize());
    if (size <= 0) {
      return null;
    }
    ImageIcon cached = thumbnailCache.get(series);
    if (cached != null) {
      return cached;
    }
    if (series.getTagValue(TagW.Thumbnail) instanceof SeriesThumbnail thumbnail) {
      java.awt.image.BufferedImage image = thumbnail.getScaledImage(size);
      if (image != null) {
        ImageIcon icon = new ImageIcon(image);
        thumbnailCache.put(series, icon);
        return icon;
      }
      thumbnailPoll.restart();
    }
    return null;
  }

  /** Folds or unfolds the rows of a study under its group row. */
  /** Unfolds the study of the series if needed, scrolls its row into view and selects it. */
  public void reveal(DicomSeries series) {
    MediaSeriesGroup study = explorer.getDataExplorerModel().getParent(series, DicomModel.study);
    String studyUID =
        study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    if (studyUID != null && collapsed.remove(studyUID)) {
      refresh();
    }
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i) instanceof SeriesRow s && s.item().series() == series) {
        table.scrollRectToVisible(table.getCellRect(i, 0, true));
        syncing = true;
        try {
          table.setRowSelectionInterval(i, i);
        } finally {
          syncing = false;
        }
        return;
      }
    }
  }

  public void toggleCollapsed(String studyUID) {
    if (studyUID == null) {
      return;
    }
    if (!collapsed.remove(studyUID)) {
      collapsed.add(studyUID);
    }
    refresh();
  }

  boolean isCollapsed(String studyUID) {
    return collapsed.contains(studyUID);
  }

  /** The rows currently shown, for the commands and the tests. */
  List<Row> getRows() {
    return rows;
  }

  public void dispose() {
    thumbnailPoll.stop();
    thumbnailCache.clear();
    explorer.getSelectionList().removeSelectionChangeListener(selectionListener);
  }

  private void syncToSelection() {
    if (syncing) {
      return;
    }
    syncing = true;
    try {
      List<DicomSeries> selected = new ArrayList<>();
      for (int viewRow : table.getSelectedRows()) {
        if (viewRow < rows.size() && rows.get(viewRow) instanceof SeriesRow s) {
          selected.add(s.item().series());
        }
      }
      SeriesSelectionModel selection = explorer.getSelectionList();
      if (!selected.equals(new ArrayList<>(selection))) {
        selection.clear();
        selection.addAll(selected);
      }
    } finally {
      syncing = false;
    }
  }

  /** An object row has no series selection: the detail tools get it as the focused item. */
  private void focusLeadObject() {
    int lead = table.getSelectionModel().getLeadSelectionIndex();
    if (lead >= 0 && lead < rows.size() && rows.get(lead) instanceof ObjectRow o) {
      explorer.focusItem(o.item());
    }
  }

  private void syncFromSelection() {
    if (syncing) {
      return;
    }
    syncing = true;
    try {
      SeriesSelectionModel selection = explorer.getSelectionList();
      table.clearSelection();
      for (int i = 0; i < rows.size(); i++) {
        if (rows.get(i) instanceof SeriesRow s && selection.isSelected(s.item().series())) {
          table.addRowSelectionInterval(i, i);
        }
      }
    } finally {
      syncing = false;
    }
  }

  private void showContextMenu(MouseEvent e, DicomSeries series) {
    LoadSeries loader =
        series.getSeriesLoader() instanceof LoadSeries loadSeries ? loadSeries : null;
    // The thumbnail adapter owns the series context menu; it only needs the event and the series
    new ThumbnailMouseAndKeyAdapter(series, explorer.getDataExplorerModel(), loader)
        .mousePressed(e);
  }

  private void open(Row row) {
    DicomModel model = explorer.getDataExplorerModel();
    switch (row) {
      case SeriesRow s ->
          ThumbnailMouseAndKeyAdapter.openSeriesInDefaultPlugin(s.item().series(), model);
      case ObjectRow o -> openObject(o.item(), model);
      case StudyRow _ -> {
        // a group row opens nothing
      }
    }
  }

  /** Opens what an object refers to: the key images of a KO, else its first loaded series. */
  private void openObject(ObjectItem item, DicomModel model) {
    if (item.element() instanceof KOSpecialElement ko) {
      MediaSeriesGroup patient = model.getParent(item.study(), DicomModel.patient);
      if (patient != null) {
        model.openRelatedSeries(ko, patient);
        return;
      }
    }
    for (String uid : item.descriptor().referencedSeriesUIDs()) {
      if (model.getSeriesNode(uid) instanceof DicomSeries series) {
        ThumbnailMouseAndKeyAdapter.openSeriesInDefaultPlugin(series, model);
        return;
      }
    }
  }

  /** The series of the row where a drag started, null for a study or an object row. */
  private DicomSeries draggedSeries() {
    return pressedRow >= 0
            && pressedRow < rows.size()
            && rows.get(pressedRow) instanceof SeriesRow s
        ? s.item().series()
        : null;
  }

  /**
   * Exports the series of the pressed row with the flavor the views accept, showing its thumbnail
   * under the pointer in the glass pane as the thumbnails do; imports dropped DICOM files.
   */
  private final class RowTransferHandler extends SequenceHandler {
    private final DragSourceMotionListener motion = e -> moveGhost(e.getLocation());
    private Point ghostOffset;

    RowTransferHandler() {
      super(false, true);
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY;
    }

    @Override
    public Transferable createTransferable(JComponent c) {
      return draggedSeries();
    }

    @Override
    public void exportAsDrag(JComponent comp, InputEvent e, int action) {
      DicomSeries series = draggedSeries();
      Icon icon = series == null ? null : ghostIcon(series);
      if (icon != null && e instanceof MouseEvent me) {
        GhostGlassPane glassPane = AppProperties.glassPane;
        glassPane.setIcon(icon);
        ghostOffset = new Point(icon.getIconWidth() / 2, icon.getIconHeight() / 2);
        Point p = me.getPoint();
        SwingUtilities.convertPointToScreen(p, comp);
        moveGhost(p);
        glassPane.setVisible(true);
        DragSource.getDefaultDragSource().addDragSourceMotionListener(motion);
      }
      super.exportAsDrag(comp, e, action);
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      DragSource.getDefaultDragSource().removeDragSourceMotionListener(motion);
      if (ghostOffset != null) {
        ghostOffset = null;
        GhostGlassPane glassPane = AppProperties.glassPane;
        glassPane.setImagePosition(null);
        glassPane.setIcon(null);
        glassPane.setVisible(false);
      }
      pressedRow = -1;
    }

    private void moveGhost(Point screen) {
      if (ghostOffset != null) {
        GhostGlassPane glassPane = AppProperties.glassPane;
        Point p = new Point(screen);
        SwingUtilities.convertPointFromScreen(p, glassPane);
        p.translate(-ghostOffset.x, -ghostOffset.y);
        glassPane.setImagePosition(p);
      }
    }

    /** The series thumbnail when it is built, else the micro-thumbnail of the row. */
    private Icon ghostIcon(DicomSeries series) {
      if (series.getTagValue(TagW.Thumbnail) instanceof SeriesThumbnail thumbnail
          && thumbnail.getIcon() != null) {
        return thumbnail.getIcon();
      }
      return microThumbnail(series);
    }

    @Override
    protected boolean dropFiles(List<Path> files) {
      return DicomSeriesHandler.dropDicomFiles(files);
    }
  }

  // ---------------------------------------------------------------------------------------------

  private final class ListModel extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("DicomExplorer.col_modality"),
      Messages.getString("DicomExplorer.col_number"),
      Messages.common("description"),
      Messages.getString("DicomExplorer.col_images"),
      Messages.getString("DicomExplorer.col_time"),
      Messages.getString("DicomExplorer.col_objects")
    };

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Row row = rows.get(rowIndex);
      return switch (row) {
        case StudyRow st ->
            columnIndex == 2
                ? (collapsed.contains(st.contents().studyUID()) ? "\u25B8 " : "\u25BE ")
                    + studyText(st.study(), st.contents())
                : "";
        case SeriesRow s -> seriesValue(s.item(), columnIndex);
        case ObjectRow o -> objectValue(o.item(), columnIndex);
      };
    }
  }

  private String seriesValue(SeriesItem item, int column) {
    return switch (column) {
      case 0 -> StringUtil.getEmptyStringIfNull(item.modality());
      case 1 ->
          item.seriesNumber() == null
              ? ""
              : item.isSplit()
                  ? item.seriesNumber() + "." + item.splitNumber()
                  : String.valueOf(item.seriesNumber());
      case 2 -> formatted(item.series(), Tag.SeriesDescription);
      case 3 -> item.imageCount() > 0 ? String.valueOf(item.imageCount()) : "";
      case 4 -> time(item.date());
      case 5 -> flags(item);
      default -> "";
    };
  }

  private static String objectValue(ObjectItem item, int column) {
    return switch (column) {
      case 0 -> item.kind().getId().toUpperCase();
      case 2 -> StringUtil.getEmptyStringIfNull(item.label());
      case 3 -> {
        int n = item.descriptor().referencedImages();
        yield n > 0 ? String.valueOf(n) : "";
      }
      case 4 -> time(item.date());
      case 5 -> StringUtil.getEmptyStringIfNull(item.descriptor().creator());
      default -> "";
    };
  }

  private String flags(SeriesItem item) {
    List<String> flags = new ArrayList<>();
    int interest = Interest.of(item.series()).count();
    if (interest > 0) {
      flags.add("\u2605" + interest);
    }
    if (item.downloadState() != DownloadState.COMPLETE) {
      flags.add(item.downloadState().getId());
    }
    if (explorer.rejectedVisibility() != RejectedVisibility.HIDDEN) {
      int rejected = item.rejectedImageCount();
      if (rejected > 0) {
        flags.add(Messages.format("DicomExplorer.rejected_count", rejected));
      }
    }
    String seriesUID = item.seriesUID();
    for (ObjectKind kind : StudyContents.kindsReferencing(seriesUID)) {
      flags.add(kind.getId().toUpperCase());
    }
    return String.join(" ", flags);
  }

  private String studyText(MediaSeriesGroup study, StudyContents contents) {
    String date = formatted(study, Tag.StudyDate);
    String description = formatted(study, Tag.StudyDescription);
    StringBuilder buf = new StringBuilder();
    if (!date.isEmpty()) {
      buf.append(date);
    }
    if (!description.isEmpty()) {
      if (!buf.isEmpty()) {
        buf.append(" - ");
      }
      buf.append(description);
    }
    if (buf.isEmpty()) {
      // No date and no description: the identifiers a reader knows, never the UID
      String id = formatted(study, Tag.StudyID);
      if (id.isEmpty()) {
        id = formatted(study, Tag.AccessionNumber);
      }
      buf.append(id.isEmpty() ? Messages.common("study") : id);
    }
    Map<ObjectKind, Integer> counts = contents.visibleCountByKind(explorer.isRejectionListed());
    if (!counts.isEmpty()) {
      buf.append("  (")
          .append(
              counts.entrySet().stream()
                  .map(e -> e.getValue() + " " + FacetChipBar.kindLabel(e.getKey()))
                  .collect(Collectors.joining(", ")))
          .append(')');
    }
    DownloadSummary summary = DownloadSummary.of(contents);
    if (!summary.isComplete()) {
      buf.append("  \u2022  ")
          .append(
              Messages.format("StudyContentTool.downloaded", summary.complete(), summary.total()));
    }
    return buf.toString();
  }

  /** Masked and formatted value of a tag, empty when absent. */
  private static String formatted(TagReadable taggable, int tagId) {
    TagW tag = TagD.get(tagId);
    Object masked = IdentityMask.maskValue(tag, taggable.getTagValue(tag));
    String text = masked == null ? null : tag.getFormattedTagValue(masked, null);
    return StringUtil.hasText(text) ? text : "";
  }

  private static String time(LocalDateTime date) {
    return date == null ? "" : TIME.format(date);
  }

  private boolean isRelated(Row row) {
    return switch (row) {
      case SeriesRow s -> explorer.isRelatedSeries(s.item().seriesUID());
      case ObjectRow o -> explorer.isRelatedObject(o.item().descriptor().sopInstanceUID());
      case StudyRow _ -> false;
      case null -> false;
    };
  }

  /** The accent blended into the background of the table: a tint, not a selection. */
  private static Color relatedBackground(JTable table) {
    Color accent = SeriesPane.relatedColor();
    Color back = table.getBackground();
    return new Color(
        (accent.getRed() + 3 * back.getRed()) / 4,
        (accent.getGreen() + 3 * back.getGreen()) / 4,
        (accent.getBlue() + 3 * back.getBlue()) / 4);
  }

  private final class RowRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Row r = row < rows.size() ? rows.get(row) : null;
      boolean group = r instanceof StudyRow;
      // The renderer keeps the last unselected background it was given: forget the tint of the
      // previous cell before the default colors are applied
      setBackground(null);
      super.getTableCellRendererComponent(t, value, isSelected && !group, false, row, column);
      if (!isSelected && isRelated(r)) {
        // Reverse highlighting: the series the focused object references, the objects referencing
        // the selected series
        setBackground(relatedBackground(t));
      }
      setFont(group ? FontItem.SMALL_SEMIBOLD.getFont() : FontItem.SMALL.getFont());
      setHorizontalAlignment(column == 1 || column == 3 ? RIGHT : LEFT);
      setIcon(null);
      if (r instanceof ObjectRow o && column == 0 && o.item().icon() != null) {
        setIcon(ResourceUtil.getIcon(o.item().icon(), 16, 16));
        setText("");
      } else if (r instanceof SeriesRow sr && column == 0) {
        setIcon(microThumbnail(sr.item().series()));
        setIconTextGap(GuiUtils.getScaleLength(4));
      }
      String tip = null;
      if (r instanceof SeriesRow s) {
        tip = formatted(s.item().series(), Tag.SeriesDescription);
      } else if (r instanceof ObjectRow o) {
        tip =
            o.item().descriptor().provenance() == null
                ? o.item().descriptor().creator()
                : o.item().descriptor().provenance().describe();
        if (o.item().element() instanceof AbstractKOSpecialElement ko) {
          tip = ko.getDocumentTitle();
        }
      }
      setToolTipText(StringUtil.hasText(tip) ? tip : null);
      return this;
    }
  }
}
