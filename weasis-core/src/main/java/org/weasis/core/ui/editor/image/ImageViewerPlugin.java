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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.ui.util.MouseEventDouble;

public abstract class ImageViewerPlugin<E extends ImageElement> extends ViewerPlugin<E> {

  public static final String F_VIEWS = Messages.getString("ImageViewerPlugin.2");

  // A model must have at least one view that inherited of DefaultView2d
  public static final Class<?> view2dClass = ViewCanvas.class;
  public static final MigLayoutModel VIEWS_1x1 =
      new MigLayoutModel(
          "1x1", // NON-NLS
          String.format(Messages.getString("ImageViewerPlugin.1"), "1x1"), // NON-NLS
          1,
          1,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_2x1 =
      new MigLayoutModel(
          "2x1", // NON-NLS
          String.format(F_VIEWS, "2x1"), // NON-NLS
          2,
          1,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_1x2 =
      new MigLayoutModel(
          "1x2", // NON-NLS
          String.format(F_VIEWS, "1x2"), // NON-NLS
          1,
          2,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_1x3 =
      new MigLayoutModel(
          "1x3", // NON-NLS
          String.format(F_VIEWS, "1x3"), // NON-NLS
          1,
          3,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_1x4 =
      new MigLayoutModel(
          "1x4", // NON-NLS
          String.format(F_VIEWS, "1x4"), // NON-NLS
          1,
          4,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_2x2_f2 =
      new MergedCellsBuilder(2, 2, "layout_c2x1", "3 views (right merged)", view2dClass.getName())
          .addCell(0, 0)
          .addMergedCell(0, 1, 1, 2)
          .addCell(1, 0)
          .build();
  public static final MigLayoutModel VIEWS_2_f1x2 =
      new MergedCellsBuilder(2, 2, "layout_r1x2", "3 views (top merged)", view2dClass.getName())
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
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_2x3 =
      new MigLayoutModel(
          "2x3", // NON-NLS
          String.format(F_VIEWS, "2x3"), // NON-NLS
          2,
          3,
          view2dClass.getName());
  public static final MigLayoutModel VIEWS_2x4 =
      new MigLayoutModel(
          "2x4", // NON-NLS
          String.format(F_VIEWS, "2x4"), // NON-NLS
          2,
          4,
          view2dClass.getName());

  public record LayoutModel(String uid, MigLayoutModel model) {}

  /** The current focused <code>ImagePane</code>. The default is 0. */
  protected ViewCanvas<E> selectedImagePane = null;

  /** The layout cell manager that handles ViewCanvas and Component placement */
  protected final LayoutCellManager<E> cellManager;

  protected SynchView synchView = SynchView.NONE;

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

  public ViewCanvas<E> getSelectedImagePane() {
    return selectedImagePane;
  }

  public List<ViewCanvas<E>> getView2ds() {
    return cellManager.getAllViewCanvases();
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
          for (ViewCanvas<E> v : cellManager) {
            resetMaximizedSelectedImagePane(v);
            v.disposeView();
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

  public static void registerInDataExplorerModel(
      Map<String, Object> properties, PropertyChangeListener instance) {
    if (properties != null && instance != null) {
      Object obj = properties.get(DataExplorerModel.class.getName());
      if (obj instanceof DataExplorerModel m) {
        // Register the PropertyChangeListener
        m.addPropertyChangeListener(instance);
      }
    }
  }

  public static LayoutModel getLayoutModel(
      Map<String, Object> properties,
      MigLayoutModel defaultModel,
      ComboItemListener<MigLayoutModel> layoutAction) {
    MigLayoutModel model = defaultModel;
    String uid = null;
    if (properties != null) {
      Object obj = properties.get(MigLayoutModel.class.getName());
      if (obj instanceof MigLayoutModel currentLayout) {
        model = currentLayout;
      } else {
        obj = properties.get(ViewCanvas.class.getName());
        if (obj instanceof Integer intVal && layoutAction != null) {
          model = ImageViewerPlugin.getBestDefaultViewLayout(layoutAction, intVal, defaultModel);
        }
      }

      // Set UID
      Object val = properties.get(ViewerPluginBuilder.UID);
      if (val instanceof String s) {
        uid = s;
      }
    }
    return new LayoutModel(uid, model);
  }

  @Override
  public void addSeries(MediaSeries<E> sequence) {
    if (sequence != null && selectedImagePane != null) {
      if (SynchData.Mode.TILE.equals(synchView.getSynchData().getMode())) {
        selectedImagePane.setSeries(sequence, null);
        updateTileOffset();
        return;
      }
      ViewCanvas<E> viewPane = getSelectedImagePane();
      if (viewPane != null) {
        viewPane.setSeries(sequence);
        viewPane.getJComponent().repaint();

        // Set selection to the next view
        setSelectedImagePane(getNextSelectedImagePane());
      }
    }
  }

  @Override
  public void removeSeries(MediaSeries<E> series) {
    if (series != null) {
      for (ViewCanvas<E> v : cellManager) {
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
    for (ViewCanvas<E> v : cellManager) {
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

    List<ViewCanvas<E>> preservedViews = preserveViewsWithImages(newLayout);
    removeComponents();

    cellManager.setLayoutModel(newLayout);
    grid.removeAll();
    updateMigLayoutContraints(newLayout);

    var graphicListener = populateGridWithComponents(newLayout, preservedViews);
    configureViewsAndFireEvents(graphicListener);
  }

  /**
   * Preserves views containing images, disposes empty ones, and adjusts count to layout capacity.
   *
   * @param newLayout the new layout model
   * @return list of preserved ViewCanvas instances
   */
  private List<ViewCanvas<E>> preserveViewsWithImages(MigLayoutModel newLayout) {
    List<ViewCanvas<E>> allCanvases = cellManager.getAllViewCanvases();

    List<ViewCanvas<E>> viewsWithImages =
        allCanvases.stream().filter(v -> v.getSeries() != null && v.getImage() != null).toList();

    allCanvases.stream()
        .filter(v -> v.getSeries() == null || v.getImage() == null)
        .forEach(ViewCanvas::disposeView);

    // Trim excess views if new layout has fewer slots
    int layoutCapacity = getViewTypeNumber(newLayout, view2dClass);
    if (viewsWithImages.size() > layoutCapacity) {
      viewsWithImages
          .subList(layoutCapacity, viewsWithImages.size())
          .forEach(ViewCanvas::disposeView);
      return new ArrayList<>(viewsWithImages.subList(0, layoutCapacity));
    }

    return new ArrayList<>(viewsWithImages);
  }

  /**
   * Populates the grid with components based on the layout cells.
   *
   * @param layout the layout model
   * @param preservedViews list of preserved ViewCanvas instances to reuse
   * @return GraphicSelectionListener if a HistogramView was created, null otherwise
   */
  private GraphicSelectionListener populateGridWithComponents(
      MigLayoutModel layout, List<ViewCanvas<E>> preservedViews) {
    GraphicSelectionListener graphicListener = null;

    for (MigCell cell : layout.getCells()) {
      Component component = createComponentForCell(cell, preservedViews);

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
   *
   * @param cell the layout cell
   * @param preservedViews list of preserved ViewCanvas instances
   * @return the created or reused component
   */
  private Component createComponentForCell(MigCell cell, List<ViewCanvas<E>> preservedViews) {
    boolean isView2d = isViewType(view2dClass, cell.type());

    if (isView2d) {
      return createViewCanvasForCell(cell, preservedViews);
    } else {
      return createNonViewComponent(cell);
    }
  }

  /** Creates or reuses a ViewCanvas for the given cell. */
  private Component createViewCanvasForCell(MigCell cell, List<ViewCanvas<E>> preservedViews) {
    ViewCanvas<E> viewCanvas =
        preservedViews.isEmpty()
            ? createFreshViewCanvas(cell.type())
            : preservedViews.removeFirst();

    if (viewCanvas != null) {
      cellManager.addViewCanvas(cell.position(), viewCanvas);
      if (viewCanvas.getSeries() != null) {
        viewCanvas.getSeries().setOpen(true);
      }
      return viewCanvas.getJComponent();
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
   * Configures all views with mouse listeners, tiled mode settings, and fires layout events.
   *
   * @param graphicListener optional GraphicSelectionListener to register with views
   */
  private void configureViewsAndFireEvents(GraphicSelectionListener graphicListener) {
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();
    if (allViews.isEmpty()) {
      return;
    }

    selectedImagePane = allViews.getFirst();
    MouseActions mouseActions = eventManager.getMouseActions();
    boolean tiledMode = SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());

    for (int i = 0; i < allViews.size(); i++) {
      ViewCanvas<E> view = allViews.get(i);
      configureView(view, i, tiledMode, mouseActions, graphicListener);
    }

    selectedImagePane.setSelected(true);
    eventManager.updateComponentsListener(selectedImagePane);

    if (selectedImagePane.getSeries() instanceof Series) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(
              this, selectedImagePane.getSeries(), selectedImagePane.getImage(), EVENT.LAYOUT));
    }
  }

  /** Configures a single view with appropriate settings. */
  private void configureView(
      ViewCanvas<E> view,
      int index,
      boolean tiledMode,
      MouseActions mouseActions,
      GraphicSelectionListener graphicListener) {
    view.closeLens(); // Close lens because update does not work

    if (tiledMode) {
      view.setTileOffset(index);
      view.setSeries(selectedImagePane.getSeries(), null);
    }

    view.enableMouseAndKeyListener(mouseActions);

    if (graphicListener != null) {
      view.getGraphicManager().addGraphicSelectionListener(graphicListener);
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
    // Update selected pane reference if needed
    if (selectedImagePane == oldView) {
      selectedImagePane = newView;
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
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();
    if (allViews.isEmpty()) {
      return;
    }

    ensureSelectedImagePane(allViews);

    MouseActions mouseActions = eventManager.getMouseActions();
    boolean tiledMode = SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());

    configureViewsAfterReplacement(allViews, tiledMode, mouseActions);

    selectedImagePane.setSelected(true);
    eventManager.updateComponentsListener(selectedImagePane);
  }

  /** Ensures selectedImagePane is valid. */
  private void ensureSelectedImagePane(List<ViewCanvas<E>> allViews) {
    if (selectedImagePane == null) {
      selectedImagePane = allViews.getFirst();
    }
  }

  /** Configures all views with appropriate settings after replacement. */
  private void configureViewsAfterReplacement(
      List<ViewCanvas<E>> allViews, boolean tiledMode, MouseActions mouseActions) {
    for (int i = 0; i < allViews.size(); i++) {
      ViewCanvas<E> view = allViews.get(i);
      view.closeLens();

      if (tiledMode) {
        view.setTileOffset(i);
        view.setSeries(selectedImagePane.getSeries(), null);
      }

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
    updateMigLayoutContraints(currentLayout);

    // Iterate through cells and add components from cellManager
    for (MigCell cell : currentLayout.getCells()) {
      Component component = cellManager.getComponent(cell.position());
      if (component != null) {
        grid.add(component, cell.getFullConstraints());
      }
    }

    grid.revalidate();
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
    cellManager.getAllViewCanvases().forEach(v -> v.getJComponent().removeFocusListener(v));
  }

  /** Adds focus listeners back to all views. */
  private void addFocusListenersToViews() {
    cellManager.getAllViewCanvases().forEach(v -> v.getJComponent().addFocusListener(v));
  }

  /** Enters fullscreen mode by creating a modal dialog with the view. */
  private void enterFullscreenMode(
      ViewCanvas<E> viewCanvas, String title, Dialog existingDialog, boolean isDetached) {
    remove(grid);

    // Configure grid for single fullscreen view
    grid.setLayout(new net.miginfocom.swing.MigLayout("fill, ins 0", "[grow,fill]", "[grow,fill]"));
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
    rebuildGridLayout();
    addFocusListenersToViews();

    fullscreenDialog.removeAll();
    fullscreenDialog.dispose();

    add(grid, BorderLayout.CENTER);
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
          if (view2dClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          // Ignore exceptions and continue counting
        }
      }
    }
    return val;
  }

  public void setSelectedImagePaneFromFocus(ViewCanvas<E> viewCanvas) {
    setSelectedImagePane(viewCanvas);
  }

  public void setSelectedImagePane(ViewCanvas<E> viewCanvas) {
    if (this.selectedImagePane != null && this.selectedImagePane.getSeries() != null) {
      this.selectedImagePane.getSeries().setSelected(false, null);
      this.selectedImagePane.getSeries().setFocused(false);
    }

    if (viewCanvas != null && viewCanvas.getSeries() != null) {
      viewCanvas.getSeries().setSelected(true, viewCanvas.getImage());
      viewCanvas.getSeries().setFocused(eventManager.getSelectedView2dContainer() == this);
    }

    boolean newView = this.selectedImagePane != viewCanvas && viewCanvas != null;
    if (newView) {
      if (this.selectedImagePane != null) {
        this.selectedImagePane.setSelected(false);
      }
      viewCanvas.setSelected(true);
      this.selectedImagePane = viewCanvas;
      eventManager.updateComponentsListener(viewCanvas);
    }
    if (newView && viewCanvas.getSeries() instanceof Series) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(
              this, selectedImagePane.getSeries(), selectedImagePane.getImage(), EVENT.SELECT));
    }
    eventManager.fireSeriesViewerListeners(
        new SeriesViewerEvent(
            this, viewCanvas == null ? null : viewCanvas.getSeries(), null, EVENT.SELECT_VIEW));
  }

  /** Return the image in the image display panel. */
  public E getImage(int i) {
    ViewCanvas<E> viewCanvas = cellManager.getViewCanvasByIndex(i);
    if (viewCanvas != null) {
      return viewCanvas.getImage();
    }
    return null;
  }

  /** Return all the <code>ImagePanel</code>s. */
  public List<ViewCanvas<E>> getImagePanels() {
    return getImagePanels(false);
  }

  public List<ViewCanvas<E>> getImagePanels(boolean selectedImagePaneLast) {
    List<ViewCanvas<E>> viewList = new ArrayList<>(cellManager.getAllViewCanvases());
    if (selectedImagePaneLast) {
      ViewCanvas<E> selectedView = getSelectedImagePane();

      if (selectedView != null && viewList.size() > 1) {
        viewList.remove(selectedView);
        viewList.add(selectedView);
      }
      return viewList;
    }
    return viewList;
  }

  public ViewCanvas<E> getNextSelectedImagePane() {
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();
    for (int i = 0; i < allViews.size() - 1; i++) {
      if (allViews.get(i) == selectedImagePane) {
        return allViews.get(i + 1);
      }
    }
    return selectedImagePane;
  }

  public abstract List<SynchView> getSynchList();

  public abstract List<MigLayoutModel> getLayoutList();

  public Boolean isContainingView(ViewCanvas<?> view2DPane) {
    return cellManager.getAllViewCanvases().stream()
        .filter(v -> Objects.equals(v, view2DPane))
        .findFirst()
        .map(_ -> Boolean.TRUE)
        .orElse(Boolean.FALSE);
  }

  public SynchView getSynchView() {
    return synchView;
  }

  public void setSynchView(SynchView synchView) {
    this.synchView = Objects.requireNonNull(synchView);
    updateTileOffset();
    eventManager.updateAllListeners(this, synchView);
  }

  public void updateTileOffset() {
    boolean isTileMode = SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());

    if (isTileMode && selectedImagePane != null) {
      updateTileModeLayout();
    } else {
      resetTileOffsets();
    }
  }

  /** Updates layout in tile mode by distributing series across views. */
  private void updateTileModeLayout() {
    MediaSeries<E> series = findAvailableSeries();
    if (series == null) {
      return;
    }

    ViewCanvas<E> selectedView = findViewWithSeries(series);
    int seriesLimit = calculateSeriesLimit(series, selectedView);
    distributeSeriesAcrossViews(series, seriesLimit);
  }

  /** Finds an available series from selectedImagePane or other views. */
  private MediaSeries<E> findAvailableSeries() {
    if (selectedImagePane.getSeries() != null) {
      return selectedImagePane.getSeries();
    }

    return cellManager.getAllViewCanvases().stream()
        .map(ViewCanvas::getSeries)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /** Finds the view containing the given series. */
  private ViewCanvas<E> findViewWithSeries(MediaSeries<E> series) {
    return cellManager.getAllViewCanvases().stream()
        .filter(v -> v.getSeries() == series)
        .findFirst()
        .orElse(selectedImagePane);
  }

  /** Calculates the series limit based on filtered series size. */
  @SuppressWarnings("unchecked")
  private int calculateSeriesLimit(MediaSeries<E> series, ViewCanvas<E> view) {
    return series.size((Filter<E>) view.getActionValue(ActionW.FILTERED_SERIES.cmd()));
  }

  /** Distributes series across views up to the specified limit. */
  private void distributeSeriesAcrossViews(MediaSeries<E> series, int limit) {
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();
    for (int i = 0; i < allViews.size(); i++) {
      ViewCanvas<E> view = allViews.get(i);
      if (i < limit) {
        view.setTileOffset(i);
        view.setSeries(series, null);
      } else {
        view.setSeries(null, null);
      }
    }
  }

  /** Resets tile offsets to 0 for all views. */
  private void resetTileOffsets() {
    cellManager.getAllViewCanvases().forEach(v -> v.setTileOffset(0));
  }

  public synchronized void setMouseActions(MouseActions mouseActions) {
    if (mouseActions == null) {
      for (ViewCanvas<E> v : cellManager) {
        v.disableMouseAndKeyListener();
        // Let the possibility get the focus
        v.iniDefaultMouseListener();
      }
    } else {
      for (ViewCanvas<E> v : cellManager) {
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
          layouts.add(buildMigLayoutModel(scaledRows, scaledCols, view2dClass.getName()));
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

    if (SynchData.Mode.TILE.equals(synchView.getSynchData().getMode())) {
      addSeries(seriesList.getFirst());
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

    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();

    // Clear excess views if layout is larger than series list
    clearExcessViews(allViews, seriesList.size());

    // Reset to first view and add all series
    if (!allViews.isEmpty()) {
      setSelectedImagePane(allViews.getFirst());
      seriesList.forEach(this::addSeries);
    }
  }

  /** Clears views beyond the required count. */
  private void clearExcessViews(List<ViewCanvas<E>> views, int requiredCount) {
    if (views.size() > requiredCount) {
      setSelectedImagePane(views.get(requiredCount));
      for (int i = requiredCount; i < views.size(); i++) {
        ViewCanvas<E> viewPane = getSelectedImagePane();
        if (viewPane != null) {
          viewPane.setSeries(null, null);
        }
        getNextSelectedImagePane();
      }
    }
  }

  /** Adds series to available empty view slots, expanding layout if necessary. */
  private void addSeriesToAvailableSlots(List<MediaSeries<E>> seriesList) {
    int emptyViewCount = countEmptyViews();
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();

    // Expand layout if not enough empty views
    if (emptyViewCount < seriesList.size()) {
      int totalNeeded = allViews.size() + seriesList.size();
      changeLayoutModel(getBestDefaultViewLayout(totalNeeded));
    }

    // Add series to empty views
    int seriesIndex = 0;
    for (ViewCanvas<E> view : cellManager) {
      if (view.getSeries() == null && seriesIndex < seriesList.size()) {
        setSelectedImagePane(view);
        addSeries(seriesList.get(seriesIndex++));
      }
    }
  }

  /** Counts the number of empty views in the current layout. */
  private int countEmptyViews() {
    return (int)
        cellManager.getAllViewCanvases().stream().filter(v -> v.getSeries() == null).count();
  }

  public void selectLayoutPositionForAddingSeries(List<MediaSeries<E>> seriesList) {
    int nbSeriesToAdd = 1;
    if (seriesList != null) {
      nbSeriesToAdd = seriesList.size();
      if (nbSeriesToAdd < 1) {
        nbSeriesToAdd = 1;
      }
    }
    List<ViewCanvas<E>> allViews = cellManager.getAllViewCanvases();
    int pos = allViews.size() - nbSeriesToAdd;
    if (pos < 0) {
      pos = 0;
    }
    setSelectedImagePane(allViews.get(pos));
  }
}
