package org.weasis.acquire.dockable.components.actions.rectify;

import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;

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
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.CropOp;
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
        super.init();
        
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();
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

        imageInfo.getPostProcessOpManager().setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);
        view.updateCanvas(false);
        view.getActionsInView().remove(DefaultView2d.PROP_LAYER_OFFSET);
        view.resetZoom();
        imageInfo.applyPreProcess(view);
        
        RenderedImage img = view.getSourceImage();
        if (img != null) {
            Rectangle2D area = null;
            if (imageInfo != null) {
                area = imageInfo.getCurrentValues().getCropZone();
            }
            if (area == null) {
                area = view.getViewModel().getModelArea();
            }
            
            try {
                if(currentCropArea == null){
                    currentCropArea = CROP_RECTANGLE_GRAPHIC.copy().buildGraphic(area);
                }
                else {
                    currentCropArea.buildGraphic(area);
                }
                currentCropArea.setSelected(true);
                AbstractGraphicModel.addGraphicToModel(view, currentCropArea);

                Rectangle2D modelArea = view.getViewModel().getModelArea();
                GeomUtil.growRectangle(modelArea, 15);
                double viewportWidth = view.getJComponent().getWidth() - 1.0;
                double viewportHeight = view.getJComponent().getHeight() - 1.0;
                view.zoom(Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight()));
            } catch (InvalidShapeException e) {
                LOGGER.error("Build crop graphic", e);
            }
        }

    }

    @Override
    public void validate() {
        AcquireImageInfo imageInfo = getImageInfo();
        ViewCanvas<ImageElement> view = getView();
        imageInfo.applyPostProcess(view);
        getImageInfo().removeLayer(view);

        if (getImageLayer() instanceof RenderedImageLayer) {
            imageInfo.getCurrentValues().setCropZone(null); // Force dirty value
            imageInfo.getNextValues().setCropZone(currentCropArea.getShape().getBounds());
            imageInfo.applyPostProcess(view);
            imageInfo.removeLayer(getView());
            view.getImage().setTag(TagW.ThumbnailPath, null);
            Panner panner = view.getPanner();
            if (panner != null) {
                panner.updateImage();
            }
        }
    }

    @Override
    public boolean reset() {
        boolean doReset = super.reset();

        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.applyPreProcess(getView());
        return doReset;
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new RectifyPanel();
    }
}
