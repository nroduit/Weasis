/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
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
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.image.RenderedImage;
import java.util.HashMap;

import javax.swing.UIManager;

import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.ImageOperationAction;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.ZoomSetting;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.graphic.ImageLayerChangeListener;
import org.weasis.core.ui.graphic.RenderedImageLayer;
import org.weasis.core.ui.graphic.model.GraphicsPane;
import org.weasis.core.ui.graphic.model.MainLayerModel;

// TODO: Auto-generated Javadoc
/**
 * The Class ZoomWin.
 * 
 * @author Nicolas Roduit
 */
public class ZoomWin<E extends ImageElement> extends GraphicsPane implements ImageOperation, ImageLayerChangeListener {

    public enum SYNCH_TYPE {
        None, ParentImage, ParentParameters
    };

    private final DefaultView2d<E> view2d;
    private RectangularShape shape;
    private int borderOffset = 2;
    private Color lineColor;
    private Color backgroundColor;
    private Stroke stroke;

    public final static String SYNCH_CMD = "synchronize"; //$NON-NLS-1$
    public final static String FREEZE_CMD = "freeze"; //$NON-NLS-1$
    private PopUpMenuOnZoom popup = null;
    private final RenderedImageLayer<E> imageLayer;
    private final MouseHandler mouseHandler;
    private OperationsManager freezeOperations;
    private SYNCH_TYPE type = SYNCH_TYPE.None;
    private final HashMap<String, Object> freezeActionsInView = new HashMap<String, Object>();

    public ZoomWin(DefaultView2d<E> view2d) {
        super(view2d.getLayerModel(), null);
        this.view2d = view2d;
        this.setOpaque(false);
        ImageViewerEventManager<E> manager = view2d.getEventManager();
        this.imageLayer = new RenderedImageLayer<E>(new OperationsManager(this), false);
        OperationsManager operations = imageLayer.getOperationsManager();
        operations.addImageOperationAction(new ZoomOperation());

        ActionState zoomAction = manager.getAction(ActionW.LENSZOOM);
        if (zoomAction instanceof SliderChangeListener) {
            actionsInView.put(ActionW.ZOOM.cmd(),
                manager.sliderValueToViewScale(((SliderChangeListener) zoomAction).getValue()));
        }

        this.popup = new PopUpMenuOnZoom(this);
        this.popup.setInvoker(this);
        this.setCursor(MainLayerModel.MOVE_CURSOR);

        ZoomSetting z = manager.getZoomSetting();
        actionsInView.put(SYNCH_CMD, z.isLensSynchronize());
        actionsInView.put(ActionW.DRAW.cmd(), z.isLensShowDrawings());
        actionsInView.put(FREEZE_CMD, null);
        actionsInView.put(ZoomOperation.INTERPOLATION_CMD, z.getInterpolation());

        Color bckColor = UIManager.getColor("Panel.background"); //$NON-NLS-1$
        this.setLensDecoration(z.getLensLineWidth(), z.getLensLineColor(), bckColor, z.isLensRound());
        this.setSize(z.getLensWidth(), z.getLensHeight());
        this.setLocation(-1, -1);
        this.imageLayer.addLayerChangeListener(this);
        this.mouseHandler = new MouseHandler();
    }

    public void setActionInView(String action, Object value) {
        if (action != null) {
            actionsInView.put(action, value);
        }
    }

