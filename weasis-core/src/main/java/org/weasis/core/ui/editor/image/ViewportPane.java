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

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * A panel that acts as a single viewport slot in an {@link ImageViewerPlugin}'s outer grid.
 *
 * <p>A {@code ViewportPane} maps directly to the DICOM concept of a <em>Hanging Protocol
 * Viewport</em>: one visible region on screen that can display either a single image stack (STACK
 * mode) or a grid of simultaneous images (TILED mode).
 *
 * <p><b>Two operating modes:</b>
 *
 * <ul>
 *   <li><b>STACK</b> – contains exactly one {@link ViewCanvas}. The user navigates through the
 *       series by scrolling. This is the default mode and corresponds to the normal Weasis
 *       behaviour.
 *   <li><b>TILED</b> – contains an inner {@code NxM} MigLayout panel hosting {@code N*M} {@link
 *       ViewCanvas} instances, each showing a different image simultaneously (matching the DICOM HP
 *       {@code TILED} image-box layout type). Each inner canvas carries a {@link
 *       ViewCanvas#setTileOffset tile offset} so that scrolling the "master" view distributes
 *       images correctly.
 * </ul>
 *
 * <p><b>Mode separation:</b> STACK and TILED modes are strictly independent. The stack canvas is
 * never reused as a tiled canvas. When switching to TILED the stack canvas's series is transferred
 * to tile 0; when switching back to STACK, tile-0's series is transferred to the stack canvas. All
 * tiled state is encapsulated in a {@link TiledLayout} inner object that exists only while the pane
 * is in TILED mode ({@code null} in STACK mode).
 *
 * <p><b>Relationship with the outer grid:</b> The outer {@link ImageViewerPlugin} grid holds {@code
 * ViewportPane} instances as regular {@code Component}s (registered via {@code
 * cellManager.addComponent()}). The plugin's {@link ImageViewerPlugin#getView2ds()} is overridden
 * to flatten the inner canvases of every pane so that synchronisation, tile-offset management, and
 * all existing event machinery keep working unchanged.
 *
 * @param <E> the type of image element displayed
 */
public class ViewportPane<E extends ImageElement> extends JPanel {

  /** Key under which every canvas of a pane stores a back-reference to its owning pane. */
  private static final String OWNER_KEY = "viewport.owner"; // NON-NLS

  // ── TiledLayout inner class ───────────────────────────────────────────────────

  /**
   * Encapsulates all state belonging exclusively to TILED mode.
   *
   * <p>An instance of this class is created when the pane switches to TILED mode and set to {@code
   * null} when it returns to STACK mode, ensuring that tiled state never leaks into the STACK path.
   */
  private final class TiledLayout {

    /** Canvases in row-major order, each with its {@link ViewCanvas#setTileOffset tile offset}. */
    private final List<ViewCanvas<E>> tiledCanvases;

    /** The MigLayout panel hosting the tiled canvases. */
    private final JPanel innerGrid;

    /** Number of tile columns. */
    private final int cols;

    /** Number of tile rows. */
    private final int rows;

    /**
     * Creates a new tiled layout, instantiating all {@code cols × rows} canvases via the factory.
     *
     * @param cols number of columns (≥ 1)
     * @param rows number of rows (≥ 1)
     * @param canvasFactory called exactly {@code cols * rows} times
     */
    TiledLayout(int cols, int rows, Supplier<ViewCanvas<E>> canvasFactory) {
      this.cols = cols;
      this.rows = rows;
      int total = cols * rows;
      tiledCanvases = new ArrayList<>(total);
      for (int i = 0; i < total; i++) {
        ViewCanvas<E> c = canvasFactory.get();
        c.registerDefaultListeners();
        c.setTileOffset(i);
        c.setActionsInView(OWNER_KEY, ViewportPane.this);
        tiledCanvases.add(c);
      }
      innerGrid = buildGrid();
    }

    /** Returns an unmodifiable view of the tiled canvases. */
    List<ViewCanvas<E>> canvases() {
      return Collections.unmodifiableList(tiledCanvases);
    }

    /** Returns the first (index-0) tiled canvas. */
    ViewCanvas<E> firstCanvas() {
      return tiledCanvases.getFirst();
    }

    /** Returns the pre-built inner grid panel. */
    JPanel grid() {
      return innerGrid;
    }

    int getCols() {
      return cols;
    }

    int getRows() {
      return rows;
    }

    /**
     * Disposes all tiled canvases, clearing their series first.
     *
     * <p>After this call the {@code TiledLayout} instance must not be used.
     */
    void dispose() {
      for (ViewCanvas<E> c : tiledCanvases) {
        c.setActionsInView(OWNER_KEY, null);
        c.setSeries(null, null);
        c.disposeView();
      }
      tiledCanvases.clear();
    }

    /**
     * Builds the inner MigLayout panel.
     *
     * <p>{@code wrap N} in the layout constraints handles column wrapping; no per-cell {@code
     * newline} hint is required.
     */
    private JPanel buildGrid() {
      String layoutConstraints = "wrap " + cols + ", ins 0, gap 0";
      String colConstraints = "[grow,fill]".repeat(cols);
      String rowConstraints = "[grow,fill]".repeat(rows);

      JPanel panel = new JPanel(new MigLayout(layoutConstraints, colConstraints, rowConstraints));
      panel.setOpaque(false);
      for (ViewCanvas<E> c : tiledCanvases) {
        panel.add(c.getJComponent(), "grow");
      }
      return panel;
    }

    /** Re-adds every tile canvas to the inner grid, restoring any reparented component. */
    void restoreGrid() {
      innerGrid.removeAll();
      for (ViewCanvas<E> c : tiledCanvases) {
        innerGrid.add(c.getJComponent(), "grow");
      }
    }
  }

  // ── ViewportPane fields ───────────────────────────────────────────────────────

  /**
   * The single canvas used in STACK mode. This canvas is <em>never</em> placed inside the tiled
   * grid; it is strictly the STACK-mode canvas.
   */
  private final ViewCanvas<E> stackCanvas;

  /**
   * Tiled-mode state; {@code null} when in STACK mode. Created on demand by {@link
   * #switchToTiled(int, int, Supplier)}.
   */
  private TiledLayout tiledLayout;

  /** The currently focused/selected canvas inside this pane. */
  private ViewCanvas<E> selectedCanvas;

  /**
   * The event manager used to register tiled canvases as SYNCH property-change listeners. May be
   * {@code null} when not set (e.g. during construction before the pane is added to a plugin).
   */
  private ImageViewerEventManager<E> eventManager;

  // ── Constructors ─────────────────────────────────────────────────────────────

  /**
   * Creates a {@code ViewportPane} in STACK mode with the given canvas.
   *
   * @param initialCanvas the canvas to use in STACK mode; must not be {@code null}
   */
  public ViewportPane(ViewCanvas<E> initialCanvas) {
    super(new BorderLayout());
    this.stackCanvas = Objects.requireNonNull(initialCanvas, "initialCanvas must not be null");
    this.selectedCanvas = stackCanvas;
    stackCanvas.setActionsInView(OWNER_KEY, this);
    showStackMode();
  }

  // ── Public API ───────────────────────────────────────────────────────────────

  /**
   * Returns {@code true} if this pane currently operates in TILED mode.
   *
   * @return {@code true} for TILED, {@code false} for STACK
   */
  public boolean isTiled() {
    return tiledLayout != null;
  }

  /**
   * Returns the currently selected (focused) canvas inside this pane.
   *
   * <ul>
   *   <li>STACK mode: always the stack canvas.
   *   <li>TILED mode: whichever tiled canvas last received focus (defaults to tile 0).
   * </ul>
   *
   * @return selected canvas, never {@code null}
   */
  public ViewCanvas<E> getSelectedCanvas() {
    return selectedCanvas;
  }

  /**
   * Sets the selected canvas. Must be one of the canvases currently active in this pane (the stack
   * canvas in STACK mode, or one of the tiled canvases in TILED mode).
   *
   * @param canvas the canvas to select
   * @throws IllegalArgumentException if the canvas is not owned by this pane in the current mode
   */
  public void setSelectedCanvas(ViewCanvas<E> canvas) {
    if (!getAllViewCanvases().contains(canvas)) {
      throw new IllegalArgumentException(
          "Canvas not owned by this ViewportPane in its current mode");
    }
    this.selectedCanvas = canvas;
  }

  /**
   * Returns the active {@link ViewCanvas} instances for the current mode.
   *
   * <ul>
   *   <li>STACK mode: single-element unmodifiable list containing the stack canvas.
   *   <li>TILED mode: unmodifiable list of all tiled canvases in row-major order.
   * </ul>
   *
   * @return unmodifiable, ordered list of active view canvases
   */
  public List<ViewCanvas<E>> getAllViewCanvases() {
    if (tiledLayout == null) {
      return Collections.singletonList(stackCanvas);
    }
    return tiledLayout.canvases();
  }

  /** Number of tiled columns; 0 when in STACK mode. */
  public int getTiledCols() {
    return tiledLayout != null ? tiledLayout.getCols() : 0;
  }

  /** Number of tiled rows; 0 when in STACK mode. */
  public int getTiledRows() {
    return tiledLayout != null ? tiledLayout.getRows() : 0;
  }

  /**
   * Sets the event manager for this pane. When the pane is in TILED mode the tiled canvases are
   * immediately registered as SYNCH property-change listeners on the new manager.
   *
   * @param eventManager the event manager; may be {@code null} to detach
   */
  public void setEventManager(ImageViewerEventManager<E> eventManager) {
    // Unregister from old manager if any
    if (this.eventManager != null && tiledLayout != null) {
      unregisterTiledCanvases(this.eventManager);
    }
    this.eventManager = eventManager;
    // Register on new manager if currently tiled
    if (eventManager != null && tiledLayout != null) {
      registerTiledCanvases(eventManager);
    }
  }

  /**
   * Switches this pane to STACK mode, restoring the stack canvas.
   *
   * <p>The series from tile 0 is transferred back to the stack canvas so no content is lost. The
   * {@link TiledLayout} is disposed and set to {@code null}.
   */
  public void switchToStack() {
    if (tiledLayout == null) {
      return;
    }
    // Unregister tiled canvases from synch before disposal
    if (eventManager != null) {
      unregisterTiledCanvases(eventManager);
    }
    // Capture tile-0 series before disposal
    MediaSeries<E> preserved = tiledLayout.firstCanvas().getSeries();
    boolean paneSelected = isAppSelected();
    tiledLayout.dispose();
    tiledLayout = null;
    selectedCanvas = stackCanvas;
    // Restore series on the stack canvas. It was parked while tiled, so it missed every mouse
    // action change the container applied meanwhile: re-apply the current bindings.
    stackCanvas.setSeries(preserved, null);
    if (eventManager != null) {
      stackCanvas.enableMouseAndKeyListener(eventManager.getMouseActions());
    }
    showStackMode();
    if (paneSelected) {
      stackCanvas.setSelected(true);
    }
  }

  /** True when this pane's selected canvas is the application-wide selected view. */
  private boolean isAppSelected() {
    return eventManager != null && eventManager.getSelectedViewPane() == selectedCanvas;
  }

  /**
   * Switches this pane to TILED mode, creating an inner {@code cols × rows} grid.
   *
   * <p>If the pane is already in TILED mode with the same dimensions, this is a no-op. When
   * switching from STACK mode the stack canvas's current series is transferred to tile 0 so no
   * content is lost. The stack canvas itself is kept alive but cleared and hidden. All tiled
   * canvases are independent of the stack canvas.
   *
   * @param cols number of tile columns (≥ 1)
   * @param rows number of tile rows (≥ 1)
   * @param canvasFactory factory called exactly {@code cols * rows} times to create tiled canvases
   */
  public void switchToTiled(int cols, int rows, Supplier<ViewCanvas<E>> canvasFactory) {
    Objects.requireNonNull(canvasFactory, "canvasFactory must not be null");
    cols = Math.max(1, cols);
    rows = Math.max(1, rows);

    if (tiledLayout != null && tiledLayout.getCols() == cols && tiledLayout.getRows() == rows) {
      return; // already in the requested tiled configuration
    }

    // Unregister old tiled canvases before disposal
    if (eventManager != null && tiledLayout != null) {
      unregisterTiledCanvases(eventManager);
    }

    // Capture the series to place on tile 0 before any disposal
    MediaSeries<E> series =
        (tiledLayout == null) ? stackCanvas.getSeries() : tiledLayout.firstCanvas().getSeries();
    boolean paneSelected = isAppSelected();

    if (tiledLayout != null) {
      tiledLayout.dispose();
    }

    // Park the stack canvas while in tiled mode: kept alive but cleared and dropped from the
    // container synchronization, since a hidden canvas must not react to synch events. It is
    // re-registered by the listener rebuild that follows the return to STACK mode.
    stackCanvas.setSeries(null, null);
    if (eventManager != null) {
      eventManager.removePropertyChangeListener(ActionW.SYNCH.cmd(), stackCanvas);
    }

    tiledLayout = new TiledLayout(cols, rows, canvasFactory);
    selectedCanvas = tiledLayout.firstCanvas();

    // Every tile shows the preserved series; each one already carries its own tile offset
    if (series != null) {
      for (ViewCanvas<E> c : tiledLayout.canvases()) {
        c.setSeries(series, null);
      }
    }

    // Register every tile as a SYNCH listener, tile 0 included
    if (eventManager != null) {
      registerTiledCanvases(eventManager);
    }

    showTiledMode();

    if (paneSelected) {
      // Transfer the focus border from the hidden stack canvas to the first tile
      stackCanvas.setSelected(false);
      selectedCanvas.setSelected(true);
    }
  }

  /**
   * Assigns a series to this pane.
   *
   * <ul>
   *   <li>STACK mode: delegates directly to the stack canvas.
   *   <li>TILED mode: sets the same series on every tiled canvas (each already carries the correct
   *       {@link ViewCanvas#setTileOffset tile offset}).
   * </ul>
   *
   * @param series the series to display (may be {@code null} to clear)
   */
  public void setSeries(MediaSeries<E> series) {
    if (tiledLayout == null) {
      stackCanvas.setSeries(series, null);
    } else {
      for (ViewCanvas<E> c : tiledLayout.canvases()) {
        c.setSeries(series, null);
      }
    }
  }

  /**
   * Re-attaches the active mode component and its canvases to this pane. Must be called after a
   * canvas was temporarily reparented outside the pane (e.g. the maximized/fullscreen view).
   */
  public void restoreView() {
    if (tiledLayout != null) {
      tiledLayout.restoreGrid();
      showTiledMode();
    } else {
      showStackMode();
    }
  }

  /**
   * Detaches the canvas carrying this pane's content, leaving the pane empty.
   *
   * <p>Used when a layout change moves the content into a cell hosting a {@link ViewCanvas}
   * directly (e.g. the histogram or DICOM-dump layouts). A TILED pane first returns to STACK mode
   * so tile 0's series lands on the returned canvas and the tiles are disposed. The pane must not
   * be used any further.
   *
   * @return the canvas carrying the content, never {@code null}
   */
  public ViewCanvas<E> extractContentCanvas() {
    switchToStack();
    setEventManager(null);
    stackCanvas.setActionsInView(OWNER_KEY, null);
    removeAll();
    return stackCanvas;
  }

  /**
   * Disposes all resources owned by this pane: the stack canvas and, if present, all tiled
   * canvases.
   *
   * <p>After this call the pane must not be used any further.
   */
  public void dispose() {
    if (tiledLayout != null) {
      tiledLayout.dispose();
      tiledLayout = null;
    }
    stackCanvas.setActionsInView(OWNER_KEY, null);
    stackCanvas.setSeries(null, null);
    stackCanvas.disposeView();
  }

  // ── Private helpers ───────────────────────────────────────────────────────────

  private void showStackMode() {
    showMode(stackCanvas.getJComponent());
  }

  private void showTiledMode() {
    showMode(tiledLayout.grid());
  }

  private void showMode(JComponent modePanel) {
    removeAll();
    add(modePanel, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  /**
   * Enables mouse/key interaction on every tiled canvas and wires the TILE synchronization.
   * Canvases created by the factory only had their default (focus/tooltip) listeners registered,
   * not the FocusHandler that drives view selection.
   */
  private void registerTiledCanvases(ImageViewerEventManager<E> mgr) {
    MouseActions mouseActions = mgr.getMouseActions();
    for (ViewCanvas<E> c : tiledLayout.canvases()) {
      c.enableMouseAndKeyListener(mouseActions);
    }
    updateSynchRegistration();
  }

  /**
   * (Re-)registers every tiled canvas as a SYNCH property-change listener with a {@link
   * SynchData.Mode#TILE} {@link ViewSynchData}. All tiles carry TILE data (including tile 0, which
   * issues the events) so that {@code DefaultView2d.isTileSyncMatch} links them.
   *
   * <p>Idempotent: it is invoked after each global listener rebuild ({@link
   * ImageViewerEventManager#updateAllListeners}) to restore the pane's TILE wiring, which the
   * container-level pass would otherwise discard. Does nothing in STACK mode or without an event
   * manager.
   */
  public void updateSynchRegistration() {
    if (eventManager == null || tiledLayout == null) {
      return;
    }
    for (ViewCanvas<E> c : tiledLayout.canvases()) {
      eventManager.removePropertyChangeListener(ActionW.SYNCH.cmd(), c);
      c.setActionsInView(ActionW.SYNCH_LINK.cmd(), getOrCreateTileSynchData(c));
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), c);
      c.updateSynchState();
    }
  }

  /**
   * Keeps the canvas's existing TILE data (preserving user sync options) or creates a new one from
   * the {@link SynchView#DEFAULT_TILE} template.
   */
  private ViewSynchData getOrCreateTileSynchData(ViewCanvas<E> canvas) {
    if (canvas.getActionValue(ActionW.SYNCH_LINK.cmd()) instanceof ViewSynchData data
        && data.getMode() == SynchData.Mode.TILE) {
      data.setAutoSyncState(SynchData.SyncState.ON);
      return data;
    }
    Map<String, Boolean> actions =
        new HashMap<>(SynchView.DEFAULT_TILE.getSynchData().getActions());
    return new ViewSynchData(SynchData.Mode.TILE, actions, true);
  }

  /**
   * Removes all tiled canvases from the SYNCH listener list of the given event manager and clears
   * their {@link ActionW#SYNCH_LINK} action.
   */
  private void unregisterTiledCanvases(ImageViewerEventManager<E> mgr) {
    for (ViewCanvas<E> c : tiledLayout.canvases()) {
      mgr.removePropertyChangeListener(ActionW.SYNCH.cmd(), c);
      c.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
    }
  }

  /**
   * Returns {@code true} when both canvases are owned by the same {@code ViewportPane}. Used to
   * scope TILE synchronization to a single pane.
   */
  public static boolean isSameViewportPane(ViewCanvas<?> canvas1, ViewCanvas<?> canvas2) {
    if (canvas1 == null || canvas2 == null) {
      return false;
    }
    ViewportPane<?> pane = ownerOf(canvas1);
    return pane != null && pane == ownerOf(canvas2);
  }

  /**
   * Returns the pane currently owning the canvas, or {@code null}.
   *
   * <p>The back-reference stored on the canvas is only a shortcut to the pane; ownership is
   * confirmed against the pane's active canvases. This keeps the answer correct while a canvas is
   * temporarily reparented outside the pane (maximized/fullscreen view) — a Swing ancestry lookup
   * would report no owner there and silently break the pane's synchronization — and rejects a
   * canvas that merely copied the action map of a real tile ({@code copyActionWState}).
   */
  private static ViewportPane<?> ownerOf(ViewCanvas<?> canvas) {
    return canvas.getActionValue(OWNER_KEY) instanceof ViewportPane<?> pane
            && pane.getAllViewCanvases().contains(canvas)
        ? pane
        : null;
  }
}
