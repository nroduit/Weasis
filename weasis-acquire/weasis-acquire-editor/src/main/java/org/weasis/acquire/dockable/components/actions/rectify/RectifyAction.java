package org.weasis.acquire.dockable.components.actions.rectify;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.graphics.CropRectangleGraphic;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class RectifyAction extends AbstractAcquireAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RectifyAction.class);

    private static final CropRectangleGraphic CROP_RECTANGLE_GRAPHIC = new CropRectangleGraphic();

    private RectangleGraphic currentCropArea;

    public RectifyAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void init() {
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();
        // Remove the crop before super.init() to get the entire image.
        imageInfo.getPostProcessOpManager().setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);
        imageInfo.getPostProcessOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, 0);
        imageInfo.getPostProcessOpManager().setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, false);
        view.updateCanvas(false);
        view.getActionsInView().remove(DefaultView2d.PROP_LAYER_OFFSET);
        view.resetZoom();

        super.init();

        int rotation = imageInfo.getCurrentValues().getFullRotation() % 360;
        view.getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, rotation - 360);
        view.getDisplayOpManager().setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, imageInfo.getCurrentValues().isFlip());

        view.getGraphicManager().setCreateGraphic(null);
        ImageViewerPlugin container = WinUtil.getParentOfClass(view.getJComponent(), ImageViewerPlugin.class);
        if (container != null) {
            final ViewerToolBar toolBar = container.getViewerToolBar();
            if (toolBar != null) {
                String cmd = ActionW.DRAW.cmd();
                if (!toolBar.isCommandActive(cmd)) {
                    MouseActions mouseActions = EventManager.getInstance().getMouseActions();
                    mouseActions.setAction(MouseActions.LEFT, cmd);
                    container.setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.LEFT, cmd);
                }
            }
        }

        updateCropGraphic();
    }

    protected void updateCropGraphic() {
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();

        RenderedImage img = view.getSourceImage();
        if (img != null) {
            Rectangle2D modelArea = view.getViewModel().getModelArea();
            Rectangle2D area =
                Optional.ofNullable((Rectangle2D) imageInfo.getNextValues().getCropZone()).orElse(modelArea);
            try {
                if (currentCropArea == null) {
                    currentCropArea = CROP_RECTANGLE_GRAPHIC.copy().buildGraphic(area);
                } else {
                    currentCropArea.buildGraphic(area);
                }
                if (!view.getGraphicManager().getModels().contains(currentCropArea)) {
                    AbstractGraphicModel.addGraphicToModel(view, currentCropArea);
                }
                currentCropArea.setSelected(true);

                GeomUtil.growRectangle(modelArea, 15);
                double viewportWidth = view.getJComponent().getWidth() - 1.0;
                double viewportHeight = view.getJComponent().getHeight() - 1.0;
                view.zoom(Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight()));
            } catch (InvalidShapeException e) {
                LOGGER.error("Build crop graphic", e);
            }
        }
    }

    private static Rectangle2D adaptToValidateCropArea(Rectangle2D area) {
        ViewCanvas<ImageElement> view = getView();
        AffineTransform transform = AffineTransform.getScaleInstance(1.0, 1.0);
        buildAffineTransform(transform, view.getDisplayOpManager(), view.getViewModel().getModelArea(),
            (Point) view.getActionValue(DefaultView2d.PROP_LAYER_OFFSET));
        Point2D pMin = new Point2D.Double(area.getMinX(), area.getMinY());
        Point2D pMax = new Point2D.Double(area.getMaxX(), area.getMaxY());
        transform.transform(pMin, pMin);
        transform.transform(pMax, pMax);

        Rectangle2D rect = new Rectangle2D.Double();
        rect.setFrameFromDiagonal(pMin, pMax);
        return rect;
    }

    static Rectangle adaptToinitCropArea(Rectangle2D area) {
        if (area == null)
            return null;
        ViewCanvas<ImageElement> view = getView();
        AffineTransform transform = AffineTransform.getScaleInstance(1.0, 1.0);
        buildAffineTransform(transform, view.getDisplayOpManager(), view.getViewModel().getModelArea(), null);
        Point2D pMin = new Point2D.Double(area.getMinX(), area.getMinY());
        Point2D pMax = new Point2D.Double(area.getMaxX(), area.getMaxY());
        try {
            transform = transform.createInverse();
            transform.transform(pMin, pMin);
            transform.transform(pMax, pMax);
        } catch (NoninvertibleTransformException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Rectangle2D rect = new Rectangle2D.Double();
        rect.setFrameFromDiagonal(pMin, pMax);
        return rect.getBounds();
    }

    private static void buildAffineTransform(AffineTransform transform, OpManager dispOp, Rectangle2D modelArea,
        Point offset) {
        Boolean flip = JMVUtils.getNULLtoFalse(dispOp.getParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP));
        Integer rotationAngle = (Integer) dispOp.getParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE);

        if (rotationAngle != null && rotationAngle != 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            transform.rotate(Math.toRadians(rotationAngle), modelArea.getWidth() / 2.0, modelArea.getHeight() / 2.0);
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
            transform.scale(-1.0, 1.0);
            transform.translate(-modelArea.getWidth(), 0.0);
        }

        if (offset != null) {
            // TODO not consistent with image coordinates after crop
            transform.translate(-offset.getX(), -offset.getY());
        }
    }

    @Override
    public boolean cancel() {
        boolean doCancel = super.cancel();
        updateCropGraphic();
        return doCancel;
    }

    @Override
    public void validate() {
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();
        validate(imageInfo, view);
    }
    
    @Override
    public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
        getImageInfo().removeLayer(view);

        if (view.getImageLayer() instanceof RenderedImageLayer) {
            imageInfo.getCurrentValues().setCropZone(null); // Force dirty value
            imageInfo.getNextValues()
                .setCropZone(adaptToValidateCropArea(currentCropArea.getShape().getBounds()).getBounds());
            imageInfo.applyPostProcess(view);
            view.getImage().setTag(TagW.ThumbnailPath, null);
            Panner panner = view.getPanner();
            if (panner != null) {
                panner.updateImage();
            }
        }
        view.getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, 0);
        view.getDisplayOpManager().setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP, false);
    }

    @Override
    public boolean reset() {
        boolean doReset = super.reset();
        updateCropGraphic();
        return doReset;
    }

    public RectangleGraphic getCurrentCropArea() {
        return currentCropArea;
    }

    public void updateCropDisplay() {
        Optional.ofNullable(currentCropArea).map(CropRectangleGraphic.class::cast).ifPresent(c -> {
            c.updateCropDisplay(getImageInfo());
            updateCropGraphic();
        });
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new RectifyPanel(this);
    }


}
