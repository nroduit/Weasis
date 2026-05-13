/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
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
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.*;
import org.weasis.core.api.media.data.*;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;

/**
 * A selection model for DICOM series that extends ArrayList to provide specialized selection
 * behavior with visual feedback and keyboard navigation.
 *
 * <p>This model manages the selection state of DICOM series, handles visual feedback through color
 * changes, and provides navigation capabilities.
 */
public class SeriesSelectionModel extends ArrayList<DicomSeries> {

  // UI Color Constants
  public static final String FOREGROUND = "List.foreground";
  public static final String BACKGROUND = "List.background";
  public static final String SELECTION_FOREGROUND = "List.selectionForeground";
  public static final String SELECTION_BACKGROUND = "List.selectionBackground";

  // Default Colors (fallback)
  private static final Color DEFAULT_FOREGROUND = Color.DARK_GRAY;
  private static final Color DEFAULT_BACKGROUND = Color.LIGHT_GRAY;
  private static final Color DEFAULT_SELECTION_FOREGROUND = Color.LIGHT_GRAY;
  private static final Color DEFAULT_SELECTION_BACKGROUND = Color.DARK_GRAY;

  private final DicomExplorer explorer;
  private final List<SelectionChangeListener> listeners = new CopyOnWriteArrayList<>();

  private DicomSeries anchorSelection;
  private DicomSeries leadSelection;
  private volatile boolean openingSeries = false;
  private final Set<DicomSeries> pendingUpdates = new LinkedHashSet<>();

  /** Functional interface for listening to selection changes. */
  @FunctionalInterface
  public interface SelectionChangeListener {
    void selectionChanged(SeriesSelectionModel model);
  }

  /**
   * Creates a new SeriesSelectionModel for the specified explorer.
   *
   * @param explorer the DICOM explorer instance, must not be null
   * @throws IllegalArgumentException if explorer is null
   */
  public SeriesSelectionModel(DicomExplorer explorer) {
    this.explorer = Objects.requireNonNull(explorer, "Explorer cannot be null");
  }

  // ========== Collection Override Methods ==========
  @Override
  public void add(int index, DicomSeries element) {
    if (element != null && !contains(element)) {
      super.add(index, element);
      updateSeriesVisualState(element, true);
      notifySelectionChanged();
    }
  }

