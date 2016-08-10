package org.weasis.acquire.dockable.components.actions.rectify;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class RectifyAction extends AbstractAcquireAction {

    public RectifyAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void validate() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.applyPostProcess(getView());
        getImageInfo().removeLayer(getView());
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
