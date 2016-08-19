package org.weasis.acquire.dockable.components.actions.meta;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class MetadataAction extends AbstractAcquireAction {

    public MetadataAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }

    @Override
    public void validate() {
        // Nothing to do
    }

    @Override
    public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
        // Nothing to do
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new MetadataPanel();
    }

}
