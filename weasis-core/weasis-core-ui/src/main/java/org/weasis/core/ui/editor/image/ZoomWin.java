/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.util.LangUtil;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.pref.ZoomSetting;
import org.weasis.opencv.data.PlanarImage;

/**
 * The Class ZoomWin.
 *
 * @author Nicolas Roduit
 */
public class ZoomWin<E extends ImageElement> extends GraphicsPane implements ImageLayerChangeListener<E> {
    private static final long serialVersionUID = 3542710545706544620L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoomWin.class);

    public static final String SYNCH_CMD = "synchronize"; //$NON-NLS-1$
    public static final String FREEZE_CMD = "freeze"; //$NON-NLS-1$

    public enum SyncType {
        NONE, PARENT_IMAGE, PARENT_PARAMETERS
    }

    private final DefaultView2d<E> view2d;
    private RectangularShape shape;
    private int borderOffset = 2;
    private Color lineColor;
    private Color backgroundColor;
    private Stroke stroke;

    private PopUpMenuOnZoom popup = null;
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

        ActionState zoomAction = manager.getAction(ActionW.LENSZOOM);
        if (zoomAction instanceof SliderChangeListener) {
            actionsInView.put(ActionW.ZOOM.cmd(), ((SliderChangeListener) zoomAction).getRealValue());
        }

        this.popup = new PopUpMenuOnZoom(this);
        this.popup.setInvoker(this);
        this.setCursor(DefaultView2d.MOVE_CURSOR);

        ZoomSetting z = manager.getZoomSetting();
        OpManager disOp = getDisplayOpManager();

        disOp.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_INTERPOLATION, z.getInterpolation());
        disOp.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX, null);

        actionsInView.put(SYNCH_CMD, z.isLensSynchronize());
        actionsInView.put(ActionW.DRAWINGS.cmd(), z.isLensShowDrawings());
        actionsInView.put(FREEZE_CMD, SyncType.NONE);

        Color bckColor = UIManager.getColor("Panel.background"); //$NON-NLS-1$
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
        imageLayer.setImage(view2d.getImage(), (OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()));
        getViewModel().setModelArea(view2d.getViewModel().getModelArea());
        SyncType type = (SyncType) actionsInView.get(ZoomWin.FREEZE_CMD);
        if (SyncType.PARENT_PARAMETERS.equals(type)) {
            freezeOperations.setFirstNode(imageLayer.getSourceRenderedImage());
            freezeOperations.handleImageOpEvent(
                new ImageOpEvent(ImageOpEvent.OpEvent.ImageChange, view2d.getSeries(), view2d.getImage(), null));
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
            ActionState lens = view2d.getEventManager().getAction(ActionW.LENS);
            if (lens instanceof ToggleButtonListener) {
                ((ToggleButtonListener) lens).setSelected(false);
            }
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            draw((Graphics2D) g);
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
        g2d.drawRect(bound.width - 12 - borderOffset, bound.height - 12 - borderOffset, 12, 12);
        g2d.draw(shape);
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
    }

    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        if ((Boolean) actionsInView.get(ActionW.DRAWINGS.cmd())) {
            Rectangle2D b = new Rectangle2D.Double(0.0, 0.0, getWidth(), getHeight());
            view2d.getGraphicManager().draw(g2d, transform, inverseTransform, b);
        }
    }

    private void drawBackground(Graphics2D g2d) {
        g2d.clearRect(0, 0, getWidth(), getHeight());
    }

    protected void updateAffineTransform() {
        // Set the position from the center of the image
        getViewModel().setModelOffset(getOffsetCenterX(), getOffsetCenterY());

        Rectangle2D modelArea = getViewModel().getModelArea();
        double viewScale = getViewModel().getViewScale();

        double rWidth = modelArea.getWidth();
        double rHeight = modelArea.getHeight();

        OpManager dispOp = getDisplayOpManager();
        boolean flip = LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue((ActionW.FLIP.cmd())));
        Integer rotationAngle = (Integer) view2d.getActionValue(ActionW.ROTATION.cmd());

        affineTransform.setToScale(flip ? -viewScale : viewScale, viewScale);
        if (rotationAngle != null && rotationAngle > 0) {
            affineTransform.rotate(Math.toRadians(rotationAngle), rWidth / 2.0, rHeight / 2.0);
        }
        if (flip) {
            affineTransform.translate(-rWidth, 0.0);
        }

        ImageOpNode node = dispOp.getNode(AffineTransformOp.OP_NAME);
        if (node != null) {
            Rectangle2D imgBounds = affineTransform.createTransformedShape(getViewModel().getModelArea()).getBounds2D();

            double diffx = 0.0;
            double diffy = 0.0;
            Rectangle2D viewBounds = new Rectangle2D.Double(0, 0, getWidth() - 2.0, getHeight() - 2.0);
            Rectangle2D srcBounds = getImageViewBounds(viewBounds.getWidth(), viewBounds.getHeight());

            Rectangle2D dstBounds;
            if (viewBounds.contains(srcBounds)) {
                dstBounds = srcBounds;
            } else {
                dstBounds = viewBounds.createIntersection(srcBounds);

                if (srcBounds.getX() < 0.0) {
                    diffx += srcBounds.getX();
                }
                if (srcBounds.getY() < 0.0) {
                    diffy += srcBounds.getY();
                }
            }

            double[] fmx = new double[6];
            affineTransform.getMatrix(fmx);
            // adjust transformation matrix => move the center to keep all the image
            fmx[4] -= imgBounds.getX() - diffx;
            fmx[5] -= imgBounds.getY() - diffy;
            affineTransform.setTransform(fmx[0], fmx[1], fmx[2], fmx[3], fmx[4], fmx[5]);

            // Convert to openCV affine matrix
            double[] m = new double[] { fmx[0], fmx[2], fmx[4], fmx[1], fmx[3], fmx[5] };
            node.setParam(AffineTransformOp.P_AFFINE_MATRIX, m);

            node.setParam(AffineTransformOp.P_DST_BOUNDS, dstBounds);
            imageLayer.updateDisplayOperations();
        }
        
        // Keep the coordinates of the original image when cropping
        Point offset = view2d.getImageLayer().getOffset();
        if (offset != null) {
            affineTransform.translate(-offset.getX(), -offset.getY());
        }

        try {
            inverseTransform.setTransform(affineTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            LOGGER.error("Create inverse transform", e); //$NON-NLS-1$
        }
    }

    public void setLensDecoration(int lineWidth, Color lineColor, Color backgroundColor, boolean roundShape) {
        this.borderOffset = lineWidth / 2 + 1;
        this.stroke = new BasicStroke(lineWidth);
        this.lineColor = lineColor;
        this.backgroundColor = backgroundColor;
        this.setBackground(backgroundColor);
        upateShape(roundShape);
    }

    @Override
    public void setSize(int width, int height) {
        shape.setFrame(borderOffset, borderOffset, width - (double) borderOffset, height - (double) borderOffset);
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
        double zoomFactor = (Boolean) actionsInView.get(SYNCH_CMD) ? view2d.getViewModel().getViewScale()
            : (Double) actionsInView.get(ActionW.ZOOM.cmd());
        zoom(zoomFactor);
    }

    protected PlanarImage getSourceImage() {
        SyncType type = (SyncType) actionsInView.get(ZoomWin.FREEZE_CMD);
        if (SyncType.PARENT_PARAMETERS.equals(type) || SyncType.PARENT_IMAGE.equals(type)) {
            return freezeOperations.getLastNodeOutputImage();
        }

        // return the image before the zoom operation from the parent view
        ImageOpNode node = view2d.getImageLayer().getDisplayOpManager().getNode(AffineTransformOp.OP_NAME);
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
                ActionState zoomAction = manager.getAction(ActionW.LENSZOOM);
                if (zoomAction instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoomAction).setRealValue(view2d.getViewModel().getViewScale());
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            int mods = e.getModifiers();
            if (pickPoint != null && (mods & InputEvent.BUTTON1_MASK) != 0) {
                Point p = e.getPoint();
                int dx = p.x - pickPoint.x;
                int dy = p.y - pickPoint.y;

                switch (cursor) {
                    case Cursor.SE_RESIZE_CURSOR:
                        int nw = pickWidth + dx;
                        int nh = pickHeight + dy;
                        nw = nw < 50 ? 50 : nw > 500 ? 500 : nw;
                        nh = nh < 50 ? 50 : nh > 500 ? 500 : nh;
                        setSize(nw, nh);
                        updateAffineTransform();
                        break;

                    default:
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

            Rectangle rect = new Rectangle(w - 12 - borderOffset, h - 12 - borderOffset, 12, 12);
            if (rect.contains(me.getPoint())) {
                return Cursor.SE_RESIZE_CURSOR;
            }

            return Cursor.MOVE_CURSOR;
        }
    }

    public void disableMouseAndKeyListener() {
        this.removeMouseListener(mouseHandler);
        this.removeMouseMotionListener(mouseHandler);
        this.removeMouseWheelListener((MouseActionAdapter) view2d.getEventManager().getAction(ActionW.LENSZOOM));
    }

    public void enableMouseListener() {
        disableMouseAndKeyListener();
        this.addMouseListener(mouseHandler);
        this.addMouseMotionListener(mouseHandler);
        this.addMouseWheelListener((MouseActionAdapter) view2d.getEventManager().getAction(ActionW.LENSZOOM));
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
        if (ActionW.SYNCH.cmd().equals(command) && value instanceof SynchEvent) {
            if (!(value instanceof SynchCineEvent)) {
                SynchData synchData = (SynchData) view2d.getActionValue(ActionW.SYNCH_LINK.cmd());
                if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
                    return;
                }

                for (Entry<String, Object> entry : ((SynchEvent) value).getEvents().entrySet()) {
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
