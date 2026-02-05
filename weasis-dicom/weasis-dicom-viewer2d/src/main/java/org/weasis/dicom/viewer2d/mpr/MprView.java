/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4d;
import org.opencv.core.Point3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.editor.image.SynchCineEvent;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.Pair;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mpr.MprController.ControlPoints;
import org.weasis.dicom.viewer2d.mpr.cmpr.CurvedMprFactory;
import org.weasis.dicom.viewer2d.mpr.cmpr.CurvedMprImageIO;

public class MprView extends View2d implements SliceCanvas {
  private static final Logger LOGGER = LoggerFactory.getLogger(MprView.class);

  public static final String SHOW_CROSS_CENTER = "show.cross.center";
  public static final String HIDE_CROSSLINES = "hide.crosslines";

  public enum Plane {
    AXIAL(2), // Normal along Z axis
    CORONAL(1), // Normal along Y axis
    SAGITTAL(0); // Normal along X axis

    private final int axisIndex;

    Plane(int axisIndex) {
      this.axisIndex = axisIndex;
    }

    public int axisIndex() {
      return axisIndex;
    }

    public Vector3d getDirection() {
      return new Vector3d().setComponent(axisIndex, 1);
    }

    public static Plane getPlane(int axisIndex) {
      for (Plane plane : MprView.Plane.values()) {
        if (plane.axisIndex == axisIndex) {
          return plane;
        }
      }
      return AXIAL;
    }
  }

  private JProgressBar progressBar;
  private Plane plane;

  protected final MprController mprController;

  public MprView(
      ImageViewerEventManager<DicomImageElement> eventManager, MprController controller) {
    super(eventManager);
    this.plane = Plane.AXIAL;
    this.mprController = Objects.requireNonNull(controller);
    infoLayer.setDisplayPreferencesValue(LayerItem.PRELOADING_BAR, false);

    // Remove PR and KO buttons
    getViewButtons().clear();
    getViewButtons().add(buildMprButton());
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
    actionsInView.put("weasis.contextmenu.close", false);
    /*
     * Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
     * head direction) This option may not be changed. Sorting stack must be disabled from menu in UI.
     */
    actionsInView.put(ActionW.SORT_STACK.cmd(), SortSeriesStack.slicePosition);
    actionsInView.put(LayerType.CROSSLINES.name(), true);
    actionsInView.put(SHOW_CROSS_CENTER, false);
    // Propagate the preset
    OpManager disOp = getDisplayOpManager();
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
    // disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);
  }

  @Override
  public synchronized void iniDefaultMouseListener() {
    super.iniDefaultMouseListener();
    if (mprController != null) {
      mprController.initListeners(this);
    }
  }

  @Override
  public Plane getPlane() {
    return plane;
  }

  public MprController getMprController() {
    return mprController;
  }

  protected MprAxis getMprAxis() {
    if (mprController == null) {
      return null;
    }
    return mprController.getMprAxis(plane);
  }

