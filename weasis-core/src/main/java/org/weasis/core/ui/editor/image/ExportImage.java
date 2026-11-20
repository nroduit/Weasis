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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.print.CellFit;
import org.weasis.core.ui.print.PrintFonts;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * A non-interactive view used for printing and exporting images.
 *
 * <p>Captures the visual state of a source {@link ViewCanvas} (image operations, zoom, center,
 * rotation, annotations) and a copy of its graphics, then renders it off-screen. The source view is
 * never touched again: its graphic model, its labels and its display chain stay as they are.
 *
 * <p>The view works in logical pixels, as on a 96 dpi screen, and draws through a {@linkplain
 * #setRenderScale render scale}: the overlay, the labels and the strokes are scaled by Java2D, the
 * image is rendered by the display pipeline straight at the device resolution and copied pixel for
 * pixel, so the destination never resamples it.
 *
 * @param <E> the type of ImageElement
 */
public class ExportImage<E extends ImageElement> extends DefaultView2d<E> {

  /** The source view whose state is captured for export. */
  private final ViewCanvas<E> sourceView;

  /**
   * Holds the current {@link Graphics2D} context during {@link #draw(Graphics2D)} so that {@link
   * #getGraphics()} returns it correctly for sub-components.
   */
  private Graphics2D currentG2d;

  private double renderScale = 1.0;

  private boolean annotations;
  private DisplayProfile appliedProfile;
  private boolean fixedLayerFont;
  private boolean crosslines = true;
  // The kinds of drawings a display profile leaves out; null follows the layers of the source
  private Set<LayerType> hiddenGraphicLayers;

  /**
   * Creates an export view that captures the visual state of the given {@code sourceView}.
   *
   * @param sourceView the canvas whose image, operations, zoom, and annotations are copied
   */
  public ExportImage(ViewCanvas<E> sourceView) {
    this(sourceView, null);
  }

  /**
   * Creates an export view of {@code sourceView} showing what {@code profile} selects, or what the
   * source shows when it is {@code null}.
   */
  public ExportImage(ViewCanvas<E> sourceView, DisplayProfile profile) {
    super(sourceView.getEventManager(), null);
    this.sourceView = sourceView;

    // Remove the OpEventListener so that resizing/repaints do not reset operation parameters
    imageLayer.removeEventListener(imageLayer.getDisplayOpManager());

    setFont(FontItem.MINI.getFont());

    // Copy info layer settings (without global preferences so export is independent)
    this.infoLayer = sourceView.getInfoLayer().getLayerCopy(this, false);
    this.annotations = sourceView.getInfoLayer().getVisible();
    infoLayer.setVisible(annotations);
    infoLayer.setShowBottomScale(false);
    // Suppress interactive-only items in the export overlay
    infoLayer.setDisplayPreferencesValue(LayerItem.PIXEL, false);
    infoLayer.setDisplayPreferencesValue(LayerItem.PRELOADING_BAR, false);

    // Copy image pipeline operations from the source view
    SimpleOpManager operations = imageLayer.getDisplayOpManager();
    for (ImageOpNode op : sourceView.getImageLayer().getDisplayOpManager().getOperations()) {
      operations.addImageOperationAction(op.copy());
    }

    // Copy all action states (zoom, window/level, rotation, …)
    sourceView.copyActionWState(actionsInView);

    setPreferredSize(new Dimension(1024, 1024));
    ViewModel model = sourceView.getViewModel();
    Rectangle2D canvas =
        new Rectangle2D.Double(
            0, 0, sourceView.getJComponent().getWidth(), sourceView.getJComponent().getHeight());
    actionsInView.put("origin.image.bound", canvas);
    actionsInView.put("origin.zoom", sourceView.getActionValue(ActionW.ZOOM.cmd()));
    actionsInView.put(
        "origin.center.offset",
        new Point2D.Double(model.getModelOffsetX(), model.getModelOffsetY()));

    imageLayer.setVisible(sourceView.getImageLayer().getVisible());
    if (profile != null) {
      applyProfile(profile);
    }

    // Do not use setSeries() because the view will be reset
    this.series = sourceView.getSeries();
    setImage(sourceView.getImage());
    copyPreprocessing();
  }

  /**
   * Applies the preprocessing of the source view, such as the crop of a presentation state's
   * displayed area or the rectified aspect ratio: setting the image drops it.
   */
  private void copyPreprocessing() {
    if (sourceView.getActionValue(ActionW.PREPROCESSING.cmd()) instanceof SimpleOpManager source) {
      SimpleOpManager preprocessing = source.copy();
      actionsInView.put(ActionW.PREPROCESSING.cmd(), preprocessing);
      // Keeps the graphics in the coordinates of the whole image
      imageLayer.setOffset(sourceView.getImageLayer().getOffset());
      imageLayer.setPreprocessing(preprocessing);
      updateCanvas(false);
    }
  }

  /**
   * Shows what {@code profile} selects, or what the source view shows when it is {@code null}. It
   * can be changed between two renderings, as the print dialog does for its preview.
   */
  public void setDisplayProfile(DisplayProfile profile) {
    if (profile == null && appliedProfile == null) {
      return;
    }
    DisplayProfile next = profile == null ? DisplayProfile.of(sourceView) : profile;
    if (!next.equals(appliedProfile)) {
      // The display operations run with the next rendering
      applyProfile(next);
    }
  }

  /** Shows what {@code selection} gives for the source view; see {@link #setDisplayProfile}. */
  public void applyDisplay(Function<ViewCanvas<?>, DisplayProfile> selection) {
    setDisplayProfile(selection == null ? null : selection.apply(sourceView));
  }

  private void applyProfile(DisplayProfile profile) {
    appliedProfile = profile;
    annotations = profile.annotations();
    imageLayer.setVisible(profile.image());
    SimpleOpManager operations = imageLayer.getDisplayOpManager();
    operations.setParamValue(
        DisplayProfile.OVERLAY_OP, DisplayProfile.OVERLAY_SHOW, profile.overlay());
    operations.setParamValue(
        DisplayProfile.SHUTTER_OP, DisplayProfile.SHUTTER_SHOW, profile.shutter());
    operations.setParamValue(
        WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), profile.pixelPadding());
    infoLayer.setVisible(profile.annotations());
    for (LayerItem item : DisplayProfile.CAPTURE_ITEMS) {
      infoLayer.setDisplayPreferencesValue(item, profile.annotationItems().contains(item));
    }
    actionsInView.put(ActionW.DRAWINGS.cmd(), profile.drawings());
    crosslines = profile.crosslines();
    hiddenGraphicLayers = profile.hiddenGraphicLayers();
  }

  /**
   * The graphics of the source view are copied into a model of this view: labels are laid out for
   * this geometry without moving those of the source, and nothing is registered on the source
   * model. Graphics on hidden layers are copied on a hidden layer.
   *
   * <p>Another image than the one the source shows, as a frame of a printed series, takes its own
   * drawings and what the source view derives for it (segmentation contours), not those of the
   * displayed image.
   */
  @Override
  public void updateGraphicManager(E img, boolean updateGraphics) {
    if (!updateGraphics) {
      return;
    }
    if (img == null || img == sourceView.getImage()) {
      graphicManager = copyForRendering(sourceView.getGraphicManager());
    } else {
      // The derived graphics kept in the model date from the last display of the image
      GraphicModel own =
          copyForRendering(
              (GraphicModel) img.getTagValue(TagW.PresentationModel),
              sourceView.getGraphicManager(),
              LayerType.DICOM_SEG);
      List<Graphic> graphics = new ArrayList<>(own.getModels());
      Map<LayerType, GraphicLayer> layers = new EnumMap<>(LayerType.class);
      for (Graphic derived : sourceView.getDerivedGraphics(img)) {
        if (hidden(sourceView.getGraphicManager(), derived.getLayerType())) {
          continue;
        }
        derived.setLayer(layers.computeIfAbsent(derived.getLayerType(), DefaultLayer::new));
        graphics.add(derived);
      }
      own.setModels(graphics);
      graphicManager = own;
    }
  }

  static GraphicModel copyForRendering(GraphicModel source) {
    return copyForRendering(source, source, null);
  }

  /**
   * @param visibility the model whose layers say what is shown: a layer of {@code source} hidden in
   *     it, by its type, is left out
   * @param skipped a layer type to leave out, or {@code null}
   */
  static GraphicModel copyForRendering(
      GraphicModel source, GraphicModel visibility, LayerType skipped) {
    XmlGraphicModel copy = new XmlGraphicModel();
    if (source == null) {
      return copy;
    }
    Map<GraphicLayer, GraphicLayer> layers = new HashMap<>();
    List<Graphic> graphics = new ArrayList<>();
    for (Graphic graphic : source.getModels()) {
      GraphicLayer layer = graphic.getLayer();
      if (layer == null || layer.getType() == skipped) {
        continue;
      }
      Graphic copied = graphic.copy();
      // A hidden layer is copied hidden: a display profile may show its kind of drawing
      copied.setLayer(
          layers.computeIfAbsent(
              layer,
              l -> {
                GraphicLayer c = copyLayer(l);
                if (visibility != source && hidden(visibility, l.getType())) {
                  c.setVisible(false);
                }
                return c;
              }));
      graphics.add(copied);
    }
    copy.setModels(graphics);
    return copy;
  }

  private static boolean hidden(GraphicModel model, LayerType type) {
    return model != null
        && model.getLayers().stream()
            .anyMatch(l -> l.getType() == type && !LangUtil.nullToTrue(l.getVisible()));
  }

  private static GraphicLayer copyLayer(GraphicLayer layer) {
    DefaultLayer copy = new DefaultLayer(layer.getType());
    copy.setName(layer.getName());
    copy.setLevel(layer.getLevel());
    copy.setVisible(LangUtil.nullToTrue(layer.getVisible()));
    copy.setLocked(layer.getLocked());
    copy.setSelectable(layer.getSelectable());
    copy.setSerializable(layer.getSerializable());
    return copy;
  }

  /** What the source view showed, for fitting this view into a cell. */
  public CellFit.SourceGeometry getSourceGeometry() {
    Rectangle2D canvas = (Rectangle2D) actionsInView.get("origin.image.bound");
    Double zoom = (Double) actionsInView.get("origin.zoom");
    Point2D center = (Point2D) actionsInView.get("origin.center.offset");
    PlanarImage img = getSourceImage();
    E image = getImage();
    double width = img == null ? 512 : img.width() * (image == null ? 1.0 : image.getRescaleX());
    double height = img == null ? 512 : img.height() * (image == null ? 1.0 : image.getRescaleY());
    Integer rotation = (Integer) actionsInView.get(ActionW.ROTATION.cmd());
    return new CellFit.SourceGeometry(
        canvas,
        zoom == null ? 0.0 : zoom,
        center == null ? new Point2D.Double() : center,
        width,
        height,
        rotation == null ? 0 : rotation);
  }

  /** Shows the overlay when asked for and the source view, or the display profile, shows it. */
  public void showAnnotations(boolean show) {
    infoLayer.setVisible(show && annotations);
  }

  /**
   * Prepares this view to fill a cell: its logical size and zoom from {@code fit}, its image
   * rendered at {@code scale} device pixels per logical pixel with an interpolation chosen for the
   * resulting magnification, and its overlay in the base font instead of the one the screen
   * heuristic picks for a small view. The display operations run once, with every parameter set.
   */
  public void configureForPrint(CellFit fit, double scale) {
    this.fixedLayerFont = true;
    infoLayer.setMinimumSizeChecked(false);
    boolean enabled = imageLayer.isEnableDispOperations();
    imageLayer.setEnableDispOperations(false);
    try {
      setRenderScale(scale);
      setSize(fit.viewportWidth(), fit.viewportHeight());
      double magnification = fit.zoom() * scale;
      getDisplayOpManager()
          .setParamValue(
              AffineTransformOp.OP_NAME,
              AffineTransformOp.P_INTERPOLATION,
              magnification > 1.0 ? Interpolation.BICUBIC : Interpolation.BILINEAR);
      zoom(fit.zoom());
      if (fit.bestFit()) {
        center();
      } else {
        setCenter(fit.center().getX(), fit.center().getY());
      }
    } finally {
      imageLayer.setEnableDispOperations(enabled);
    }
    updateAffineTransform();
  }

  /**
   * Device pixels per logical pixel. The image is rendered at that resolution; everything else is
   * scaled by Java2D when drawn.
   */
  public void setRenderScale(double renderScale) {
    if (renderScale <= 0) {
      throw new IllegalArgumentException("Render scale must be positive");
    }
    if (this.renderScale != renderScale) {
      this.renderScale = renderScale;
      updateAffineTransform();
    }
  }

  @Override
  protected double getRenderScale() {
    return renderScale;
  }

  @Override
  public Font getLayerFont() {
    return fixedLayerFont
        ? PrintFonts.of(FontItem.DEFAULT_SEMIBOLD.getFont())
        : super.getLayerFont();
  }

  @Override
  public void drawLayers(
      Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
    if (crosslines && hiddenGraphicLayers == null) {
      super.drawLayers(g2d, transform, inverseTransform);
      return;
    }
    // Every graphic was copied, so what the profile leaves out is skipped here, not removed
    if (LangUtil.nullToTrue((Boolean) actionsInView.get(ActionW.DRAWINGS.cmd()))) {
      Object[] oldRenderingHints =
          GuiUtils.setRenderingHints(g2d, true, false, requiredTextAntialiasing());
      g2d.translate(0.5, 0.5);
      for (Graphic graphic : graphicManager.getAllGraphics()) {
        if (isShown(graphic.getLayer())) {
          graphic.paint(g2d, transform);
        }
      }
      g2d.translate(-0.5, -0.5);
      GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    }
  }

  private boolean isShown(GraphicLayer layer) {
    LayerType type = layer.getType();
    if (type == LayerType.CROSSLINES && !crosslines) {
      return false;
    }
    if (hiddenGraphicLayers != null && DisplayProfile.GRAPHIC_LAYERS.contains(type)) {
      return !hiddenGraphicLayers.contains(type);
    }
    return LangUtil.nullToTrue(layer.getVisible());
  }

  @Override
  public void disposeView() {
    disableMouseAndKeyListener();
    removeFocusListener(this);
    ToolTipManager.sharedInstance().unregisterComponent(this);
    imageLayer.removeLayerChangeListener(this);
    // The graphic model is a copy of this view: nothing to unregister from the source view
    setViewModel(null);
  }

  @Override
  public Graphics getGraphics() {
    if (currentG2d != null) {
      return currentG2d;
    }
    return super.getGraphics();
  }

  // The labels are computed with the calibration of the source view: a slide measures in the
  // pixels of its base level, not in those of the thumbnail this copy holds as its image
  @Override
  public MeasurableLayer getMeasurableLayer() {
    return sourceView.getMeasurableLayer();
  }

  @Override
  public void paintComponent(Graphics g) {
    if (g instanceof Graphics2D g2d) {
      draw(g2d);
    }
  }

  /**
   * Draws this view into {@code g2d}, whose unit is the device pixel: the view covers its logical
   * size times the render scale.
   */
  @Override
  public void draw(Graphics2D g2d) {
    currentG2d = g2d;
    AffineTransform oldTransform = g2d.getTransform();
    Stroke oldStroke = g2d.getStroke();
    Paint oldColor = g2d.getPaint();
    Font oldFont = g2d.getFont();

    if (renderScale != 1.0) {
      g2d.scale(renderScale, renderScale);
    }
    // Set font size according to the view size
    g2d.setFont(getLayerFont());
    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g2d, true, true, true);

    // Lay out the labels of the copied graphics for this view
    graphicManager.updateLabels(Boolean.TRUE, this);

    Point2D offset = getClipViewCoordinatesOffset();
    g2d.translate(offset.getX(), offset.getY());
    drawImage(g2d);
    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-offset.getX(), -offset.getY());

    if (infoLayer != null) {
      infoLayer.paint(g2d);
    }
    GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    g2d.setFont(oldFont);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
    g2d.setTransform(oldTransform);
    currentG2d = null;
  }

  /**
   * The image raster is at device resolution already: it is drawn under the inverse of the render
   * scale so that it lands pixel for pixel. Rounding noise in the composite transform is snapped
   * away, since a scale of 1.0000001 would still make Java2D resample.
   */
  @Override
  protected void drawImage(Graphics2D g2d) {
    if (renderScale == 1.0) {
      paintImage(g2d);
      return;
    }
    Graphics2D gi = (Graphics2D) g2d.create();
    try {
      gi.scale(1.0 / renderScale, 1.0 / renderScale);
      AffineTransform t = gi.getTransform();
      if (isUnitScale(t)) {
        gi.setTransform(
            new AffineTransform(
                1, 0, 0, 1, Math.round(t.getTranslateX()), Math.round(t.getTranslateY())));
      }
      paintImage(gi);
    } finally {
      gi.dispose();
    }
  }

  /** Paints the image as the source view does, through its tiles for a whole slide. */
  private void paintImage(Graphics2D g2d) {
    if (sourceView instanceof DefaultView2d<E> source) {
      source.drawImage(g2d, this);
    } else {
      imageLayer.drawImage(g2d);
    }
  }

  private static boolean isUnitScale(AffineTransform t) {
    double eps = 1e-6;
    return Math.abs(t.getScaleX() - 1.0) < eps
        && Math.abs(t.getScaleY() - 1.0) < eps
        && Math.abs(t.getShearX()) < eps
        && Math.abs(t.getShearY()) < eps;
  }

  @Override
  public void handleLayerChanged(ImageLayer<E> layer) {
    // No-op: export views do not react to live layer changes
  }

  @Override
  public void enableMouseAndKeyListener(MouseActions mouseActions) {
    // No-op: export views are not interactive
  }

  @Override
  public MouseActionAdapter getMouseAdapter(String command) {
    return null;
  }

  @Override
  public JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected) {
    return null;
  }

  @Override
  public JPopupMenu buildContextMenu(MouseEvent evt) {
    return null;
  }

  @Override
  public boolean hasValidContent() {
    return getSourceImage() != null;
  }

  /** Turns the double buffering of a component off for an off-screen paint. */
  public static boolean disableDoubleBuffering(JComponent c) {
    if (c == null) {
      return false;
    }
    c.setDoubleBuffered(false);
    return c.isDoubleBuffered();
  }

  public static void restoreDoubleBuffering(JComponent c, boolean wasBuffered) {
    if (c != null) {
      c.setDoubleBuffered(wasBuffered);
    }
  }
}
