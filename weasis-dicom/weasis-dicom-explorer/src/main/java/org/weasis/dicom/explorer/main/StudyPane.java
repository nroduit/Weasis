/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.util.FontItem;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.DownloadSummary;
import org.weasis.dicom.explorer.items.ItemActions;
import org.weasis.dicom.explorer.items.StudyContents;

/**
 * A panel component that displays a DICOM study and manages its associated series panes. This
 * component provides layout management.
 */
public class StudyPane extends JPanel {

  private static final String LAYOUT_CONSTRAINTS = "fillx, flowy, insets 0";
  private static final String COLUMN_CONSTRAINTS = "[fill]";
  private static final String SUB_PANEL_CONSTRAINTS = "shrinky 100";
  private static final int GAP = 5;
  private static final int SUB_PANEL_INSET = 3; // subPanel MigLayout "insets Nlp"
  private static final int BORDER_INSET = 3; // study pane empty border left/right
  private static final int SEPARATION = 4; // gap kept between the last column and the scrollbar

  private final JPanel subPanel;
  private final MediaSeriesGroup dicomStudy;
  private final TitledBorder titleBorder;
  private final ComponentResizeHandler resizeHandler;
  private String hint;
  // Header under the title: one chip per kind of object with its count, then, while series of the
  // study are not downloaded, "n of N series downloaded" and Download all
  private final JPanel downloadHeader =
      new JPanel(new MigLayout("insets 0 3 0 3, fillx, gapx 4", "[][grow][][]"));
  private final JPanel chips = new JPanel(new MigLayout("insets 0, gapx 3"));
  // one button per plugin that offers actions on this study (ExplorerActionProvider)
  private final JPanel providers = new JPanel(new MigLayout("insets 0, gapx 1"));
  private List<String> shownProviders = List.of();
  private Supplier<List<DicomSeries>> selection = List::of;
  private final JLabel downloadLabel = new JLabel();
  private final JButton downloadAll =
      new JButton(Messages.getString("StudyContentTool.download_all"));
  private Runnable downloadAction;
  private Consumer<ObjectKind> objectKindAction;
  private Map<ObjectKind, Integer> shownCounts = Map.of();
  private int columns; // thumbnail columns of the sub-panel layout, 0 before the first refresh

  /**
   * Creates a new StudyPane for the specified DICOM study.
   *
   * @param dicomStudy the DICOM study to display, must not be null
   * @throws IllegalArgumentException if dicomStudy is null
   */
  public StudyPane(MediaSeriesGroup dicomStudy) {
    super(new MigLayout(LAYOUT_CONSTRAINTS, COLUMN_CONSTRAINTS));

    this.dicomStudy = Objects.requireNonNull(dicomStudy);
    // Sub-panel padding uses a Swing border (measurable via getInsets), not MigLayout "lp" insets,
    // so the dock width can reserve exactly one column at any UI scale.
    this.subPanel = new JPanel(new MigLayout("insets 0, gap " + GAP + " " + GAP + ", flowx"));
    this.subPanel.setBorder(GuiUtils.getEmptyBorder(SUB_PANEL_INSET));
    this.titleBorder = GuiUtils.getTitledBorder(dicomStudy.toString());
    this.resizeHandler = new ComponentResizeHandler();

    initializeComponent();
  }

  private void initializeComponent() {
    setBorder(
        BorderFactory.createCompoundBorder(
            GuiUtils.getEmptyBorder(0, BORDER_INSET, 0, BORDER_INSET), titleBorder));
    setFocusable(false);
    downloadLabel.setFont(FontItem.MINI.getFont());
    downloadAll.setFont(FontItem.MINI.getFont());
    downloadAll.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    downloadAll.setFocusable(false);
    downloadAll.addActionListener(
        _ -> {
          Runnable action = downloadAction;
          if (action != null) {
            action.run();
          }
        });
    chips.setOpaque(false);
    downloadLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    downloadHeader.add(chips);
    downloadHeader.add(downloadLabel, "growx");
    downloadHeader.add(downloadAll);
    providers.setOpaque(false);
    downloadHeader.add(providers);
    downloadHeader.setVisible(false);
    add(downloadHeader, "hidemode 3, growx");
    add(subPanel, SUB_PANEL_CONSTRAINTS);
    addComponentListener(resizeHandler);
  }

