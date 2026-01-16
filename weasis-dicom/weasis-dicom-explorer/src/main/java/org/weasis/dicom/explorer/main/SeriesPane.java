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

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.font.FontRenderContext;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class SeriesPane extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesPane.class);

  private static final String LAYOUT_CONSTRAINTS = "wrap 1, insets 0";
  private static final String COLUMN_CONSTRAINTS = "[center]";

  private final DicomSeries dicomSeries;
  private final JLabel label;
  private final DicomModel model;
  private SeriesThumbnail thumbnail;
  private int currentThumbnailSize;

  public SeriesPane(DicomSeries dicomSeries, DicomModel model) {
    this.dicomSeries = Objects.requireNonNull(dicomSeries);
    this.model = Objects.requireNonNull(model);
    this.label = createDescriptionLabel();

    initializeLayout();
    initializeComponents();
  }

  private void initializeLayout() {
    setLayout(new MigLayout(LAYOUT_CONSTRAINTS, COLUMN_CONSTRAINTS));
    setBackground(FlatUIUtils.getUIColor(SeriesSelectionModel.BACKGROUND, Color.LIGHT_GRAY));
    setFocusable(false);
  }

  private void initializeComponents() {
    currentThumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();

    thumbnail = getOrCreateThumbnail();
    if (thumbnail != null) {
      add(thumbnail);
    }
    updateSize(currentThumbnailSize);
    add(label);
  }

  public void updateThumbnail() {
    SeriesThumbnail newThumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (newThumb != this.thumbnail) {
      this.thumbnail = newThumb;
      removeAll();
      if (this.thumbnail != null) {
        add(this.thumbnail);
      }
      add(label);
      updateSize(currentThumbnailSize);
      revalidate();
      repaint();
    }
  }

  private SeriesThumbnail getOrCreateThumbnail() {
    SeriesThumbnail thumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (thumb == null) {
      thumb = createThumbnail(dicomSeries, model, currentThumbnailSize);
      if (thumb != null) {
        dicomSeries.setTag(TagW.Thumbnail, thumb);
      }
    }
    return thumb;
  }

  private JLabel createDescriptionLabel() {
    String description = getSeriesDescription();
    JLabel descLabel = new JLabel(description, SwingConstants.CENTER);
    descLabel.setFont(FontItem.MINI.getFont());
    descLabel.setFocusable(false);
    return descLabel;
  }

  private String getSeriesDescription() {
    String desc = TagD.getTagValue(dicomSeries, Tag.SeriesDescription, String.class);
    return desc == null ? StringUtil.EMPTY_STRING : desc;
  }

  /**
   * Updates the thumbnail size and label size based on the provided thumbnail size.
   *
   * @param thumbnailSize the new size for the thumbnail
   */
  public void updateSize(int thumbnailSize) {
    if (thumbnailSize <= 0) {
      LOGGER.warn("Invalid thumbnail size: {}", thumbnailSize);
      return;
    }

    // Only update if size has changed
    if (currentThumbnailSize == thumbnailSize
        && label.getMaximumSize() != null
        && label.getMaximumSize().width == thumbnailSize) {
      return;
    }

    currentThumbnailSize = thumbnailSize;
    updateThumbnailSize(thumbnailSize);
    updateLabelSize(thumbnailSize);
    SwingUtilities.invokeLater(
        () -> {
          revalidate();
          repaint();
        });
  }

  private void updateThumbnailSize(int thumbnailSize) {
    SeriesThumbnail thumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (thumb != null) {
      thumb.setThumbnailSize(thumbnailSize);
    }
  }

  private void updateLabelSize(int thumbnailSize) {
    try {
      FontRenderContext frc = new FontRenderContext(null, false, false);
      int width = GuiUtils.getScaleLength(thumbnailSize);
      int height = (int) (label.getFont().getStringBounds("0", frc).getHeight() + 1.0f);

      Dimension dimension = new Dimension(width, height);
      label.setPreferredSize(dimension);
      label.setMaximumSize(dimension);
    } catch (Exception e) {
      LOGGER.warn("Error updating label size of thumbnail: {}", thumbnailSize, e);
    }
  }

  /**
   * Updates the text of the label to reflect the current series description. This method is called
   * to refresh the label when the series description changes.
   */
  public void updateText() {
    String description = getSeriesDescription();
    SwingUtilities.invokeLater(() -> label.setText(description));
  }

  /**
   * Checks if this SeriesPane is associated with the given MediaSeriesGroup.
   *
   * @param sequence the MediaSeriesGroup to check
   * @return true if this SeriesPane is associated with the given sequence, false otherwise
   */
  public boolean isSeries(MediaSeriesGroup sequence) {
    return this.dicomSeries.equals(sequence);
  }

  public DicomSeries getDicomSeries() {
    return dicomSeries;
  }

  /**
   * Gets the label used for displaying the series description.
   *
   * @return the JLabel containing the series description
   */
  public JLabel getLabel() {
    return label;
  }

  public DicomModel getModel() {
    return model;
  }

  public int getCurrentThumbnailSize() {
    return currentThumbnailSize;
  }

  /**
   * Creates a thumbnail for the given DicomSeries using the specified DicomModel and thumbnail
   * size. This method is designed to be called from the GUI thread to ensure thread safety.
   *
   * @param series the DicomSeries for which to create the thumbnail
   * @param dicomModel the DicomModel used for creating the thumbnail
   * @param thumbnailSize the desired size of the thumbnail
   * @return a SeriesThumbnail object or null if creation fails
   */
  public static SeriesThumbnail createThumbnail(
      DicomSeries series, DicomModel dicomModel, int thumbnailSize) {

    if (series == null || dicomModel == null || thumbnailSize <= 0) {
      LOGGER.warn(
          "Invalid parameters for thumbnail creation: series={}, model={}, size={}",
          series,
          dicomModel,
          thumbnailSize);
      return null;
    }

    Callable<SeriesThumbnail> callable =
        () -> createThumbnailInternal(series, dicomModel, thumbnailSize);
    FutureTask<SeriesThumbnail> future = new FutureTask<>(callable);

    try {
      GuiExecutor.invokeAndWait(future);
      return future.get();
    } catch (InterruptedException e) {
      LOGGER.warn("Thumbnail creation interrupted for series: {}", series);
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException e) {
      LOGGER.error("Error creating thumbnail for series: {}", series, e);
      return null;
    }
  }

  private static SeriesThumbnail createThumbnailInternal(
      DicomSeries series, DicomModel dicomModel, int thumbnailSize) {
    Function<String, Set<ResourceUtil.ResourceIconPath>> drawIcons =
        HiddenSeriesManager::getRelatedIcons;
    SeriesThumbnail thumb = new SeriesThumbnail(series, thumbnailSize, drawIcons);

    // Set progress bar if series is being loaded
    if (series.getSeriesLoader() instanceof LoadSeries loader) {
      thumb.setProgressBar(loader.isDone() ? null : loader.getProgressBar());
    }
    // Register listeners and adapters
    thumb.registerListeners();
    ThumbnailMouseAndKeyAdapter thumbAdapter =
        new ThumbnailMouseAndKeyAdapter(series, dicomModel, null);
    thumb.addMouseListener(thumbAdapter);
    thumb.addKeyListener(thumbAdapter);
    return thumb;
  }

  /** Cleanup method to properly dispose of resources when the pane is no longer needed. */
  public void dispose() {
    SeriesThumbnail thumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (thumb != null) {
      thumb.removeMouseAndKeyListener();
    }
  }
}
