package org.weasis.acquire.dockable.components.actions.annotate;

import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public class AnnotateAction extends AbstractAcquireAction {

    public AnnotateAction(AcquireActionButtonsPanel panel) {
        super(panel);
    }
    
    @Override
    public void validate() {
        LOGGER.info("validate ANNOTATE"); 
    }

    @Override
    public AcquireActionPanel newCentralPanel() {
        return new AnnotatePanel();
    }
}
