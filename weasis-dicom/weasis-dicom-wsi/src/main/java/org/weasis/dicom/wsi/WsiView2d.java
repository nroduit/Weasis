/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.SpecialElementOverlay;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.sr.SRSpecialElement;
import org.weasis.dicom.viewer2d.InfoLayer;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.wsi.ann.AnnSpecialElement;
import org.weasis.dicom.wsi.ann.AnnSpecialElement.SlideAnnotations;
import org.weasis.dicom.wsi.ann.AnnotationIndex;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;
import org.weasis.dicom.wsi.model.PixelSpacing;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.dicom.wsi.source.DicomTileSource;
import org.weasis.dicom.wsi.sr.SlideReportOverlay;
import org.weasis.dicom.wsi.tile.TilePlan;
import org.weasis.dicom.wsi.view.AnnotationOverlay;
import org.weasis.dicom.wsi.view.OverviewMap;
import org.weasis.dicom.wsi.view.PanBounds;
import org.weasis.dicom.wsi.view.PanInertia;
import org.weasis.dicom.wsi.view.ScaleBar;
import org.weasis.dicom.wsi.view.ScaleSnap;
import org.weasis.dicom.wsi.view.TiledImageLayer;
import org.weasis.dicom.wsi.view.TiledImageLayer.PlanTarget;
import org.weasis.dicom.wsi.view.WsiMeasurableLayer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * A 2D view of the DICOM viewer that shows a slide from its tiles: the model space is the base
 * level of the slide, so the zoom, pan, rotation and flip of the 2D view, its graphics and its
 * measurement tools apply as for an image, and the measurements are calibrated by the base level
 * pixel spacing. The image layer of the view stays hidden; the overview, the magnification and a
 * scale bar are drawn over the tiles. A pan glides on when released with some speed and the slide
 * never leaves the view (see {@link PanBounds}); keys 1 – 9 zoom to the magnification presets and
 * the wheel snaps to the scales where a level is shown pixel for pixel. The zoom and the pan follow
 * {@link WsiSettings}.
 */
public class WsiView2d extends View2d {
  private static final Logger LOGGER = LoggerFactory.getLogger(WsiView2d.class);

  private static final int OVERVIEW_SIDE = 200;
  private static final int LABEL_SIDE = 600;
  private static final int SCALE_BAR_MAX = 160;
  private static final int BOX_MARGIN = 10;
  private static final int BOX_PADDING = 6;
  private static final int BAR_THICKNESS = 4;
  private static final Color BOX_BACKGROUND = new Color(0, 0, 0, 160);

  private final SlideSession session = new SlideSession(this::repaint, this::slideOpened);
  private final OverviewMouse overviewMouse = new OverviewMouse();
  private final MouseWheelListener wheelZoom = this::wheelZoom;
  private final PanTracker panTracker = new PanTracker();
  private final PanInertia inertia =
      new PanInertia(
          new PanInertia.Target() {
            @Override
            public void panBy(double dx, double dy) {
              double scale = viewScale();
              moveOrigin(-dx / scale, -dy / scale);
              repaint();
            }

            @Override
            public Point2D correction() {
              return panCorrection();
            }
          });
  private final Consumer<Boolean> animationListener = _ -> applyZoomSettings();
  private final Consumer<Integer> stepListener = _ -> applyZoomSettings();
  private OverviewMap overview;
  private volatile List<AnnSpecialElement> annotationElements; // null when stale
  private int focalPlane;
  private int opticalPath;
  private boolean overviewVisible = true;
  private boolean diagnosticsVisible = true;
  private double paintMs;
  private int slideWidth;

