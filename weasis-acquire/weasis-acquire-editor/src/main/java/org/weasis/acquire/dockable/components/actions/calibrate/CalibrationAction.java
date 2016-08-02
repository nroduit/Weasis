package org.weasis.acquire.dockable.components.actions.calibrate;

import java.util.List;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.graphics.CalibrationGraphic;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class CalibrationAction extends AbstractAcquireAction {
    private static final CalibrationGraphic CALIBRATION_LINE_GRAPHIC = new CalibrationGraphic();

    public CalibrationAction(AcquireActionButtonsPanel panel) {
        super(panel);

    }

    @Override
    public void init() {
        super.init();

        ViewCanvas<ImageElement> view = getView();
        view.getGraphicManager().setCreateGraphic(CALIBRATION_LINE_GRAPHIC);
    }

    @Override
    public void validate() {
        LOGGER.info("validate CALIBRATION");

        List<Graphic> selectedGraphics = getView().getGraphicManager().getSelectedGraphics();
        if (selectedGraphics.isEmpty()) {
            return;
        }

        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
    }

    @Override
    public boolean cancel() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        return true;
    }

    @Override
    public boolean reset() {
        AcquireImageInfo imageInfo = getImageInfo();

        boolean reset = super.reset();

        imageInfo.removeLayer(getView());

        return reset;
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new CalibrationPanel();
    }
}
