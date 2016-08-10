package org.weasis.acquire.dockable.components.actions.annotate.comp;

import java.util.Objects;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;

public class AnnotationActionState {
    private static AnnotationActionState instance = null;

    private AnnotationActionState() {
    }

    public static AnnotationActionState getInstance() {
        if (Objects.isNull(instance)) {
            instance = new AnnotationActionState();
            EventManager eventManager = EventManager.getInstance();

            // Remove actions which are not useful
            eventManager.removeAction(ActionW.SCROLL_SERIES);
            eventManager.removeAction(ActionW.WINDOW);
            eventManager.removeAction(ActionW.LEVEL);
            eventManager.removeAction(ActionW.ROTATION);
            eventManager.removeAction(ActionW.FLIP);
            eventManager.removeAction(ActionW.FILTER);
            eventManager.removeAction(ActionW.INVERSESTACK);
            eventManager.removeAction(ActionW.INVERT_LUT);
            eventManager.removeAction(ActionW.LUT);
            eventManager.removeAction(ActionW.LAYOUT);
            eventManager.removeAction(ActionW.SYNCH);
        }
        return instance;

    }
}