  /** The series selected in the explorer, read when a plugin button of the header is clicked. */
  public void setSelectionSupplier(Supplier<List<DicomSeries>> selection) {
    this.selection = selection == null ? List::of : selection;
  }

  /** What a click on an object chip does: filter the series referenced by that kind of object. */
  public void setObjectKindAction(Consumer<ObjectKind> action) {
    this.objectKindAction = action;
  }

  /**
   * Refreshes the header: one chip per kind of object the study holds with its count, and, while
   * series are not all downloaded, how many are and a button for the rest. Hidden when the study
   * has neither. Called when the model changes or a transfer starts, stops or completes.
   *
   * @param rejectionsListed whether the rejection notes count as objects (site setting)
   */
  public void refreshHeader(DicomModel model, boolean rejectionsListed) {
    StudyContents contents = StudyContents.of(model, dicomStudy);
    Map<ObjectKind, Integer> counts = contents.visibleCountByKind(rejectionsListed);
    if (!counts.equals(shownCounts)) {
      shownCounts = counts;
      chips.removeAll();
      counts.forEach((kind, count) -> chips.add(chip(kind, count)));
    }
    DownloadSummary summary = DownloadSummary.of(contents);
    boolean partial = !summary.isComplete();
    downloadLabel.setVisible(partial);
    downloadAll.setVisible(partial && summary.hasPending());
    if (partial) {
      downloadLabel.setText(
          Messages.format("StudyContentTool.downloaded", summary.complete(), summary.total()));
      downloadAction = () -> ItemActions.downloadAll(StudyContents.of(model, dicomStudy));
    } else {
      downloadAction = null;
    }
    List<JButton> buttons = ExplorerActionMenus.buttons(model, dicomStudy, selection, false);
    List<String> ids = buttons.stream().map(JButton::getName).toList();
    if (!ids.equals(shownProviders)) {
      shownProviders = ids;
      providers.removeAll();
      buttons.forEach(providers::add);
    }
    boolean shown = partial || !counts.isEmpty() || !ids.isEmpty();
    if (downloadHeader.isVisible() != shown) {
      downloadHeader.setVisible(shown);
    }
    downloadHeader.revalidate();
    downloadHeader.repaint();
  }

  private JButton chip(ObjectKind kind, int count) {
    String label = FacetChipBar.kindLabel(kind);
    JButton chip = new JButton(count + " " + label);
    chip.setFont(FontItem.MINI.getFont());
    chip.setFocusable(false);
    chip.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
    chip.putClientProperty(FlatClientProperties.STYLE, "margin: 0,5,0,5"); // NON-NLS
    chip.setToolTipText(Messages.format("DicomExplorer.chip_kind", label));
    chip.addActionListener(
        _ -> {
          Consumer<ObjectKind> action = objectKindAction;
          if (action != null) {
            action.accept(kind);
          }
        });
    return chip;
  }

  /** The kinds of objects the header shows, for tests and the agent. */
  public Map<ObjectKind, Integer> getObjectCounts() {
    return shownCounts;
  }

  /** The download line of the study header, for tests and the agent: null while hidden. */
  public String getDownloadText() {
    return downloadLabel.isVisible() ? downloadLabel.getText() : null;
  }

