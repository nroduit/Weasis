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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.swing.UIManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.pref.ZoomSetting;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * The Class ZoomWin.
 *
 * @author Nicolas Roduit
 */
public class ZoomWin<E extends ImageElement> extends GraphicsPane
    implements ImageLayerChangeListener<E> {

  public static final String SYNCH_CMD = "synchronize"; // NON-NLS
  public static final String FREEZE_CMD = "freeze"; // NON-NLS

  public enum SyncType {
    NONE,
    PARENT_IMAGE,
    PARENT_PARAMETERS
  }

  private final DefaultView2d<E> view2d;
  private RectangularShape shape;
  private int borderOffset = 2;
  private Color lineColor;
  private Color backgroundColor;
  private Stroke stroke;

  private final PopUpMenuOnZoom popup;
  private final RenderedImageLayer<E> imageLayer;
  private final MouseHandler mouseHandler;
  private SimpleOpManager freezeOperations;
  private final HashMap<String, Object> freezeActionsInView = new HashMap<>();

  public ZoomWin(DefaultView2d<E> view2d) {
    super(null);
    this.view2d = view2d;
    this.setOpaque(false);
    ImageViewerEventManager<E> manager = view2d.getEventManager();
    this.imageLayer = new RenderedImageLayer<>();
    SimpleOpManager operations = imageLayer.getDisplayOpManager();
    operations.addImageOperationAction(new AffineTransformOp());

    manager
        .getAction(ActionW.LENS_ZOOM)
        .ifPresent(c -> actionsInView.put(ActionW.ZOOM.cmd(), c.getRealValue()));

    this.popup = new PopUpMenuOnZoom(this);
    this.popup.setInvoker(this);
    this.setCursor(DefaultView2d.MOVE_CURSOR);

    ZoomSetting z = manager.getZoomSetting();
    OpManager disOp = getDisplayOpManager();

    disOp.setParamValue(
        AffineTransformOp.OP_NAME,
        AffineTransformOp.P_INTERPOLATION,
        Interpolation.getInterpolation(z.getInterpolation()));
    disOp.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX, null);

    actionsInView.put(SYNCH_CMD, z.isLensSynchronize());
    actionsInView.put(ActionW.DRAWINGS.cmd(), z.isLensShowDrawings());
    actionsInView.put(FREEZE_CMD, SyncType.NONE);

    Color bckColor = UIManager.getColor("Panel.background");
    this.setLensDecoration(z.getLensLineWidth(), z.getLensLineColor(), bckColor, z.isLensRound());
    this.setSize(z.getLensWidth(), z.getLensHeight());
    this.setLocation(-1, -1);
    this.imageLayer.addLayerChangeListener(this);
    this.mouseHandler = new MouseHandler();
  }

  public void setActionInView(String action, Object value) {
    Optional.ofNullable(action).ifPresent(a -> actionsInView.put(a, value));
  }

  private void refreshZoomWin() {
    Point loc = getLocation();
    if (loc.x == -1 && loc.y == -1) {
      centerZoomWin();
      return;
    }
    Rectangle rect = view2d.getBounds();
    rect.x = 0;
    rect.y = 0;
    Rectangle2D r = rect.createIntersection(getBounds());
    if (r.getWidth() < 25.0 || r.getHeight() < 25.0) {
      centerZoomWin();
    }
  }

  public void updateImage() {
    view2d.graphicManager.addGraphicChangeHandler(graphicsChangeHandler);
    imageLayer.setImage(
        view2d.getImage(), (OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
    getViewModel().setModelArea(view2d.getViewModel().getModelArea());
    SyncType type = (SyncType) actionsInView.get(ZoomWin.FREEZE_CMD);
    if (SyncType.PARENT_PARAMETERS.equals(type)) {
      freezeOperations.setFirstNode(imageLayer.getSourceRenderedImage());
      freezeOperations.handleImageOpEvent(
          new ImageOpEvent(
              ImageOpEvent.OpEvent.IMAGE_CHANGE, view2d.getSeries(), view2d.getImage(), null));
      freezeOperations.process();
    }
  }

  public void showLens(boolean val) {
    if (val) {
      updateImage();
      refreshZoomWin();
      updateZoom();
      enableMouseListener();
      setVisible(true);
    } else {
      setVisible(false);
      view2d.graphicManager.removeGraphicChangeHandler(graphicsChangeHandler);
      disableMouseAndKeyListener();
    }
  }

  public void centerZoomWin() {
    int magPosx = (view2d.getWidth() / 2) - (getWidth() / 2);
    int magPosy = (view2d.getHeight() / 2) - (getHeight() / 2);
    setLocation(magPosx, magPosy);
  }

  public void hideZoom() {
    if (Objects.equals(view2d.getEventManager().getSelectedViewPane(), view2d)) {
      view2d.getEventManager().getAction(ActionW.LENS).ifPresent(b -> b.setSelected(false));
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    if (g instanceof Graphics2D graphics2D) {
      draw(graphics2D);
    }
  }

  protected void draw(Graphics2D g2d) {
    Stroke oldStroke = g2d.getStroke();
    Paint oldColor = g2d.getPaint();
    Shape oldClip = g2d.getClip();
    g2d.clip(shape);
    g2d.setBackground(backgroundColor);
    drawBackground(g2d);

    // Set font size according to the view size
    g2d.setFont(MeasureTool.viewSetting.getFont());

    // Paint the visible area
    Point2D p = getClipViewCoordinatesOffset();
    g2d.translate(p.getX(), p.getY());
    imageLayer.drawImage(g2d);
    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-p.getX(), -p.getY());

    g2d.setClip(oldClip);
    g2d.setStroke(stroke);
    g2d.setPaint(lineColor);
    Rectangle bound = getBounds();
    int size = GuiUtils.getScaleLength(12);
    g2d.drawRect(bound.width - size - borderOffset, bound.height - size - borderOffset, size, size);
    g2d.draw(shape);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
  }

  public void drawLayers(
      Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
    if (LangUtil.getNULLtoTrue((Boolean) actionsInView.get(ActionW.DRAWINGS.cmd()))) {
      Object[] oldRenderingHints =
          GuiUtils.setRenderingHints(g2d, true, false, view2d.requiredTextAntialiasing());
      Rectangle2D b = new Rectangle2D.Double(0.0, 0.0, getWidth(), getHeight());
      view2d.getGraphicManager().draw(g2d, transform, inverseTransform, b);
      GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    }
  }

  private void drawBackground(Graphics2D g2d) {
    g2d.clearRect(0, 0, getWidth(), getHeight());
  }

  protected void updateAffineTransform() {
    // Set the position from the center of the image
    getViewModel().setModelOffset(getOffsetCenterX(), getOffsetCenterY());

    ImageOpNode node = getDisplayOpManager().getNode(AffineTransformOp.OP_NAME);
    super.updateAffineTransform(view2d, node, imageLayer, -2.0);
  }

  public void setLensDecoration(
      int lineWidth, Color lineColor, Color backgroundColor, boolean roundShape) {
    this.borderOffset = lineWidth / 2 + 1;
    this.stroke = new BasicStroke(lineWidth);
    this.lineColor = lineColor;
    this.backgroundColor = backgroundColor;
    this.setBackground(backgroundColor);
    upateShape(roundShape);
  }

  @Override
  public void setSize(int width, int height) {
    shape.setFrame(
        borderOffset, borderOffset, width - (double) borderOffset, height - (double) borderOffset);
    super.setSize(width + borderOffset, height + borderOffset);
  }

  private void upateShape(boolean round) {
    if (round) {
      shape = new java.awt.geom.Ellipse2D.Double(getX(), getY(), getWidth(), getHeight());
    } else {
      shape = new java.awt.geom.Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
    }
  }

  public double getOffsetCenterX() {
    double magPosx = (getX() + getWidth() * 0.5) - view2d.getWidth() * 0.5;
    return view2d.viewToModelLength(magPosx) + view2d.getViewModel().getModelOffsetX();
  }

  public double getOffsetCenterY() {
    double magPosy = (getY() + getHeight() * 0.5) - view2d.getHeight() * 0.5;
    return view2d.viewToModelLength(magPosy) + view2d.getViewModel().getModelOffsetY();
  }

  @Override
  public void zoom(Double viewScale) {
    E img = imageLayer.getSourceImage();
    ImageOpNode node = imageLayer.getDisplayOpManager().getNode(AffineTransformOp.OP_NAME);
    if (img != null && node != null) {
      node.setParam(Param.INPUT_IMG, getSourceImage());
      actionsInView.put(ActionW.ZOOM.cmd(), viewScale);
      super.zoom(Math.abs(viewScale));
      updateAffineTransform();
    }
  }

  public void updateZoom() {
    double zoomFactor =
        (Boolean) actionsInView.get(SYNCH_CMD)
            ? view2d.getViewModel().getViewScale()
            : (Double) actionsInView.get(ActionW.ZOOM.cmd());
    zoom(zoomFactor);
  }

  protected PlanarImage getSourceImage() {
    SyncType type = (SyncType) actionsInView.get(ZoomWin.FREEZE_CMD);
    if (SyncType.PARENT_PARAMETERS.equals(type) || SyncType.PARENT_IMAGE.equals(type)) {
      return freezeOperations.getLastNodeOutputImage();
    }

    // return the image before the zoom operation from the parent view
    ImageOpNode node =
        view2d.getImageLayer().getDisplayOpManager().getNode(AffineTransformOp.OP_NAME);
    if (node != null) {
      return (PlanarImage) node.getParam(Param.INPUT_IMG);
    }
    return view2d.getImageLayer().getDisplayOpManager().getLastNodeOutputImage();
  }

  public void setFreezeImage(SyncType type) {
    actionsInView.put(ZoomWin.FREEZE_CMD, type);
    if (Objects.isNull(type) || SyncType.NONE.equals(type)) {
      freezeActionsInView.clear();
      freezeOperations = null;
      actionsInView.put(ZoomWin.FREEZE_CMD, SyncType.NONE);
    } else {
      freezeParentParameters();
    }
    imageLayer.updateDisplayOperations();
    updateZoom();
  }

  void freezeParentParameters() {
    SimpleOpManager pManager = view2d.getImageLayer().getDisplayOpManager();
    freezeActionsInView.clear();
    view2d.copyActionWState(freezeActionsInView);

    freezeOperations = new SimpleOpManager();
    for (ImageOpNode op : pManager.getOperations()) {
      if (AffineTransformOp.OP_NAME.equals(op.getParam(Param.NAME))) {
        break;
      }
      ImageOpNode operation = op.copy();
      freezeOperations.addImageOperationAction(operation);
    }

    freezeOperations.setFirstNode(imageLayer.getSourceRenderedImage());
    freezeOperations.process();
  }

  class MouseHandler extends MouseAdapter {
    private Point pickPoint = null;
    private int pickWidth;
    private int pickHeight;
    private int cursor;

    @Override
    public void mousePressed(MouseEvent e) {
      ImageViewerPlugin<E> pane = view2d.getEventManager().getSelectedView2dContainer();
      if (pane == null) {
        return;
      }
      if (pane.isContainingView(view2d)) {
        pane.setSelectedImagePane(view2d);
      }
      if (e.isPopupTrigger()) {
        popup.enableMenuItem();
        popup.show(e.getComponent(), e.getX(), e.getY());
      }
      pickPoint = e.getPoint();
      pickWidth = getWidth();
      pickHeight = getHeight();
      cursor = getCursor(e);
    }

    @Override
    public void mouseReleased(MouseEvent mouseevent) {
      pickPoint = null;
      if (mouseevent.isPopupTrigger()) {
        popup.enableMenuItem();
        popup.show(mouseevent.getComponent(), mouseevent.getX(), mouseevent.getY());
      } else if (mouseevent.getClickCount() == 2) {
        ImageViewerEventManager<E> manager = view2d.getEventManager();
        manager
            .getAction(ActionW.LENS_ZOOM)
            .ifPresent(c -> c.setRealValue(view2d.getViewModel().getViewScale()));
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      int mods = e.getModifiers();
      if (pickPoint != null && (mods & InputEvent.BUTTON1_MASK) != 0) {
        Point p = e.getPoint();
        int dx = p.x - pickPoint.x;
        int dy = p.y - pickPoint.y;

        if (cursor == Cursor.SE_RESIZE_CURSOR) {
          int nw = pickWidth + dx;
          int nh = pickHeight + dy;
          nw = nw < 50 ? 50 : Math.min(nw, 500);
          nh = nh < 50 ? 50 : Math.min(nh, 500);
          setSize(nw, nh);
          updateAffineTransform();
        } else {
          setLocation(getX() + dx, getY() + dy);
          updateAffineTransform();
        }
        setCursor(Cursor.getPredefinedCursor(cursor));
      }
    }

    @Override
    public void mouseMoved(MouseEvent me) {
      setCursor(Cursor.getPredefinedCursor(getCursor(me)));
    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {
      setCursor(Cursor.getDefaultCursor());
    }

    public int getCursor(MouseEvent me) {
      Component c = me.getComponent();
      int w = c.getWidth();
      int h = c.getHeight();
      int size = GuiUtils.getScaleLength(12);
      Rectangle rect = new Rectangle(w - size - borderOffset, h - size - borderOffset, size, size);
      if (rect.contains(me.getPoint())) {
        return Cursor.SE_RESIZE_CURSOR;
      }

      return Cursor.MOVE_CURSOR;
    }
  }

  public void disableMouseAndKeyListener() {
    this.removeMouseListener(mouseHandler);
    this.removeMouseMotionListener(mouseHandler);
    this.removeMouseWheelListener(
        view2d.getEventManager().getAction(ActionW.LENS_ZOOM).orElse(null));
  }

  public void enableMouseListener() {
    disableMouseAndKeyListener();
    this.addMouseListener(mouseHandler);
    this.addMouseMotionListener(mouseHandler);
    this.addMouseWheelListener(view2d.getEventManager().getAction(ActionW.LENS_ZOOM).orElse(null));
  }

  public ViewCanvas<E> getView2d() {
    return view2d;
  }

  @Override
  public void handleLayerChanged(ImageLayer layer) {
    repaint();
  }

  public void setCommandFromParentView(String command, Object value) {
    String cmd = null;
    if (ActionW.SYNCH.cmd().equals(command) && value instanceof SynchEvent synchEvent) {
      if (!(value instanceof SynchCineEvent)) {
        SynchData synchData = (SynchData) view2d.getActionValue(ActionW.SYNCH_LINK.cmd());
        if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
          return;
        }

        for (Entry<String, Object> entry : synchEvent.getEvents().entrySet()) {
          if (synchData != null && !synchData.isActionEnable(entry.getKey())) {
            continue;
          }
          cmd = entry.getKey();
          break;
        }
      }
    } else {
      cmd = command;
    }

    if (cmd != null) {
      if (command.equals(ActionW.PROGRESSION.cmd())) {
        updateImage();
      } else if (command.equals(ActionW.ROTATION.cmd()) || command.equals(ActionW.FLIP.cmd())) {
        refreshZoomWin();
      }
    }
  }

  public OpManager getDisplayOpManager() {
    return imageLayer.getDisplayOpManager();
  }
}