  public WsiView2d(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);
    // The image of the series is the thumbnail: the tiles are painted instead (drawImage)
    imageLayer.setVisible(false);
    // The ruler of the 2D view takes the pixel size of the thumbnail: the scale bar of the slide
    // replaces it, so this view keeps its own annotation settings
    infoLayer = new InfoLayer(this, false);
    infoLayer.setDisplayPreferencesValue(LayerItem.SCALE, false);
    addSlideListeners();
    // A zoom out at the edge may leave the slide out of the bounds
    getZoomAnimator().setIdleAction(inertia::settle);
    applyZoomSettings();
    Settings.addListener(WsiSettings.ZOOM_ANIMATION, animationListener);
    Settings.addListener(WsiSettings.ZOOM_STEP, stepListener);
  }

  private void applyZoomSettings() {
    getZoomAnimator().setDuration(WsiSettings.zoomDuration());
    getZoomAnimator().setWheelStep(WsiSettings.wheelStep());
  }

  // The 2D view removes every mouse listener when it binds the mouse actions
  @Override
  public void enableMouseAndKeyListener(MouseActions actions) {
    super.enableMouseAndKeyListener(actions);
    addSlideListeners();
  }

  private void addSlideListeners() {
    addMouseListener(overviewMouse);
    addMouseMotionListener(overviewMouse);
    addMouseListener(panTracker);
    addMouseMotionListener(panTracker);
    addMouseWheelListener(wheelZoom);
  }

  // A slide is one image: the wheel, which scrolls a series in the 2D view, zooms at the cursor
  // when the user bindings give it another action (the slide profile binds it to the zoom)
  private void wheelZoom(MouseWheelEvent e) {
    inertia.stop();
    if (session.layer().isEmpty()
        || ActionW.ZOOM.cmd().equals(eventManager.getMouseActions().getWheel())) {
      return;
    }
    getZoomAnimator().wheel(e, wheelAnchor(e));
  }

  @Override
  public boolean isSmoothZoom() {
    return true;
  }

  @Override
  public boolean isZoomAtCursor() {
    return Settings.get(WsiSettings.ZOOM_AT_CURSOR);
  }

  @Override
  protected double snapViewScale(double from, double to) {
    if (!Settings.get(WsiSettings.ZOOM_SNAP)) {
      return to;
    }
    return session.slide().map(s -> ScaleSnap.snap(from, to, ScaleSnap.nativeScales(s))).orElse(to);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    int preset = presetOfKey(e);
    double[] presets = WsiSettings.presets();
    if (preset >= 0 && preset < presets.length && baseMagnification().isPresent()) {
      inertia.stop();
      zoomToMagnification(presets[preset]);
      e.consume();
      return;
    }
    super.keyPressed(e);
  }

  // The keys pan the view too (arrows): back in view once released
  @Override
  public void keyReleased(KeyEvent e) {
    super.keyReleased(e);
    if (!inertia.isRunning() && session.layer().isPresent()) {
      inertia.settle();
    }
  }

  // Keys 1 – 9, on the main row or the keypad, without modifier: index of the preset, else -1
  private static int presetOfKey(KeyEvent e) {
    int modifiers =
        InputEvent.SHIFT_DOWN_MASK
            | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK
            | InputEvent.ALT_GRAPH_DOWN_MASK;
    if ((e.getModifiersEx() & modifiers) != 0) {
      return -1;
    }
    int code = e.getKeyCode();
    if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
      return code - KeyEvent.VK_1;
    }
    if (code >= KeyEvent.VK_NUMPAD1 && code <= KeyEvent.VK_NUMPAD9) {
      return code - KeyEvent.VK_NUMPAD1;
    }
    return -1;
  }

  // The pan that brings the slide back within the bounds, in view pixels
  private Point2D panCorrection() {
    Optional<Slide> slide = session.slide();
    if (slide.isEmpty() || getWidth() == 0 || getHeight() == 0) {
      return new Point2D.Double();
    }
    // The space of the view transform, as painted (drawImage)
    Point2D p = getClipViewCoordinatesOffset();
    Rectangle2D view = new Rectangle2D.Double(-p.getX(), -p.getY(), getWidth(), getHeight());
    Rectangle2D bounds =
        affineTransform
            .createTransformedShape(
                new Rectangle2D.Double(0, 0, slide.get().width(), slide.get().height()))
            .getBounds2D();
    return PanBounds.correction(bounds, view);
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
    super.setSeries(newSeries, selectedMedia);
    session.open(newSeries);
    showCarrierImage(newSeries);
  }

  // The graphics of the 2D view belong to its image, the thumbnail of the slide. A slide without
  // thumbnail has no image in its series: an element of its first pyramid instance carries them,
  // out of the series (never painted, the image layer is hidden; kept for the session only)
  private void showCarrierImage(MediaSeries<DicomImageElement> newSeries) {
    if (newSeries == null
        || getImage() != null
        || !(newSeries.getFirstSpecialElement() instanceof DicomSpecialElement special)) {
      return;
    }
    setImage(new DicomImageElement(special.getMediaReader(), 0));
  }

  /** Reads the slide again when instances of its series arrived after it opened. */
  public void refreshSlide() {
    session.refresh();
  }

  public boolean isOfSlide(MediaElement element) {
    return session.isOfSeries(element);
  }

  public Optional<Slide> slide() {
    return session.slide();
  }

  @Override
  public void disposeView() {
    Settings.removeListener(WsiSettings.ZOOM_ANIMATION, animationListener);
    Settings.removeListener(WsiSettings.ZOOM_STEP, stepListener);
    inertia.stop();
    session.close();
    super.disposeView();
  }

  private void slideOpened(Slide slide) {
    boolean resized = slide.width() != slideWidth;
    slideWidth = slide.width();
    // A slide read again may have fewer planes or paths than the ones shown
    focalPlane = Math.min(focalPlane, slide.base().focalPlanes() - 1);
    opticalPath = Math.min(opticalPath, slide.base().opticalPaths().size() - 1);
    session.layer().ifPresent(l -> l.setPlane(focalPlane, opticalPath));
    if (slide.base().pixelSpacing().isPresent()) {
      actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.MICROMETER);
    }

    if (resized) {
      // The model area was the thumbnail until the slide was read, then a level read late may
      // become the base: the slide is fitted again
      updateCanvas(false);
      // The 2D view stops at 1/12, far above the fit of a slide: down to one pixel for the slide
      ((DefaultViewModel) getViewModel())
          .setViewScaleMin(1.0 / Math.max(slide.width(), slide.height()));
      // Best fit whatever the zoom type of the view: a real size would use the thumbnail spacing
      zoom(getBestFitViewScale());
      center();
    }
    annotationElements = null;
    applyReportOverlays();
    repaint();
    // The toolbar shows the planes, paths and annotations of the slide once it is read
    notifyToolbar();
  }

  /** The focal plane shown, as an index from 0. */
  public int focalPlane() {
    return focalPlane;
  }

  /** The optical path shown, as an index in the paths of the slide. */
  public int opticalPath() {
    return opticalPath;
  }

  /**
   * Shows another focal plane and optical path of the slide, by indices from 0.
   *
   * @throws IllegalArgumentException when the slide has no such plane or path
   */
  public void setPlane(int focalPlane, int opticalPath) {
    Slide slide = session.slide().orElseThrow(() -> new IllegalStateException("No slide"));
    if (focalPlane < 0 || focalPlane >= slide.base().focalPlanes()) {
      throw new IllegalArgumentException(
          "The slide has " + slide.base().focalPlanes() + " focal plane(s): " + (focalPlane + 1));
    }
    if (opticalPath < 0 || opticalPath >= slide.base().opticalPaths().size()) {
      throw new IllegalArgumentException(
          "The slide has the optical paths " + slide.base().opticalPaths());
    }
    this.focalPlane = focalPlane;
    this.opticalPath = opticalPath;
    session.layer().ifPresent(l -> l.setPlane(focalPlane, opticalPath));
    repaint();
  }

  // The zoom of the 2D view ranges from 1/12 to 12: a slide needs a far smaller scale to be seen
  // whole, within the range of the view model set when it opened
  @Override
  public double adjustViewScale(double viewScale) {
    if (session.slide().isEmpty()) {
      return super.adjustViewScale(viewScale);
    }
    return Math.clamp(
        viewScale, getViewModel().getViewScaleMin(), getViewModel().getViewScaleMax());
  }

  @Override
  protected Rectangle getImageBounds(DicomImageElement img) {
    return session
        .slide()
        .map(s -> new Rectangle(0, 0, s.width(), s.height()))
        .orElseGet(() -> super.getImageBounds(img));
  }

  // A capture: the tiles through the transform of the off-screen copy of this view
  @Override
  protected void drawImage(Graphics2D g2d, DefaultView2d<DicomImageElement> export) {
    Optional<TiledImageLayer> layer = session.layer();
    if (layer.isEmpty()) {
      return;
    }
    Point2D p = export.getClipViewCoordinatesOffset();
    Rectangle2D view =
        new Rectangle2D.Double(-p.getX(), -p.getY(), export.getWidth(), export.getHeight());
    layer.get().paint(g2d, export.getAffineTransform(), view);
    paintAnnotations(g2d, export.getAffineTransform(), view);
  }

  @Override
  public void awaitDisplayed(long millis) {
    session.layer().ifPresent(l -> l.awaitPlanned(millis));
  }

  /** One indexed group of bulk annotations of the slide, with the element it belongs to. */
  public record AnnotationGroup(
      AnnSpecialElement element, int index, SlideAnnotations annotations) {
    public Group group() {
      return annotations.groups().get(index);
    }

    public boolean shown() {
      return element.isShown(index);
    }

    public void setShown(boolean shown) {
      element.setShown(index, shown);
    }
  }

  /** The bulk annotation objects of the patient that annotate the slide. */
  public List<AnnSpecialElement> annotationElements() {
    List<AnnSpecialElement> list = annotationElements;
    if (list == null) {
      list = List.of();
      Optional<Slide> slide = session.slide();
      MediaSeries<DicomImageElement> current = getSeries();
      if (slide.isPresent()
          && current != null
          && current.getTagValue(TagW.ExplorerModel) instanceof DicomModel model) {
        MediaSeriesGroup patient = model.getParent(current, DicomModel.patient);
        list =
            model.getSpecialElementsFromPatient(AnnSpecialElement.class, patient).stream()
                .filter(e -> e.appliesTo(slide.get()))
                .toList();
      }
      annotationElements = list;
    }
    return list;
  }

  /** The annotation groups of the slide, in the order of the objects and of their groups. */
  public List<AnnotationGroup> annotationGroups() {
    Optional<Slide> slide = session.slide();
    if (slide.isEmpty()) {
      return List.of();
    }
    List<AnnotationGroup> groups = new ArrayList<>();
    for (AnnSpecialElement element : annotationElements()) {
      element
          .annotations(slide.get(), this::repaint)
          .ifPresent(
              a -> {
                for (int i = 0; i < a.groups().size(); i++) {
                  groups.add(new AnnotationGroup(element, i, a));
                }
              });
    }
    return groups;
  }

  // A special element arrived or left: the annotations and reports of the slide may have changed
  @Override
  public void updateOverlays() {
    annotationElements = null;
    super.updateOverlays();
    applyReportOverlays();
    notifyToolbar();
  }

  // The 2D view attaches the reports of the image it shows (the thumbnail); a slide needs the
  // reports of its pyramid instances, brought to the base level
  @Override
  public void setImage(DicomImageElement img) {
    super.setImage(img);
    applyReportOverlays();
  }

  private void applyReportOverlays() {
    Optional<Slide> slide = session.slide();
    DicomImageElement image = getImage();
    MediaSeries<DicomImageElement> current = getSeries();
    if (slide.isEmpty()
        || image == null
        || current == null
        || !(current.getTagValue(TagW.ExplorerModel) instanceof DicomModel model)) {
      return;
    }
    MediaSeriesGroup patient = model.getParent(current, DicomModel.patient);
    List<SpecialElementOverlay> reports =
        model.getSpecialElementsFromPatient(SRSpecialElement.class, patient).stream()
            .<SpecialElementOverlay>map(sr -> new SlideReportOverlay(sr, slide.get()))
            .toList();
    SpecialElementOverlay.apply(graphicManager, image, LayerType.DICOM_SR, reports);
    repaint();
  }

  private void notifyToolbar() {
    if (SwingUtilities.getAncestorOfClass(ImageViewerPlugin.class, this)
        instanceof ImageViewerPlugin<?> container) {
      eventManager.fireSeriesViewerListeners(
          new SeriesViewerEvent(container, getSeries(), getImage(), EVENT.SELECT_VIEW));
    }
  }

  // The shown groups whose index is built, over the part of the slide the view shows
  private void paintAnnotations(Graphics2D g2d, AffineTransform modelToView, Rectangle2D view) {
    List<AnnotationGroup> groups = annotationGroups();
    if (groups.isEmpty()) {
      return;
    }
    List<AnnotationIndex> indexes = new ArrayList<>();
    for (AnnotationGroup group : groups) {
      List<AnnotationIndex> built = group.annotations().indexes();
      if (group.shown() && group.index() < built.size()) {
        indexes.add(built.get(group.index()));
      }
    }
    try {
      Rectangle2D viewport = modelToView.createInverse().createTransformedShape(view).getBounds2D();
      AnnotationOverlay.paint(
          g2d, modelToView, viewport, indexes, Settings.get(WsiSettings.ANNOTATIONS_MAX));
    } catch (NoninvertibleTransformException e) {
      LOGGER.debug("View transform cannot be inverted", e);
    }
  }

  /** The tile layer of the slide, once it is read. */
  public Optional<TiledImageLayer> layer() {
    return session.layer();
  }

  /** The point of the slide at the centre of the view, in base level pixels. */
  public Point2D viewCentre() {
    return viewToModel(getWidth() / 2.0, getHeight() / 2.0);
  }

  /**
   * Shows the slide at a scale and centred on a base level pixel, at once, and paints the view so
   * that the tiles it needs are planned.
   *
   * @param scale the view scale, null to keep it
   * @param centre the base level pixel at the centre of the view, null to keep it
   */
  public void goTo(Double scale, Point2D centre) {
    inertia.stop();
    if (scale != null) {
      zoom(scale);
    }
    if (centre != null) {
      centreOn(centre);
    }
    if (getWidth() > 0 && getHeight() > 0) {
      paintImmediately(0, 0, getWidth(), getHeight());
    }
  }

  // The model offset is the displacement of the view centre from the centre of the slide
  private void centreOn(Point2D model) {
    Rectangle2D area = getViewModel().getModelArea();
    setCenter(model.getX() - area.getWidth() / 2, model.getY() - area.getHeight() / 2);
  }

  @Override
  protected void drawImage(Graphics2D g2d) {
    Optional<TiledImageLayer> layer = session.layer();
    if (layer.isEmpty()) {
      return;
    }
    Point2D p = getClipViewCoordinatesOffset();
    long start = System.nanoTime();
    Rectangle2D view = new Rectangle2D.Double(-p.getX(), -p.getY(), getWidth(), getHeight());
    layer.get().paint(g2d, affineTransform, view, zoomTarget());
    paintAnnotations(g2d, affineTransform, view);
    double ms = (System.nanoTime() - start) / 1e6;
    paintMs = paintMs == 0 ? ms : paintMs * 0.9 + ms * 0.1;
  }

  // Where a running zoom ends: the view scaled at the anchor of the animation to its target
  private PlanTarget zoomTarget() {
    Optional<Double> target = getZoomAnimator().targetScale();
    if (target.isEmpty()) {
      return null;
    }
    double w = getWidth();
    double h = getHeight();
    Point2D anchor = getZoomAnimator().anchor();
    Point2D pivot =
        anchor == null
            ? viewToModel(w / 2, h / 2)
            : viewToModel(w / 2 + anchor.getX(), h / 2 + anchor.getY());
    Rectangle2D viewport = new Rectangle2D.Double();
    viewport.setFrameFromDiagonal(viewToModel(0.0, 0.0), viewToModel(w, h));
    viewport.add(viewToModel(w, 0.0));
    viewport.add(viewToModel(0.0, h));
    double ratio = viewScale() / target.get();
    return new PlanTarget(
        new Rectangle2D.Double(
            pivot.getX() + (viewport.getX() - pivot.getX()) * ratio,
            pivot.getY() + (viewport.getY() - pivot.getY()) * ratio,
            viewport.getWidth() * ratio,
            viewport.getHeight() * ratio),
        1.0 / target.get());
  }

  @Override
  public MeasurableLayer getMeasurableLayer() {
    return session
        .slide()
        .<MeasurableLayer>map(s -> new WsiMeasurableLayer(s, imageLayer::getSourceTagValue))
        .orElseGet(super::getMeasurableLayer);
  }

  @Override
  protected void drawOnTop(Graphics2D g2d) {
    super.drawOnTop(g2d);
    Optional<TiledImageLayer> layer = session.layer();
    if (layer.isEmpty()) {
      overview = null;
      return;
    }
    Graphics2D g = (Graphics2D) g2d.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      drawInfo(g, layer.get());
      drawScaleBar(g, layer.get().slide());
      overview = overviewVisible ? drawOverview(g, layer.get()) : null;
    } finally {
      g.dispose();
    }
  }

  // ---- Actions of the slide toolbar

  public void fitToView() {
    inertia.stop();
    getZoomAnimator().animateTo(getBestFitViewScale(), null, this::center);
  }

  public void oneToOne() {
    getZoomAnimator().animateTo(1.0, null, null);
  }

  /** Rotates by quarter turns clockwise, negative counterclockwise. */
  public void rotate(int quarterTurns) {
    int degrees = actionsInView.get(ActionW.ROTATION.cmd()) instanceof Integer d ? d : 0;
    int rotation = Math.floorMod(degrees + 90 * quarterTurns, 360);
    if (eventManager.getSelectedViewPane() == this) {
      eventManager.getAction(ActionW.ROTATION).ifPresent(a -> a.setSliderValue(rotation));
    } else {
      actionsInView.put(ActionW.ROTATION.cmd(), rotation);
      updateAffineTransform();
      repaint();
    }
  }

  public OptionalDouble baseMagnification() {
    return session.slide().map(Slide::magnification).orElseGet(OptionalDouble::empty);
  }

  /** Zooms to a nominal magnification (10 for 10×). */
  public void zoomToMagnification(double magnification) {
    baseMagnification()
        .ifPresent(base -> getZoomAnimator().animateTo(magnification / base, null, null));
  }

  /** Shows the label image of the slide, unless identity masking is on. */
  public void showLabel() {
    Optional<DicomTileSource> source = session.source();
    if (source.isEmpty()) {
      return;
    }
    // The label shows what is written on the glass, often the patient name or the case number
    if (IdentityMask.active().isPresent()) {
      JOptionPane.showMessageDialog(this, Messages.getString("WsiView.label_masked"));
      return;
    }
    if (source.get().slide().associatedImage(WsiImageType.LABEL).isEmpty()) {
      JOptionPane.showMessageDialog(this, Messages.getString("WsiView.no_label"));
      return;
    }
    CompletableFuture.supplyAsync(() -> readLabel(source.get()))
        .thenAccept(image -> SwingUtilities.invokeLater(() -> showLabelDialog(image)));
  }

  public void toggleOverview() {
    overviewVisible = !overviewVisible;
    repaint();
  }

  public void toggleDiagnostics() {
    diagnosticsVisible = !diagnosticsVisible;
    repaint();
  }

  private static BufferedImage readLabel(DicomTileSource source) {
    try {
      PlanarImage image = source.readAssociatedImage(WsiImageType.LABEL).orElse(null);
      if (image == null) {
        return null;
      }
      BufferedImage result = ImageConversion.toBufferedImage(image);
      image.release();
      return result;
    } catch (IOException e) {
      LOGGER.warn("Cannot read the label", e);
      return null;
    }
  }

  private void showLabelDialog(BufferedImage image) {
    if (image == null) {
      JOptionPane.showMessageDialog(this, Messages.getString("WsiView.no_label"));
      return;
    }
    double ratio =
        Math.min(1.0, LABEL_SIDE / (double) Math.max(image.getWidth(), image.getHeight()));
    Image scaled =
        image.getScaledInstance(
            (int) Math.round(image.getWidth() * ratio),
            (int) Math.round(image.getHeight() * ratio),
            Image.SCALE_SMOOTH);
    JOptionPane.showMessageDialog(
        this,
        new JLabel(new ImageIcon(scaled)),
        Messages.getString("WsiView.label_title"),
        JOptionPane.PLAIN_MESSAGE);
  }

  // ---- Overlays

  private double viewScale() {
    return getViewModel().getViewScale();
  }

  private void drawInfo(Graphics2D g, TiledImageLayer layer) {
    Slide slide = layer.slide();
    TilePlan plan = layer.plan();
    List<String> lines = new ArrayList<>();
    slide
        .magnification()
        .ifPresent(m -> lines.add(Messages.format("WsiView.magnification", m * viewScale())));
    if (slide.base().focalPlanes() > 1 || slide.base().opticalPaths().size() > 1) {
      lines.add(
          Messages.format(
              "WsiView.plane",
              focalPlane + 1,
              slide.base().focalPlanes(),
              slide.base().opticalPaths().get(opticalPath)));
    }
    if (diagnosticsVisible) {
      String diagnostics =
          Messages.format(
              "WsiView.diagnostics",
              plan.level(),
              slide.levels().size(),
              1.0 / viewScale(),
              layer.readyTiles(),
              plan.painted().size(),
              paintMs);
      lines.add(
          plan.truncated()
              ? diagnostics + " · " + Messages.getString("WsiView.truncated")
              : diagnostics);
    }
    // Above the bottom left corner annotations of the 2D view
    drawTextBox(g, lines, BOX_MARGIN, getHeight() / 2);
  }

  private void drawScaleBar(Graphics2D g, Slide slide) {
    Optional<PixelSpacing> spacing = slide.base().pixelSpacing();
    if (spacing.isEmpty()) {
      return;
    }
    Optional<ScaleBar> bar = ScaleBar.of(spacing.get().column() / viewScale(), SCALE_BAR_MAX);
    if (bar.isEmpty()) {
      return;
    }
    FontMetrics fm = g.getFontMetrics();
    int length = (int) Math.round(bar.get().lengthPx());
    int width = Math.max(length, fm.stringWidth(bar.get().label())) + 2 * BOX_PADDING;
    int height = fm.getHeight() + BAR_THICKNESS + 3 * BOX_PADDING;
    int x = getWidth() - width - BOX_MARGIN;
    int y = getHeight() / 2 - height / 2;
    g.setColor(BOX_BACKGROUND);
    g.fillRoundRect(x, y, width, height, 6, 6);
    g.setColor(Color.WHITE);
    g.fillRect(
        x + (width - length) / 2, y + height - BOX_PADDING - BAR_THICKNESS, length, BAR_THICKNESS);
    g.drawString(
        bar.get().label(),
        x + (width - fm.stringWidth(bar.get().label())) / 2,
        y + BOX_PADDING + fm.getAscent());
  }

  // The first line in a larger bold font, the box centred vertically on y
  private void drawTextBox(Graphics2D g, List<String> lines, int x, int y) {
    if (lines.isEmpty()) {
      return;
    }
    Font base = g.getFont();
    Font first = base.deriveFont(Font.BOLD, base.getSize2D() * 1.4f);
    int width = 0;
    int height = 2 * BOX_PADDING;
    for (int i = 0; i < lines.size(); i++) {
      FontMetrics fm = g.getFontMetrics(i == 0 ? first : base);
      width = Math.max(width, fm.stringWidth(lines.get(i)));
      height += fm.getHeight();
    }
    width += 2 * BOX_PADDING;
    int top = y - height / 2;
    g.setColor(BOX_BACKGROUND);
    g.fillRoundRect(x, top, width, height, 6, 6);
    g.setColor(Color.WHITE);
    int baseline = top + BOX_PADDING;
    for (int i = 0; i < lines.size(); i++) {
      g.setFont(i == 0 ? first : base);
      FontMetrics fm = g.getFontMetrics();
      baseline += fm.getAscent();
      g.drawString(lines.get(i), x + BOX_PADDING, baseline);
      baseline += fm.getDescent() + fm.getLeading();
    }
    g.setFont(base);
  }

  private OverviewMap drawOverview(Graphics2D g, TiledImageLayer layer) {
    Optional<BufferedImage> thumbnail = layer.thumbnail();
    if (thumbnail.isEmpty()) {
      return null;
    }
    Slide slide = layer.slide();
    OverviewMap map = OverviewMap.of(getWidth(), slide.width(), slide.height(), OVERVIEW_SIDE);
    Rectangle box = map.box();
    g.setColor(BOX_BACKGROUND);
    g.fillRect(box.x - 3, box.y - 3, box.width + 6, box.height + 6);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(thumbnail.get(), box.x, box.y, box.width, box.height, null);
    double w = getWidth();
    double h = getHeight();
    g.setColor(Color.YELLOW);
    g.setStroke(new BasicStroke(2f));
    // Zoomed out, the view shows more than the slide: the outline stops at the overview
    g.clip(new Rectangle(box.x - 1, box.y - 1, box.width + 2, box.height + 2));
    g.draw(
        map.outline(
            viewToModel(0.0, 0.0), viewToModel(w, 0.0), viewToModel(w, h), viewToModel(0.0, h)));
    return map;
  }

  // A drag of the button bound to the pan action: glides on when released with some speed
  private class PanTracker extends MouseAdapter {
    private boolean tracking;

    @Override
    public void mousePressed(MouseEvent e) {
      inertia.stop();
      // A press in the overview is consumed by it (added before)
      tracking = !e.isConsumed() && session.layer().isPresent() && isPanButton(e);
      if (tracking) {
        inertia.begin(e.getX(), e.getY());
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (tracking) {
        inertia.track(e.getX(), e.getY());
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (tracking) {
        tracking = false;
        if (Settings.get(WsiSettings.PAN_INERTIA)) {
          inertia.release();
        } else {
          inertia.settle();
        }
      }
    }

    private boolean isPanButton(MouseEvent e) {
      MouseActions actions = eventManager.getMouseActions();
      String action =
          switch (e.getButton()) {
            case MouseEvent.BUTTON1 -> actions.getLeft();
            case MouseEvent.BUTTON2 -> actions.getMiddle();
            case MouseEvent.BUTTON3 -> actions.getRight();
            default -> null;
          };
      return ActionW.PAN.cmd().equals(action);
    }
  }

  // A click or a drag in the overview centres the view on that place of the slide
  private class OverviewMouse extends MouseAdapter {
    private boolean dragging;

    @Override
    public void mousePressed(MouseEvent e) {
      dragging = overview != null && overview.contains(e.getPoint());
      if (dragging) {
        centreOn(e);
        e.consume();
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (dragging) {
        centreOn(e);
        e.consume();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (dragging) {
        dragging = false;
        e.consume();
      }
    }

    // Outside the overview, a double click zooms in twice at the cursor
    @Override
    public void mouseClicked(MouseEvent e) {
      boolean inOverview = overview != null && overview.contains(e.getPoint());
      if (e.getClickCount() == 2
          && SwingUtilities.isLeftMouseButton(e)
          && !inOverview
          && session.layer().isPresent()) {
        getZoomAnimator().zoomBy(2.0, wheelAnchor(e));
      }
    }

    private void centreOn(MouseEvent e) {
      WsiView2d.this.centreOn(overview.toModel(e.getPoint()));
      repaint();
    }
  }
}
