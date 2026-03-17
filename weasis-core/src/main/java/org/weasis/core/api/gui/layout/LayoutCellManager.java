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

import java.awt.Component;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * Manages the mapping between layout cells and their associated components or ViewCanvas instances.
 * Ensures proper positioning based on {@link MigCell#position()}.
 *
 * <p>This class provides a centralized way to handle component/canvas placement in layouts,
 * guaranteeing that the position in the layout cells matches the actual component position.
 *
 * @param <E> the type of ImageElement handled by ViewCanvas instances
 */
public class LayoutCellManager<E extends ImageElement> implements Iterable<ViewCanvas<E>> {

  /**
   * Represents an entry in a layout cell, which can contain either a ViewCanvas or a generic
   * Component.
   */
  public static class CellEntry<E extends ImageElement> {
    private final MigCell cell;
    private final ViewCanvas<E> viewCanvas;
    private final Component component;

    private CellEntry(MigCell cell, ViewCanvas<E> viewCanvas, Component component) {
      this.cell = Objects.requireNonNull(cell, "cell cannot be null");
      if (viewCanvas == null && component == null) {
        throw new IllegalArgumentException("Either viewCanvas or component must be non-null");
      }
      this.viewCanvas = viewCanvas;
      this.component = component;
    }

    public static <E extends ImageElement> CellEntry<E> forViewCanvas(
        MigCell cell, ViewCanvas<E> viewCanvas) {
      Objects.requireNonNull(viewCanvas, "viewCanvas cannot be null");
      return new CellEntry<>(cell, viewCanvas, viewCanvas.getJComponent());
    }

    public static <E extends ImageElement> CellEntry<E> forComponent(
        MigCell cell, Component component) {
      Objects.requireNonNull(component, "component cannot be null");
      return new CellEntry<>(cell, null, component);
    }

    public MigCell getCell() {
      return cell;
    }

    public Optional<ViewCanvas<E>> getViewCanvas() {
      return Optional.ofNullable(viewCanvas);
    }

    public Component getComponent() {
      return component;
    }

    public boolean isViewCanvas() {
      return viewCanvas != null;
    }

    public int getPosition() {
      return cell.position();
    }
  }

  private final Map<Integer, CellEntry<E>> entriesByPosition = new HashMap<>();
  private MigLayoutModel layoutModel;

  /**
   * Creates a LayoutCellManager and initializes it with the given layout model.
   *
   * @param layoutModel the layout model to use
   */
  public LayoutCellManager(MigLayoutModel layoutModel) {
    setLayoutModel(layoutModel);
  }

  /**
   * Sets the layout model and clears all existing entries.
   *
   * @param layoutModel the new layout model
   */
  public void setLayoutModel(MigLayoutModel layoutModel) {
    this.layoutModel = Objects.requireNonNull(layoutModel.copy(), "layoutModel cannot be null");
    entriesByPosition.clear();
  }

  /** Gets the current layout model. */
  public MigLayoutModel getLayoutModel() {
    return layoutModel;
  }

  /**
   * Adds a ViewCanvas at the specified cell position.
   *
   * @param position the cell position
   * @param viewCanvas the ViewCanvas to add
   * @return the created CellEntry, or null if no cell exists at this position
   */
  public CellEntry<E> addViewCanvas(int position, ViewCanvas<E> viewCanvas) {
    Objects.requireNonNull(viewCanvas, "viewCanvas cannot be null");
    MigCell cell = getCellAtPosition(position);
    if (cell == null) {
      return null;
    }
    CellEntry<E> entry = CellEntry.forViewCanvas(cell, viewCanvas);
    entriesByPosition.put(position, entry);
    return entry;
  }

  /**
   * Adds a Component at the specified cell position.
   *
   * @param position the cell position
   * @param component the Component to add
   * @return the created CellEntry, or null if no cell exists at this position
   */
  public CellEntry<E> addComponent(int position, Component component) {
    Objects.requireNonNull(component, "component cannot be null");
    MigCell cell = getCellAtPosition(position);
    if (cell == null) {
      return null;
    }
    CellEntry<E> entry = CellEntry.forComponent(cell, component);
    entriesByPosition.put(position, entry);
    return entry;
  }

  /**
   * Replaces an existing entry at the specified position.
   *
   * @param position the cell position
   * @param viewCanvas the new ViewCanvas
   * @return the previous entry, or null if none existed
   */
  public CellEntry<E> replaceViewCanvas(int position, ViewCanvas<E> viewCanvas) {
    Objects.requireNonNull(viewCanvas, "viewCanvas cannot be null");
    MigCell cell = getCellAtPosition(position);
    if (cell == null) {
      return null;
    }
    CellEntry<E> newEntry = CellEntry.forViewCanvas(cell, viewCanvas);
    return entriesByPosition.put(position, newEntry);
  }

  /**
   * Replaces an existing entry at the specified position.
   *
   * @param position the cell position
   * @param component the new Component
   * @return the previous entry, or null if none existed
   */
  public CellEntry<E> replaceComponent(int position, Component component) {
    Objects.requireNonNull(component, "component cannot be null");
    MigCell cell = getCellAtPosition(position);
    if (cell == null) {
      return null;
    }
    CellEntry<E> newEntry = CellEntry.forComponent(cell, component);
    return entriesByPosition.put(position, newEntry);
  }

  /**
   * Gets the entry at the specified position.
   *
   * @param position the cell position
   * @return the CellEntry, or null if none exists at this position
   */
  public CellEntry<E> getEntry(int position) {
    return entriesByPosition.get(position);
  }

  /**
   * Gets the component at the specified position.
   *
   * @param position the cell position
   * @return the Component, or null if none exists at this position
   */
  public Component getComponent(int position) {
    CellEntry<E> entry = entriesByPosition.get(position);
    return entry != null ? entry.getComponent() : null;
  }

  /**
   * Gets the ViewCanvas at the specified position.
   *
   * @param position the cell position
   * @return an Optional containing the ViewCanvas, or empty if none exists or it's not a ViewCanvas
   */
  public Optional<ViewCanvas<E>> getViewCanvas(int position) {
    CellEntry<E> entry = entriesByPosition.get(position);
    return entry != null ? entry.getViewCanvas() : Optional.empty();
  }

  /**
   * Removes the entry at the specified position.
   *
   * @param position the cell position
   * @return the removed entry, or null if none existed
   */
  public CellEntry<E> remove(int position) {
    return entriesByPosition.remove(position);
  }

  /**
   * Gets all entries in position order.
   *
   * @return a list of all CellEntry instances, ordered by position
   */
  public List<CellEntry<E>> getAllEntries() {
    return entriesByPosition.values().stream()
        .sorted(Comparator.comparingInt(CellEntry::getPosition))
        .toList();
  }

  /**
   * Gets all ViewCanvas instances in position order.
   *
   * @return a list of all ViewCanvas instances, ordered by position
   */
  public List<ViewCanvas<E>> getAllViewCanvases() {
    return entriesByPosition.values().stream()
        .filter(CellEntry::isViewCanvas)
        .sorted(Comparator.comparingInt(CellEntry::getPosition))
        .map(e -> e.getViewCanvas().orElseThrow())
        .toList();
  }

  /**
   * Gets all Components in position order.
   *
   * @return a list of all Components, ordered by position
   */
  public List<Component> getAllComponents() {
    return entriesByPosition.values().stream()
        .sorted(Comparator.comparingInt(CellEntry::getPosition))
        .map(CellEntry::getComponent)
        .toList();
  }

  /**
   * Gets all non-ViewCanvas Components in position order.
   *
   * @return a list of Components that are not ViewCanvas instances, ordered by position
   */
  public List<Component> getNonViewCanvasComponents() {
    return entriesByPosition.values().stream()
        .filter(e -> !e.isViewCanvas())
        .sorted(Comparator.comparingInt(CellEntry::getPosition))
        .map(CellEntry::getComponent)
        .toList();
  }

  /**
   * Gets the number of ViewCanvas entries.
   *
   * @return the count of ViewCanvas entries
   */
  public int getViewCanvasCount() {
    return (int) entriesByPosition.values().stream().filter(CellEntry::isViewCanvas).count();
  }

  /** Clears all entries but keeps the layout model. */
  public void clear() {
    entriesByPosition.clear();
  }

  /**
   * Gets the size (number of entries).
   *
   * @return the number of entries
   */
  public int size() {
    return entriesByPosition.size();
  }

  /**
   * Checks if there are no entries.
   *
   * @return true if empty, false otherwise
   */
  public boolean isEmpty() {
    return entriesByPosition.isEmpty();
  }

  /**
   * Finds the position of a specific ViewCanvas.
   *
   * @param viewCanvas the ViewCanvas to find
   * @return the position, or -1 if not found
   */
  public int findPositionOfViewCanvas(ViewCanvas<E> viewCanvas) {
    return entriesByPosition.entrySet().stream()
        .filter(e -> e.getValue().getViewCanvas().orElse(null) == viewCanvas)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(-1);
  }

  /**
   * Finds the position of a specific Component.
   *
   * @param component the Component to find
   * @return the position, or -1 if not found
   */
  public int findPositionOfComponent(Component component) {
    return entriesByPosition.entrySet().stream()
        .filter(e -> e.getValue().getComponent() == component)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(-1);
  }

  /**
   * Gets a ViewCanvas by its index in the sorted list of ViewCanvas instances.
   *
   * @param index the index in the ViewCanvas list (0-based)
   * @return the ViewCanvas at the specified index, or null if index is out of bounds
   */
  public ViewCanvas<E> getViewCanvasByIndex(int index) {
    List<ViewCanvas<E>> viewCanvases = getAllViewCanvases();
    if (index >= 0 && index < viewCanvases.size()) {
      return viewCanvases.get(index);
    }
    return null;
  }

  /**
   * Finds the index of a specific ViewCanvas in the sorted list of ViewCanvas instances.
   *
   * @param viewCanvas the ViewCanvas to find
   * @return the index (0-based), or -1 if not found
   */
  public int indexOfViewCanvas(ViewCanvas<E> viewCanvas) {
    List<ViewCanvas<E>> viewCanvases = getAllViewCanvases();
    return viewCanvases.indexOf(viewCanvas);
  }

  /**
   * Gets the cell at the specified position from the layout model.
   *
   * @param position the cell position
   * @return the MigCell, or null if not found
   */
  private MigCell getCellAtPosition(int position) {
    if (layoutModel == null) {
      return null;
    }
    return layoutModel.getCells().stream()
        .filter(c -> c.position() == position)
        .findFirst()
        .orElse(null);
  }

  /**
   * Gets the cell for a given entry.
   *
   * @param entry the cell entry
   * @return the MigCell associated with this entry
   */
  public MigCell getCell(CellEntry<E> entry) {
    return entry.getCell();
  }

  /**
   * Returns an iterator over ViewCanvas elements in position order.
   *
   * @return an iterator over ViewCanvas instances
   */
  @Override
  public Iterator<ViewCanvas<E>> iterator() {
    return getAllViewCanvases().iterator();
  }
}
