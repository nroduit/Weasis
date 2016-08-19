package org.weasis.acquire.dockable.components.actions.calibrate;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class CalibrationAction extends AbstractAcquireAction {

    public CalibrationAction(AcquireActionButtonsPanel panel) {
        super(panel);

    }

    @Override
    public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
        imageInfo.removeLayer(view);
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new CalibrationPanel();
    }
}