    private void refreshZoomWin() {
        imageLayer.setImage(view2d.getImage());
        getViewModel().setModelArea(view2d.getViewModel().getModelArea());
        Point loc = getLocation();
        if ((loc.x == -1 && loc.y == -1)) {
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

    public void showLens(boolean val) {
        if (val) {
            refreshZoomWin();
            enableMouseListener();
            setVisible(true);
        } else {
            setVisible(false);
            disableMouseAndKeyListener();
        }
    }

    public void centerZoomWin() {
        int magPosx = ((view2d.getWidth() / 2) - (getWidth() / 2));
        int magPosy = ((view2d.getHeight() / 2) - (getHeight() / 2));
        setLocation(magPosx, magPosy);
    }

    public void hideZoom() {
        ActionState lens = view2d.getEventManager().getAction(ActionW.LENS);
        if (lens instanceof ToggleButtonListener) {
            ((ToggleButtonListener) lens).setSelected(false);
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
        double viewScale = getViewModel().getViewScale();
        double offsetX = getViewModel().getModelOffsetX() * viewScale;
        double offsetY = getViewModel().getModelOffsetY() * viewScale;
        // Paint the visible area
        g2d.translate(-offsetX, -offsetY);
        // Set font size according to the view size
        g2d.setFont(getFont());

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);

        g2d.translate(offsetX, offsetY);

        g2d.setClip(oldClip);
        g2d.setStroke(stroke);
        g2d.setPaint(lineColor);
        Rectangle bound = getBounds();
        g2d.drawRect(bound.width - 12 - borderOffset, bound.height - 12 - borderOffset, 12, 12);
        g2d.draw(shape);
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
    }

    @Override
    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        if ((Boolean) actionsInView.get(ActionW.DRAW.cmd())) {
            getLayerModel().draw(g2d, transform, inverseTransform);
        }
    }

    private void drawBackground(Graphics2D g2d) {
        g2d.clearRect(0, 0, getWidth(), getHeight());
    }

    protected void updateAffineTransform() {
        double viewScale = getViewModel().getViewScale();
        // Get Rotation and flip from parent (it does not make sens to have different geometric position)
        Boolean flip = (Boolean) view2d.getActionValue(ActionW.FLIP.cmd());
        if (flip != null && flip) {
            // Using only one allows to enable or disable flip with the rotation action

            // case FlipMode.TOP_BOTTOM:
            // at = new AffineTransform(new double[] {1.0,0.0,0.0,-1.0});
            // at.translate(0.0, -imageHt);
            // break;
            // case FlipMode.LEFT_RIGHT :
            // at = new AffineTransform(new double[] {-1.0,0.0,0.0,1.0});
            // at.translate(-imageWid, 0.0);
            // break;
            // case FlipMode.TOP_BOTTOM_LEFT_RIGHT:
            // at = new AffineTransform(new double[] {-1.0,0.0,0.0,-1.0});
            // at.translate(-imageWid, -imageHt);
            affineTransform.setToScale(-viewScale, viewScale);
            affineTransform.translate(-getViewModel().getModelArea().getWidth(), 0.0);
        } else {
            affineTransform.setToScale(viewScale, viewScale);
        }

        Integer rotationAngle = (Integer) view2d.getActionValue(ActionW.ROTATION.cmd());
        if (rotationAngle != null && rotationAngle > 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            Rectangle2D imageCanvas = getViewModel().getModelArea();
            affineTransform.rotate(rotationAngle * Math.PI / 180.0, imageCanvas.getWidth() / 2.0,
                imageCanvas.getHeight() / 2.0);
        }
        try {
            inverseTransform.setTransform(affineTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
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
        shape.setFrame(borderOffset, borderOffset, width - borderOffset, height - borderOffset);
        super.setSize(width + borderOffset, height + borderOffset);
    }

    // public void applyPreferences(Preferences prefs) {
    // if (prefs != null) {
    // Preferences p = prefs.node(ZoomWin.PREFERENCE_NODE);
    // showDrawings = p.getBoolean(P_SHOW_DRAWINGS, showDrawings);
    // synchronize = p.getBoolean(P_ZOOM_SYNCH, synchronize);
    // round = p.getBoolean(P_ROUND, round);
    // }
    // }
    //
    // public void savePreferences(Preferences prefs) {
    // if (prefs != null) {
    // Preferences p = prefs.node(ZoomWin.PREFERENCE_NODE);
    // BundlePreferences.putBooleanPreferences(p, P_SHOW_DRAWINGS, showDrawings);
    // BundlePreferences.putBooleanPreferences(p, P_ZOOM_SYNCH, synchronize);
    // BundlePreferences.putBooleanPreferences(p, P_ROUND, round);
    // }
    // }

    private void upateShape(boolean round) {
        if (round) {
            shape = new java.awt.geom.Ellipse2D.Double(getX(), getY(), getWidth(), getHeight());
        } else {
            shape = new java.awt.geom.Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
        }
    }

    public double getCenterX() {
        return view2d.viewToModelX(getX() + (getWidth() - 1) * 0.5);
    }

    public double getCenterY() {
        return view2d.viewToModelY(getY() + (getHeight() - 1) * 0.5);
    }

    @Override
    public void zoom(double viewScale) {
        actionsInView.put(ActionW.ZOOM.cmd(), viewScale);
        super.zoom(getCenterX(), getCenterY(), Math.abs(viewScale));
        imageLayer.updateImageOperation(ZoomOperation.name);
        updateAffineTransform();
    }

    public void updateZoom() {
        double zoomFactor =
            (Boolean) actionsInView.get(SYNCH_CMD) ? view2d.getViewModel().getViewScale() : (Double) actionsInView
                .get(ActionW.ZOOM.cmd());
        zoom(zoomFactor);
    }

    @Override
    public E getImage() {
        return imageLayer.getSourceImage();
    }

    public RenderedImage getSourceImage() {
        RenderedImage img = (RenderedImage) actionsInView.get(ZoomWin.FREEZE_CMD);
        if (img == null) {
            // return the image before the zoom operation from the parent view
            return view2d.getImageLayer().getOperationsManager().getSourceImage(ZoomOperation.name);
        }
        return img;
    }

    public void setFreezeImage(RenderedImage image, SYNCH_TYPE type) {
        this.type = type;
        actionsInView.put(ZoomWin.FREEZE_CMD, image);
        if (image == null) {
            freezeActionsInView.clear();
            freezeOperations = null;
            type = SYNCH_TYPE.None;
        }
        imageLayer.updateAllImageOperations();
    }

    RenderedImage freezeParentImage() {
        OperationsManager pManager = view2d.getImageLayer().getOperationsManager();
        freezeActionsInView.clear();
        view2d.copyActionWState(freezeActionsInView);
        final E image = view2d.getImage();

        freezeOperations = new OperationsManager(new ImageOperation() {

            @Override
            public RenderedImage getSourceImage() {
                ImageElement image = getImage();
                if (image == null) {
                    return null;
                }
                return image.getImage();
            }

            @Override
            public ImageElement getImage() {
                return image;
            }

            @Override
            public Object getActionValue(String action) {
                if (action == null) {
                    return null;
                }
                return freezeActionsInView.get(action);
            }
        });
        for (ImageOperationAction op : pManager.getOperations()) {
            try {
                if (!ZoomOperation.name.equals(op.getOperationName())) {
                    ImageOperationAction operation = (ImageOperationAction) op.clone();
                    freezeOperations.addImageOperationAction(operation);
                }
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }
        return freezeOperations.updateAllOperations();
    }

    RenderedImage freezeParentParameters() {
        OperationsManager pManager = view2d.getImageLayer().getOperationsManager();
        freezeActionsInView.clear();
        view2d.copyActionWState(freezeActionsInView);

        freezeOperations = new OperationsManager(new ImageOperation() {

            @Override
            public RenderedImage getSourceImage() {
                ImageElement image = view2d.getImage();
                if (image == null) {
                    return null;
                }
                return image.getImage();
            }

            @Override
            public ImageElement getImage() {
                return view2d.getImage();
            }

            @Override
            public Object getActionValue(String action) {
                if (action == null) {
                    return null;
                }
                return freezeActionsInView.get(action);
            }
        });
        for (ImageOperationAction op : pManager.getOperations()) {
            try {
                if (!ZoomOperation.name.equals(op.getOperationName())) {
                    ImageOperationAction operation = (ImageOperationAction) op.clone();
                    freezeOperations.addImageOperationAction(operation);
                }
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }
        return freezeOperations.updateAllOperations();
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
                ImageViewerEventManager manager = view2d.getEventManager();
                ActionState zoomAction = manager.getAction(ActionW.LENSZOOM);
                if (zoomAction instanceof SliderChangeListener) {
                    ((SliderChangeListener) zoomAction).setValue(manager.viewScaleToSliderValue(view2d.getViewModel()
                        .getViewScale()));
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
                        zoom(getCenterX(), getCenterY(), getViewModel().getViewScale());
                        break;

                    default:
                        setLocation(getX() + dx, getY() + dy);
                        zoom(getCenterX(), getCenterY(), getViewModel().getViewScale());
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

    public DefaultView2d<E> getView2d() {
        return view2d;
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
        repaint();
    }

    public void setCommandFromParentView(String command, Object value) {
        if (ActionW.SCROLL_SERIES.cmd().equals(command)) {
            if (freezeOperations == null) {
                // when image of parent has changed process some update
                refreshZoomWin();
            } else if (SYNCH_TYPE.ParentParameters.equals(type)) {
                refreshZoomWin();
                setFreezeImage(freezeOperations.updateAllOperations(), type);
            }
        } else if (freezeOperations != null
            && (ActionW.ROTATION.cmd().equals(command) || ActionW.FLIP.cmd().equals(command))) {
            freezeActionsInView.put(command, value);
            setFreezeImage(freezeOperations.updateAllOperations(), type);
        }
    }

}