  @Override
  protected void setImage(DicomImageElement img) {
    if (img == null) {
      imageLayer.setImage(null, null);
    }
    Object oldZoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
    setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
    super.setImage(img);
    setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, oldZoomType);
  }

  public void setType(Plane plane) {
    this.plane = plane == null ? Plane.AXIAL : plane;
    if (mprController != null) {
      mprController.initListeners(this);
    }
  }

  public Point3 getVolumeCoordinatesFromMouse(int x, int y) {
    if (mprController.getVolume() == null) {
      return null;
    }
    Vector3d crossHair = mprController.getAxesControl().getCenterForCanvas(this);
    Vector3d coordinates = getVolumeCoordinatesFromMouse(x, y, crossHair);
    Vector3d volumeCoordinates = coordinates.mul(mprController.getVolume().getSliceSize());
    return new Point3(volumeCoordinates.x, volumeCoordinates.y, volumeCoordinates.z);
  }

  public Point2D getPlaneCoordinatesFromMouse(int x, int y) {
    if (mprController.getVolume() == null) {
      return null;
    }
    int size = mprController.getVolume().getSliceSize();
    Point2D pt = super.getImageCoordinatesFromMouse(x, y);
    pt.setLocation(pt.getX() / size, pt.getY() / size);
    return pt;
  }

  public Vector3d getVolumeCoordinatesFromMouse(int x, int y, Vector3d crossHair) {
    Point2D pt = getPlaneCoordinatesFromMouse(x, y);
    return getVolumeCoordinates(new Vector3d(pt.getX(), pt.getY(), crossHair.z));
  }

  /**
   * Convert image coordinates to volume coordinates.
   * Use this when you have coordinates from a graphic (which are in image space),
   * not mouse/screen coordinates.
   * 
   * Note: The MPR uses a cubic texture (sliceSize³) for rendering, but the actual
   * volume may be non-cubic (e.g., 640x640x218). This method returns coordinates
   * in actual voxel space.
   */
  public Point3 getVolumeCoordinatesFromImage(double imgX, double imgY) {
    if (mprController.getVolume() == null) {
      return null;
    }
    Volume<?> volume = mprController.getVolume();
    int sliceSize = volume.getSliceSize();
    Vector3i volSize = volume.getSize();
    Vector3d voxelRatio = volume.getVoxelRatio();
    
    // The volume is centered in the sliceSize×sliceSize image.
    // The rendered extent along each axis is volSize * voxelRatio.
    // The centering offset is (sliceSize - volSize*voxelRatio) / 2.
    double offsetX = (sliceSize - volSize.x * voxelRatio.x) / 2.0;
    double offsetY = (sliceSize - volSize.y * voxelRatio.y) / 2.0;
    double offsetZ = (sliceSize - volSize.z * voxelRatio.z) / 2.0;
    
    // Get the raw center position (not canvas-transformed) for the depth axis
    Vector3d rawCenter = mprController.getAxesControl().getCenter();
    
    // Convert image pixel coordinate to raw voxel index:
    // voxelIndex = (imgCoord - offset) / voxelRatio
    double voxelX, voxelY, voxelZ;
    switch (plane) {
      case AXIAL -> {
        // Axial: image X->volume X, image Y->volume Y, depth->volume Z
        voxelX = (imgX - offsetX) / voxelRatio.x;
        voxelY = (imgY - offsetY) / voxelRatio.y;
        voxelZ = rawCenter.z * volSize.z;
      }
      case CORONAL -> {
        // Coronal: image X->volume X, image Y->volume Z, depth->volume Y
        voxelX = (imgX - offsetX) / voxelRatio.x;
        voxelZ = (imgY - offsetZ) / voxelRatio.z;
        voxelY = rawCenter.y * volSize.y;
      }
      case SAGITTAL -> {
        // Sagittal: image X->volume Y, image Y->volume Z, depth->volume X
        voxelY = (imgX - offsetY) / voxelRatio.y;
        voxelZ = (imgY - offsetZ) / voxelRatio.z;
        voxelX = rawCenter.x * volSize.x;
      }
      default -> {
        voxelX = (imgX - offsetX) / voxelRatio.x;
        voxelY = (imgY - offsetY) / voxelRatio.y;
        voxelZ = rawCenter.z * volSize.z;
      }
    }
    
    return new Point3(voxelX, voxelY, voxelZ);
  }

  public Vector3d getVolumeCoordinates(Vector3d planePosition) {
    Vector3d p = new Vector3d(planePosition);
    transform(getDisplayPointToTexturePointMatrix(), p);
    return p;
  }

  protected Matrix4d getDisplayPointToTexturePointMatrix() {
    AxesControl axes = mprController.getAxesControl();
    Vector3d center = new Vector3d(0.5, 0.5, 0.5);

    Matrix4d resultMatrix = new Matrix4d();
    Quaterniond r = mprController.getRotation(plane);
    Vector3d crossHairOffset = axes.getCenter().sub(center);
    Vector3d t = new Vector3d(center).add(crossHairOffset);
    resultMatrix.translate(t.x, t.y, t.z).rotate(r).translate(-t.x, -t.y, -t.z);

    switch (plane) {
      case CORONAL -> resultMatrix.rotateX(-Math.toRadians(90)).scale(1.0, -1.0, 1.0);
      case SAGITTAL -> resultMatrix.rotateY(Math.toRadians(90)).rotateZ(Math.toRadians(90));
    }
    return resultMatrix;
  }

  public static Vector3d transform(Matrix4d m, Vector3d point) {
    Vector4d dest = new Vector4d(point, 1);
    m.transform(dest);
    point.x = dest.x;
    point.y = dest.y;
    point.z = dest.z;
    return point;
  }

  @Override
  public Rectangle2D getImageViewBounds(double viewportWidth, double viewportHeight) {
    return super.getImageViewBounds(viewportWidth, viewportHeight);
  }

  @Override
  public double getBestFitViewScale() {
    if (mprController == null) {
      return 1.0;
    }
    return mprController.getBestFitViewScale();
  }

  @Override
  public void reset() {
    super.reset();
    if (mprController != null) {
      mprController.reset();
      eventManager.updateComponentsListener(this);
    }
  }

  @Override
  public JPopupMenu buildContextMenu(final MouseEvent evt) {
    LOGGER.info("MprView.buildContextMenu called at ({}, {})", evt.getX(), evt.getY());
    ComboItemListener<SeriesComparator<?>> action =
        eventManager.getAction(ActionW.SORT_STACK).orElse(null);
    JPopupMenu ctx;
    if (action != null && action.isActionEnabled()) {
      // Force to disable sort stack menu
      action.enableAction(false);
      ctx = super.buildContextMenu(evt);
      action.enableAction(true);
    } else {
      ctx = super.buildContextMenu(evt);
    }

    // Add "Generate Curved MPR" option if a polyline is under the cursor
    addCurvedMprMenuItem(ctx, evt);
    return ctx;
  }

  private void addCurvedMprMenuItem(JPopupMenu popupMenu, MouseEvent evt) {
    LOGGER.info("addCurvedMprMenuItem called");
    if (popupMenu == null) {
      LOGGER.warn("popupMenu is null");
      return;
    }
    if (mprController == null) {
      LOGGER.warn("mprController is null");
      return;
    }
    if (mprController.getVolume() == null) {
      LOGGER.warn("volume is null");
      return;
    }

    // Log all graphics in the manager
    List<Graphic> allGraphics = getGraphicManager().getAllGraphics();
    LOGGER.info("Total graphics in manager: {}", allGraphics.size());
    for (Graphic g : allGraphics) {
      LOGGER.info("  Graphic: {} complete={} class={}", g, g.isGraphicComplete(), g.getClass().getSimpleName());
    }

    // Try to find graphic under the cursor (like "Remove this point" does)
    PolylineGraphic polyline = null;
    Point2D imageCoords = getImageCoordinatesFromMouse(evt.getX(), evt.getY());
    LOGGER.info("Mouse at screen ({}, {}), image coords: {}", evt.getX(), evt.getY(), imageCoords);
    
    MouseEventDouble mouseEvt = new MouseEventDouble(
        this, MouseEvent.MOUSE_RELEASED, evt.getWhen(), 16, 0, 0, 0, 0, 1, true, 1);
    mouseEvt.setSource(this);
    mouseEvt.setImageCoordinates(imageCoords);
    
    java.util.Optional<Graphic> graphicUnderCursor = 
        getGraphicManager().getFirstGraphicIntersecting(mouseEvt);
    LOGGER.info("Graphic under cursor: {}", graphicUnderCursor.orElse(null));
    
    if (graphicUnderCursor.isPresent()) {
      Graphic g = graphicUnderCursor.get();
      LOGGER.info("Found graphic: {} isPolyline={} isComplete={}", 
          g.getClass().getSimpleName(), 
          g instanceof PolylineGraphic, 
          g.isGraphicComplete());
      if (g instanceof PolylineGraphic pl && pl.isGraphicComplete()) {
        polyline = pl;
      }
    }

    if (polyline != null) {
      LOGGER.info("Adding Curved MPR menu item for polyline with {} points", polyline.getHandlePointList().size());
      final PolylineGraphic finalPolyline = polyline;
      popupMenu.addSeparator();
      JMenuItem curvedMprItem = new JMenuItem("Generate Curved MPR");
      curvedMprItem.addActionListener(e -> {
        List<Point2D> pts = finalPolyline.getHandlePointList();
        if (pts.size() >= 2) {
          java.util.List<org.joml.Vector3d> points3D = new java.util.ArrayList<>();
          for (Point2D pt : pts) {
            if (pt != null) {
              // Use getVolumeCoordinatesFromImage since polyline points are in image coords
              Point3 volCoord = getVolumeCoordinatesFromImage(pt.getX(), pt.getY());
              if (volCoord != null) {
                points3D.add(new org.joml.Vector3d(volCoord.x, volCoord.y, volCoord.z));
              }
            }
          }
          if (points3D.size() >= 2) {
            CurvedMprFactory.openCurvedMpr(this, points3D);
          }
        }
      });
      popupMenu.add(curvedMprItem);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    super.propertyChange(evt);

    final String command = evt.getPropertyName();
    if (command.equals(ActionW.SYNCH.cmd())) {
      SynchEvent synch = (SynchEvent) evt.getNewValue();
      if (synch.getView() == this && synch instanceof SynchCineEvent cineEvent) {
        if (LangUtil.getNULLtoTrue((Boolean) actionsInView.get(LayerType.CROSSLINES.name()))) {
          // Compute crossline from the location of selected image
          Number location = cineEvent.getLocation();
          if (location != null) {
            computeCrosslines(location.doubleValue());
          }
        }
      }
    }

    // if (series == null) {
    // return;
    // }
    // final String name = evt.getPropertyName();
    // if (name.equals(ActionW.SYNCH.cmd())) {
    // SynchEvent synch = (SynchEvent) evt.getNewValue();
    // SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
    // if (synchData != null && Mode.None.equals(synchData.getMode())) {
    // return;
    // }
    // for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
    // final String command = entry.getKey();
    // final Object val = entry.getValue();
    // if (synchData != null && !synchData.isActionEnable(command)) {
    // continue;
    // }
    //
    // }
    // }
  }

  @Override
  public void zoom(Double viewScale) {
    super.zoom(viewScale);
    computeCrosslines(0);
  }

  @Override
  protected void drawOnTop(Graphics2D g2d) {
    MprAxis axis = getMprAxis();
    if (axis != null && infoLayer != null && LangUtil.getNULLtoFalse(infoLayer.getVisible())) {
      axis.getAxisDirection().drawAxes(g2d, this);
    }

    super.drawOnTop(g2d);
    drawProgressBar(g2d, progressBar);
    
    // Draw debug visualization for curved MPR
    drawCurvedMprDebug(g2d);
  }
  
  /**
   * Draw debug visualization for the curved MPR curve and sampling directions.
   * Shows: original points (red), smoothed curve (green), perpendicular directions (cyan).
   */
  private void drawCurvedMprDebug(Graphics2D g2d) {
    CurvedMprImageIO.DebugCurveData debug = CurvedMprImageIO.getLastDebugData();
    if (debug == null || plane != Plane.AXIAL) {
      return; // Only draw on axial view where the curve was drawn
    }
    
    Volume<?> vol = mprController.getVolume();
    if (vol == null) return;
    
    int sliceSize = vol.getSliceSize();
    Vector3i volSize = vol.getSize();
    Vector3d voxelRatio = vol.getVoxelRatio();
    
    // Centering offsets: the volume is centered in the sliceSize×sliceSize image
    double offsetX = (sliceSize - volSize.x * voxelRatio.x) / 2.0;
    double offsetY = (sliceSize - volSize.y * voxelRatio.y) / 2.0;
    
    java.awt.Stroke oldStroke = g2d.getStroke();
    java.awt.Color oldColor = g2d.getColor();
    
    // Draw original user points (large red circles)
    g2d.setColor(Color.RED);
    g2d.setStroke(new java.awt.BasicStroke(2f));
    for (Vector3d pt : debug.originalPoints) {
      double imgX = pt.x * voxelRatio.x + offsetX;
      double imgY = pt.y * voxelRatio.y + offsetY;
      Point2D screenPt = getImageCoordinatesToScreen(imgX, imgY);
      int r = 6;
      g2d.drawOval((int)screenPt.getX() - r, (int)screenPt.getY() - r, r*2, r*2);
    }
    
    // Draw smoothed curve (green line)
    g2d.setColor(Color.GREEN);
    g2d.setStroke(new java.awt.BasicStroke(1.5f));
    Point2D prevPt = null;
    for (Vector3d pt : debug.smoothedPoints) {
      double imgX = pt.x * voxelRatio.x + offsetX;
      double imgY = pt.y * voxelRatio.y + offsetY;
      Point2D screenPt = getImageCoordinatesToScreen(imgX, imgY);
      if (prevPt != null) {
        g2d.drawLine((int)prevPt.getX(), (int)prevPt.getY(), 
                     (int)screenPt.getX(), (int)screenPt.getY());
      }
      prevPt = screenPt;
    }
    
    // Draw perpendicular directions at sampled points (cyan lines showing slab extent)
    g2d.setColor(Color.CYAN);
    g2d.setStroke(new java.awt.BasicStroke(1f));
    // Convert slab thickness from mm to voxels for correct visualization
    double pixelMm = vol.getMinPixelRatio();
    double halfSlabVoxels = (debug.slabThicknessMm / 2.0) / pixelMm;
    
    // Sample every Nth point to avoid clutter
    int step = Math.max(1, debug.sampledPoints.size() / 30);
    for (int i = 0; i < debug.sampledPoints.size(); i += step) {
      Vector3d pt = debug.sampledPoints.get(i);
      Vector3d dir = debug.perpDirections.get(i);
      
      // Compute endpoints of perpendicular line (in voxel coords)
      Vector3d p1 = new Vector3d(pt).add(new Vector3d(dir).mul(halfSlabVoxels));
      Vector3d p2 = new Vector3d(pt).sub(new Vector3d(dir).mul(halfSlabVoxels));
      
      // Convert to image coordinates
      double img1X = p1.x * voxelRatio.x + offsetX;
      double img1Y = p1.y * voxelRatio.y + offsetY;
      double img2X = p2.x * voxelRatio.x + offsetX;
      double img2Y = p2.y * voxelRatio.y + offsetY;
      
      Point2D screen1 = getImageCoordinatesToScreen(img1X, img1Y);
      Point2D screen2 = getImageCoordinatesToScreen(img2X, img2Y);
      
      g2d.drawLine((int)screen1.getX(), (int)screen1.getY(),
                   (int)screen2.getX(), (int)screen2.getY());
    }
    
    // Draw info text
    g2d.setColor(Color.YELLOW);
    g2d.drawString("Debug: orig=" + debug.originalPoints.size() + 
                   " smooth=" + debug.smoothedPoints.size() +
                   " sampled=" + debug.sampledPoints.size() +
                   " slab=" + debug.slabThicknessMm + "mm", 10, 50);
    
    g2d.setStroke(oldStroke);
    g2d.setColor(oldColor);
  }
  
  private Point2D getImageCoordinatesToScreen(double imgX, double imgY) {
    java.awt.geom.AffineTransform transform = getAffineTransform();
    if (transform == null) {
      return new Point2D.Double(imgX, imgY);
    }
    Point2D.Double src = new Point2D.Double(imgX, imgY);
    Point2D.Double dst = new Point2D.Double();
    transform.transform(src, dst);
    return dst;
  }

  public ControlPoints getControlPoints(Line2D line, Point2D center) {
    Path2D path = getVisibleImageViewBounds();
    line = GeomUtil.cropLine(line, path);
    ControlPoints c = new ControlPoints();
    c.p1 = line.getP1();
    c.p2 = line.getP2();
    c.p1Rotate = GeomUtil.getCollinearPointWithRatio(center, c.p1, 0.75);
    c.p2Rotate = GeomUtil.getCollinearPointWithRatio(center, c.p2, 0.75);
    //   c.p1Extend  = GeomUtil.getCollinearPointWithRatio(center, p1, 0.5);
    //   c.p2Extend  = GeomUtil.getCollinearPointWithRatio(center, p2, 0.5);
    return c;
  }

  protected void addCrossline(MprAxis axis) {
    Pair<MprAxis, MprAxis> pair = mprController.getCrossAxis(axis);
    if (pair != null) {
      DicomImageElement imageElement = axis.getImageElement();
      if (imageElement == null) {
        return;
      }

      GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(this, LayerType.CROSSLINES);
      getGraphicManager().deleteByLayer(layer);
      layer.setVisible(!getViewProperty(this, HIDE_CROSSLINES));
      int centerGap = getCenterGap();
      if (LangUtil.getNULLtoFalse((Boolean) actionsInView.get(SHOW_CROSS_CENTER))) {
        centerGap = 0;
      }
      Vector3d center = mprController.getCrossHairPosition(axis);
      Point2D centerPt = new Point2D.Double(center.x, center.y);
      processImage(pair.first(), layer, axis, false, centerPt, centerGap);
      processImage(pair.second(), layer, axis, true, centerPt, centerGap);
    }
  }

  private void processImage(
      MprAxis currentAxis,
      GraphicLayer layer,
      MprAxis axis,
      boolean vertical,
      Point2D centerPt,
      int centerGap) {

    Vector3d center = mprController.getCrossHairPosition(axis);
    List<Point2D> pts = mprController.getLinePoints(axis, center, vertical);
    if (pts == null) return;

    Color color = axis.getAxisDColor(vertical);
    boolean selected =
        mprController.getCurrentAxis() == axis && currentAxis == mprController.getSelectedAxis();
    addCrosshairLine(
        layer, pts, color, centerPt, centerGap, currentAxis.getThicknessExtension(), selected);
  }

  public boolean isVerticalLine(MprAxis axis) {
    if (mprController != null) {
      Pair<MprAxis, MprAxis> pair = mprController.getCrossAxis(getMprAxis());
      if (pair != null) {
        return axis == pair.second();
      }
    }
    return false;
  }

  protected void addCrosshairLine(
      GraphicLayer layer,
      List<Point2D> pts,
      Color color,
      Point2D center,
      int centerGap,
      int lineThickness,
      boolean selected) {
    if (pts != null && pts.size() == 2) {
      try {
        Graphic graphic;
        CrossLineGraphic line = new CrossLineGraphic();
        line.setCenterGap(center);
        line.setGapSize(centerGap);
        line.setExtendLength(lineThickness);
        line.setLineThickness(selected ? 3f : Graphic.DEFAULT_LINE_THICKNESS);
        line.setMprView(this);
        graphic = line.buildGraphic(pts);

        graphic.setPaint(color);
        graphic.setLabelVisible(Boolean.FALSE);
        graphic.setLayer(layer);

        graphicManager.addGraphic(graphic);
      } catch (InvalidShapeException e) {
        LOGGER.error("Add crosshair line", e);
      }
    }
  }

  @Override
  public List<Point2D> getCrossLine(GeometryOfSlice sliceGeometry, LocalizerPoster localizer) {
    return super.getCrossLine(sliceGeometry, localizer);
  }

  @Override
  public void drawLayers(
      Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
    addCrossline(getMprAxis());
    super.drawLayers(g2d, transform, inverseTransform);
  }

  public void setProgressBar(JProgressBar bar) {
    this.progressBar = bar;
  }

  public JProgressBar getProgressBar() {
    return progressBar;
  }

  @Override
  protected void computeCrosslines(double location) {
    if (mprController != null) {
      MprAxis axis = getMprAxis();
      if (axis == null) {
        return;
      }
      mprController.recenter(axis, getCenterMode());
    }
  }

  protected int getCenterMode() {
    return eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
  }

  protected int getCenterGap() {
    return eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
  }

  public void recenterAxis(boolean all) {
    MprAxis axis = getMprAxis();
    if (axis != null) {
      if (all) {
        mprController.centerAll(2);
      } else {
        mprController.recenter(axis, 2);
      }
    }
  }

  public ViewButton buildMprButton() {
    ViewButton button =
        new ViewButton(
            (invoker, x, y) -> {
              JPopupMenu popupMenu = new JPopupMenu();
              JMenu menu = new JMenu(Messages.getString("all.views"));

              if (getCenterMode() != 2) {
                JMenuItem item = new JMenuItem(Messages.getString("center"));
                item.addActionListener(e -> recenterAxis(false));
                item.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK));
                popupMenu.add(item);

                item = new JMenuItem(Messages.getString("center"));
                item.addActionListener(e -> recenterAxis(true));
                item.setAccelerator(
                    KeyStroke.getKeyStroke(
                        KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
                menu.add(item);
              }

              int gap = getCenterGap();
              if (gap > 0) {
                boolean showCenter = getViewProperty(this, SHOW_CROSS_CENTER);
                JCheckBoxMenuItem boxMenuItem =
                    new JCheckBoxMenuItem(Messages.getString("show.center.crosshair"), showCenter);
                boxMenuItem.addActionListener(
                    e -> showCrossCenter((JCheckBoxMenuItem) e.getSource(), false));
                boxMenuItem.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK));
                popupMenu.add(boxMenuItem);

                showCenter = getAllViewsProperty(SHOW_CROSS_CENTER);
                boxMenuItem = new JCheckBoxMenuItem(boxMenuItem.getText(), showCenter);
                boxMenuItem.addActionListener(
                    e -> showCrossCenter((JCheckBoxMenuItem) e.getSource(), true));
                boxMenuItem.setAccelerator(
                    KeyStroke.getKeyStroke(
                        KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
                menu.add(boxMenuItem);
              }

              boolean showCrossLines = !getViewProperty(this, HIDE_CROSSLINES);
              JCheckBoxMenuItem boxMenuItem =
                  new JCheckBoxMenuItem(Messages.getString("show.crosshair"), showCrossLines);
              boxMenuItem.addActionListener(
                  e -> showCrossLines((JCheckBoxMenuItem) e.getSource(), false));
              boxMenuItem.setAccelerator(
                  KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.ALT_DOWN_MASK));
              popupMenu.add(boxMenuItem);

              showCrossLines = !getAllViewsProperty(HIDE_CROSSLINES);
              boxMenuItem = new JCheckBoxMenuItem(boxMenuItem.getText(), showCrossLines);
              boxMenuItem.addActionListener(
                  e -> showCrossLines((JCheckBoxMenuItem) e.getSource(), true));
              boxMenuItem.setAccelerator(
                  KeyStroke.getKeyStroke(
                      KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
              menu.add(boxMenuItem);

              JMenu menuItem =
                  mprController
                      .getMipTypeOption()
                      .createUnregisteredRadioMenu(Messages.getString("mip.type"));
              menu.add(menuItem);

              menuItem = buildMipThicknessMenu(false);
              popupMenu.add(menuItem);

              menuItem = buildMipThicknessMenu(true);
              menu.add(menuItem);

              //              JMenuItem item = new JMenuItem("Export this view in DICOM");
              //              item.addActionListener(e -> rebuildView(false));
              //              popupMenu.add(item);
              //
              //              item = new JMenuItem("Export in DICOM");
              //              item.addActionListener(e -> rebuildView(true));
              //              menu.add(item);

              JMenuItem item = new JMenuItem(Messages.getString("change.mpr.pref"));
              item.addActionListener(
                  _ -> {
                    PreferenceDialog dialog =
                        new PreferenceDialog(SwingUtilities.getWindowAncestor(this));
                    dialog.showPage(Messages.getString("MPRFactory.title"));
                    GuiUtils.showCenterScreen(dialog);
                  });
              menu.add(item);

              popupMenu.add(menu);
              popupMenu.show(invoker, x, y);
            },
            ResourceUtil.getIcon(OtherIcon.VIEW_SETTING).derive(24, 24),
            Messages.getString("MPRFactory.title"));
    button.setVisible(true);
    button.setPosition(GridBagConstraints.NORTHEAST);
    return button;
  }

  private JMenu buildMipThicknessMenu(boolean all) {
    MprAxis axis = getMprAxis();
    JMenu menu = new JMenu(Messages.getString("mip.thickness"));
    DicomImageElement img = axis.getImageElement();
    if (img == null) {
      return menu;
    }
    String abbr = img.getPixelSpacingUnit().getAbbreviation();
    double minRatio = mprController.getVolume().getMinPixelRatio();
    int oldThickness = axis.getThicknessExtension();
    List<MprAxis> allAxis =
        List.of(mprController.getAxial(), mprController.getCoronal(), mprController.getSagittal());
    List<MprAxis> mprAxisList = all ? allAxis : List.of(axis);

    List<Integer> extendsValues = List.of(1, 2, 3, 5, 7, 10, 15, 20);
    for (Integer i : extendsValues) {
      double thickness = (i * 2 + 1) * minRatio;
      String title = i + " (" + DecFormatter.allNumber(thickness) + " " + abbr + ")";
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(title, i == oldThickness);
      item.addActionListener(
          e -> {
            JRadioButtonMenuItem source = (JRadioButtonMenuItem) e.getSource();
            if (source.isSelected()) {
              int thicknessValue = Integer.parseInt(source.getText().substring(0, 2).trim());
              for (MprAxis mprAxis : mprAxisList) {
                mprAxis.setThicknessExtension(thicknessValue);
                mprAxis.updateImage();
              }
              if (mprAxisList != allAxis) {
                allAxis.forEach(mprAxis -> mprAxis.getMprView().repaint());
              }
            }
          });
      menu.add(item);
    }
    menu.add(new JPopupMenu.Separator());

    menu.add(new JMenuItem(Messages.getString("reset.thickness")))
        .addActionListener(
            e -> {
              for (MprAxis mprAxis : mprAxisList) {
                mprAxis.setThicknessExtension(0);
                mprAxis.updateImage();
              }
              if (mprAxisList != allAxis) {
                allAxis.forEach(mprAxis -> mprAxis.getMprView().repaint());
              }
            });

    menu.add(new JMenuItem(Messages.getString("custom.thickness")))
        .addActionListener(
            e -> {
              int maxValue = mprController.getVolume().getSliceSize();
              JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING));
              SpinnerNumberModel spinnerModel = new SpinnerNumberModel(maxValue, 1, maxValue, 1);
              JSpinner thicknessSpinner = new JSpinner(spinnerModel);
              JLabel conversionLabel = new JLabel();
              thicknessSpinner.addChangeListener(
                  _ -> {
                    int value = (Integer) thicknessSpinner.getValue();
                    double mmValue = (value * 2 + 1) * minRatio;
                    conversionLabel.setText(
                        " %d pix = %s %s" // NON-NLS
                            .formatted(value, DecFormatter.allNumber(mmValue), abbr));
                  });

              // Adding components to the panel
              panel.add(new JLabel(Messages.getString("MipView.img_extend") + StringUtil.COLON));
              panel.add(thicknessSpinner);
              panel.add(conversionLabel);
              FontMetrics metrics = conversionLabel.getFontMetrics(conversionLabel.getFont());
              String maxExpectedLabel =
                  " %d pix = %s %s" // NON-NLS
                      .formatted(
                          maxValue, DecFormatter.allNumber((maxValue * 2 + 1) * minRatio), abbr);
              conversionLabel.setPreferredSize(
                  new Dimension(metrics.stringWidth(maxExpectedLabel), metrics.getHeight()));
              int initialValue = Math.max(oldThickness, 1);
              thicknessSpinner.setValue(initialValue);

              int result =
                  JOptionPane.showConfirmDialog(
                      null,
                      panel,
                      Messages.getString("custom.thickness"),
                      JOptionPane.OK_CANCEL_OPTION,
                      JOptionPane.PLAIN_MESSAGE);

              if (result == JOptionPane.OK_OPTION) {
                int customThickness = (Integer) thicknessSpinner.getValue();

                for (MprAxis mprAxis : mprAxisList) {
                  mprAxis.setThicknessExtension(customThickness);
                  mprAxis.updateImage();
                }

                if (mprAxisList != allAxis) {
                  allAxis.forEach(mprAxis -> mprAxis.getMprView().repaint());
                }
              }
            });
    return menu;
  }

  public boolean getAllViewsProperty(String key) {
    if (mprController != null) {
      return getViewProperty(mprController.getAxial().getMprView(), key)
          && getViewProperty(mprController.getCoronal().getMprView(), key)
          && getViewProperty(mprController.getSagittal().getMprView(), key);
    }
    return false;
  }

  public static boolean getViewProperty(MprView view, String key) {
    if (view != null) {
      return LangUtil.getNULLtoFalse((Boolean) view.actionsInView.get(key));
    }
    return false;
  }

  private void showCrossLines(JCheckBoxMenuItem item, boolean all) {
    showCrossLines(item.isSelected(), all);
  }

  public void showCrossLines(boolean selected, boolean all) {
    if (all) {
      showCrossLines(mprController.getAxial().getMprView(), selected);
      showCrossLines(mprController.getCoronal().getMprView(), selected);
      showCrossLines(mprController.getSagittal().getMprView(), selected);
    } else {
      showCrossLines(this, selected);
    }
  }

  private static void showCrossLines(MprView view, boolean selected) {
    view.actionsInView.put(HIDE_CROSSLINES, !selected);
    GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(view, LayerType.CROSSLINES);
    layer.setVisible(selected);
    view.repaint();
  }

  private void showCrossCenter(JCheckBoxMenuItem item, boolean all) {
    showCrossCenter(item.isSelected(), all);
  }

  public void showCrossCenter(boolean selected, boolean all) {
    if (all) {
      showCrossCenter(mprController.getAxial().getMprView(), selected);
      showCrossCenter(mprController.getCoronal().getMprView(), selected);
      showCrossCenter(mprController.getSagittal().getMprView(), selected);
    } else {
      showCrossCenter(this, selected);
    }
  }

  protected static void showCrossCenter(MprView view, boolean selected) {
    if (view != null) {
      view.actionsInView.put(SHOW_CROSS_CENTER, selected);
      view.repaint();
    }
  }

  private void rebuildView(boolean all) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogType(JFileChooser.OPEN_DIALOG);
    fc.setControlButtonsAreShown(true);
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    int returnVal = fc.showOpenDialog(this);
    File folder = null;

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      try {
        folder = fc.getSelectedFile();
      } catch (SecurityException e) {
        LOGGER.warn("directory cannot be accessed", e);
      }
    }

    if (all) {
      rebuildView(mprController.getAxial(), folder);
      rebuildView(mprController.getCoronal(), folder);
      rebuildView(mprController.getSagittal(), folder);
    } else {
      rebuildView(getMprAxis(), folder);
    }
  }

  protected void rebuildView(MprAxis axis, File folder) {
    if (axis == null || folder == null || !folder.canWrite()) return;

    Vector3d oldPosition = mprController.getAxesControl().getCenter();
    String uid = UIDUtils.createUID();
    int sliceImageSize = mprController.getVolume().getSliceSize();

    File dir = new File(folder, uid);
    dir.mkdirs();

    MprView view = axis.getMprView();
    JProgressBar bar = ObliqueMpr.createProgressBar(axis.getMprView(), sliceImageSize);
    bar.setVisible(true);
    GuiExecutor.invokeAndWait(
        () -> {
          bar.setValue(0);
          view.repaint();
        });
    bar.addChangeListener(
        e -> {
          if (bar.getValue() == bar.getMaximum()) {
            view.setProgressBar(null);
          }
          view.repaint();
        });

    SwingWorker<Void, Integer> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() {
            for (int i = 0; i < sliceImageSize; i++) {
              axis.setSliceIndex(i);
              DicomImageElement imageElement = axis.getImageElement();
              if (imageElement != null) {
                imageElement.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
                String iuid = TagD.getTagValue(imageElement, Tag.SOPInstanceUID, String.class);
                imageElement.saveToFile(new File(dir, iuid));
              }
              publish(i + 1);
            }
            return null;
          }

          @Override
          protected void process(List<Integer> chunks) {
            for (int progress : chunks) {
              bar.setValue(progress);
            }
          }

          @Override
          protected void done() {
            bar.setValue(bar.getMaximum());
            mprController.getAxesControl().setCenter(oldPosition);
          }
        };
    worker.execute();
  }

  protected void setRotation(double rotation) {
    MprAxis axis = getMprAxis();
    Pair<MprAxis, MprAxis> pair = mprController.getCrossAxis(axis);
    if (pair != null) {
      Volume<?> volume = mprController.getVolume();
      Quaterniond r = new Quaterniond(volume.getRotation());
      if (plane == Plane.AXIAL) {
        r.mul(new Quaterniond().rotateZ(rotation));
      } else if (plane == Plane.CORONAL) {
        r.mul(new Quaterniond().rotateY(rotation));
      } else if (plane == Plane.SAGITTAL) {
        r.mul(new Quaterniond().rotateX(rotation));
      }
      mprController.getRotation(plane).mul(r);
    }
    repaint();
  }

  @Override
  public JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final List<Graphic> selected) {
    LOGGER.info("MprView.buildGraphicContextMenu called, selected={}", selected != null ? selected.size() : 0);
    JPopupMenu popupMenu = super.buildGraphicContextMenu(evt, selected);
    if (popupMenu != null && selected != null && selected.size() == 1) {
      Graphic graphic = selected.getFirst();
      LOGGER.info("Selected graphic: {} isPolyline={} isComplete={}", 
          graphic.getClass().getSimpleName(),
          graphic instanceof PolylineGraphic,
          graphic.isGraphicComplete());
      if (graphic instanceof PolylineGraphic polyline && graphic.isGraphicComplete()) {
        LOGGER.info("Adding Curved MPR menu item in buildGraphicContextMenu");
        popupMenu.addSeparator();
        JMenuItem curvedMprItem = new JMenuItem("Generate Curved MPR");
        curvedMprItem.addActionListener(e -> {
          List<Point2D> pts = polyline.getHandlePointList();
          if (pts.size() >= 2) {
            java.util.List<org.joml.Vector3d> points3D = new java.util.ArrayList<>();
            for (Point2D pt : pts) {
              if (pt != null) {
                // Use getVolumeCoordinatesFromImage since polyline points are in image coords
                Point3 volCoord = getVolumeCoordinatesFromImage(pt.getX(), pt.getY());
                if (volCoord != null) {
                  points3D.add(new org.joml.Vector3d(volCoord.x, volCoord.y, volCoord.z));
                }
              }
            }
            if (points3D.size() >= 2) {
              CurvedMprFactory.openCurvedMpr(this, points3D);
            }
          }
        });
        popupMenu.add(curvedMprItem);
      }
    }
    return popupMenu;
  }
}
