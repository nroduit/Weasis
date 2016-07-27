package org.weasis.acquire.dockable.components.actions.meta;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;

public class MetadataAction extends AbstractAcquireAction {

    public MetadataAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void validate() {
        
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new MetadataPanel();
    }

}