  /**
   * Estimated horizontal space a study pane adds around a single thumbnail column (its border plus
   * the sub-panel padding). Used only as a fallback for the initial dock width before any study
   * pane exists; once one does, {@link #getSingleColumnWidth()} gives the exact width.
   *
   * @param c a component used to measure the (font-dependent) titled border insets
   */
  public static int getHorizontalChrome(Component c) {
    Insets border =
        BorderFactory.createCompoundBorder(
                GuiUtils.getEmptyBorder(0, BORDER_INSET, 0, BORDER_INSET),
                GuiUtils.getTitledBorder(" "))
            .getBorderInsets(c);
    Insets subPanelPadding = GuiUtils.getEmptyBorder(SUB_PANEL_INSET).getBorderInsets(c);
    return border.left + border.right + subPanelPadding.left + subPanelPadding.right;
  }

  /**
   * Exact width of this study pane laid out with a single thumbnail column: the series pane's
   * preferred width plus the measured sub-panel padding and study-pane border.
   *
   * @return the one-column width in device pixels, or 0 if the study has no series pane yet
   */
  public int getSingleColumnWidth() {
    if (subPanel.getComponentCount() == 0) {
      return 0;
    }
    int itemWidth = subPanel.getComponent(0).getPreferredSize().width;
    Insets sub = subPanel.getInsets();
    Insets study = getInsets();
    return itemWidth + sub.left + sub.right + study.left + study.right;
  }

  @Override
  public void remove(int index) {
    if (isValidIndex(index)) {
      subPanel.remove(index);
      refreshLayoutAsync();
    }
  }

  @Override
  public void remove(Component comp) {
    if (comp != null && subPanel.isAncestorOf(comp)) {
      subPanel.remove(comp);
      refreshLayoutAsync();
    }
  }

  @Override
  public void removeAll() {
    subPanel.removeAll();
  }

  /**
   * Refreshes the sub-panel layout: sets the number of thumbnail columns that fit in the width of
   * the enclosing scroll viewport, and invalidates the sub-panel when that number changes.
   */
  public void refreshLayout() {
    if (subPanel.getLayout() instanceof MigLayout migLayout) {
      int width = subPanel.getWidth();
      Container parent = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
      if (parent instanceof JScrollPane scrollPane) {
        width = scrollPane.getViewport().getWidth();
        Insets insets = getInsets();
        width -= (insets.left + insets.right);
      }

      // Exclude the sub-panel padding (and a small separation) so the last column and the study
      // border never extend under the vertical scrollbar
      Insets subInsets = subPanel.getInsets();
      width -= subInsets.left + subInsets.right + GuiUtils.getScaleLength(SEPARATION);

      if (width > 0 && subPanel.getComponentCount() > 0) {
        int itemWidth = subPanel.getComponent(0).getPreferredSize().width;
        int count = Math.max(1, (width + GAP) / (itemWidth + GAP));
        if (count != columns) {
          columns = count;
          migLayout.setLayoutConstraints(
              "insets 0, gap " + GAP + " " + GAP + ", flowx, wrap " + count);
          // Revalidating only this pane leaves the sub-panel valid, so it keeps its cached
          // preferred size and more columns never show when the explorer gets wider
          subPanel.revalidate();
        }
      }
    }
  }

  public void refreshLayoutAsync() {
    SwingUtilities.invokeLater(
        () -> {
          refreshLayout();
          revalidate();
          Optional.ofNullable(getParent()).ifPresent(Component::repaint);
        });
  }

  /**
   * Checks if a specific series is currently visible in this study pane.
   *
   * @param series the series to check for visibility
   * @return true if the series is visible, false otherwise
   */
  public boolean isSeriesVisible(MediaSeriesGroup series) {
    if (series == null) {
      return false;
    }
    return getSeriesPaneStream().anyMatch(seriesPane -> seriesPane.isSeries(series));
  }

  /**
   * Gets a list of all series panes contained in this study pane.
   *
   * @return a list of SeriesPane components
   */
  public List<SeriesPane> getSeriesPaneList() {
    return getSeriesPaneStream().toList();
  }

