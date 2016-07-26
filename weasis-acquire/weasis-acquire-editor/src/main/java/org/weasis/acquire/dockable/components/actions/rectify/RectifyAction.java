package org.weasis.acquire.dockable.components.actions.rectify;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.ui.model.layer.LayerType;

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
        getView().getGraphicManager().deleteByLayerType(LayerType.ACQUIRE);
    }

    @Override
    public boolean cancel() {
        boolean cancel = super.cancel();

        getView().getGraphicManager().deleteByLayerType(LayerType.ACQUIRE);
        return cancel;
    }

    @Override
    public boolean reset() {
        boolean doReset = super.reset();

        if (doReset) {
            getView().getGraphicManager().deleteByLayerType(LayerType.ACQUIRE);
        }

        return doReset;
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new RectifyPanel();
    }
}
