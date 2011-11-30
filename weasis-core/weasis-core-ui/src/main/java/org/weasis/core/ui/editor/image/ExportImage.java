package org.weasis.core.ui.editor.image;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.HashMap;

import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.FlipOperation;
import org.weasis.core.api.image.ImageOperationAction;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.RotationOperation;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.graphic.RenderedImageLayer;

public class ExportImage<E extends ImageElement> extends DefaultView2d {

    private final RenderedImageLayer<E> imageLayer;
    private final HashMap<String, Object> freezeActionsInView = new HashMap<String, Object>();
    private final DefaultView2d<E> view2d;
    private final AnnotationsLayer infoLayer;
    private OperationsManager freezeOperations;
    private Font font;

    public ExportImage(DefaultView2d<E> view2d) {
        super(view2d.eventManager, view2d.getLayerModel(), null);
        this.view2d = view2d;
        this.imageLayer = new RenderedImageLayer<E>(new OperationsManager(this), false);
        this.font = FontTools.getFont10();
        this.infoLayer = view2d.getInfoLayer().getLayerCopy(this);
        OperationsManager operations = imageLayer.getOperationsManager();
        operations.addImageOperationAction(new ZoomOperation());
        operations.addImageOperationAction(new RotationOperation());
        operations.addImageOperationAction(new FlipOperation());

        actionsInView.put(ActionW.ZOOM.cmd(), 1.0);
        actionsInView.put(ActionW.ROTATION.cmd(), view2d.getActionValue(ActionW.ROTATION.cmd()));
        actionsInView.put(ActionW.FLIP.cmd(), view2d.getActionValue(ActionW.FLIP.cmd()));
        actionsInView.put(ActionW.DRAW.cmd(), true);
        actionsInView.put(ZoomWin.FREEZE_CMD, null);

        setPreferredSize(new Dimension(1024, 1024));
        imageLayer.setImage(view2d.getImage(), (OperationsManager) view2d.getActionValue(ActionW.PREPROCESSING.cmd()));
        getViewModel().setModelArea(view2d.getViewModel().getModelArea());
        setFreezeImage(freezeParentParameters());

    }

    private void setFreezeImage(RenderedImage image) {
        actionsInView.put(ZoomWin.FREEZE_CMD, image);
        if (image == null) {
            freezeActionsInView.clear();
            freezeOperations = null;
        }
        imageLayer.updateAllImageOperations();
    }

    @Override
    public E getImage() {
        return imageLayer.getSourceImage();
    }

    @Override
    public RenderedImage getSourceImage() {
        RenderedImage img = (RenderedImage) actionsInView.get(ZoomWin.FREEZE_CMD);
        if (img == null) {
            // return the image before the zoom operation from the parent view
            return view2d.getImageLayer().getOperationsManager().getSourceImage(ZoomOperation.name);
        }
        return img;
    }

    @Override
    public void zoom(double viewScale) {
        if (viewScale == 0.0) {
            final double viewportWidth = getWidth() - 1;
            final double viewportHeight = getHeight() - 1;
            final Rectangle2D modelArea = getViewModel().getModelArea();
            viewScale = Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight());
        }
        super.zoom(viewScale);
        imageLayer.updateImageOperation(ZoomOperation.name);
        updateAffineTransform();
    }

    private RenderedImage freezeParentParameters() {
        OperationsManager pManager = view2d.getImageLayer().getOperationsManager();
        freezeActionsInView.clear();
        view2d.copyActionWState(freezeActionsInView);

        freezeOperations = new OperationsManager(new ImageOperation() {

            @Override
            public RenderedImage getSourceImage() {
                return view2d.getSourceImage();
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

    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            draw((Graphics2D) g);
        }
    }

    @Override
    public void draw(Graphics2D g2d) {
        Stroke oldStroke = g2d.getStroke();
        Paint oldColor = g2d.getPaint();

        g2d.setBackground(Color.black);
        drawBackground(g2d);
        double viewScale = getViewModel().getViewScale();
        double offsetX = getViewModel().getModelOffsetX() * viewScale;
        double offsetY = getViewModel().getModelOffsetY() * viewScale;
        // Paint the visible area
        g2d.translate(-offsetX, -offsetY);
        // Set font size according to the view size
        g2d.setFont(font);

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);
        AnnotationsLayer infoLayer = view2d.getInfoLayer();
        if (infoLayer != null) {
            infoLayer.paint(g2d);
        }
        g2d.translate(offsetX, offsetY);
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

    @Override
    protected void updateAffineTransform() {
        double viewScale = getViewModel().getViewScale();
        affineTransform.setToScale(viewScale, viewScale);

        // Get Rotation and flip from parent (it does not make sens to have different geometric position)
        Boolean flip = (Boolean) view2d.getActionValue(ActionW.FLIP.cmd());
        Integer rotationAngle = (Integer) view2d.getActionValue(ActionW.ROTATION.cmd());
        if (rotationAngle != null && rotationAngle > 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            Rectangle2D imageCanvas = getViewModel().getModelArea();
            affineTransform.rotate(rotationAngle * Math.PI / 180.0, imageCanvas.getWidth() / 2.0,
                imageCanvas.getHeight() / 2.0);
        }
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
            affineTransform.scale(-1.0, 1.0);
            affineTransform.translate(-getViewModel().getModelArea().getWidth(), 0.0);
        }

        try {
            inverseTransform.setTransform(affineTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
        // TODO Auto-generated method stub

    }

    @Override
    public void enableMouseAndKeyListener(MouseActions mouseActions) {
        // TODO Auto-generated method stub

    }
}
