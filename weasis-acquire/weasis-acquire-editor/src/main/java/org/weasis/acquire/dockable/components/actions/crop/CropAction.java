package org.weasis.acquire.dockable.components.actions.crop;

import java.util.List;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.graphics.CropRectangleGraphic;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class CropAction extends AbstractAcquireAction {
    private static final CropRectangleGraphic CROP_RECTANGLE_GRAPHIC = new CropRectangleGraphic();

    public CropAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void init() {
        super.init();

        ViewCanvas<ImageElement> view = getView();
        view.getGraphicManager().setCreateGraphic(CROP_RECTANGLE_GRAPHIC);
    }

    @Override
    public void validate() {
        ViewCanvas<ImageElement> view = getView();
        ImageLayer<ImageElement> layer = getImageLayer();

        List<Graphic> selectedGraphics = view.getGraphicManager().getSelectedGraphics();
        if (selectedGraphics.isEmpty()) {
            return;
        }

        if (layer instanceof RenderedImageLayer) {
            AcquireImageInfo imageInfo = getImageInfo();

            imageInfo.getNextValues().setCropZone(selectedGraphics.get(0).getBounds(null));
            imageInfo.applyPostProcess(view);
        }
    }

    @Override
    public boolean cancel() {
        // Special case for crop: the preProcessor must be cleared manually cause the object is not dirty
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        imageInfo.clearPreProcess();
        imageInfo.applyPreProcess(getView());

        return true;
    }

    @Override
    public boolean reset() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        boolean reset = super.reset();
        boolean dirty = imageInfo.isDirtyFromDefault();

        if (!dirty) {
            imageInfo.clearPreProcess();
            imageInfo.applyPreProcess(getView());
        }

        return reset;
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new CropPanel();
    }
}
