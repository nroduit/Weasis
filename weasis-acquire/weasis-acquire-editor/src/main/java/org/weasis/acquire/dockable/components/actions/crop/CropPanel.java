package org.weasis.acquire.dockable.components.actions.crop;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.image.CropOp;

public class CropPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 1L;

    public CropPanel() {
        add(new JLabel("Draw a rectangle"));
    }
    
    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        AcquireImageInfo imageInfo = AcquireManager.getCurrentAcquireImageInfo();
        imageInfo.getNextValues().setCropZone(values.getCropZone());
        CropOp crop = new CropOp();
        crop.setParam(CropOp.P_AREA, imageInfo.getNextValues().getCropZone());
        imageInfo.removePreProcessImageOperationAction(CropOp.class);
        imageInfo.addPreProcessImageOperationAction(crop);
        imageInfo.applyPreProcess(EventManager.getInstance().getSelectedViewPane());
    }
}