  @Override
  public boolean add(DicomSeries series) {
    if (series != null && !contains(series)) {
      boolean added = super.add(series);
      if (added) {
        updateSeriesVisualState(series, true);
        notifySelectionChanged();
      }
      return added;
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends DicomSeries> collection) {
    if (collection == null || collection.isEmpty()) {
      return false;
    }

    boolean modified = false;
    for (DicomSeries series : collection) {
      if (series != null && !contains(series)) {
        super.add(series);
        updateSeriesVisualState(series, true);
        modified = true;
      }
    }

    if (modified) {
      notifySelectionChanged();
    }
    return modified;
  }

  @Override
  public boolean addAll(int index, Collection<? extends DicomSeries> collection) {
    if (collection == null || collection.isEmpty()) {
      return false;
    }
    List<DicomSeries> toAdd =
        collection.stream()
            .filter(Objects::nonNull)
            .filter(series -> !contains(series))
            .collect(Collectors.toList());
    if (toAdd.isEmpty()) {
      return false;
    }

    boolean modified = super.addAll(index, toAdd);
    if (modified) {
      toAdd.forEach(series -> updateSeriesVisualState(series, true));
      notifySelectionChanged();
    }
    return modified;
  }

  @Override
  public void clear() {
    if (isEmpty()) {
      return;
    }
    // Update visual state for all series
    forEach(series -> updateSeriesVisualState(series, false));

    // Clear selection anchors
    anchorSelection = null;
    leadSelection = null;
    super.clear();
    notifySelectionChanged();
  }

  @Override
  public DicomSeries remove(int index) {
    if (index < 0 || index >= size()) {
      return null;
    }
    DicomSeries removed = super.remove(index);
    if (removed != null) {
      updateSeriesVisualState(removed, false);
      updateSelectionAnchors(removed);
      notifySelectionChanged();
    }
    return removed;
  }

  @Override
  public boolean remove(Object obj) {
    if (!(obj instanceof DicomSeries series)) {
      return false;
    }
    boolean removed = super.remove(series);
    if (removed) {
      updateSeriesVisualState(series, false);
      updateSelectionAnchors(series);
      notifySelectionChanged();
    }
    return removed;
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    if (fromIndex >= toIndex || fromIndex < 0 || toIndex > size()) {
      return;
    }
    // Update visual state for removed series
    for (int i = fromIndex; i < toIndex; i++) {
      DicomSeries series = get(i);
      updateSeriesVisualState(series, false);
      updateSelectionAnchors(series);
    }
    super.removeRange(fromIndex, toIndex);
    notifySelectionChanged();
  }

  @Override
  public DicomSeries set(int index, DicomSeries element) {
    if (index < 0 || index >= size() || element == null) {
      return null;
    }

    DicomSeries old = super.set(index, element);
    if (old != null) {
      updateSeriesVisualState(old, false);
      updateSelectionAnchors(old);
    }
    updateSeriesVisualState(element, true);
    notifySelectionChanged();
    return old;
  }

  // ========== Selection Management Methods ==========

  /**
   * Gets the anchor selection series.
   *
   * @return the anchor selection, or null if none set
   */
  public DicomSeries getAnchorSelection() {
    return anchorSelection;
  }

  /**
   * Gets the lead selection series.
   *
   * @return the lead selection, or null if none set
   */
  public DicomSeries getLeadSelection() {
    return leadSelection;
  }

  /**
   * Checks if the model is currently in the process of opening series.
   *
   * @return true if opening series, false otherwise
   */
  public boolean isOpeningSeries() {
    return openingSeries;
  }

  /**
   * Sets the opening series state.
   *
   * @param openingSeries true if opening series, false otherwise
   */
  public void setOpeningSeries(boolean openingSeries) {
    this.openingSeries = openingSeries;
  }

  /** Selects all series in the current patient. */
  public void selectAll() {
    DicomSeries first = getFirstElement();
    DicomSeries last = getLastElement();
    if (first != null && last != null) {
      setSelectionInterval(first, last);
    }
  }

  /** Selects the first series in the current patient. */
  public void selectFirst() {
    DicomSeries first = getFirstElement();
    if (first != null) {
      setSelectionInterval(first, first);
    }
  }

  /** Selects the last series in the current patient. */
  public void selectLast() {
    DicomSeries last = getLastElement();
    if (last != null) {
      setSelectionInterval(last, last);
    }
  }

  /**
   * Selects a specific series, clearing previous selection.
   *
   * @param series the series to select
   */
  public void selectSeries(DicomSeries series) {
    if (series != null) {
      setSelectionInterval(series, series);
    }
  }

  /** Selects the next series relative to the current lead selection. */
  public void selectNext() {
    DicomSeries current = getLeadSelection();
    DicomSeries next = getNextElement(current);
    if (next != null) {
      setSelectionInterval(next, next);
    }
  }

  /** Selects the previous series relative to the current lead selection. */
  public void selectPrevious() {
    DicomSeries current = getLeadSelection();
    DicomSeries previous = getPreviousElement(current);
    if (previous != null) {
      setSelectionInterval(previous, previous);
    }
  }

  /**
   * Sets the selection interval between two series.
   *
   * @param anchor the anchor series
   * @param lead the lead series
   */
  void setSelectionInterval(DicomSeries anchor, DicomSeries lead) {
    if (anchor == null || lead == null) {
      return;
    }

    clear();
    addSelectionInterval(anchor, lead);
  }

  /**
   * Removes the selection interval between two series.
   *
   * @param anchor the anchor series
   * @param lead the lead series
   */
  void removeSelectionInterval(DicomSeries anchor, DicomSeries lead) {
    if (anchor == null || lead == null) {
      return;
    }

    List<DicomSeries> toRemove = findSeriesInInterval(anchor, lead);
    toRemove.forEach(this::remove);

    requestFocusForSeries(lead);
    updateSelectionAnchors(anchor, lead);
  }

  /**
   * Adds a selection interval between two series.
   *
   * @param anchor the anchor series
   * @param lead the lead series
   */
  void addSelectionInterval(DicomSeries anchor, DicomSeries lead) {
    if (anchor == null || lead == null) {
      return;
    }
    if (anchor.equals(lead)) {
      add(anchor);
    } else {
      List<DicomSeries> intervalSeries = findSeriesInInterval(anchor, lead);
      addAll(intervalSeries);
    }

    requestFocusForSeries(lead);
    updateSelectionAnchors(anchor, lead);
  }

  // ========== Navigation Methods ==========

  /**
   * Gets the first series in the current patient.
   *
   * @return the first series, or null if none available
   */
  public DicomSeries getFirstElement() {
    return getPatientSeries().findFirst().orElse(null);
  }

  /**
   * Gets the last series in the current patient.
   *
   * @return the last series, or null if none available
   */
  public DicomSeries getLastElement() {
    return getPatientSeries().reduce((_, second) -> second).orElse(null);
  }

  /**
   * Gets the previous series relative to the given element.
   *
   * @param element the reference element
   * @return the previous series, or the last element if at the beginning
   */
  public DicomSeries getPreviousElement(DicomSeries element) {
    if (element == null) {
      return null;
    }

    List<DicomSeries> allSeries = getPatientSeries().toList();
    if (allSeries.isEmpty()) {
      return null;
    }

    int index = allSeries.indexOf(element);
    if (index <= 0) {
      return allSeries.getLast();
    }
    return allSeries.get(index - 1);
  }

  /**
   * Gets the next series relative to the given element.
   *
   * @param element the reference element
   * @return the next series, or the first element if at the end
   */
  public DicomSeries getNextElement(DicomSeries element) {
    if (element == null) {
      return null;
    }

    List<DicomSeries> allSeries = getPatientSeries().toList();
    if (allSeries.isEmpty()) {
      return null;
    }

    int index = allSeries.indexOf(element);
    if (index < 0 || index >= allSeries.size() - 1) {
      return allSeries.getFirst();
    }
    return allSeries.get(index + 1);
  }

  // ========== Selection Adjustment ==========

  /**
   * Adjusts the selection based on input event and target series.
   *
   * @param event the input event (mouse or keyboard)
   * @param series the target series
   */
  public void adjustSelection(InputEvent event, DicomSeries series) {
    if (event == null || series == null) {
      return;
    }

    boolean isControlDown = isControlModifierDown(event);
    boolean isShiftDown = event.isShiftDown();
    boolean isAnchorSelected = anchorSelection != null && contains(anchorSelection);

    if (isControlDown) {
      handleControlModifiedSelection(event, series, isShiftDown, isAnchorSelected);
    } else if (isShiftDown) {
      handleShiftModifiedSelection(series);
    } else {
      handleSimpleSelection(series);
    }
  }

  // ========== Listener Management ==========

  /**
   * Adds a selection change listener.
   *
   * @param listener the listener to add
   */
  public void addSelectionChangeListener(SelectionChangeListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /**
   * Removes a selection change listener.
   *
   * @param listener the listener to remove
   */
  public void removeSelectionChangeListener(SelectionChangeListener listener) {
    listeners.remove(listener);
  }

  // ========== Private Helper Methods ==========

  /** Updates the visual state of a series (background/foreground colors). */
  private void updateSeriesVisualState(DicomSeries series, boolean selected) {
    if (series == null) {
      return;
    }

    SwingUtilities.invokeLater(
        () -> {
          Optional<Thumbnail> thumbnail = getThumbnailForSeries(series);
          thumbnail.ifPresent(thumb -> updateThumbnailVisualState(thumb, selected));
        });
  }

  /** Updates the visual state of a thumbnail. */
  private void updateThumbnailVisualState(Thumbnail thumbnail, boolean selected) {
    Container parent = thumbnail.getParent();
    if (!(parent instanceof JPanel)) {
      return;
    }

    Color background =
        selected
            ? FlatUIUtils.getUIColor(SELECTION_BACKGROUND, DEFAULT_SELECTION_BACKGROUND)
            : FlatUIUtils.getUIColor(BACKGROUND, DEFAULT_BACKGROUND);

    Color foreground =
        selected
            ? FlatUIUtils.getUIColor(SELECTION_FOREGROUND, DEFAULT_SELECTION_FOREGROUND)
            : FlatUIUtils.getUIColor(FOREGROUND, DEFAULT_FOREGROUND);
    parent.setBackground(background);
    if (parent instanceof SeriesPane pane) {
      pane.getLabel().setForeground(foreground);
    }
  }

  /** Gets the thumbnail for a series. */
  private Optional<Thumbnail> getThumbnailForSeries(DicomSeries series) {
    if (series == null) {
      return Optional.empty();
    }

    Object thumbnailObj = series.getTagValue(TagW.Thumbnail);
    return thumbnailObj instanceof Thumbnail thumb ? Optional.of(thumb) : Optional.empty();
  }

  /** Requests focus for a series thumbnail. */
  private void requestFocusForSeries(DicomSeries series) {
    if (series == null) {
      return;
    }
    getThumbnailForSeries(series)
        .ifPresent(
            thumbnail -> {
              if (!thumbnail.hasFocus() && thumbnail.isRequestFocusEnabled()) {
                SwingUtilities.invokeLater(thumbnail::requestFocus);
              }
            });
  }

  /** Updates selection anchors after a series is removed. */
  private void updateSelectionAnchors(DicomSeries removed) {
    if (anchorSelection == removed) {
      anchorSelection = null;
    }
    if (leadSelection == removed) {
      leadSelection = null;
    }
  }

  /** Updates selection anchors with new values. */
  private void updateSelectionAnchors(DicomSeries anchor, DicomSeries lead) {
    this.anchorSelection = anchor;
    this.leadSelection = lead;
  }

  /** Finds all series in the interval between two series. */
  private List<DicomSeries> findSeriesInInterval(DicomSeries anchor, DicomSeries lead) {
    List<DicomSeries> result = new ArrayList<>();

    Optional<MediaSeriesGroupNode> patient = Optional.ofNullable(explorer.getSelectedPatient());
    if (patient.isEmpty()) {
      return result;
    }

    boolean inInterval = false;
    boolean foundFirst = false;

    outer:
    for (StudyPane studyPane : explorer.getPaneManager().getStudyList(patient.get())) {
      for (SeriesPane seriesPane : studyPane.getSeriesPaneList()) {
        DicomSeries series = seriesPane.getDicomSeries();

        if (series == anchor || series == lead) {
          result.add(series);
          if (foundFirst) {
            break outer; // Found both endpoints
          }
          foundFirst = true;
          inInterval = true;
        } else if (inInterval) {
          result.add(series);
        }
      }
    }

    return result;
  }

  /** Gets a stream of all series in the current patient. */
  private Stream<DicomSeries> getPatientSeries() {
    Optional<MediaSeriesGroupNode> patient = Optional.ofNullable(explorer.getSelectedPatient());
    if (patient.isEmpty()) {
      return Stream.empty();
    }

    return explorer.getPaneManager().getStudyList(patient.get()).stream()
        .flatMap(studyPane -> studyPane.getSeriesPaneList().stream())
        .map(SeriesPane::getDicomSeries)
        .filter(Objects::nonNull);
  }

  /** Checks if the control modifier key is down. */
  private boolean isControlModifierDown(InputEvent event) {
    return (event.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0;
  }

  private void handleControlModifiedSelection(
      InputEvent event, DicomSeries series, boolean isShiftDown, boolean isAnchorSelected) {
    if (isShiftDown) {
      // Ctrl+Shift: extend selection from anchor to series
      if (isAnchorSelected) {
        addSelectionInterval(anchorSelection, series);
      } else {
        removeSelectionInterval(anchorSelection, series);
      }
    } else {
      // Ctrl only: toggle the series selection without affecting other selections
      toggleSelection(series);
      // Update the anchor and lead to this series
      updateSelectionAnchors(series, series);
    }
  }

  private void handleShiftModifiedSelection(DicomSeries series) {
    setSelectionInterval(anchorSelection, series);
  }

  private void handleSimpleSelection(DicomSeries series) {
    setSelectionInterval(series, series);
  }

  /** Notifies all listeners of selection changes. */
  private void notifySelectionChanged() {
    if (!listeners.isEmpty()) {
      SwingUtilities.invokeLater(
          () ->
              listeners.forEach(
                  listener -> {
                    try {
                      listener.selectionChanged(this);
                    } catch (Exception e) {
                      // Log error but continue with other listeners
                      System.err.println("Error in selection change listener: " + e.getMessage());
                    }
                  }));
    }
  }

  /**
   * Ensures the given series is selected. If selection is empty, adds the series.
   *
   * @param series the series to ensure is selected
   */
  public void ensureSelection(DicomSeries series) {
    if (isEmpty() && series != null) {
      selectSeries(series);
    }
  }

  /**
   * Opens the selected series in the default viewer with error handling.
   *
   * @param dicomModel the DICOM model
   * @return true if series were opened successfully
   */
  public boolean openSelectedSeries(DicomModel dicomModel) {
    if (isEmpty()) {
      return false;
    }

    setOpeningSeries(true);
    try {
      ViewerPluginBuilder.openInDefaultViewer(
          new ArrayList<>(this), dicomModel, ViewerOpenOptions.defaults());
      return true;
    } catch (Exception ex) {
      // Log the error but don't rethrow to avoid breaking the UI
      System.err.println("Failed to open selected series: " + ex.getMessage());
      return false;
    } finally {
      setOpeningSeries(false);
    }
  }

  /** Requests focus on the lead selection thumbnail. */
  public void requestFocusOnSelection() {
    DicomSeries lead = getLeadSelection();
    if (lead != null) {
      requestFocusForSeries(lead);
    }
  }

  /**
   * Checks if the given series is currently selected.
   *
   * @param series the series to check
   * @return true if the series is selected
   */
  public boolean isSelected(DicomSeries series) {
    return contains(series);
  }

  /**
   * Toggles the selection state of a series.
   *
   * @param series the series to toggle
   */
  public void toggleSelection(DicomSeries series) {
    if (series == null) {
      return;
    }

    if (contains(series)) {
      remove(series);
    } else {
      add(series);
    }
  }

  /**
   * Gets the count of selected series.
   *
   * @return the number of selected series
   */
  public int getSelectionCount() {
    return size();
  }

  /**
   * Checks if there's a valid selection to work with.
   *
   * @return true if there are selected series
   */
  public boolean hasSelection() {
    return !isEmpty();
  }
}