  /**
   * Shows all series for this study using the provided pane manager.
   *
   * @param paneManager the pane manager to use for retrieving series data
   * @throws IllegalArgumentException if paneManager is null
   */
  public void showAllSeries(DicomPaneManager paneManager) {
    Objects.requireNonNull(paneManager);
    removeAll();
    List<SeriesPane> seriesList = paneManager.getSeriesList(dicomStudy);
    if (seriesList.isEmpty()) {
      revalidate();
      return;
    }

    SeriesFilter filter = paneManager.getSeriesFilter();
    int thumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();
    int index = 0;
    for (SeriesPane series : seriesList) {
      if (series != null && filter.test(series.getDicomSeries(), dicomStudy)) {
        addPane(series, index++, thumbnailSize);
      }
    }
    revalidate();
  }

  /**
   * Adds a series pane at the specified index with the given thumbnail size.
   *
   * @param seriesPane the series pane to add
   * @param index the index at which to add the pane
   * @param thumbnailSize the size for thumbnails
   */
  public void addPane(SeriesPane seriesPane, int index, int thumbnailSize) {
    if (seriesPane == null) {
      return;
    }
    seriesPane.updateSize(thumbnailSize);
    if (index >= 0 && index <= subPanel.getComponentCount()) {
      subPanel.add(seriesPane, index);
    } else {
      subPanel.add(seriesPane);
    }
    updateText();
  }

  /**
   * Adds a series pane with the current thumbnail size.
   *
   * @param seriesPane the series pane to add
   */
  public void addPane(SeriesPane seriesPane) {
    int thumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();
    addPane(seriesPane, subPanel.getComponentCount(), thumbnailSize);
  }

  /**
   * Gets the DICOM study associated with this pane.
   *
   * @return the DICOM study
   */
  public MediaSeriesGroup getDicomStudy() {
    return dicomStudy;
  }

  /** Updates the title text of this study pane. */
  public void updateText() {
    if (titleBorder != null) {
      titleBorder.setTitle(hint == null ? dicomStudy.toString() : dicomStudy + "  \u2022  " + hint);
      repaint();
    }
  }

  /**
   * What the hanging protocols say of the study, shown after its title: the protocol that would
   * hang it and the kept session offered; null for nothing.
   */
  public void setHint(String hint) {
    if (!Objects.equals(this.hint, hint)) {
      this.hint = hint;
      updateText();
    }
  }

  public String getHint() {
    return hint;
  }

  /** Re-resolves this study's title and its series captions after session masking changed. */
  public void refreshIdentityLabels() {
    updateText();
    getSeriesPaneStream().forEach(SeriesPane::updateText);
  }

  /**
   * Checks if this study pane represents the specified DICOM study.
   *
   * @param study the study to check against
   * @return true if this pane represents the specified study, false otherwise
   */
  public boolean isStudy(MediaSeriesGroup study) {
    return Objects.equals(dicomStudy, study);
  }

  /**
   * Gets the number of series panes currently displayed.
   *
   * @return the number of series panes
   */
  public int getSeriesCount() {
    return (int) getSeriesPaneStream().count();
  }

  /**
   * Checks if the study pane has any series panes.
   *
   * @return true if there are series panes, false otherwise
   */
  public boolean hasSeriesPanes() {
    return getSeriesCount() > 0;
  }

  /**
   * Updates the thumbnail size for all series panes.
   *
   * @param thumbnailSize the new thumbnail size
   */
  public void updateThumbnailSize(int thumbnailSize) {
    if (thumbnailSize <= 0) {
      return;
    }

    getSeriesPaneStream().forEach(seriesPane -> seriesPane.updateSize(thumbnailSize));
    refreshLayoutAsync();
  }

  private Stream<SeriesPane> getSeriesPaneStream() {
    return Stream.of(subPanel.getComponents())
        .filter(SeriesPane.class::isInstance)
        .map(SeriesPane.class::cast);
  }

  private boolean isValidIndex(int index) {
    return index >= 0 && index < subPanel.getComponentCount();
  }

  private class ComponentResizeHandler extends ComponentAdapter {
    @Override
    public void componentResized(ComponentEvent e) {
      refreshLayoutAsync();
    }
  }
}
