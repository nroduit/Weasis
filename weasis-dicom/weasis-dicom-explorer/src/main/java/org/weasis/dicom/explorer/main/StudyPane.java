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

import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;

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
    add(subPanel, SUB_PANEL_CONSTRAINTS);
    addComponentListener(resizeHandler);
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

  /** Refreshes the layout of the sub-panel to accommodate component changes. */
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
        int count = (width + GAP) / (itemWidth + GAP);
        migLayout.setLayoutConstraints(
            "insets 0, gap " + GAP + " " + GAP + ", flowx, wrap " + Math.max(1, count));
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
      titleBorder.setTitle(dicomStudy.toString());
      repaint();
    }
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
