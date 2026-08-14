/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.layout.LayoutCellManager;
import org.weasis.core.api.gui.layout.MergedCellsBuilder;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.util.MouseEventDouble;

public abstract class ImageViewerPlugin<E extends ImageElement> extends ViewerPlugin<E> {

  public static final String F_VIEWS = Messages.getString("ImageViewerPlugin.2");

  // A model must have at least one view that inherited of DefaultView2d
  public static final Class<?> VIEWPORT_CLASS = ViewportPane.class;
  public static final MigLayoutModel VIEWS_1x1 =
      new MigLayoutModel(
          "1x1", // NON-NLS
          String.format(Messages.getString("ImageViewerPlugin.1"), "1x1"), // NON-NLS
          1,
          1,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_2x1 =
      new MigLayoutModel(
          "2x1", // NON-NLS
          String.format(F_VIEWS, "2x1"), // NON-NLS
          2,
          1,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_1x2 =
      new MigLayoutModel(
          "1x2", // NON-NLS
          String.format(F_VIEWS, "1x2"), // NON-NLS
          1,
          2,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_1x3 =
      new MigLayoutModel(
          "1x3", // NON-NLS
          String.format(F_VIEWS, "1x3"), // NON-NLS
          1,
          3,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_1x4 =
      new MigLayoutModel(
          "1x4", // NON-NLS
          String.format(F_VIEWS, "1x4"), // NON-NLS
          1,
          4,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_2x2_f2 =
      new MergedCellsBuilder(
              2, 2, "layout_c2x1", "3 views (right merged)", VIEWPORT_CLASS.getName())
          .addCell(0, 0)
          .addMergedCell(0, 1, 1, 2)
          .addCell(1, 0)
          .build();
  public static final MigLayoutModel VIEWS_2_f1x2 =
      new MergedCellsBuilder(2, 2, "layout_r1x2", "3 views (top merged)", VIEWPORT_CLASS.getName())
          .addMergedCell(0, 0, 2, 1)
          .addCell(1, 0)
          .addCell(1, 1)
          .build();
  public static final MigLayoutModel VIEWS_2x2 =
      new MigLayoutModel(
          "2x2", // NON-NLS
          String.format(F_VIEWS, "2x2"), // NON-NLS
          2,
          2,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_2x3 =
      new MigLayoutModel(
          "2x3", // NON-NLS
          String.format(F_VIEWS, "2x3"), // NON-NLS
          2,
          3,
          VIEWPORT_CLASS.getName());
  public static final MigLayoutModel VIEWS_2x4 =
      new MigLayoutModel(
          "2x4", // NON-NLS
          String.format(F_VIEWS, "2x4"), // NON-NLS
          2,
          4,
          VIEWPORT_CLASS.getName());

  public record LayoutModel(String uid, MigLayoutModel model) {}

  /** The currently selected/focused {@link ViewportPane}. The default is {@code null}. */
  protected ViewportPane<E> selectedPane = null;

  /**
   * The selected canvas for layouts whose cells host a {@link ViewCanvas} directly, without a
   * {@link ViewportPane} (e.g. MPR and 3D layouts). {@code null} when a pane is selected.
   */
  protected ViewCanvas<E> selectedCanvas = null;

  /** The layout cell manager that handles ViewCanvas and Component placement */
  protected final LayoutCellManager<E> cellManager;

  protected SynchView synchView = SynchView.DEFAULT_STACK;

  protected final ImageViewerEventManager<E> eventManager;
  protected final JPanel grid;
  protected final MigLayout migLayout;

  protected ImageViewerPlugin(ImageViewerEventManager<E> eventManager, String pluginName) {
    this(eventManager, VIEWS_1x1, pluginName, null, null, null);
  }

  protected ImageViewerPlugin(
      ImageViewerEventManager<E> eventManager,
      MigLayoutModel layoutModel,
      String uid,
      String pluginName,
      Icon icon,
      String tooltips) {
    super(uid, pluginName, icon, tooltips);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }
    this.eventManager = eventManager;
    this.cellManager = new LayoutCellManager<>(VIEWS_1x1.copy());
    grid = new JPanel();
    grid.setFocusCycleRoot(true);
    migLayout = new MigLayout();
    grid.setLayout(migLayout);
    add(grid, BorderLayout.CENTER);

    setLayoutModel(layoutModel);
    GridMouseHandler mouseHandler = new GridMouseHandler(cellManager, grid);
    grid.addMouseListener(mouseHandler);
    grid.addMouseMotionListener(mouseHandler);
  }

  /**
   * Returns true if type is instance of defaultClass. This operation is delegated in each bundle to
   * be sure all classes are visible.
   */
  public abstract boolean isViewType(Class<?> defaultClass, String type);

  public abstract int getViewTypeNumber(MigLayoutModel layout, Class<?> defaultClass);

  public abstract ViewCanvas<E> createDefaultView(String classType);

  public abstract Component createComponent(String clazz);

  public abstract Class<?> getSeriesViewerClass();

  public abstract MigLayoutModel getDefaultLayoutModel();

  /**
   * Returns the selected/focused canvas: the one of the selected {@link ViewportPane}, or the
   * directly selected canvas for layouts without panes (e.g. MPR, 3D).
   *
   * @return the selected {@link ViewCanvas}, or {@code null} if none
   */
  public ViewCanvas<E> getSelectedViewCanvas() {
    return selectedPane != null ? selectedPane.getSelectedCanvas() : selectedCanvas;
  }

  /**
   * Whether this container's layouts host {@link ViewportPane} slots, enabling per-viewport tiled
   * mode and hanging-protocol layouts. Containers with fixed, specialized views (MPR, 3D) return
   * {@code false}.
   */
  public boolean supportsViewportPanes() {
    return true;
  }

  /**
   * Returns the currently selected {@link ViewportPane}.
   *
   * @return the selected pane, or {@code null} if none
   */
  public ViewportPane<E> getSelectedViewportPane() {
    return selectedPane;
  }

  /**
   * Returns the {@link ViewportPane} that owns the given canvas, or {@code null} when the canvas is
   * hosted directly by a layout cell (MPR, 3D).
   *
   * @param canvas the canvas to look up
   * @return the owning pane, or {@code null}
   */
  public ViewportPane<E> getOwningPane(ViewCanvas<E> canvas) {
    if (canvas == null) {
      return null;
    }
    for (ViewportPane<E> pane : getViewportPanes()) {
      if (pane.getAllViewCanvases().contains(canvas)) {
        return pane;
      }
    }
    return null;
  }

  /**
   * Assigns a series to the given view. A canvas belonging to a TILED {@link ViewportPane} receives
   * the series through its whole pane so every tile keeps showing the same series at successive
   * tile offsets.
   *
   * @param view the target canvas
   * @param series the series to display (may be {@code null} to clear)
   */
  public void setSeriesToView(ViewCanvas<E> view, MediaSeries<E> series) {
    ViewportPane<E> pane = getOwningPane(view);
    if (pane != null && pane.isTiled()) {
      pane.setSeries(series);
    } else if (view != null) {
      view.setSeries(series, null);
    }
  }

  public List<ViewCanvas<E>> getView2ds() {
    return getAllCanvasesIncludingPanes();
  }

  /**
   * Returns all {@link ViewCanvas} instances visible in this plugin, including those nested inside
   * {@link ViewportPane} components.
   *
   * <p>For a plain (non-HP) layout every entry comes directly from {@code cellManager}. For an
   * HP-driven layout, each outer cell holds a {@link ViewportPane}: STACK panes contribute their
   * single canvas, TILED panes contribute all inner tile canvases.
   *
   * @return flattened, ordered list of every active ViewCanvas
   */
  public List<ViewCanvas<E>> getAllCanvasesIncludingPanes() {
    List<ViewCanvas<E>> direct = cellManager.getAllViewCanvases();
    List<ViewCanvas<E>> result = new ArrayList<>(direct);
    for (Component c : cellManager.getNonViewCanvasComponents()) {
      if (c instanceof ViewportPane<?> pane) {
        @SuppressWarnings("unchecked")
        ViewportPane<E> typedPane = (ViewportPane<E>) pane;
        result.addAll(typedPane.getAllViewCanvases());
      }
    }
    return result;
  }

  /**
   * Returns all top-level {@link ViewportPane} components owned by this plugin.
   *
   * @return list of viewport panes (may be empty for classic non-HP layouts)
   */
  public List<ViewportPane<E>> getViewportPanes() {
    List<ViewportPane<E>> panes = new ArrayList<>();
    for (Component c : cellManager.getNonViewCanvasComponents()) {
      if (c instanceof ViewportPane<?> pane) {
        @SuppressWarnings("unchecked")
        ViewportPane<E> typedPane = (ViewportPane<E>) pane;
        panes.add(typedPane);
      }
    }
    return panes;
  }

  /**
   * Returns the canvases participating in container-level (cross-viewport) synchronization: all
   * direct canvases plus the canvases of non-tiled panes. Canvases inside a TILED pane are excluded
   * — they synchronize with each other within their pane only.
   *
   * @return mutable list of synchronizable canvases
   */
  public List<ViewCanvas<E>> getSynchableImagePanels() {
    List<ViewCanvas<E>> result = new ArrayList<>(cellManager.getAllViewCanvases());
    for (ViewportPane<E> pane : getViewportPanes()) {
      if (!pane.isTiled()) {
        result.addAll(pane.getAllViewCanvases());
      }
    }
    return result;
  }

  public ImageViewerEventManager<E> getEventManager() {
    return eventManager;
  }

  @Override
  public void close() {
    super.close();

    GuiExecutor.execute(
        () -> {
          removeComponents();
          // Dispose direct canvases
          for (ViewCanvas<E> v : cellManager) {
            resetMaximizedSelectedImagePane(v);
            v.disposeView();
          }
          // Dispose panes (their active canvases and the hidden stack canvas of tiled panes)
          for (ViewportPane<E> pane : getViewportPanes()) {
            for (ViewCanvas<E> v : pane.getAllViewCanvases()) {
              resetMaximizedSelectedImagePane(v);
            }
            pane.dispose();
          }
        });
  }

  /**
   * Get the layout of this panel.
   *
   * @return the layoutModel
   */
  public synchronized MigLayoutModel getLayoutModel() {
    return cellManager.getLayoutModel();
  }

  /**
   * Get the cell manager holding the mapping between layout cells and their components/views.
   *
   * @return the layout cell manager
   */
  public LayoutCellManager<E> getCellManager() {
    return cellManager;
  }

  public MigLayoutModel getOriginalLayoutModel() {
    // Get the non clone layout from the list
    Optional<ComboItemListener<MigLayoutModel>> layout = eventManager.getAction(ActionW.LAYOUT);
    if (layout.isPresent()) {
      MigLayoutModel currentLayout = cellManager.getLayoutModel();
      for (Object element : layout.get().getAllItem()) {
        if (element instanceof MigLayoutModel gbm) {
          if ((currentLayout.getIcon() != null && gbm.getIcon() == currentLayout.getIcon())
              || currentLayout.toString().equals(gbm.toString())) {
            return gbm;
          }
        }
      }
    }
    return cellManager.getLayoutModel();
  }

  public static MigLayoutModel buildMigLayoutModel(int rows, int cols, String type) {
    StringBuilder buf = new StringBuilder();
    buf.append(rows);
    buf.append("x"); // NON-NLS
    buf.append(cols);
    return new MigLayoutModel(
        buf.toString(), String.format(ImageViewerPlugin.F_VIEWS, buf), rows, cols, type);
  }

  /**
   * Registers the given listener in the data explorer model for property-change events.
   *
   * @param model the data explorer model (may be {@code null})
   * @param instance the listener to register (may be {@code null})
   */
  public static void registerInDataExplorerModel(
      DataExplorerModel model, PropertyChangeListener instance) {
    if (model != null && instance != null) {
      model.addPropertyChangeListener(instance);
    }
  }

  /**
   * Determines the layout model to use when creating a viewer.
   *
   * @param options typed open options (may be {@code null})
   * @param defaultModel the fallback layout model
   * @param layoutAction the layout action providing available layouts (may be {@code null})
   */
  public static LayoutModel getLayoutModel(
      ViewerOpenOptions options,
      MigLayoutModel defaultModel,
      ComboItemListener<MigLayoutModel> layoutAction) {
    MigLayoutModel model = defaultModel;
    String uid = null;
    if (options != null) {
      int viewCount = options.seriesCount();
      if (viewCount > 1 && layoutAction != null) {
        model = ImageViewerPlugin.getBestDefaultViewLayout(layoutAction, viewCount, defaultModel);
      }
      uid = options.uid();
    }
    return new LayoutModel(uid, model);
  }

  @Override
  public void addSeries(MediaSeries<E> sequence) {
    if (sequence != null) {
      ViewCanvas<E> viewPane = getSelectedViewCanvas();
      if (viewPane != null) {
        setSeriesToView(viewPane, sequence);
        viewPane.getJComponent().repaint();

        // Set selection to the next view slot
        setSelectedImagePane(getNextSelectedImagePane());
      }
    }
  }

  @Override
  public void removeSeries(MediaSeries<E> series) {
    if (series != null) {
      for (ViewCanvas<E> v : getAllCanvasesIncludingPanes()) {
        if (v.getSeries() == series) {
          v.setSeries(null, null);
        }
      }
    }
  }

  public boolean closeIfNoContent() {
    if (getOpenSeries().isEmpty()) {
      close();
      handleFocusAfterClosing();
      return true;
    }
    return false;
  }

  @Override
  public List<MediaSeries<E>> getOpenSeries() {
    List<MediaSeries<E>> list = new ArrayList<>();
    for (ViewCanvas<E> v : getAllCanvasesIncludingPanes()) {
      MediaSeries<E> s = v.getSeries();
      if (s != null) {
        list.add(s);
      }
    }
    return list;
  }

  public void changeLayoutModel(MigLayoutModel layoutModel) {
    eventManager
        .getAction(ActionW.LAYOUT)
        .ifPresent(itemListener -> itemListener.setSelectedItem(layoutModel));
  }

  protected void removeComponents() {
    for (Component c : cellManager.getNonViewCanvasComponents()) {
      if (c instanceof SeriesViewerListener viewerListener) {
        eventManager.removeSeriesViewerListener(viewerListener);
      }
    }
  }

  protected JComponent buildInstance(Class<?> cl) throws Exception {
    JComponent component;
    if (hasSeriesViewerConstructor(cl)) {
      component = (JComponent) cl.getConstructor(SeriesViewer.class).newInstance(this);
    } else {
      component = (JComponent) cl.getDeclaredConstructor().newInstance();
    }

    if (component instanceof SeriesViewerListener viewerListener) {
      eventManager.addSeriesViewerListener(viewerListener);
    }
    return component;
  }

  private boolean hasSeriesViewerConstructor(Class<?> clazz) {
    for (Constructor<?> constructor : clazz.getConstructors()) {
      Class<?>[] types = constructor.getParameterTypes();
      if (types.length == 1 && types[0].isAssignableFrom(SeriesViewer.class)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Set a layout to this view panel. The layout is defined by the provided number corresponding the
   * layout definition in the property file.
   */
  protected synchronized void setLayoutModel(MigLayoutModel layoutModel) {
    MigLayoutModel newLayout = layoutModel == null ? VIEWS_1x1.copy() : layoutModel.copy();

    PreservedContent<E> preserved = preserveContent(newLayout);
    removeComponents();

    cellManager.setLayoutModel(newLayout);
    grid.removeAll();
    updateMigLayoutContraints(newLayout);

    var graphicListener = populateGridWithComponents(newLayout, preserved);
    configureViewsAndFireEvents(graphicListener);
  }

  /** Content carried over to a new layout: whole panes (keeping their mode) and direct canvases. */
  private record PreservedContent<E extends ImageElement>(
      List<ViewportPane<E>> panes, List<ViewCanvas<E>> canvases) {}

  /**
   * Preserves displayed content across a layout change and adjusts it to the new layout capacity.
   *
   * <p>A {@link ViewportPane} with content is preserved as a whole, keeping its STACK/TILED
   * configuration and series — a pane counts as ONE slot whatever its tile count. Directly hosted
   * canvases (MPR, 3D, and the mixed layouts pairing a view with a histogram or a DICOM dump) are
   * preserved individually.
   *
   * <p>Panes and canvases share a single slot budget: a pane can be unwrapped to fill a {@link
   * ViewCanvas} cell and a canvas can be wrapped into a new pane, so content survives a switch
   * between pane-based and canvas-based layouts. Empty panes/canvases and any excess beyond the new
   * layout's capacity are disposed.
   */
  private PreservedContent<E> preserveContent(MigLayoutModel newLayout) {
    List<ViewportPane<E>> panes = new ArrayList<>();
    for (ViewportPane<E> pane : getViewportPanes()) {
      boolean hasContent =
          pane.getAllViewCanvases().stream()
              .anyMatch(v -> v.getSeries() != null && v.getImage() != null);
      if (hasContent) {
        panes.add(pane);
      } else {
        pane.dispose();
      }
    }

    List<ViewCanvas<E>> canvases = new ArrayList<>();
    for (ViewCanvas<E> v : cellManager.getAllViewCanvases()) {
      if (v.getSeries() != null && v.getImage() != null) {
        canvases.add(v);
      } else {
        v.disposeView();
      }
    }

    // Drop the excess from the end, canvases first: a pane carries the richer state (tile grid,
    // per-tile synchronization) and is the more expensive content to rebuild.
    int excess = panes.size() + canvases.size() - getViewSlotNumber(newLayout);
    for (; excess > 0 && !canvases.isEmpty(); excess--) {
      canvases.removeLast().disposeView();
    }
    for (; excess > 0 && !panes.isEmpty(); excess--) {
      panes.removeLast().dispose();
    }

    ComboItemListener<SynchView> synch = eventManager.getAction(ActionW.SYNCH).orElse(null);
    if (synch != null && synch.getSelectedItem() instanceof SynchView sel) {
      sel.resetSynchData();
    }
    return new PreservedContent<>(panes, canvases);
  }

  /**
   * Populates the grid with components based on the layout cells.
   *
   * @param layout the layout model
   * @param preserved panes and canvases to reuse
   * @return GraphicSelectionListener if a HistogramView was created, null otherwise
   */
  private GraphicSelectionListener populateGridWithComponents(
      MigLayoutModel layout, PreservedContent<E> preserved) {
    GraphicSelectionListener graphicListener = null;

    for (MigCell cell : layout.getCells()) {
      Component component = createComponentForCell(cell, preserved);

      if (component instanceof HistogramView histogramView) {
        graphicListener = histogramView;
      }

      if (component != null) {
        grid.add(component, cell.getFullConstraints());
      }
    }

    layout.applyConstraintsToLayout(grid);
    return graphicListener;
  }

  /**
   * Creates a component for the given cell, either reusing a preserved view or creating new one.
   */
  private Component createComponentForCell(MigCell cell, PreservedContent<E> preserved) {
    if (isViewType(VIEWPORT_CLASS, cell.type())) {
      return createViewportPaneForCell(cell, preserved);
    }
    if (isViewType(ViewCanvas.class, cell.type())) {
      return createViewCanvasForCell(cell, preserved);
    }
    return createNonViewComponent(cell);
  }

  /**
   * Creates or reuses a {@link ViewportPane} for the given cell and registers it in the cell
   * manager.
   *
   * <p>A preserved pane is reused as-is, keeping its STACK/TILED configuration and displayed
   * series. Otherwise a new STACK pane is built around a preserved canvas when available, or a
   * fresh canvas created via {@link #createDefaultView(String)}.
   */
  private Component createViewportPaneForCell(MigCell cell, PreservedContent<E> preserved) {
    if (!preserved.panes().isEmpty()) {
      ViewportPane<E> pane = preserved.panes().removeFirst();
      cellManager.addComponent(cell.position(), pane);
      return pane;
    }
    ViewCanvas<E> initialCanvas = takePreservedCanvas(preserved);
    if (initialCanvas == null) {
      initialCanvas = createFreshViewCanvas(null);
    }
    if (initialCanvas == null) {
      return null;
    }
    ViewportPane<E> pane = new ViewportPane<>(initialCanvas);
    pane.setEventManager(eventManager);
    cellManager.addComponent(cell.position(), pane);
    if (initialCanvas.getSeries() != null) {
      initialCanvas.getSeries().setOpen(true);
    }
    return pane;
  }

  /** Creates or reuses a ViewCanvas for the given cell. */
  private Component createViewCanvasForCell(MigCell cell, PreservedContent<E> preserved) {
    ViewCanvas<E> viewCanvas = takePreservedCanvas(preserved);
    if (viewCanvas == null) {
      viewCanvas = createFreshViewCanvas(cell.type());
    }

    if (viewCanvas != null) {
      cellManager.addViewCanvas(cell.position(), viewCanvas);
      if (viewCanvas.getSeries() != null) {
        viewCanvas.getSeries().setOpen(true);
      }
      return viewCanvas.getJComponent();
    }
    return null;
  }

  /**
   * Takes the next preserved canvas, unwrapping a preserved pane when no bare canvas is left, or
   * {@code null} when nothing is left to reuse.
   */
  private ViewCanvas<E> takePreservedCanvas(PreservedContent<E> preserved) {
    if (!preserved.canvases().isEmpty()) {
      return preserved.canvases().removeFirst();
    }
    if (!preserved.panes().isEmpty()) {
      return preserved.panes().removeFirst().extractContentCanvas();
    }
    return null;
  }

  /** Creates a fresh ViewCanvas with default listeners. */
  private ViewCanvas<E> createFreshViewCanvas(String type) {
    ViewCanvas<E> viewCanvas = createDefaultView(type);
    if (viewCanvas != null) {
      viewCanvas.registerDefaultListeners();
    }
    return viewCanvas;
  }

  /** Creates a non-ViewCanvas component for the given cell. */
  private Component createNonViewComponent(MigCell cell) {
    Component component = createComponent(cell.type());
    if (component != null) {
      if (component instanceof JComponent jComponent) {
        jComponent.setOpaque(true);
      }
      cellManager.addComponent(cell.position(), component);
    }
    return component;
  }

  /**
   * Configures all views with mouse listeners and fires layout events.
   *
   * @param graphicListener optional GraphicSelectionListener to register with views
   */
  private void configureViewsAndFireEvents(GraphicSelectionListener graphicListener) {
    List<ViewCanvas<E>> allViews = getAllCanvasesIncludingPanes();
    if (allViews.isEmpty()) {
      return;
    }

    // Prefer the first ViewportPane as the selected pane; direct-canvas layouts (MPR, 3D)
    // have no pane and select the first canvas instead.
    List<ViewportPane<E>> panes = getViewportPanes();
    if (!panes.isEmpty()) {
      selectedPane = panes.getFirst();
      selectedCanvas = null;
    } else {
      selectedPane = null;
      selectedCanvas = allViews.getFirst();
    }

    ViewCanvas<E> selected = getSelectedViewCanvas();
    if (selected == null) {
      return;
    }

    MouseActions mouseActions = eventManager.getMouseActions();

    for (ViewCanvas<E> view : allViews) {
      view.closeLens();
      view.enableMouseAndKeyListener(mouseActions);
      if (graphicListener != null) {
        view.getGraphicManager().addGraphicSelectionListener(graphicListener);
      }
    }

    selected.setSelected(true);
    eventManager.updateComponentsListener(selected);

    if (selected.getSeries() instanceof Series) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(this, selected.getSeries(), selected.getImage(), EVENT.LAYOUT));
    }
  }

  public void replaceView(ViewCanvas<E> oldView, ViewCanvas<E> newView) {
    if (oldView == null || newView == null) {
      return;
    }

    int position = cellManager.findPositionOfViewCanvas(oldView);
    if (position == -1) {
      return; // Old view not found
    }

    performViewReplacement(oldView, newView, position);
    updateViewsAfterReplacement();
  }

  /** Performs the actual view replacement operations. */
  private void performViewReplacement(ViewCanvas<E> oldView, ViewCanvas<E> newView, int position) {
    // If the replaced view was selected, update the owning pane's selection
    ViewportPane<E> owning = getOwningPane(oldView);
    if (owning != null && owning == selectedPane) {
      owning.setSelectedCanvas(newView);
    }
    if (selectedCanvas == oldView) {
      selectedCanvas = newView;
    }

    // Dispose old view and replace in cell manager
    oldView.disposeView();
    cellManager.replaceViewCanvas(position, newView);

    // Mark series as open
    if (newView.getSeries() != null) {
      newView.getSeries().setOpen(true);
    }

    // Rebuild grid layout
    rebuildGridLayout();
  }

  /** Updates all views after replacement with proper configuration. */
  private void updateViewsAfterReplacement() {
    List<ViewCanvas<E>> allViews = getAllCanvasesIncludingPanes();
    if (allViews.isEmpty()) {
      return;
    }

    ensureSelection(allViews);

    ViewCanvas<E> selected = getSelectedViewCanvas();
    if (selected == null) {
      return;
    }

    MouseActions mouseActions = eventManager.getMouseActions();
    configureViewsAfterReplacement(allViews, mouseActions);

    selected.setSelected(true);
    eventManager.updateComponentsListener(selected);
  }

  /** Ensures a valid selection exists, preferring the first pane then the first canvas. */
  private void ensureSelection(List<ViewCanvas<E>> allViews) {
    if (getSelectedViewCanvas() == null) {
      List<ViewportPane<E>> panes = getViewportPanes();
      if (!panes.isEmpty()) {
        selectedPane = panes.getFirst();
        selectedCanvas = null;
      } else if (!allViews.isEmpty()) {
        selectedCanvas = allViews.getFirst();
      }
    }
  }

  /** Configures all views with appropriate settings after replacement. */
  private void configureViewsAfterReplacement(
      List<ViewCanvas<E>> allViews, MouseActions mouseActions) {
    for (ViewCanvas<E> view : allViews) {
      view.closeLens();
      view.enableMouseAndKeyListener(mouseActions);
    }
  }

  /**
   * Rebuilds the grid layout by adding all components from the cellManager in their proper
   * positions. This method efficiently updates the grid without recreating components.
   */
  private void rebuildGridLayout() {
    grid.removeAll();

    MigLayoutModel currentLayout = cellManager.getLayoutModel();
    migLayout.setLayoutConstraints(currentLayout.getLayoutConstraints());

    // Iterate through cells and add components from cellManager
    for (MigCell cell : currentLayout.getCells()) {
      Component component = cellManager.getComponent(cell.position());
      if (component != null) {
        grid.add(component, cell.getFullConstraints());
      }
    }

    currentLayout.applyConstraintsToLayout(grid);
  }

  public void resetMaximizedSelectedImagePane(ViewCanvas<E> viewCanvas) {
    if (grid.getComponentCount() == 1) {
      Dialog fullscreenDialog = WinUtil.getParentDialog(grid);
      if (fullscreenDialog != null
          && fullscreenDialog
              .getTitle()
              .equals(Messages.getString("ImageViewerPlugin.fullscreen"))) {
        maximizedSelectedImagePane(viewCanvas, null);
      }
    }
  }

  public void maximizedSelectedImagePane(ViewCanvas<E> defaultView2d, MouseEvent evt) {
    if (shouldPreventMaximization(defaultView2d, evt)) {
      return;
    }

    removeFocusListenersFromViews();

    String titleDialog = Messages.getString("ImageViewerPlugin.fullscreen");
    Dialog fullscreenDialog = WinUtil.getParentDialog(grid);
    boolean isDetached =
        fullscreenDialog != null && !titleDialog.equals(fullscreenDialog.getTitle());

    grid.removeAll();

    if (isDetached || fullscreenDialog == null) {
      enterFullscreenMode(defaultView2d, titleDialog, fullscreenDialog, isDetached);
    } else {
      exitFullscreenMode(defaultView2d, fullscreenDialog);
    }
  }

  /**
   * Checks if maximization should be prevented due to incomplete graphics or graphic intersection.
   */
  private boolean shouldPreventMaximization(ViewCanvas<E> viewCanvas, MouseEvent evt) {
    List<DragGraphic> selectedGraphics =
        viewCanvas.getGraphicManager().getSelectedDraggableGraphics();

    // Prevent if any graphics are incomplete
    if (selectedGraphics.stream()
        .anyMatch(g -> Objects.equals(g.getPtsNumber(), Graphic.UNDEFINED))) {
      return true;
    }

    // Prevent if click intersects with a graphic
    if (evt != null) {
      MouseEventDouble mouseEvt = new MouseEventDouble(evt);
      mouseEvt.setImageCoordinates(viewCanvas.getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
      return viewCanvas.getGraphicManager().getFirstGraphicIntersecting(mouseEvt).isPresent();
    }

    return false;
  }

  /** Removes focus listeners from all views. */
  private void removeFocusListenersFromViews() {
    getAllCanvasesIncludingPanes().forEach(v -> v.getJComponent().removeFocusListener(v));
  }

  /** Adds focus listeners back to all views. */
  private void addFocusListenersToViews() {
    getAllCanvasesIncludingPanes().forEach(v -> v.getJComponent().addFocusListener(v));
  }

  /** Enters fullscreen mode by creating a modal dialog with the view. */
  private void enterFullscreenMode(
      ViewCanvas<E> viewCanvas, String title, Dialog existingDialog, boolean isDetached) {
    remove(grid);

    // Configure grid for single fullscreen view
    grid.setLayout(new MigLayout("fill, ins 0", "[grow,fill]", "[grow,fill]"));
    grid.add(viewCanvas.getJComponent(), "grow");
    viewCanvas.getJComponent().addFocusListener(viewCanvas);

    // Create and configure fullscreen dialog
    Frame parentFrame = WinUtil.getParentFrame(this);
    Dialog fullscreenDialog =
        new JDialog(
            isDetached ? existingDialog : parentFrame, title, ModalityType.APPLICATION_MODAL);

    fullscreenDialog.add(grid, BorderLayout.CENTER);
    fullscreenDialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            maximizedSelectedImagePane(viewCanvas, null);
          }
        });

    configureDialogBounds(fullscreenDialog, parentFrame, existingDialog, isDetached);
  }

  /** Configures dialog bounds based on parent frame state and monitor configuration. */
  private void configureDialogBounds(
      Dialog dialog, Frame parentFrame, Dialog existingDialog, boolean isDetached) {
    if (!isDetached
        && (parentFrame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
      dialog.setBounds(parentFrame.getBounds());
      dialog.setVisible(true);
    } else {
      Monitor monitor =
          Monitor.getMonitor(
              isDetached
                  ? existingDialog.getGraphicsConfiguration()
                  : parentFrame.getGraphicsConfiguration());
      if (monitor != null) {
        dialog.setBounds(monitor.getFullscreenBounds());
        dialog.setVisible(true);
      }
    }
  }

  /** Exits fullscreen mode by restoring the original grid layout. */
  private void exitFullscreenMode(ViewCanvas<E> viewCanvas, Dialog fullscreenDialog) {
    viewCanvas.getJComponent().removeFocusListener(viewCanvas);
    Arrays.stream(fullscreenDialog.getWindowListeners())
        .forEach(fullscreenDialog::removeWindowListener);
    fullscreenDialog.removeAll();
    fullscreenDialog.dispose();

    rebuildGridLayout();
    // Re-attach the maximized canvas to its owning pane (it was reparented into the
    // fullscreen grid and rebuildGridLayout only restores the outer cells)
    ViewportPane<E> owning = getOwningPane(viewCanvas);
    if (owning != null) {
      owning.restoreView();
    }
    add(grid, BorderLayout.CENTER);
    addFocusListenersToViews();

    viewCanvas.getJComponent().requestFocusInWindow();
  }

  private void updateMigLayoutContraints(MigLayoutModel layoutModel) {
    migLayout.setLayoutConstraints(layoutModel.getLayoutConstraints());
    migLayout.setColumnConstraints(layoutModel.getColumnConstraints());
    migLayout.setRowConstraints(layoutModel.getRowConstraints());
  }

  public static int getViewTypeNumber(MigLayoutModel layout) {
    int val = 0;
    if (layout != null) {
      for (MigCell cell : layout.getCells()) {
        try {
          Class<?> clazz = Class.forName(cell.type());
          if (VIEWPORT_CLASS.isAssignableFrom(clazz) || ViewCanvas.class.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          // Ignore exceptions and continue counting
        }
      }
    }
    return val;
  }

  /**
   * Number of view slots in the layout: {@link ViewportPane} cells plus direct {@link ViewCanvas}
   * cells (MPR, 3D). Delegated to the bundle-specific counters so all cell classes are visible.
   */
  private int getViewSlotNumber(MigLayoutModel layout) {
    return getViewTypeNumber(layout, VIEWPORT_CLASS) + getViewTypeNumber(layout, ViewCanvas.class);
  }

  public void setSelectedImagePaneFromFocus(ViewCanvas<E> viewCanvas) {
    setSelectedImagePane(viewCanvas);
  }

  public void setSelectedImagePane(ViewCanvas<E> viewCanvas) {
    ViewCanvas<E> current = getSelectedViewCanvas();
    if (current != null && current.getSeries() != null) {
      current.getSeries().setSelected(false, null);
      current.getSeries().setFocused(false);
    }

    if (viewCanvas != null && viewCanvas.getSeries() != null) {
      viewCanvas.getSeries().setSelected(true, viewCanvas.getImage());
      viewCanvas.getSeries().setFocused(eventManager.getSelectedView2dContainer() == this);
    }

    boolean newView = current != viewCanvas && viewCanvas != null;
    if (newView) {
      if (current != null) {
        current.setSelected(false);
      }
      viewCanvas.setSelected(true);
      // Update the selection to the pane owning this canvas, or to the canvas itself when it
      // is not hosted by a ViewportPane (MPR, 3D)
      ViewportPane<E> owning = getOwningPane(viewCanvas);
      if (owning != null) {
        selectedPane = owning;
        selectedPane.setSelectedCanvas(viewCanvas);
        selectedCanvas = null;
      } else {
        selectedPane = null;
        selectedCanvas = viewCanvas;
      }
      eventManager.updateComponentsListener(viewCanvas);
    }
    if (newView && viewCanvas.getSeries() instanceof Series) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(this, viewCanvas.getSeries(), viewCanvas.getImage(), EVENT.SELECT));
    }
    eventManager.fireSeriesViewerListeners(
        new SeriesViewerEvent(
            this, viewCanvas == null ? null : viewCanvas.getSeries(), null, EVENT.SELECT_VIEW));
  }

  /** Return the image in the image display panel. */
  public E getImage(int i) {
    List<ViewCanvas<E>> allViews = getAllCanvasesIncludingPanes();
    if (i >= 0 && i < allViews.size()) {
      return allViews.get(i).getImage();
    }
    return null;
  }

  /** Return all the <code>ImagePanel</code>s. */
  public List<ViewCanvas<E>> getImagePanels() {
    return getImagePanels(false);
  }

  public List<ViewCanvas<E>> getImagePanels(boolean selectedImagePaneLast) {
    List<ViewCanvas<E>> viewList = new ArrayList<>(getAllCanvasesIncludingPanes());
    if (selectedImagePaneLast) {
      ViewCanvas<E> selectedView = getSelectedViewCanvas();

      if (selectedView != null && viewList.size() > 1) {
        viewList.remove(selectedView);
        viewList.add(selectedView);
      }
      return viewList;
    }
    return viewList;
  }

  /**
   * Returns one selectable canvas per layout slot, in cell-position order: the selected canvas of
   * each {@link ViewportPane} (a whole pane is a single slot, whatever its tile count) and every
   * directly hosted canvas (MPR, 3D).
   */
  public List<ViewCanvas<E>> getSelectableSlots() {
    List<ViewCanvas<E>> slots = new ArrayList<>();
    for (MigCell cell : cellManager.getLayoutModel().getCells()) {
      Component comp = cellManager.getComponent(cell.position());
      if (comp instanceof ViewportPane<?> pane) {
        @SuppressWarnings("unchecked")
        ViewportPane<E> typedPane = (ViewportPane<E>) pane;
        slots.add(typedPane.getSelectedCanvas());
      } else {
        cellManager.getViewCanvas(cell.position()).ifPresent(slots::add);
      }
    }
    return slots;
  }

  public ViewCanvas<E> getNextSelectedImagePane() {
    ViewCanvas<E> current = getSelectedViewCanvas();
    List<ViewCanvas<E>> slots = getSelectableSlots();
    for (int i = 0; i < slots.size() - 1; i++) {
      if (slots.get(i) == current) {
        return slots.get(i + 1);
      }
    }
    return current;
  }

  public abstract List<MigLayoutModel> getLayoutList();

  /**
   * Synchronizable options shown in the per-view and toolbar sync popups for this container. The
   * default returns {@link SynchOptionsCheckBoxGroup#getSyncOptions()} (the 2D set: Scroll, Pan,
   * Zoom, Rotation, Flip, Window/Level, Spatial Unit). Containers with different semantics — e.g.
   * the 3D volume viewer — should override this to expose only the actions that make sense.
   */
  public List<SynchOptionsCheckBoxGroup.SyncOption> getSyncOptions() {
    return SynchOptionsCheckBoxGroup.getSyncOptions();
  }

  /**
   * Whether auto-sync semantics for this container are confined to its own views. {@code false}
   * (default) means auto-sync can fan out across sibling containers — used by the 2D viewer where
   * STACK mode synchronises views across every visible container with the same group. {@code true}
   * means auto-sync is meaningful only within this container (e.g. the 3D volume viewer, where each
   * container owns its own volume), so the toolbar's "Apply to all views" entry should stay inside
   * this container.
   */
  public boolean isAutoSyncContainerScoped() {
    return false;
  }

  public Boolean isContainingView(ViewCanvas<?> view2DPane) {
    return getAllCanvasesIncludingPanes().stream().anyMatch(v -> Objects.equals(v, view2DPane));
  }

  public SynchView getSynchView() {
    return synchView;
  }

  public void setSynchView(SynchView synchView) {
    this.synchView = Objects.requireNonNull(synchView);
    eventManager.updateAllListeners(this, synchView);
  }

  public synchronized void setMouseActions(MouseActions mouseActions) {
    List<ViewCanvas<E>> all = getAllCanvasesIncludingPanes();
    if (mouseActions == null) {
      for (ViewCanvas<E> v : all) {
        v.disableMouseAndKeyListener();
        // Let the possibility get the focus
        v.iniDefaultMouseListener();
      }
    } else {
      for (ViewCanvas<E> v : all) {
        v.enableMouseAndKeyListener(mouseActions);
      }
    }
  }

  public MigLayoutModel getBestDefaultViewLayout(int size) {
    if (size <= 1) {
      return getDefaultLayoutModel();
    }

    return eventManager
        .getAction(ActionW.LAYOUT)
        .map(layout -> findBestLayoutForSize(layout.getAllItem(), size))
        .orElse(getDefaultLayoutModel());
  }

  /** Finds the best layout model for the specified size from available layouts. */
  private MigLayoutModel findBestLayoutForSize(Object[] layouts, int targetSize) {
    record LayoutCandidate(MigLayoutModel model, int sizeDiff, int dimensionDiff) {}

    return java.util.Arrays.stream(layouts)
        .filter(MigLayoutModel.class::isInstance)
        .map(MigLayoutModel.class::cast)
        .map(
            model -> {
              int layoutSize = getViewTypeNumber(model, getSeriesViewerClass());
              int sizeDiff = Math.abs(layoutSize - targetSize);
              Dimension dim = model.getGridSize();
              int dimensionDiff = Math.abs(dim.width - dim.height);
              return new LayoutCandidate(model, sizeDiff, dimensionDiff);
            })
        .filter(
            candidate -> {
              int layoutSize = getViewTypeNumber(candidate.model, getSeriesViewerClass());
              return layoutSize >= targetSize;
            })
        .min(
            Comparator.comparingInt(LayoutCandidate::sizeDiff)
                .thenComparingInt(LayoutCandidate::dimensionDiff))
        .map(LayoutCandidate::model)
        .orElse(getDefaultLayoutModel());
  }

  public static MigLayoutModel getBestDefaultViewLayout(
      ActionState layout, int size, MigLayoutModel defaultModel) {
    if (size <= 1) {
      return defaultModel;
    }

    if (layout instanceof ComboItemListener<?> comboItemListener) {
      return findBestStaticLayoutForSize(comboItemListener.getAllItem(), size, defaultModel);
    }
    return defaultModel;
  }

  /** Static version of findBestLayoutForSize for use in static context. */
  private static MigLayoutModel findBestStaticLayoutForSize(
      Object[] layouts, int targetSize, MigLayoutModel defaultModel) {
    record LayoutCandidate(
        MigLayoutModel model,
        int sizeDiff,
        int dimensionDiff,
        boolean landscape,
        boolean hasComponent) {}

    return java.util.Arrays.stream(layouts)
        .filter(MigLayoutModel.class::isInstance)
        .map(MigLayoutModel.class::cast)
        .map(
            model -> {
              int layoutSize = getViewTypeNumber(model);
              int sizeDiff = Math.abs(layoutSize - targetSize);
              boolean hasComponent = model.getCellCount() > layoutSize;
              Dimension dim = model.getGridSize();
              int dimensionDiff = Math.abs(dim.width - dim.height);
              boolean landscape = dim.width > dim.height;
              return new LayoutCandidate(model, sizeDiff, dimensionDiff, landscape, hasComponent);
            })
        .filter(
            candidate -> {
              int layoutSize = getViewTypeNumber(candidate.model);
              return layoutSize >= targetSize && !candidate.hasComponent;
            })
        .min(
            Comparator.comparingInt(LayoutCandidate::sizeDiff)
                .thenComparingInt(LayoutCandidate::dimensionDiff)
                .thenComparing(LayoutCandidate::landscape))
        .map(LayoutCandidate::model)
        .orElse(defaultModel);
  }

  /** Generates a list of layout models based on viewer dimensions and base models. */
  protected static ArrayList<MigLayoutModel> getLayoutList(
      ImageViewerPlugin<?> viewerPlugin, List<MigLayoutModel> baseLayoutModels) {
    LayoutDimensions dims = calculateLayoutDimensions(viewerPlugin, baseLayoutModels);
    ArrayList<MigLayoutModel> layouts = new ArrayList<>(baseLayoutModels);

    // Exclude 1x1 when generating dynamic layouts
    if (dims.shouldGenerateDynamicLayouts()) {
      generateDynamicLayouts(layouts, dims);
    }

    layouts.sort(Comparator.comparingInt(MigLayoutModel::getCellCount));
    return layouts;
  }

  /** Calculates layout dimensions based on viewer aspect ratio. */
  private static LayoutDimensions calculateLayoutDimensions(
      ImageViewerPlugin<?> viewerPlugin, List<MigLayoutModel> baseModels) {
    int width = viewerPlugin.getWidth();
    int height = viewerPlugin.getHeight();
    double ratio = width / (double) height;

    int cols = ratio >= 1.0 ? (int) Math.round(ratio * 1.5) : 1;
    int rows = ratio < 1.0 ? (int) Math.round((1.0 / ratio) * 1.5) : 1;

    // Calculate minimum dimensions from existing models
    int minCols = cols;
    int minRows = rows;
    for (MigLayoutModel model : baseModels) {
      Dimension dim = model.getGridSize();
      minCols = Math.max(minCols, dim.width);
      minRows = Math.max(minRows, dim.height);
    }

    int factorLimit =
        cols == 1 ? (int) Math.round(width / 512.0) : (int) Math.round(height / 512.0);
    factorLimit = Math.max(1, factorLimit);

    return new LayoutDimensions(cols, rows, minCols, minRows, factorLimit);
  }

  /** Generates dynamic layout models based on calculated dimensions. */
  private static void generateDynamicLayouts(
      ArrayList<MigLayoutModel> layouts, LayoutDimensions dims) {
    if (dims.cols > dims.rows) {
      int step = 1 + (dims.cols / 20);
      for (int i = dims.cols / 2; i < dims.cols; i += step) {
        addDynamicLayout(layouts, dims.factorLimit, i, dims.rows, dims.minCols, dims.minRows);
      }
    } else {
      int step = 1 + (dims.rows / 20);
      for (int i = dims.rows / 2; i < dims.rows; i += step) {
        addDynamicLayout(layouts, dims.factorLimit, dims.cols, i, dims.minCols, dims.minRows);
      }
    }
    addDynamicLayout(layouts, dims.factorLimit, dims.cols, dims.rows, dims.minCols, dims.minRows);
  }

  /** Adds dynamic layout variations based on factor scaling. */
  private static void addDynamicLayout(
      List<MigLayoutModel> layouts, int factorLimit, int cols, int rows, int minCols, int minRows) {
    for (int factor = 1; factor <= factorLimit; factor++) {
      if (factor > 2 || factor * rows > minRows || factor * cols > minCols) {
        int scaledRows = factor * rows;
        int scaledCols = factor * cols;
        if (scaledRows < 50 && scaledCols < 50) {
          layouts.add(buildMigLayoutModel(scaledRows, scaledCols, VIEWPORT_CLASS.getName()));
        }
      }
    }
  }

  /** Helper record for layout dimension calculations. */
  private record LayoutDimensions(int cols, int rows, int minCols, int minRows, int factorLimit) {

    boolean shouldGenerateDynamicLayouts() {
      return cols != rows && cols != 0 && rows != 0;
    }
  }

  public MigLayoutModel getViewLayout(String title) {
    if (title != null) {
      Optional<ComboItemListener<MigLayoutModel>> layout = eventManager.getAction(ActionW.LAYOUT);
      if (layout.isPresent()) {
        Object[] list = layout.get().getAllItem();
        for (Object m : list) {
          if (m instanceof MigLayoutModel model && title.equals(model.getId())) {
            return model;
          }
        }
      }
    }
    return VIEWS_1x1;
  }

  public void addSeriesList(List<MediaSeries<E>> seriesList, boolean bestDefaultLayout) {
    if (seriesList == null || seriesList.isEmpty()) {
      return;
    }

    setSelectedAndGetFocus();

    if (bestDefaultLayout) {
      addSeriesWithBestLayout(seriesList);
    } else {
      addSeriesToAvailableSlots(seriesList);
    }

    repaint();
  }

  /** Adds series with best fitting layout, adjusting grid size as needed. */
  private void addSeriesWithBestLayout(List<MediaSeries<E>> seriesList) {
    changeLayoutModel(getBestDefaultViewLayout(seriesList.size()));

    List<ViewCanvas<E>> slots = getSelectableSlots();

    // Clear excess view slots if the layout is larger than the series list
    for (int i = seriesList.size(); i < slots.size(); i++) {
      setSeriesToView(slots.get(i), null);
    }

    // Reset to the first slot and add all series
    if (!slots.isEmpty()) {
      setSelectedImagePane(slots.getFirst());
      seriesList.forEach(this::addSeries);
    }
  }

  /** Adds series to available empty view slots, expanding layout if necessary. */
  private void addSeriesToAvailableSlots(List<MediaSeries<E>> seriesList) {
    int emptyViewCount = countEmptyViews();

    // Expand layout if not enough empty view slots
    if (emptyViewCount < seriesList.size()) {
      int totalNeeded = getSelectableSlots().size() + seriesList.size();
      changeLayoutModel(getBestDefaultViewLayout(totalNeeded));
    }

    // Add series to empty slots only, so occupied views are never overwritten
    int seriesIndex = 0;
    for (ViewCanvas<E> view : getSelectableSlots()) {
      if (view.getSeries() == null && seriesIndex < seriesList.size()) {
        setSelectedImagePane(view);
        setSeriesToView(view, seriesList.get(seriesIndex++));
        view.getJComponent().repaint();
      }
    }
  }

  /** Counts the number of empty view slots in the current layout. */
  private int countEmptyViews() {
    return (int) getSelectableSlots().stream().filter(v -> v.getSeries() == null).count();
  }

  public void selectLayoutPositionForAddingSeries(List<MediaSeries<E>> seriesList) {
    int nbSeriesToAdd = 1;
    if (seriesList != null) {
      nbSeriesToAdd = seriesList.size();
      if (nbSeriesToAdd < 1) {
        nbSeriesToAdd = 1;
      }
    }
    List<ViewCanvas<E>> slots = getSelectableSlots();
    int pos = slots.size() - nbSeriesToAdd;
    if (pos < 0) {
      pos = 0;
    }
    setSelectedImagePane(slots.get(pos));
  }
}
